#define PROXY_SERVER_PORT     9748
#define ANARCAST_SERVER_PORT  9209
#define INFORM_SERVER_PORT    7342
#define HASH_LEN              20
#define DEFAULT_INFORM_SERVER "localhost"
#define PART_SIZE             64 * 1024

extern int errno;

#include <unistd.h>
#include <sys/mman.h>

char *
mbuf (size_t len)
{
    int c;
    char *p, b[1024];
    
    strcpy(b, "/tmp/anarcast-XXXXXX");
    if ((c = mkstemp(b)) == -1)
	err(1, "mkstemp(3) failed");
    
    if (ftruncate(c, len) == -1)
	err(1, "ftruncate(2) failed");
    
    p = mmap(0, len, PROT_READ|PROT_WRITE, MAP_SHARED, c, 0);
    if (p == MAP_FAILED)
	err(1, "mmap(2) failed");
    
    close(c);
    return p;
}

