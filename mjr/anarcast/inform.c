#define DATABASE_SIZE (1024*1024)
#define WEED_INTERVAL (60*60)

#include "anarcast.h"

void weed_dead_servers ();

char *hosts, *end, *off[FD_SETSIZE];

int
main ()
{
    unsigned int l, m, active;
    long last_weeding;
    fd_set r, w;

    if ((l = open("inform_database", O_RDWR|O_CREAT, 0644)) == -1)
	err(1, "open() of inform_database failed");
    
    if (ftruncate(l, DATABASE_SIZE) == -1)
	err(1, "ftruncate() of infrom_database failed");

    hosts = mmap(0, DATABASE_SIZE, PROT_READ|PROT_WRITE, MAP_SHARED, l, 0);
    if (hosts == MAP_FAILED)
	err(1, "mmap() of inform_database failed");
    
    m = 0;
    if (!(end = memmem(hosts, DATABASE_SIZE, &m, 4)))
	err(1, "cannot find end of inform_database");

    if (close(l) == -1)
	err(1, "close() failed");
    
    if ((l = listening_socket(INFORM_SERVER_PORT)) == -1)
	err(1, "can't grab port %d", INFORM_SERVER_PORT);
    
    last_weeding = time(NULL);
    active = 0;
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) {
	int n;
	fd_set s = r, x = w;
	struct timeval tv = {2,0};
	
	n = select(m, &s, &x, NULL, &tv);
	if (n == -1) err(1, "select(2) failed");
	if (!n) {
	    if (!active && time(NULL) > last_weeding + WEED_INTERVAL) {
		puts("Weeding....");
		weed_dead_servers();
		last_weeding = time(NULL);
	    }
	    continue;
	}

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) {
		struct sockaddr_in a;
		int c, b = sizeof(a);
		if ((c = accept(n, &a, &b)) == -1) {
		    ioerror();
		    continue;
		}
		active++;
		FD_SET(c, &w);
		off[c] = hosts;
		if (c == m) m++;
		if (!memmem(hosts, end-hosts, &a.sin_addr.s_addr, 4)) {
		    memcpy(end, &a.sin_addr.s_addr, 4);
		    end += 4;
		}
		printf("%s added.\n", inet_ntoa(a.sin_addr));
	    }

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) {
		int c = write(n, off[n], end-off[n]);
		if (c <= 0) {
		    if (off[n] != hosts) ioerror();
		    active--;
		    FD_CLR(n, &w);
		    close(n);
		} else if ((off[n] += c) == end) {
		    active--;
		    FD_CLR(n, &w);
		    close(n);
		}
	    }
    }
}

void
weed_dead_servers ()
{
    int f, c;
    char *p, *q, *b = mbuf(DATABASE_SIZE);
    
    memset(b, 0, DATABASE_SIZE);
    
    for (p = q = hosts, f = 0 ; *(int*)p ; p += 4) {
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_port = htons(ANARCAST_SERVER_PORT);
	a.sin_addr.s_addr = *(int*)p;
	
	if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	    err(1, "socket(2) failed");
	
	if (connect(c, &a, sizeof(a)) != -1) {
	    memcpy(q, p, 4);
	    q += 4;
	} else {
	    printf("%s unreachable.\n", inet_ntoa(a.sin_addr));
	    f++;
	}
	
	close(c);
    }
    
    printf("%d of %d total hosts unreachable.\n", f, (end-hosts)/4);
    
    if (f * 2 > (end-hosts)/4) {
	puts("Too many unreachable hosts--not updating database.");
    } else {
	memcpy(hosts, b, q-b);
	end = q;
    }
    
    if (munmap(b, DATABASE_SIZE) == -1)
	err(1, "munmap() failed");
}

