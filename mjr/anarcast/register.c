#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    struct hostent *h;
    extern int h_errno;
    int c;

    if (argc != 2) {
	printf("Usage: %s <database server address>\n", argv[0]);
	exit(2);
    }

    if (!(h = gethostbyname(argv[1]))) {
	printf("%s: %s.\n", argv[1], hstrerror(h_errno));
	exit(1);
    }
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(REGISTER_SERVER_PORT);
    a.sin_addr.s_addr = ((struct in_addr *)h->h_addr)->s_addr;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	die("connect to server failed");
    
    if (close(c) == -1)
	die("close() failed");

    printf("Registered with %s.\n", argv[1]);
    
    return 0;
}

