#include "fcp.h"

int
main (int argc, char **argv)
{
    fcp_metadata *m = fcp_metadata_new();
    fcp_document d;
    char buf[1024];
    int len = fcp_request(m, &d, argv[1], 100, 10);
    if (len < 0) return 1;
    while (len) {
	int n = fcp_read(&d, buf, 1024);
	if (n < 0) return 1;
	fwrite(buf, 1, n, stdout);
	len -= n;
    }
    fcp_close(&d);
    fcp_metadata_free(m);
    return 0;
}
