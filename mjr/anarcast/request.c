#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    struct stat st;
    char *data;
    unsigned int l;
    int f, s;
    off_t o;
    
    if (argc != 2) {
	fprintf(stderr, "Usage: %s <keyfile>\n", argv[0]);
	exit(2);
    }

    if (stat(argv[1], &st) == -1)
	die("stat() failed");

    if ((st.st_size-4) % HASHLEN) {
	fprintf(stderr, "Invalid keyfile.\n");
	exit(1);
    }

    if ((f = open(argv[1], O_RDONLY)) == -1)
	die("open() failed");

    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(PROXY_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");

    if (connect(s, &a, sizeof(a)) == -1)
	die("connect() to proxy failed");
    
    if (writeall(s, "r", 1) != 1 || writeall(s, &st.st_size, 4) != 4)
	die("writeall() of request to proxy failed");
    
    o = 0;
    if (sendfile(s, f, &o, st.st_size) != st.st_size)
	die("sendfile() of key to proxy failed");
    
    if (readall(s, &l, 4) != 4)
	die("readall() of data length from proxy failed");
    
    data = mbuf(l);
    if (readall(s, data, l) != l)
	die("readall() of data from proxy failed");

    if (writeall(1, data, l) != l)
	die("writeall() of data to stdout failed");
    
    return 0;
}

