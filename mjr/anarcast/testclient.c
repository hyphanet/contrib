#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <err.h>
#include <time.h>
#include "anarcast.h"

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    unsigned char *data, h[HASH_LEN], b[HASH_LEN*2+1];
    int s, r;
    unsigned int dl;
    off_t o = 0;
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(ANARCAST_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    
    if ((s = socket(AF_INET, SOCK_STREAM, 0)) == -1
	    || connect(s, &a, sizeof(a)) == -1)
	err(1, "can't make socket");
    
    writeall(s, "i", 1);
    
    dl = 1234567;
    writeall(s, &dl, 4);
    
    data = mbuf(dl);
    r = open("/dev/urandom", O_RDONLY);
    readall(r, data, dl);
    writeall(s, data, dl);
    
    readall(s, h, 20);
    bytestohex(b, h, 20);
    puts(b);
    
    return 0;
}

