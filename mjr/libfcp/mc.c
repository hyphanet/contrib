#include "fcp.h"

int
main (int argc, char **argv)
{
    char uri[128];
    fcp_metadata *m = fcp_metadata_new();
    fcp_redirect(m, "index.html", "freenetCHK@blahblahblah");
    fcp_redirect(m, "fc.you", "freenet:FUCK@YOU");
    fcp_date_redirect(m, "date.me", "freenet:SSK@asdf/", "-something", 0, 86400);
    printf("%d\n", fcp_insert(m, "filde.ohyeh", fopen("somedata", "r"), 1000000, 1, 10));
    strcpy(uri, "freenet:CHK@");
    fcp_metadata_insert(m, uri, 1);
    puts(uri);
    fcp_metadata_free(m);
    return 0;
}

