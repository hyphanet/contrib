#define LISTEN_PORT               6666
#define DATABASE_SYNC_INTERVAL    60
#define RECENT_ADDITIONS_LENGTH   48
#define FPROXY_ADDRESS            "http://localhost:8081/"
#define ACCEPT_THREADS		  16
#define _GNU_SOURCE // rwlocks! yay!

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <limits.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <ctype.h>
#include <pthread.h>

pthread_rwlock_t lock = PTHREAD_RWLOCK_INITIALIZER;

struct item {
    char *key;
    char *name;
    char *desc;
    long ctime;
    struct item *next;
} *database = NULL, *recent_additions[RECENT_ADDITIONS_LENGTH];

int listening_socket ();                   // Create and bind a new listening socket.
int load_key_database ();                  // Load key database.
void * accept_thread (void *arg);          // Accept and run transactions.
void run (int conn);                       // Handle a HTTP connection.
void send_index (FILE *socket);            // Send the default page.
void run_search (FILE *socket, char *url); // Run a search.
void run_add (FILE *socket, char *url);    // Add a Freenet URI from CGI GET input.
void send_data (FILE *socket);             // Send the entire database as flat HTML.
void send_error_404 (FILE *socket);        // Send a 404 file not found error message.
int url_decode (char *url);                // URL decode.

int
main ()
{
    int i, socket = listening_socket();
    pthread_t t;
    
    if (socket == -1) {
	fprintf(stderr, "Error initializing listening socket.\n");
	exit(1);
    }

    if (load_key_database() == -1) {
	fprintf(stderr, "Error loading key database.\n");
	exit(1);
    }
    
    signal(SIGPIPE, SIG_IGN);

    for (i = 0 ; i < ACCEPT_THREADS ; i++) {
	pthread_create(&t, NULL, accept_thread, (void *) &socket);
	pthread_detach(t);
    }
    
    for (;;) {
	FILE *data;
	struct item *i;
	sleep(DATABASE_SYNC_INTERVAL);
	data = fopen("beable_database", "w");
	if (!data) {
	    fprintf(stderr, "Error opening database for write.\n");
	    continue;
	}
	pthread_rwlock_wrlock(&lock);
	for (i = database; i ; i = i->next)
	    fprintf(data, "%s\n%s\n%s\n%lx\nEND\n",
		    i->key, i->name, i->desc, i->ctime);
	pthread_rwlock_unlock(&lock);
	fclose(data);
    }
}

int
listening_socket ()
{
    struct sockaddr_in addr;
    int r = 1, sock;
    
    memset((char *) &addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(LISTEN_PORT);
    addr.sin_addr.s_addr = htonl(INADDR_ANY);

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	return -1;
    
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *) &r, sizeof(r));
    
    if (bind(sock, (struct sockaddr *) &addr, sizeof(addr)) < 0)
	return -1;
    
    if (listen(sock, SOMAXCONN) < 0)
	return -1;

    return sock;
}

int
load_key_database ()
{
    int i;
    long ctime, last;
    struct item *f, *tail = NULL;
    char key[256], name[256], desc[1024], end[256];
    FILE *data = fopen("beable_database", "r");
    
    while (data && !feof(data)) {
	i = fscanf(data, "%[^\n]\n%[^\n]\n%[^\n]\n%lx\n%[^\n]\n",
		   key, name, desc, &ctime, end);
	if (i != 5 || strcmp(end, "END") != 0)
	    return -1;
	if (!database) {
	    database = malloc(sizeof(struct item));
	    database->key = strdup(key);
	    database->name = strdup(name);
	    database->desc = strdup(desc);
	    database->ctime = ctime;
	    database->next = NULL;
	    tail = database;
	} else {
	    tail->next = malloc(sizeof(struct item));
	    tail->next->key = strdup(key);
	    tail->next->name = strdup(name);
	    tail->next->desc = strdup(desc);
	    tail->next->ctime = ctime;
	    tail->next->next = NULL;
	    tail = tail->next;
	}
    }
    
    for (i = 0 ; i < RECENT_ADDITIONS_LENGTH ; i++)
	recent_additions[i] = NULL;
    
    last = time(NULL);
    for (i = 0 ; i < RECENT_ADDITIONS_LENGTH ; i++) {
	for (ctime = 0, f = database ; f ; f = f->next) {
	    if (f->ctime > ctime && f->ctime < last) {
		recent_additions[i] = f;
		ctime = f->ctime;
	    }
	}
	if (!ctime) break;
	last = ctime;
    }

    if (data) fclose(data);
    return 0;
}

void *
accept_thread (void *arg)
{
    int conn;
    
    for (;;)
	if ((conn = accept(*(int *)arg, NULL, 0)) != -1)
	    run(conn);
}

