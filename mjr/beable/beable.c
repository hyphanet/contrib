#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <pthread.h>

#define LISTEN_PORT               6666
#define DATABASE_SYNC_INTERVAL    10

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

struct item {
    char *key;
    char *name;
    char *desc;
    struct item *next;
} *database = NULL;

int listening_socket ();                   // Create and bind a new listening socket.
int load_key_database ();                  // Load key database.
void * database_sync_thread (void *arg);   // Synchronizes the database in memory with the disk.
void get_connection (int socket);          // Wait for a new connection and spawn a thread to handle it.
void * handler_thread (void *arg);         // Handle a HTTP connection.
void send_index (FILE *socket);            // Send the default page.
void run_search (FILE *socket, char *url); // Run a search.
void run_add (FILE *socket, char *url);    // Add a Freenet URI from CGI GET input.
void send_data (FILE *socket);             // Send the entire database as flat HTML.
void send_error_404 (FILE *socket);        // Send a 404 file not found error message.
int url_decode (char *url);                // URL decode.

int
main ()
{
    int socket = listening_socket();

    if (socket == -1) {
	fprintf(stderr, "Error initializing listening socket.\n");
	exit(1);
    }

    if (load_key_database() == -1) {
	fprintf(stderr, "Error loading key database.\n");
	exit(1);
    }
    
    for (;;)
	get_connection(socket);
}

int
listening_socket ()
{
    struct sockaddr_in address;
    int r = 1, sock;
    
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons(LISTEN_PORT);
    address.sin_addr.s_addr = htonl(INADDR_ANY);

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	return -1;
    
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *) &r, sizeof(r));
    
    if (bind(sock, (struct sockaddr *) &address, sizeof(address)) < 0)
	return -1;
    
    if (listen(sock, 1) < 0)
	return -1;

    return sock;
}

int
load_key_database ()
{
    pthread_t t;
    int i;
    struct item *tail;
    char key[256], name[256], desc[1024], end[256];
    FILE *data = fopen("beable_database", "r");
    if (!data) return -1;
    
    while (!feof(data)) {
	i = fscanf(data, "%[^\n]\n%[^\n]\n%[^\n]\n%[^\n]\n", key, name, desc, end);
	if (i != 4 || strcmp(end, "END") != 0) return -1;
	if (!database) {
	    database = malloc(sizeof(struct item));
	    database->key = strdup(key);
	    database->name = strdup(name);
	    database->desc = strdup(desc);
	    database->next = NULL;
	    tail = database;
	} else {
	    tail->next = malloc(sizeof(struct item));
	    tail->next->key = strdup(key);
	    tail->next->name = strdup(name);
	    tail->next->desc = strdup(desc);
	    tail->next->next = NULL;
	    tail = tail->next;
	}
    }

    fclose(data);
    pthread_create(&t, NULL, database_sync_thread, NULL);
    return 0;
}

void *
database_sync_thread (void *arg)
{
    for (;;) {
	FILE *data;
	struct item *i;
	sleep(DATABASE_SYNC_INTERVAL);
	if (system("cp -f beable_database beable_database_backup") != 0) {
	    fprintf(stderr, "Error backing up database.\n");
	    continue;
	}
	data = fopen("beable_database", "w");
	if (!data) {
	    fprintf(stderr, "Error opening database for write.\n");
	    continue;
	}
	pthread_mutex_lock(&mutex);
	for (i = database; i ; i = i->next)
	    fprintf(data, "%s\n%s\n%s\nEND\n", i->key, i->name, i->desc);
	pthread_mutex_unlock(&mutex);
	fclose(data);
	printf("written!\n");
    }
}

void
get_connection (int socket)
{
    struct sockaddr_in incoming;
    int *conn = malloc(sizeof(int));
    int b = sizeof(incoming);
    pthread_t t;
    
    if ((*conn = accept(socket, &incoming, &b)) < 0)
	return;
    
    pthread_create(&t, NULL, handler_thread, conn);
    pthread_detach(t);
}

void *
handler_thread (void *arg)
{
    FILE *socket;
    int i;
    char buf[1024], method[5], url[512];

    if (!(socket = fdopen(*(int *)arg, "r+")))
	pthread_exit(NULL);
    
    i = fscanf(socket, "%4s %511s\n", method, url);
    if (i != 2)	goto end;
    
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
    pthread_exit(NULL);
}

