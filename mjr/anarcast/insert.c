#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct stat st;
    struct sockaddr_in a;
    unsigned char *data, *y, *z;
    unsigned int l;
    int s, r;
    
    if (argc != 2) {
	fprintf(stderr, "Usage: %s <file>\n", argv[0]);
	exit(2);
    }

    if (stat(argv[1], &st) == -1)
	die("stat() failed");
    
    if ((r = open(argv[1], O_RDONLY)) == -1)
	die("open() failed");
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(PROXY_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");

    if (connect(s, &a, sizeof(a)) == -1)
	die("connect() to server failed");
    
    if (writeall(s, "i", 1) != 1 || writeall(s, &st.st_size, 4) != 4)
	die("writeall() of header to server failed");
    
    data = mbuf(st.st_size);
    
    if (readall(r, data, st.st_size) != st.st_size)
	die("readall() failed");
    
    if (writeall(s, data, st.st_size) != st.st_size)
	die("writeall() of data failed");
    
    if (readall(s, &l, 4) != 4)
	die("readall() of key length failed");

    if (l > 20000)
	die("bogus key length");
    
    if (!(y = malloc(l)) || !(z = malloc(l*2+1)))
	die("malloc() of key buffer failed");
    
    if (readall(s, y, l) != l)
	die("readall() of key failed");
    
    bytestohex(z, y, l);
    puts(z);
    
    return 0;
}

