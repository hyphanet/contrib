#include "fcp.h"

int
main (int argc, char **argv)
{
    fcp_metadata *m = fcp_metadata_new();
    fcp_document d;
    char buf[1024];
    int len = fcp_request(m, &d, argv[1], 10, 10);
    while (len) {
	int n = fcp_read(&d, buf, 1024);
	fwrite(buf, 1, n, stdout);
	len -= n;
    }
    fcp_metadata_free(m);
    return 0;
}