void
run (int conn)
{
    FILE *socket;
    char buf[1024], method[5], url[512];
    
    if (!(socket = fdopen(conn, "r+")))
	return;
    
    if (!fgets(buf, 1024, socket))
	goto end;
    
    if (sscanf(buf, "%4s %511s", method, url) != 2)
	goto end;
    
    if (strcmp(method, "GET") != 0)
	goto end;

    while (fgets(buf, 1024, socket))
	if (buf[0] == '\n' || buf[0] == '\r')
	    break;
    
    url_decode(url);
    if (!strcmp(url, "/"))                send_index(socket);
    else if (!strncmp(url, "/search", 7)) run_search(socket, url);
    else if (!strncmp(url, "/add", 4))    run_add(socket, url);
    else if (!strncmp(url, "/data", 5))   send_data(socket);
    else                                  send_error_404(socket);

end:
    fclose(socket);
}

void
send_index (FILE *socket)
{
    char one[]=
	"HTTP/1.1 200 OK"
	"\nContent-Type: text/html"
	"\nCache-control: no-cache"
	"\n"
	"\n<html>"
	"\n<head><title>Beable</title></head>"
	"\n<body link=red vlink=red alink=red bgcolor=white>"
	"\n"
	"\n<center>"
	"\n<h1>Beable!</h1>"
	"\n<table cellspacing=0 cellpadding=0>"
	"\n  <tr align=center>"
	"\n    <td>"
	"\n      <form action=\"/search\" method=get>"
	"\n	   <input type=text name=1 size=55 maxlength=255>"
	"\n	   <input type=submit value=\"Search\">"
	"\n      </form>"
	"\n    </td>"
	"\n  </tr>"
	"\n  <tr align=center>"
	"\n    <td>"
	"\n      <a href=\"http://freenetproject.org/\">Get Freenet</a>"
	"\n      || <a href=\"/add\">Add A Link</a>"
	"\n      || <a href=\"/data\">Download Database</a>"
	"\n    </td>"
	"\n  </tr>"
	"\n</table>"
	"\n"
	"\n<p><table border=1>"
	"\n  <tr>"
	"\n    <td><b>Name</b></td>"
	"\n    <td><b>Description</b></td>"
	"\n  </tr>\n";
    
    char two[]=
	"</table>"
	"\n</center>"
	"\n</body>"
	"\n</html>\n";
    
    int i;
    
    fputs(one, socket);
    pthread_rwlock_rdlock(&lock);
    for (i = 0 ; recent_additions[i] && i < RECENT_ADDITIONS_LENGTH ; i++)
	fprintf(socket, "  <tr>"
			"\n    <td><a href=\"%s%s\">%s</a></td>"
			"\n    <td>%s</td>"
			"\n  </tr>\n",
			FPROXY_ADDRESS,
			recent_additions[i]->key,
			recent_additions[i]->name,
			recent_additions[i]->desc);
    pthread_rwlock_unlock(&lock);
    fputs(two, socket);
}

void
run_search (FILE *socket, char *url)
{
    char one[]=
	"HTTP/1.1 200 OK"
	"\nContent-Type: text/html"
	"\n"
	"\n<html>"
	"\n<head><title>Search Results</title></head>"
	"\n<body link=red vlink=red alink=red bgcolor=white>"
	"\n<center>"
	"\n"
	"\n<h1>Search Results</h1>"
	"\n<p><table border=1>"
	"\n  <tr>"
	"\n    <td><b>Name</b></td>"
	"\n    <td><b>Description</b></td>"
	"\n  </tr>\n";
    
    char two[]=
	"</table>"
	"\n</center>"
	"\n</body>"
	"\n</html>\n";
    
    struct item *i;
    char *p, *query, *search[32];
    int m, n;
    
    if (strncmp(url, "/search?1=", 10) != 0) {
        send_error_404(socket);
        return;
    }

    n = 0;
    p = query = &url[10];
    while (*query++) {
	if (*p == ' ') {
	    p++;
	    continue;
	}
	if (*p == '"') {
	    p++;
	    while (*++query)
	       if (*query == '"')
		   break;
	    if (!*query)
		break;
	    *query = ' ';
	}
	if (*query == ' ') {
	    *query = 0;
	    search[n++] = p;
	    p = ++query;
	}
	if (n == 31)
	    break;
    }
    if (*p)
        search[n++] = p;
    
    for (m = 0 ; m < n ; m++) {
	int l = strlen(search[m]);
	while (l--)
	    search[m][l] = tolower(search[m][l]);
    }
	
    
    fputs(one, socket);
    pthread_rwlock_rdlock(&lock);
    for (i = database ; i ; i = i->next) {
	int nlen = strlen(i->name);
	int dlen = strlen(i->desc);
	char name[nlen], desc[dlen];
	while (nlen--)
	    name[nlen] = tolower(i->name[nlen]);
	while (dlen--)
	    desc[dlen] = tolower(i->desc[dlen]);
	for (m = 0 ; m < n ; m++)
	    if (!strstr(name, search[m]) && !strstr(desc, search[m]))
		    break;
	if (m != n)
	    continue;
        fprintf(socket, "  <tr>"
	                "\n    <td><a href=\"%s%s\">%s</a></td>"
			"\n    <td>%s</td>"
			"\n  </tr>\n",
			FPROXY_ADDRESS,
			i->key,
			i->name,
			i->desc);
    }
    pthread_rwlock_unlock(&lock);
    fputs(two, socket);
}

