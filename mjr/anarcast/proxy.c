#include <pthread.h>
#include "anarcast.h"
#include "sha.c"
#include "aes.c"

void * run_thread (void *arg);
void insert (int c);
void insert_parts (int c, char *p, int len, int pc);
void request (int c);
void inform ();
inline void addref (char p[20]);

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
    
//    inform(argv[1] ? argv[1] : DEFAULT_INFORM_SERVER);
    
    if ((l = listening_socket(PROXY_SERVER_PORT)) == -1)
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
    char b[1024], *p, *hash;
    unsigned int len, f, i, *off;
    keyInstance key;
    cipherInstance cipher;
    fd_set r, w;
    
    if (readall(c, &len, 4) != 4)
	return;

    p = mbuf(len);
    if (readall(c, p, len) != len) {
	ioerror();
	munmap(p, len);
	return;
    }
    
    sha_buffer(p, len, b);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	err(1, "cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, b) != TRUE)
	err(1, "makeKey() failed");
    if (blockEncrypt(&cipher, &key, p, len, p) <= 0)
	err(1, "blockEncrypt() failed");
    
    do binary[i] = n % 2;
    while (n >>= 2);
    
    f = len/PART_SIZE;
    if (f * PART_SIZE < len) f++;
    f += f/16 + f%16/
    printf("Inserting %d parts.\n", f);
    
    if (!(hash = alloca(f * HASH_LEN)))
	err(1, "alloca() failed");
    
    if (!(off = alloca(f * sizeof(unsigned int))))
	err(1, "alloca() failed");
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    
    for (i = 0 ; i < f ; i++) {
	int c, j;
/*	struct sockaddr_in a;
	char b[20];
	
	if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	    err(1, "socket(2) failed");
	
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_port = htons(ANARCAST_SERVER_PORT);
*/	
	j = len - i*PART_SIZE;
	sha_buffer(&p[i*PART_SIZE], j < PART_SIZE ? j : PART_SIZE, &hash[i*HASH_LEN]);
	bytestohex(b, &hash[i*HASH_LEN], HASH_LEN);
	printf("%d: %s\n", i,b);
/*	
	for (j = 0 ;;) {
	    if (!(a.sin_addr.s_addr = getref(hash[i], j++))) {
		puts("Can't connect to ANYONE!!!");
		return;
	    }
	    if (connect(c, &a, sizeof(a)) != -1)
		break;
	}
	FD_SET(c, &r);*/
    }

    munmap(p, len);
    puts("all done now");
}

void
request (int c)
{
}

void
inform (char *server)
{
    int c, n, m, i;
    char b[20];
    struct sockaddr_in a;
    struct hostent *h;
    
    if (!(h = gethostbyname(server)))
	err(1, "can't lookup %s", server);
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = *h->h_addr;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	err(1, "socket(2) failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	err(1, "can't connect to %s", server);

    if (readall(c, &n, 4) != 4)
	err(1, "read of length failed");
    
    if (n < 0 || n > 100000) {
	printf("Invalid server count: %d.\n", n);
	exit(1);
    }
    
    if (!n) {
	puts("No servers in network. :(");
	exit(1);
    }
    
    m = n;
    while (n--) {
	if (readall(c, &i, 4) != 4)
	    err(1, "read from inform server failed");
       	sha_buffer((char *)&i, 4, b);
	addref(b);
    }

    printf("%d Anarcast servers loaded.\n", m);
}

inline void
addref (char p[20])
{
}

