#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <curses.h>
#include <term.h>
#include <readline/readline.h>
#include <readline/history.h>
#include <string.h>
#include <unistd.h>
#include <netdb.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <dirent.h>
#include <pthread.h>

#include "gt.h"

int load_root ();
char * stripwhite (char *string);
int new_xfer (char *name, char *key);
void * get_thread (void *args);
FILE * fcp_connect ();

char ** generator (char *text, int start, int end);
char * cmd_generator (char *text, int state);
char * name_generator (char *text, int state);
char * dummy_generator (char *text, int state) {return NULL;}

typedef struct {
    char *name;
    Function *func;
    char *doc;
} command;

int com_help(), com_cd(), com_ls(), com_state(), com_get(), com_get(), com_add(), com_quit();

command commands[] = {
    {"help",  com_help,  "Display this text."},
    {"cd",    com_cd,    "Change to another directory."},
    {"ls",    com_ls,    "List the contents of the current directory."},
    {"xfers", com_state, "Display the state of current transfers."},
    {"get",   com_get,   "Launch a Freenet request for a file."},
    {"add",   com_add,   "Check out a new tree from a Freenet URI."},
    {"quit",  com_quit,  "Quit."},
    {NULL, NULL, NULL}
};

char home[256];
char **catalogs; // the catalogs at root
gremlin_tree *gt; // the current gremlin tree
gt_entity *ls; // the contents of the current dir in the tree

typedef struct {
    char *name, *key;
    long start_time, size, read;
} xfer;

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
int xfer_count;
xfer **xfers; // active downloads (interspersed with nulls from xfers completing)

int
main (int argc, char **argv)
{
    int i, retval;
    char *line, cmd[256];
    rl_attempted_completion_function = (CPPFunction *) generator;
    rl_completion_entry_function = (Function *) dummy_generator;
    if (load_root() != 0) return 1;
    xfer_count = 0;
    while (1) {
	line = readline("[gremlin] ");
	if (!line) {
	    printf("\n");
	    return 0;
	}
	if (strlen(line)) {
	    retval = -1;
	    add_history(line);
	    for (i = 0 ; commands[i].name ; i++) {
		sscanf(line, "%s", cmd);
	        if (strcmp(cmd, commands[i].name) == 0) {
		    retval = commands[i].func(&line[strlen(cmd)+1]);
		    break;
		}
	    }
	    if (retval == -1)
		printf("Unknown command: %s\n", cmd);
	}
	free(line);
    }
}

int
load_root ()
{
    int i, n;
    struct dirent **namelist;
    sprintf(home, "%s/.gremlin", getenv("HOME"));
    gt = NULL;
    n = scandir(home, &namelist, 0, alphasort);
    catalogs = calloc(n - 1, sizeof(char *));
    for (i = 2 ; i < n ; i++) {
	catalogs[i - 2] = strdup(namelist[i]->d_name);
	free(namelist[n]);
    }
    catalogs[n - 2] = NULL;
    free(namelist);
    return 0;
}

char *
stripwhite (char *string)
{
    char *s, *t;
    for (s = string; whitespace(*s); s++);
    if (*s == 0) return s;
    t = s + strlen (s) - 1;
    while (t > s && whitespace(*t)) t--;
    *++t = '\0';
    return s;
}

char **
generator (char *text, int start, int end)
{
    if (start == 0) return completion_matches(text, cmd_generator);
    return completion_matches(text, name_generator);
}

char *
cmd_generator (char *text, int state)
{
    int i;
    for (i = state ; commands[i].name ; i++)
	if (strncmp(commands[i].name, text, strlen(text)) == 0)
	    return strdup(commands[i].name);
    return NULL;
}

char *
name_generator (char *text, int state)
{
    int i, len = strlen(text);
    if (!gt) { // list the root catalogs
	for (i = state ; catalogs[i] ; i++) {
	    if (strncmp(catalogs[i], text, len) == 0)
		return strdup(catalogs[i]);
	}
	return NULL;
    }
    for (i = state ; ls[i].name ; i++)
	if (strncmp(ls[i].name, text, len) == 0)
	    return strdup(ls[i].name);
    return NULL;
}

int
com_help(char *arg)
{
    int i;
    printf("\n");
    for (i = 0 ; commands[i].name ; i++)
	printf("%-10s%s\n", commands[i].name, commands[i].doc);
    printf("\n");
    return 0;
}

