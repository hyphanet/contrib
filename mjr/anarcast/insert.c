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
	err(1, "stat() of %s failed", argv[1]);
    
    if ((r = open(argv[1], O_RDONLY)) == -1)
	err(1, "open() of %s failed", argv[1]);
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(PROXY_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	err(1, "socket() failed");

    if (connect(s, &a, sizeof(a)) == -1)
	err(1, "connect() to server failed");
    
    if (writeall(s, "i", 1) != 1 || writeall(s, &st.st_size, 4) != 4)
	err(1, "writeall() of header to server failed");
    
    data = mbuf(st.st_size);
    
    if (readall(r, data, st.st_size) != st.st_size)
	err(1, "readall() of %s failed", argv[1]);
    
    if (writeall(s, data, st.st_size) != st.st_size)
	err(1, "writeall() of data failed");
    
    if (readall(s, &l, 4) != 4)
	err(1, "readall() of key length failed");

    if (l > 20000)
	err(1, "bogus key length of %d bytes", l);
    
    if (!(y = malloc(l)) || !(z = malloc(l*2+1)))
	err(1, "malloc() of key buffer failed");
    
    if (readall(s, y, l) != l)
	err(1, "readall() of key failed");
    
    bytestohex(z, y, l);
    puts(z);
    
    return 0;
}

