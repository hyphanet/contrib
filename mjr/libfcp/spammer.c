#include <fcp.h>
#include <sys/stat.h>

int
main (int argc, char **argv)
{
    int i = atoi(argv[3]), end = i + atoi(argv[4]);
    char uri[128];
    FILE *data = fopen(argv[2], "r");
    struct stat s;
    stat(argv[2], &s);
    while (i++ < end) {
        sprintf(uri, "%s%d", argv[1], i);
	rewind(data);
	printf("Inserting %s.\n", uri);
	fflush(stdout);
	fcp_insert_raw(data, NULL, uri, s.st_size, FCP_DATA, 10);
    }
    return 0;
}