int
com_cd (char *arg)
{
    FILE *in;
    char path[256];
    int i, status;
    arg = stripwhite(arg);
    if (strlen(arg) == 0 || strcmp(arg, "/") == 0
	    || (strcmp(arg, "..") == 0 && gt && gt->depth == 0)) { // cd to root
	if (ls) gt_free(ls);
	if (gt) free(gt);
	gt = NULL;
	return 0;
    }
    if (!gt) { // open a catalog
        for (i = 0 ; catalogs[i] ; i++) {
	    if (strcmp(catalogs[i], arg) == 0) {
		sprintf(path, "%s/%s", home, catalogs[i]);
		in = fopen(path, "r");
		if (!in) {
		    printf("Can't open catalog %s!\n", catalogs[i]);
		    return 1;
		}
		gt = malloc(sizeof(gremlin_tree));
		status = gt_init(gt, in);
		if (status != 0) {
		    printf("Invalid catalog: %s!\n", catalogs[i]);
		    free(gt); gt = NULL;
		    return 1;
		}
		ls = gt_ls(gt);
		return 0;
	    }
	}
	printf("%s: No such file or directory.\n", arg);
	return 1;
    }
    if (strcmp(arg, "..") == 0) { // back up
	gt_cd(gt, "..", NULL);
	gt_free(ls);
	ls = gt_ls(gt);
	return 0;
    }
    for (i = 0 ; ls[i].name ; i++) { // normal cd
	if (strcmp(ls[i].name, arg) == 0) {
	    if (ls[i].type != GT_DIR) {
		printf("%s: Not a directory.\n", ls[i].name);
		return 1;
	    }
	    gt_cd(gt, ls[i].name, ls[i].data);
	    gt_free(ls);
	    ls = gt_ls(gt);
	    return 0;
	}
    }
    printf("%s: No such file or directory.\n", arg);
    return 1;
}

int
com_ls (char *arg)
{
    int i, c, width = 0;
    if (!gt) { // list the root catalogs
	for (i = 0 ; catalogs[i] ; i++)
	    if (width < strlen(catalogs[i]))
		    width = strlen(catalogs[i]) + 3;
	c = 130/width; if (c <= 0) c = 1;
	for (i = 0 ; catalogs[i] ; i++) {
	    printf("\033[1;31;40m%-*s\033[m", width, catalogs[i]);
	    if (i && catalogs[i+1] && (i+1)%c == 0) printf("\n");
	}
	printf("\n");
	return 0;
    }
    for (i = 0 ; ls[i].name ; i++) // list a tree directory
	if (width < strlen(ls[i].name)) width = strlen(ls[i].name) + 2;
    c = 130/width; if (c <= 0) c = 1;
    for (i = 0 ; ls[i].name ; i++) {
	if (ls[i].type == GT_DIR) printf("\033[1;34;40m");
	printf("%-*s", width, ls[i].name);
	if (ls[i].type == GT_DIR) printf("\033[m");
	if (i && ls[i+1].name && (i+1)%c == 0) printf("\n");
    }
    printf("\n");
    return 0;
}

int
com_state (char *arg)
{
    int i, len = 0, count = 0;
    double kbps, percent;
    long remtime;
    pthread_mutex_lock(&mutex);
    for (i = 0 ; i < xfer_count ; i++) {
	if (xfers[i] && strlen(xfers[i]->name) > len) {
	    count++;
	    len = strlen(xfers[i]->name);
	}
    }
    if (!count) {
	printf("No active transfers.\n");
	goto end;
    }
    printf("\033[31;40m%-*s  %% Complete  K/s      Time Remaining\033[m\n", len, "Name");
    for (i = 0 ; i < xfer_count ; i++) {
	if (!xfers[i]) continue;
	kbps = xfers[i]->size ? (double) (xfers[i]->read / 1000)
	    / (time(NULL) - xfers[i]->start_time) : 0;
	percent = xfers[i]->size ? (double) xfers[i]->read / xfers[i]->size * 100 : 0;
	remtime = xfers[i]->size ? (xfers[i]->size - xfers[i]->read) / 1000 / kbps : 356400;
	if (remtime < 0 || remtime > 356400) remtime = 356400;
	printf("%-*s  %05.2f%%      %-7.2f  %02d:%02d:%02d\n",
		len, xfers[i]->name, percent, kbps, (int) remtime / 3600,
		(int) (remtime % 3600) / 60, (int) remtime % 60);
    }
end:
    pthread_mutex_unlock(&mutex);
    return 0;
}

int
com_get (char *arg)
{
    int i;
    arg = stripwhite(arg);
    if (!gt) { // we're in the root, no way
	printf("%s: Not a regular file.\n", arg);
	return 1;
    }
    for (i = 0 ; ls[i].name ; i++) {
	if (strcmp(ls[i].name, arg) == 0) {
	    if (ls[i].type == GT_DIR) {
		printf("%s: Not a regular file.\n", arg);
		return 1;
	    }
	    new_xfer(ls[i].name, ls[i].data);
	    return 0;
	}
    }
    printf("%s: No such file or directory.\n", arg);
    return 1;
}

