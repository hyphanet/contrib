#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>
#include <getopt.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include "fcp.h"

int htl = 10;
int threads = 10;
int retries = 3;

void insertdir (char *dir, int depth);
void insert (char *file, int depth);
int isdir (char *dir);

void
usage (char *me)
{
    fprintf(stderr, "Usage: %s [options] files/directories\n\n"
	    	    "  -h --htl            Hops to live.\n"
		    "  -t --threads        Concurrency.\n"
		    "  -r --retries        Number of retries per insert before abort.\n\n",
		    me);
    exit(2);
}

void
insertdir (char *dir, int depth)
{
    struct dirent **namelist;
    char arg[1024];
    int n, i;
    char *r = rindex(dir, '/');
    n = depth++;
    while (n--) putchar('\t');
    puts(r ? r + 1 : dir);
    n = scandir(dir, &namelist, 0, alphasort);
    for (i = 0 ; i < n ; i++) {
	if (namelist[i]->d_name[0] == '.') continue;
	if (strcmp(dir, ".") == 0) strcpy(arg, namelist[i]->d_name);
	else sprintf(arg, "%s/%s", dir, namelist[i]->d_name);
	if (isdir(arg)) insertdir(arg, depth);
	else insert(arg, depth);
	free(namelist[i]);
    }
    free(namelist);
}

void
insert (char *file, int depth)
{
    FILE *data;
    char uri[128], *t = rindex(file, '/');
    int status;
    int r = retries + 1;
    struct stat s;
    status = stat(file, &s);
    if (status != 0) {
	fprintf(stderr, "Can't stat file %s!\n", file);
	pthread_exit(NULL);
    }
    data = fopen(file, "r");
    if (!data) {
	fprintf(stderr, "Can't open file %s!\n", file);
	pthread_exit(NULL);
    }
    strcpy(uri, "freenet:CHK@");
    if (s.st_size < 128 * 1024) {
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_insert_raw(data, uri, s.st_size, DATA, htl);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed!\n", file);
	    pthread_exit(NULL);
	}
    } else {
	fcp_metadata *m = fcp_metadata_new();
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_insert(m, "", data, s.st_size, htl, threads);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed!\n", file);
	    pthread_exit(NULL);
	}
	r = retries + 1;
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_metadata_insert(m, uri, htl);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed!\n", file);
	    pthread_exit(NULL);
	}
	fcp_metadata_free(m);
    }
    while (depth--) putchar('\t');
    sprintf(uri, "%s//", uri);
    printf("%s=%s\n", t ? t + 1 : file, uri);
}

int
isdir (char *dir)
{
    DIR *d = opendir(dir);
    if (!d) return 0;
    closedir(d);
    return 1;
}


int
main (int argc, char **argv)
{
    int c, i;
    char *arg;
    extern int optind;
    extern char *optarg;
    
    static struct option long_options[] =
    {
	{"htl",       1, NULL, 'h'},
	{"threads",   1, NULL, 't'},
	{"retries",   1, NULL, 'r'},
	{0, 0, 0, 0}
    };
    
    while ((c = getopt_long(argc, argv, "h:t:r:", long_options, NULL)) != EOF) {
        switch (c) {
        case 'h':
            htl = atoi(optarg);
            break;
	case 't':
	    threads = atoi(optarg);
	    break;
	case 'r':
	    retries = atoi(optarg);
	    break;
        case '?':
            usage(argv[0]);
	    return 1;
        }
    }
    
    if (htl < 1) {
	fprintf(stdout, "Invalid hops to live.\n");
	return 1;
    }
    if (threads < 1) {
	fprintf(stdout, "Invalid number of threads.\n");
	return 1;
    }
    if (retries < 0) {
	fprintf(stdout, "Invalid number of retries.\n");
	return 1;
    }
    
    if (!argv[optind]) usage(argv[0]);
    
    arg = argv[(c = optind)];
    for (i = 1 ; argv[c] ; arg = argv[++c]) {
	if (arg[strlen(arg)-1] == '/') arg[strlen(arg)-1] = '\0';
        if (isdir(arg)) insertdir(arg, 0);
	else insert(arg, 0);
    }

    return 0;
}

