#include <pthread.h>
#include "anarcast.h"
#include "sha.c"
#include "aes.c"

void * run_thread (void *arg);
void insert (int c);
void request (int c);
void inform (char *server);
inline void addref (unsigned int addr);

int
main (int argc, char **argv)
{
    int l, c;
    pthread_t t;
    
    if (argc != 2) {
	fprintf(stderr, "Usage: %s <inform server>\n", argv[0]);
        exit(2);
    }
    
    chdir_to_home();
    inform(argv[1]);
    l = listening_socket(PROXY_SERVER_PORT);
    
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
    char hash[HASH_LEN], *p;
    unsigned int len;
    keyInstance key;
    cipherInstance cipher;
    
    if (readall(c, &len, 4) != 4) {
	ioerror();
	return;
    }

    p = mbuf(len);
    if (readall(c, p, len) != len) {
	ioerror();
	munmap(p, len);
	return;
    }
    
    sha_buffer(p, len, hash);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	err(1, "cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, hash) != TRUE)
	err(1, "makeKey() failed");
    if (blockEncrypt(&cipher, &key, p, len, p) <= 0)
	err(1, "blockEncrypt() failed");
    
    munmap(p, len);
}

void
request (int c)
{
    int i;

    if (readall(c, &i, 4) != 4) {
	ioerror();
	return;
    }

    if (i % HASH_LEN) {
	puts("Bad key length.");
	return;
    }
}

void
inform (char *server)
{
    int c, n;
    struct sockaddr_in a;
    struct hostent *h;
    extern int h_errno;
    
    if (!(h = gethostbyname(server))) {
	printf("%s: %s.\n", server, hstrerror(h_errno));
	exit(1);
    }
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = ((struct in_addr *)h->h_addr)->s_addr;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	err(1, "socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	err(1, "connect() to %s failed", server);
    
    for (n = 0 ; ; n++) {
	unsigned int i;
	int j = readall(c, &i, 4);
	if (!j) break;
    	if (j != 4) err(1, "inform server hung up unexpectedly");
	addref(i);
    }

    printf("%d Anarcast servers loaded.\n", n);

    if (!n) {
	puts("No servers, exiting.");
	exit(0);
    }
}

inline void
addref (unsigned int addr)
{
    struct in_addr x;
    char hash[HASH_LEN], hex[HASH_LEN*2+1];
    x.s_addr = addr;
    sha_buffer((char *) &addr, 4, hash);
    bytestohex(hex, hash, HASH_LEN);
    printf("Added %-16s (%s)\n", inet_ntoa(x), hex);
    
}

