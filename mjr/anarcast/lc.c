#include "anarcast.h"

struct {
    char *name;
    char *key;
    int keylen;
    char a;
} keys[16384];

int count;

void
load_candidates ()
{
    int fd, len;
    char *p, *candidates;
    struct stat s;

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
    
    for (count = 0, p = candidates ; p != candidates + len ; count++) {
	int namelen = strlen(p);
	int keylen = *(unsigned int *)(p+namelen+1);
	keys[count].name = p;
	keys[count].key = p + namelen + 5;
	keys[count].keylen = keylen;
	keys[count].a = 0;
	p += namelen + keylen + 5;
	if (!namelen || !keylen || p > candidates + len) {
	    puts("Corrupt candidates file! Aborting.");
	    exit(1);
	}
    }
}

void
list_candidates ()
{
    int i;
    load_candidates();
    for (i = 0 ; i < count ; i++)
	printf("%d\t%s\n", i+1, keys[i].name);
    exit(0);
}

void
download (int num)
{
    struct stat st;
    struct sockaddr_in a;
    int fd, s, l, i;
    char buf[1024];

    if (stat(keys[num].name, &st) != -1) {
	printf("%s already exists. Skipping.\n", keys[num].name);
	return;
    }

    if ((fd = open(keys[num].name, O_WRONLY|O_CREAT)) == -1)
	die("open() failed");

    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(PROXY_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");
    
    if (connect(s, &a, sizeof(a)) == -1)
	die("connect() to proxy failed");
    
    if (writeall(s, "r", 1) != 1 || writeall(s, &keys[num].keylen, 4) != 4)
	die("writeall() of request to proxy failed");

    printf("Requesting %s: ", keys[num].name);
    fflush(stdout);
    
    if (readall(s, &l, 4) != 4) {
	puts("Failed.");
	return;
    }
    
    for (i = 0 ; i != l ; ) {
	int j = l - i > 1024 ? 1024 : l - i;
	if (readall(s, buf, j) != j)
	    die("readall() of data from proxy failed");
        if (writeall(fd, buf, j) != j)
	    die("writeall() of data to file failed");
	i += j;
    }

    if (close(s) == -1 || close(fd) == -1)
	die("close() failed");
    
    puts("Success.");
}

int
main (int argc, char **argv)
{
    int i;
    
    if (argc == 1)
	list_candidates();
    
    if (argv[1][0] == '-' && argv[1][1]) {
	printf("\
Usage: %s <candidate numbers to download>\n\
With no arguments, list candidate files. Otherwise, download the specified\n\
file numbers. File numbers may be single numbers, a range X-Y, or - to\n\
download all files. Multiple file number arguments are acceptable.\n", argv[0]);
	exit(2);
    }

    load_candidates();
    
    if (argc == 2 && argv[1][0] == '-') {
	for (i = 0 ; i < count ; i++)
	    keys[i].a = 1;
	argc--;
    }
    
    for (i = 1 ; i < argc ; i++) {
	char *p, *q = argv[i] + strlen(argv[i]);;
	int n = strtol(argv[i], &p, 10);
	if (p != q) { // a range
	    int start, end;
	    char *dash = index(argv[i], '-');
	    if (dash == argv[i] || dash == q)
		goto invalid;
	    start = strtol(argv[i], &p, 10);
	    if (p != dash || start < 1)
		goto invalid;
	    end = strtol(dash + 1, &p, 10);
	    if (p != q || end > count || start >= end)
		goto invalid;
	    for ( ; start <= end ; start++)
		keys[start-1].a = 1;
	} else if (n < 1 || n > count)
	    goto invalid;
	else
	    keys[n-1].a = 1;
    }

    for (i = 0 ; i < count ; i++)
	if (keys[i].a)
	    download(i);

    return 0;

invalid:
    printf("Invalid argument: %s.\n", argv[i]);
    exit(1);
}

