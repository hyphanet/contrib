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
#include "aes.c"

int listening_socket ();
void * run_thread (void *arg);
void insert (int c);
void request (int c);
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
	if ((c = accept(l, NULL, 0)) != -1) {
	    int *i = malloc(4);
	    *i = c;
	    pthread_create(&t, NULL, run_thread, i);
	    pthread_detach(t);
	}
}

void *
run_thread (void *arg)
{
    int c = *(int*)arg;
    char d;
    if (read(c, &d, 1) == 1) {
        if (d == 'r') request(c);
        if (d == 'i') insert(c);
    }
    close(c);
    free(arg);
    pthread_exit(NULL);
}

void
insert (int c)
{
    char b[1024], *p;
    unsigned int len, f, i;
    keyInstance key;
    cipherInstance cipher;
    
    if (read(c, &len, 4) != 4)
	return;
    
    strcpy(b, "/tmp/anarcast-XXXXXX");
    if ((f = mkstemp(b)) == -1)
        err(1, "mkstemp(3) failed");
    
    if (ftruncate(f, len) == -1)
        err(1, "ftruncate(2) failed");
    
    p = mmap(0, len, PROT_WRITE|PROT_READ, MAP_SHARED, f, 0);
    if (p == MAP_FAILED)
        err(1, "mmap(2) failed");
    close(f);
    
    for (f = 0 ; f < len ; ) {
	f += (i = read(c, &p[f], len - f));
	if (i <= 0) {
	    munmap(p, len);
	    return;
	}
    }

    sha_buffer(p, len, b);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	err(1, "cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, b) != TRUE)
	err(1, "makeKey() failed");
    if (blockEncrypt(&cipher, &key, p, len, p) <= 0)
	err(1, "blockEncrypt() failed");
    
    munmap(p, len);
    puts("foo");
}

void
request (int c)
{
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
    strcpy(b, "/tmp/anarcast-XXXXXX");
    if ((d = mkstemp(b)) == -1)
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