int
new_xfer (char *name, char *key)
{
    int i, *arg = malloc(sizeof(int));
    pthread_t thread;
    xfer *x = malloc(sizeof(xfer));
    x->name = strdup(name);
    x->key = strdup(key);
    x->start_time = time(NULL);
    x->read = 0;
    x->size = 0; // not started

    for (i = 0 ; i < xfer_count ; i++) { // insert this xfer at the first null location
	if (!xfers[i]) {
	    xfers[i] = x;
	    *arg = i;
	    pthread_create(&thread, NULL, get_thread, arg);
	    return 0;
	}
    }
    
    *arg = xfer_count;
    xfers = realloc(xfers, sizeof(xfer) * (xfer_count + 1));
    xfers[xfer_count++] = x; // append
    pthread_create(&thread, NULL, get_thread, arg);
    return 0;
}

void *
get_thread (void *args)
{
    xfer *x = xfers[*(int *)args]; // where am I?
    char line[128];
    int status, mlen = 0, len = 0, c, read = 0;
    FILE *sock = fcp_connect(), *out, *temp = tmpfile();
    
    out = fopen(x->name, "w");
    if (!out) {
	rl_save_prompt();
	rl_message("%s: Can't open %s for writing!", x->name);
	rl_restore_prompt();
	goto goodbye;
    }

    if (!temp) {
	rl_save_prompt();
	rl_message("%s: Can't open temp file for writing!", x->name);
	rl_restore_prompt();
	fclose(out);
	unlink(x->name);
	goto goodbye;
    }
    
    if (!sock) {
	rl_save_prompt();
	rl_message("%s: Connecting to Freenet node failed.", x->name);
	rl_restore_prompt();
	fclose(temp);
	fclose(out);
	unlink(x->name);
	goto goodbye;
    }

    fprintf(sock, "ClientGet\nURI=freenet:CHK@%s\nHopsToLive=%x\nEndMessage\n", x->key, 3);
    fgets(line, 128, sock);
    if (strncmp(line, "DataFound", 9) != 0) {
	rl_save_prompt();
	rl_message("%s: Data not found in Freenet.", x->name);
	rl_restore_prompt();
	fclose(out);
	unlink(x->name);
	fclose(temp);
	goto goodbye;
    }
    for (c = 0 ; c < 2 ; c++) {
	fgets(line, 128, sock);
	sscanf(line, "MetadataLength=%x", &mlen);
	sscanf(line, "DataLength=%x", &len);
    }
    fgets(line, 128, sock);
    if (strncmp(line, "EndMessage", 10) != 0) goto readfail;
    if (len <= 0 || mlen < 0) goto readfail;

    // transfer started.
    pthread_mutex_lock(&mutex);
    x->size = len;
    pthread_mutex_unlock(&mutex);
    
    len -= mlen;
    while (mlen > 0) {
	fscanf(sock, "DataChunk\nLength=%x\nData", &status);
	fgetc(sock);
	if (status <= 0) goto readfail;
	while (status--) {
	    c = fgetc(sock);
	    if (c == EOF) goto readfail;
	    if (mlen-- < 0) fputc(c, out);
	    else fputc(c, temp);
	    if (read++ % 32 == 0) {
		pthread_mutex_lock(&mutex);
		x->read = read;
		pthread_mutex_unlock(&mutex);
	    }
	}
    }
    len += mlen;
    fclose(temp);

    while (len > 0) {
	fscanf(sock, "DataChunk\nLength=%x\nData", &status);
	if (status <= 0) goto readfail;
	fgetc(sock);
	len -= status;
	while (status--) {
	    c = fgetc(sock);
	    if (c == EOF) goto readfail;
	    fputc(c, out);
	    if (read++ % 32 == 0) {
		pthread_mutex_lock(&mutex);
		x->read = read;
		pthread_mutex_unlock(&mutex);
	    }
	}
    }
    
    rl_save_prompt();
    rl_message("%s: Transfer successful!", x->name);
    rl_restore_prompt();
    goto goodbye;
    
readfail:
    fclose(temp);
    fclose(out);
    unlink(x->name);
    rl_save_prompt();
    rl_message("%s: Request failed with read error from Freenet node.", x->name);
    rl_restore_prompt();

goodbye:
    pthread_mutex_lock(&mutex);
    free(x->name);
    free(x->key);
    free(x);
    xfers[*(int *)args] = NULL;
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

FILE *
fcp_connect ()
{
    struct in_addr addr;
    struct sockaddr_in address;
    struct servent *serv;
    int connected_socket, connected;
    serv = getservbyname("fcp", "tcp");
    if (!serv) return NULL;
    addr.s_addr = inet_addr("127.0.0.1");
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = (serv->s_port);
    address.sin_addr.s_addr = addr.s_addr;
    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket, (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) return NULL;
    return fdopen(connected_socket, "w+");
}

int
com_add (char *arg)
{
    return 0;
}

int
com_quit (char *arg)
{
    exit(0);
}

