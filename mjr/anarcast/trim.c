#include "anarcast.h"
#include <dirent.h>

struct block {
    char name[256];
    unsigned int size;
    time_t mtime;
} *blocks;

int compare (const void *x, const void *y);

int
main (int argc, char **argv)
{
    struct dirent **d;
    char *p;
    unsigned int max, total;
    extern int errno;
    int n, i;
    
    if (argc != 2) {
	printf("Usage: %s <maximum size of directory contents in bytes>\n", argv[0]);
	exit(2);
    }

    max = strtoul(argv[1], &p, 0);
    if (p != argv[1] + strlen(argv[1])) {
	puts("Invalid argument.");
	exit(2);
    }

    if ((i = scandir(".", &d, 0, alphasort)) == -1)
	die("scandir() failed");
    
    blocks = malloc(sizeof(struct block) * i);
    n = total = 0;
    while (i--) {
	char *name = d[i]->d_name;
	if (strcmp(name, ".") && strcmp(name, "..")) {
	    struct stat s;
	    if (stat(name, &s) == -1) {
		printf("Cannot stat %s! Exiting. (%s)\n", name, strerror(errno));
		exit(1);
	    }
	    strcpy(blocks[n].name, name);
	    blocks[n].size = s.st_size;
	    blocks[n].mtime = s.st_mtime;
	    total += s.st_size;
	    n++;
	}
    }

    qsort(blocks, n, sizeof(struct block), compare);
    
    for (i = 0 ; total > max && i < n ; i++) {
	if (unlink(blocks[i].name) == -1) {
	    printf("Cannot unlink %s! Exiting. (%s)\n", blocks[i].name, strerror(errno));
	    exit(1);
	}
	printf("Unlinked %s.\n", blocks[i].name);
	total -= blocks[i].size;
    }
    
    return 0;
}

int
compare (const void *x, const void *y)
{
    struct block *a = (struct block *) x, *b = (struct block *) y;
    return a->mtime < b->mtime ? -1 : b->mtime < a->mtime ? 1 : 0;
}

