#include <err.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>
#include "anarcast.h"
#include "sha.c"

int listening_socket ();
void * run_thread (void *arg);
void inform ();
void bytestohex (char *hex, char *bytes, int blen);

int
main (int argc, char **argv)
{
    int l, c;
    pthread_t t;
    char b[1024];
    
    sprintf(b, "%s/.anarcast", getenv("HOME"));
    mkdir(b, 0755);
    if (chdir(b) == -1)
	err(1, "can't change to %s", b);
    
    if ((l = listening_socket()) == -1)
	err(1, "can't grab port %d", PROXY_SERVER_PORT);
    
    for (;;)
	if ((c = accept(l, NULL, 0)) != -1)
	    pthread_create(&t, NULL, run_thread, &c);
}

void *
run_thread (void *arg)
{
    pthread_exit(NULL);
}

int
listening_socket ()
{
    struct sockaddr_in a;
    int r = 1, s;

    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(PROXY_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_ANY);

    if ((s = socket(AF_INET, SOCK_STREAM, 0)) < 0)
        return -1;

    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char *) &r, sizeof(r));

    if (bind(s, &a, sizeof(a)) < 0)
        return -1;

    if (listen(s, SOMAXCONN) < 0)
        return -1;
    
    return s;
}

void
inform (char *server)
{
    int c, d, i, t;
    off_t o = 0;
    char b[1024];
    struct sockaddr_in a;
    struct hostent *h = gethostbyname(server);
    if (!h) {
	warn("can't lookup %s", server);
	return;
    }
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = *h->h_addr;
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	err(1, "socket(2) failed");
    if (connect(c, &a, sizeof(a)) == -1) {
	warn("can't connect to %s", server);
	return;
    }
    if ((d = mkstemp("/tmp/anarcast-XXXXXX")) == -1)
	err(1, "mkstemp(3) failed");
    t = 0;
    while ((i = read(c, b, sizeof(b))) > 0) {
	if (write(d, b, i) != i)
	    err(1, "write(2) to server list failed");
	t += i;
    }
    close(c);
    if (t % 4) {
	warn("transfer from inform server ended prematurely (list not updated)");
        close(d);
	return;
    }
    if ((i = open("servers", O_WRONLY, O_CREAT | O_TRUNC)) == -1)
	err(1, "can't open servers file");
    if (sendfile(i, d, &o, t) == -1)
	err(1, "sendfile(2) failed");
    close(d);
    close(i);
    printf("%d known servers.\n", t / 4);
}

void
bytestohex (char *hex, char *bytes, int blen)
{
    static char hextable[] = "0123456789ABCDEF";
    for ( ; blen-- ; bytes++) {
	*hex++ = hextable[*bytes >> 4 & 0x0f];
	*hex++ = hextable[*bytes & 0x0f];
    }
    *hex = 0;
}

