#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <err.h>
#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    unsigned char *data, b[1024];
    int s, i, dl;
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(ANARCAST_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1
	    || connect(s, &a, sizeof(a)) == -1)
	err(1, "can't make socket");
    
    writeall(s, "r", 1);

    if (!(i = hextobytes(argv[1], b, strlen(argv[1]))))
	err(1, "bad hex");
    
    // hash
    writeall(s, b, i);
    
    // data length
    readall(s, &dl, 4);
    printf("length: %d\n", dl);
    
    data = mbuf(dl);
    printf("read: %d\n", readall(s, data, dl));
    
    return 0;
}