void
run_add (FILE *socket, char *url)
{
    char form[]=
	"HTTP/1.1 200 OK"
	"\nContent-Type: text/html"
	"\n"
	"\n<html>"
	"\n<head><title>Beable</title></head>"
	"\n<body link=red vlink=red alink=red bgcolor=white>"
	"\n"
	"\n<center>"
	"\n<h1>Beable!</h1>"
	"\n<form action=\"/add\" method=get>"
	"\n  <p>Freenet URI: <input type=text name=1 size=55 maxlength=255>"
	"\n  <p>Short Name:  <input type=text name=2 size=55 maxlength=255>"
	"\n  <p>Description: <textarea cols=55 rows=3 name=3></textarea>"
	"\n  <p><input type=submit value=\"Add Link\">"
	"\n</form>"
	"\n"
	"\n</center>"
	"\n</body>"
	"\n</html>\n";
    
    int n;
    char *p, *q, *key = NULL, *name = NULL, *desc = NULL;
    struct item *i, *j;
    
    if (strcmp(url, "/add") == 0) {
	fputs(form, socket);
	return;
    }

    if (strlen(url) < 6 || url[4] != '?')
	goto fof;
    
    p = &url[5];
    while((q = strsep(&p, "&"))) {
	if (q[1] != '=') goto fof;
	if (q[0] == '1') key = &q[2];
	else if (q[0] == '2') name = &q[2];
	else if (q[0] == '3') desc = &q[2];
    }

    if (!key || !name || !desc)
	goto fof;
    
    j = malloc(sizeof(struct item));
    j->key = strdup(key);
    j->name = strdup(name);
    j->desc = strdup(desc);
    j->ctime = time(NULL);
    
    pthread_rwlock_wrlock(&lock);
    if (!database) {
	database = j;
	j->next = NULL;
    }
    else if (strcmp(database->name, name) >= 0) {
	j->next = database;
	database = j;
    }
    else {
        for (i = database ; ; i = i->next) {
	    if (!i->next || strcmp(i->next->name, name) >= 0) {
                j->next = i->next;
                i->next = j;
		break;
	    }
	}
    }
    for (n = RECENT_ADDITIONS_LENGTH ; n > 0 ; n--)
	recent_additions[n] = recent_additions[n-1];
    recent_additions[0] = j;
    pthread_rwlock_unlock(&lock);
    
    fprintf(socket, "HTTP/1.0 301 Moved Permanently"
	            "\nConnection: close"
		    "\nLocation: /\n\n");

fof:
    send_error_404(socket);
    return;
}

void
send_data (FILE *socket)
{
    char one[]=
	"HTTP/1.1 200 OK"
	"\nContent-Type: text/html"
	"\n"
	"\n<html>"
	"\n<head><title>Beable Database</title></head>"
	"\n<body link=red vlink=red alink=red bgcolor=white>"
	"\n"
	"\n<center>"
	"\n<table border=1>"
	"\n  <tr>"
	"\n    <td><b>Name</b></td>"
	"\n    <td><b>Description</b></td>"
	"\n  </tr>\n";
    
    char two[]=
	"</table>"
	"\n</center>"
	"\n</body>"
	"\n</html>\n";
    
    struct item *i;
    
    fputs(one, socket);
    pthread_rwlock_rdlock(&lock);
    for (i = database ; i ; i = i->next)
	fprintf(socket, "  <tr>"
			"\n    <td><a href=\"%s%s\">%s</a></td>"
			"\n    <td>%s</td>"
			"\n  </tr>\n",
			FPROXY_ADDRESS,
			i->key,
			i->name,
			i->desc);
    pthread_rwlock_unlock(&lock);
    fputs(two, socket);
}

void
send_error_404 (FILE *socket)
{
    char message[]=
	"HTTP/1.1 404 Not Found"
	"\nContent-Type: text/html"
	"\n"
	"\n<html>"
	"\n<head><title>404 Not Found</title></head>"
	"\n<body>"
	"\n<h1>The requested URL was not found.</h1>"
	"\n</body>"
	"\n</html>\n";
    
    fputs(message, socket);
}

int
url_decode (char *url)
{
    char tmp[512];
    int i, j = 0, len = strlen(url);
    
    for (i = 0 ; i < len ; i++) {
	if (url[i] == '+') {
	    tmp[j++] = ' ';
	} else if (url[i] == '%') {
	    char c, *end, n[3];
	    if (len < i + 3) return -1;
	    n[0] = url[i+1];
	    n[1] = url[i+2];
	    n[2] = 0;
	    c = strtol(n, &end, 16);
	    if (*end != 0) return -1;
	    if (c == '\r' || c == '\n') c = ' ';
	    tmp[j++] = c;
	    i += 2;
	} else
	    tmp[j++] = url[i];
    }
        
	tmp[j] = 0;
	strcpy(url, tmp);
	return 0;
}