void
send_index (FILE *socket)
{
    char one[]="HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html>\n<head><title>Beable</title></head>\n<body link=red vlink=red alink=red bgcolor=white>\n<center>\n\n<h1>Beable!</h1>\n<table cellspacing=0 cellpadding=0>\n<tr align=center><td><form action=\"/search\" method=get>Search: <input type=text name=1 size=55 maxlength=255> <input type=submit value=\"Search\"></form>\n</td></tr>\n<tr align=center><td><a href=\"http://freenetproject.org/\">Get Freenet</a> || <a href=\"/add\">Add A Link</a> || <a href=\"/data\">Download Database</a></td></tr>\n</table>\n\n<p><table border=1>\n<tr><td><b>URI</b></td></td><td><b>Description</b></td></tr>\n";
    char two[]="</table>\n\n</center>\n</body>\n</html>\r\n";
    
    int i;
    
    fputs(one, socket);
    
    for (i = 0 ; i < 10 ; i++)
	fprintf(socket, "<tr><td><a href=\"http://www.google.com/\">The Mightly Wumpii</a></td><td>Currect events in the world of the Wumpii. Recipes you can try at home. Pizza is the great holy savior. Elves rock.</td></tr>\n");
    
    fputs(two, socket);
}

void
run_search (FILE *socket, char *url)
{
    char one[]="HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html>\n<head><title>Search Results</title></head>\n<body link=red vlink=red alink=red bgcolor=white>\n<center>\n\n<h1>Search Results</h1>\n\n<p><table border=1>\n<tr><td><b>URI</b></td></td><td><b>Description</b></td></tr>\n";
    char two[]="</table>\n\n</center>\n</body>\n</html>\r\n";
    
    struct item *i;
    char *query;
    
    if (strncmp(url, "/search?1=", 10) != 0) {
        send_error_404(socket);
        return;
    }

    query = &url[10];
    fputs(one, socket);
    pthread_mutex_lock(&mutex);
    for (i = database ; i ; i = i->next)
	if (strstr(i->name, query) || strstr(i->desc, query))
	    fprintf(socket, "<tr><td><a href=\"%s\">%s</a></td><td>%s</td></tr>\n", i->key, i->name, i->desc);
    pthread_mutex_unlock(&mutex);
    fputs(two, socket);
}

void
run_add (FILE *socket, char *url)
{
    char form[]="HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html>\n<head><title>Beable</title></head>\n<body link=red vlink=red alink=red bgcolor=white>\n<center>\n\n<h1>Beable!</h1>\n<form action=\"/add\" method=get>\n<p>Freenet URI: <input type=text name=1 size=55 maxlength=255>\n<p>Short Name: <input type=text name=2 size=55 maxlength=255>\n<p>Description: <textarea cols=55 rows=3 name=3></textarea>\n<p><input type=submit value=\"Add Link\"></form>\n\n</center>\n</body>\n</html>\r\n";
    
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
    
    pthread_mutex_lock(&mutex);
    for (i = database ; ; i = i->next) {
	if (!i->next || strcmp(i->name, name) >= 0) {
	    j = malloc(sizeof(struct item));
	    j->key = strdup(key);
	    j->name = strdup(name);
	    j->desc = strdup(desc);
	    j->next = i->next;
	    i->next = j;
            pthread_mutex_unlock(&mutex);
            return send_index(socket);
	}
    }

fof:
    send_error_404(socket);
    return;
}

void
send_data (FILE *socket)
{
    char one[]="HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html>\n<head><title>Beable Database</title></head>\n<body link=red vlink=red alink=red bgcolor=white>\n<center>\n\n<table border=1>\n<tr><td><b>URI</b></td></td><td><b>Description</b></td></tr>\n";
    char two[]="</table>\n\n</center>\n</body>\n</html>\r\n";
    struct item *i;
    
    fputs(one, socket);
    pthread_mutex_lock(&mutex);
    for (i = database ; i ; i = i->next)
	fprintf(socket, "<tr><td><a href=\"%s\">%s</a></td><td>%s</td></tr>\n", i->key, i->name, i->desc);
    pthread_mutex_unlock(&mutex);
    fputs(two, socket);
}

void
send_error_404 (FILE *socket)
{
    char message[]="HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n<html><head><title>404 Not Found</title></head><body><h1>The requested URL was not found.</h1></body></html>\r\n";
    
    fputs(message, socket);
}

int
url_decode (char *url)
{
    char tmp[512];
    int i, j = 0, len = strlen(url);
    
    for (i = 0 ; i < len ; i++) {
	if (url[i] == '\r' || url[i] == '\n' || url[i] == '+') {
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
