#define _GNU_SOURCE // gunnuuu!!!!

#include <errno.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

// our adorable client proxy server port
#define PROXY_SERVER_PORT     9748

// our obstreperous server port
#define ANARCAST_SERVER_PORT  9209

// our bouncy, fluorescent inform server port
#define INFORM_SERVER_PORT    7342

// the length of a SHA hash
#define HASHLEN               20

// a cute suicide note. goodbye, terrible world!
#define die(msg) {                                                           \
    extern int errno;                                                        \
    fprintf(stderr, "%s/%s/%d: %s (%s)\n", __FILE__, __FUNCTION__, __LINE__, \
	    msg, strerror(errno));                                           \
    exit(1);                                                                 \
}

// a safe malloc
inline void *
malloc (size_t size)
{
    char *p;
    if (!(p = realloc(NULL, size)))
	die("malloc() failed");
    return p;
}

// a little warning for IO errors
inline void
ioerror ()
{
    extern int errno;
    printf("I/O Error: %s.\n", strerror(errno));
}

// the reading rainbow!
inline int
readall (int c, void *b, int len)
{
    int j = 0, i = 0;
    
    while ((i += j) < len)
	if ((j = read(c, &((char *)b)[i], len-i)) <= 0)
	    return i;
    
    return i;
}

// the wrongheaded writing wriggler!
inline int
writeall (int c, const void *b, int len)
{
    int j = 0, i = 0;
    
    while ((i += j) < len)
	if ((j = write(c, &((char *)b)[i], len-i)) <= 0)
	    return i;
    
    return i;
}

// mmap a temp file. you have to munmap this when you're done
inline char *
mbuf (size_t len)
{
    char *p = mmap(0, len, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    
    if (p == MAP_FAILED)
	die("mmap() failed");
    
    return p;
}

// some high schools will expel you for using this function. watch out!
inline void
bytestohex (char *hex, const void *bytes, int blen)
{
    static char hextable[] = "0123456789ABCDEF";
    
    for ( ; blen-- ; bytes++) {
        *hex++ = hextable[(*(char *)bytes >> 4) & 0x0f];
        *hex++ = hextable[*(char *)bytes & 0x0f];
    }
    
    *hex = 0;
}

// return a wonderful, nonthreadsafe timestamp
inline char *
timestr ()
{
    long t;
    struct tm *tm;
    static char ts[128];
    
    if ((t = time(NULL)) == -1)
	die("time() failed");

    if (!(tm = localtime(&t)))
	die("localtime() failed");
    
    if (!strftime(ts, 128, "%T", tm))
	die("strftime() failed");
    
    return ts;
}

// make a listening socket on port
int
listening_socket (int port, int addr)
{
    struct sockaddr_in a;
    int r = 1, s;
    
    signal(SIGPIPE, SIG_IGN); // is this sane?
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(port);
    a.sin_addr.s_addr = htonl(addr);

    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");

    if (setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &r, sizeof(r)) == -1)
	die("setsockopt() failed");

    if (bind(s, &a, sizeof(a)) == -1)
	die("bind() failed");

    if (listen(s, SOMAXCONN) == -1)
	die("listen() failed");

    return s;
}

// xor two byte arrays, store result in a, leave b alone
inline void
xor (void *a, const void *b, int len)
{
    int i;
    
    for (i = 0 ; i < len / 4 ; i++)
	((int *)a)[i] ^= ((int *)b)[i];
    
    if (!(i = len % 4)) return;
    
    do ((char *)a)[len-i] ^= ((char *)b)[len-i];
    while (--i);
}

// go home!
inline void
chdir_to_home ()
{
    char b[1024];
    
    sprintf(b, "%s/.anarcast", getenv("HOME"));
    
    mkdir(b, 0755);
    
    if (chdir(b) == -1)
	die("chdir() failed");
}

// sir, you'll have to move along. you're blocking traffic
inline void
set_nonblock (int c)
{
    int i;
    
    if ((i = fcntl(c, F_GETFL, 0)) == -1)
	die("fnctl() failed");
    
    if (fcntl(c, F_SETFL, i | O_NONBLOCK) == -1)
	die("fnctl() failed");
}
