#define MAX_HOSTS       16384
#define VERIFY_INTERVAL 60
#define WRITE_INTERVAL  60

#include <err.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include "anarcast.h"

int listening_socket ();

int n;
unsigned int hosts[MAX_HOSTS];

long last_verify, last_write;

int
main (int argc, char **argv)
{
    int f, l, c, d, i;
    struct sockaddr_in a;

    if ((l = listening_socket()) == -1)
	err(1, "can't grab port %d", INFORM_SERVER_PORT);
    
    if ((f = open("inform_database", O_RDWR | O_CREAT, 0644)) == -1)
	err(1, "open(2) of inform_database failed");

    if (read(f, &hosts, sizeof(hosts)) != sizeof(hosts)) {
	puts("initializing new/bad inform_database");
        memset(&hosts, 0, sizeof(hosts));
	if (lseek(f, SEEK_SET, 0) == -1)
	    err(1, "lseek(2) failed");
	if (write(f, &hosts, sizeof(hosts)) != sizeof(hosts))
	    err(1, "write(2) failed");
	if (lseek(f, SEEK_SET, 0) == -1)
	    err(1, "lseek(2) failed");
    }

    for (n = 0 ; n < MAX_HOSTS ; n++)
	if (!hosts[n]) break;
    
    last_verify = time(NULL);
    last_write = time(NULL);
    
    for (;;) {
	i = sizeof(a);
	if ((c = accept(l, &a, &i)) != -1) {
	    d = 0;
	    for (i = 0 ; i < n ; i++) {
	        if (write(c, &hosts[i], 4) != 4)
		    break;
		if (hosts[i] == a.sin_addr.s_addr)
		    d = 1;
	    }
	    if (!d) hosts[n++] = a.sin_addr.s_addr;
	    close(c);
	}
	if (time(NULL) > last_write + WRITE_INTERVAL) {
	    last_write = time(NULL);
	    if (write(f, &hosts, sizeof(hosts)) != sizeof(hosts))
		err(1, "write(2) failed");
	    if (lseek(f, SEEK_SET, 0) == -1)
		err(1, "lseek(2) failed");
	}
	if (time(NULL) > last_verify + VERIFY_INTERVAL) {
	    int o = 0;
	    unsigned int h[MAX_HOSTS];
	    last_verify = time(NULL);
	    for (i = 0 ; i < n ; i++) {
	        struct sockaddr_in a;
		memset(&a, 0, sizeof(a));
		a.sin_family = AF_INET;
		a.sin_port = htons(ANARCAST_SERVER_PORT);
		a.sin_addr.s_addr = hosts[i];
		if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
		    err(1, "socket(2) failed");
		if (connect(c, &a, sizeof(a)) != -1)
		    h[o++] = hosts[i];
		close(c);
	    }
	    if (o != n) printf("%d of %d total hosts unreachable.", o, n);
	    else printf("all hosts still online :)\n");
	    if (o * 2 > n) {
		puts("too many unreachable hosts - not updating list");
		continue;
	    }
	    n = o;
	    memcpy(hosts, h, sizeof(hosts));
	    if (write(f, &hosts, sizeof(hosts)) != sizeof(hosts))
		err(1, "write(2) failed");
	    if (lseek(f, SEEK_SET, 0) == -1)
		err(1, "lseek(2) failed");
	}
    }
}
    
int
listening_socket ()
{
    struct sockaddr_in a;
    int r = 1, s;

    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = htonl(INADDR_ANY);

    if ((s = socket(AF_INET, SOCK_STREAM, 0)) < 0)
        return -1;

    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char *) &r, sizeof(r));

    if (bind(s, &a, sizeof(a)) < 0)
        return -1;

    if (listen(s, SOMAXCONN) < 0)
        return -1;
    
    return s;
}

