#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    unsigned char *data, *key;
    int s, o, i, l;
    
    if (argc != 3) {
	fprintf(stderr, "Usage: %s <key> <outfile|->\n", argv[0]);
	exit(2);
    }

    i = strlen(argv[1]);
    
    if (i % 40)
	err(1, "bad key length");
    
    if (!(key = malloc(i/2)))
	err(1, "malloc() of key buffer failed");
    
    if (hextobytes(argv[1], key, i) != i/2)
	err(1, "key is not hex");

    if (!strcmp(argv[2], "-"))
	o = 1;
    else
	if ((o = open(argv[2], O_WRONLY|O_CREAT|O_TRUNC, 0644)) == -1)
	    err(1, "open() of %s failed", argv[2]);
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(ANARCAST_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	err(1, "socket() failed");

    if (connect(s, &a, sizeof(a)) == -1)
	err(1, "connect() to server failed");
    
    if (writeall(s, "r", 1) != 1 || writeall(s, key, i/2) != i/2)
	err(1, "writeall() of request to server failed");
    
    if (readall(s, &l, 4) != 4)
	err(1, "readall() of data length from server failed");
    
    data = mbuf(l);
    
    if (readall(s, data, l) != l)
	err(1, "readall() of data from server failed");
    
    if (writeall(o, data, l) != l)
	err(1, "writeall() of data to output file failed");
    
    return 0;
}

