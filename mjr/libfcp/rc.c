#include <stdlib.h>
#include <stdio.h>
#include <getopt.h>
#include <fcp.h>

void
usage (char *me)
{
    fprintf(stderr, "Usage: %s [options] URI [file]\n\n"
	    	    "  -t --threads        Concurrency. (default 10)\n"
		    "  -h --htl            Hops to live. (default 10)\n\n",
		    me);
    exit(2);
}

int
run (char *uri, FILE *dest, int threads, int htl)
{
    fcp_metadata *m = fcp_metadata_new();
    fcp_document *d = fcp_document_new();
    char buf[1024];
    
    int len = fcp_request(m, d, uri, threads, htl);
    
    if (len < 0) {
	fprintf(stderr, "Request failed: %s.\n",
		fcp_status_to_string(len));
	return 1;
    }
    
    while (len) {
	int n = fcp_read(d, buf, 1024);
	if (n < 0) return 1;
	fwrite(buf, 1, n, dest);
	len -= n;
    }
    
    fcp_close(d);
    fcp_metadata_free(m);
    return 0;
}

int
main (int argc, char **argv)
{
    int htl = 10, threads = 10;
    char c, *uri, *file;
    FILE *dest;
    
    extern int optind;
    extern char *optarg;

    static struct option long_options[] =
    {
        {"htl",       1, NULL, 'h'},
        {"threads",   1, NULL, 't'},
        {0, 0, 0, 0}
    };

    while ((c = getopt_long(argc, argv, "h:t:", long_options, NULL)) != EOF) {
        switch (c) {
        case 'h':
            htl = atoi(optarg);
            break;
        case 't':
            threads = atoi(optarg);
            break;
        case '?':
            usage(argv[0]);
            return 1;
        }
    }

    if (htl < 0) {
        fprintf(stdout, "Invalid hops to live.\n");
        return 1;
    }
    if (threads < 1) {
        fprintf(stdout, "Invalid number of threads.\n");
        return 1;
    }
    
    uri = argv[optind];
    if (!uri) usage(argv[0]);
    
    file = argv[optind+1];
    dest = file ? fopen(file, "w") : stdout;
    if (!dest) {
	fprintf(stderr, "Error opening %s for writing!\n", file);
	exit(1);
    }

    return run(uri, dest, threads, htl);
}

