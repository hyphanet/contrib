#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <unistd.h>

FILE *
fcp_connect ()
{
    struct in_addr addr;
    struct sockaddr_in address;
    struct servent *serv;
    int connected_socket, connected;
    serv = getservbyname("fcp", "tcp");
    if (!serv) return NULL;
    addr.s_addr = inet_addr("127.0.0.1");
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = (serv->s_port);
    address.sin_addr.s_addr = addr.s_addr;
    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket,
	    (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) return NULL;
    return fdopen(connected_socket, "w+");
}

int
main (int argc, char **argv)
{
    int c = 0, i, n, status;
    long dlen = -1, mlen = 0;
    char buf[1024], name[512], val[512];
    FILE *data = tmpfile(), *sock = fcp_connect();
    if (!sock) return 1;
    fprintf(sock, "ClientGet\n"
	          "URI=%s\n"
		  "HopsToLive=%x\n"
		  "EndMessage\n",
	    	  argv[1], 10);
    fgets(buf, 512, sock);
    printf("%s", buf);
    if (strncmp(buf, "DataFound", 9) != 0) return 1;
    while (fgets(buf, 512, sock)) {
	printf("%s", buf);
	status = sscanf(buf, "%[^=]=%s", name, val);
        if (status != 2) break;
        if (strcmp(name, "DataLength") == 0)
	    dlen = strtol(val, NULL, 16);
        else if (strcmp(name, "MetadataLength") == 0)
            mlen = strtol(val, NULL, 16);
        else break;
    }
    if (dlen < 0 || (mlen && mlen != dlen)
	    || strncmp(buf, "EndMessage", 10) != 0)
	return 1;
    while (dlen) {
	status = fscanf(sock, "DataChunk\nLength=%x\nData", &n);
	fgetc(sock);
	if (status != 1 || n < 0) return 1;
	dlen -= n;
	while (n) {
	    i = fread(buf, 1, n > 1024 ? 1024 : n, sock);
	    if (!i) return 1;
	    fwrite(buf, 1, i, data);
	    n -= i;
	    c+=i;
	    printf("%d bytes read.\n", c);
	}
    }
    return 0;
}

