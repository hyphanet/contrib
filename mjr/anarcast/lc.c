#include "anarcast.h"

int
main (int argc, char **argv)
{
    int fd, len, i;
    char *candidates, *p;
    struct stat s;
    
    if (argc != 1) {
	printf("Usage: %s (no arguments)\n", argv[0]);
	exit(2);
    }
    
    if (stat(".candidates", &s) == -1) {
	extern int errno;
	if (errno != ENOENT)
	    die("stat() of candidates file failed");
	puts("Candidates file does not exist.");
	exit(1);
    }
    
    if ((fd = open(".candidates", O_RDONLY)) == -1)
	die("open() of candidates file failed");
    
    len = s.st_size;
    candidates = mmap(0, len, PROT_READ, MAP_SHARED, fd, 0);
    if (candidates == MAP_FAILED)
	die("mmap() of candidates file failed");

    for (i = 1, p = candidates ; p != candidates + len ; i++) {
	int namelen = strlen(p);
	int keylen = *(unsigned int *)(p+namelen+1);
	printf("%d\t%s\n", i, p);
	p += namelen + keylen + 5;
	if (!namelen || !keylen || p > candidates + len) {
	    puts("Corrupt candidates file! Aborting.");
	    exit(1);
	}
    }
    
    if (munmap(candidates, len) == -1)
	die("munmap() failed");
    
    return 0;
}

