#include "anarcast.h"

#define DATABASE_SIZE      (1024*1024)
#define VERIFY_INTERVAL    (60*60)
#define VERIFY_CONCURRENCY 1

int
main (int argc, char **argv)
{
    int l, m, active, v_active, next, updated;
    long last_verify;
    fd_set r, w;

    char *hosts,  // the start of our data. woohoo
         *end,    // the end of our data. add new data here and move this up
         *hosts0, // our verified addresses
         *end0;   // end of verified addresses
    
    struct {
	char type; // 'n' == normal, 'v' == verification
	unsigned int count; // number of addresses to send
	unsigned int off; // offset in database
	int peeraddy; // addr of peer
    } a[FD_SETSIZE];
    
    if (argc != 1) {
	fprintf(stderr, "Usage: %s (no switches)\n", argv[0]);
	exit(2);
    }
    
    chdir_to_home();
    
    if ((l = open("inform_database", O_RDWR|O_CREAT, 0644)) == -1)
	die("open() of inform_database failed");
    
    if (ftruncate(l, DATABASE_SIZE) == -1)
	die("ftruncate() of inform_database failed");

    hosts = mmap(0, DATABASE_SIZE, PROT_READ|PROT_WRITE, MAP_SHARED, l, 0);
    if (hosts == MAP_FAILED)
	die("mmap() of inform_database failed");
    
    m = 0;
    if (!(end = memmem(hosts, DATABASE_SIZE, &m, 4)))
	die("cannot find end of inform_database");
    
    // maybe the last host ended in \0*.
    while ((end - hosts) % 4)
	end++;

    if (close(l) == -1)
	die("close() failed");
    
    printf("%d Anarcast servers in database.\n", (end-hosts)/4);
    
    hosts0 = end0 = mbuf(DATABASE_SIZE);
    
    l = listening_socket(INFORM_SERVER_PORT, INADDR_ANY);
    last_verify = 0;
    v_active = 0;
    updated = 0;
    active = 0;
    next = 0;
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) {
	int n;
	fd_set s, x;
	struct timeval tv = {60, 0};
	
	if (next || time(NULL) > last_verify + VERIFY_INTERVAL) {
	    if (!next) {
		updated = 0;
		last_verify = time(NULL);
		memcpy(hosts0, hosts, DATABASE_SIZE);
		end0 = hosts0;
	    }
	    while (v_active < VERIFY_CONCURRENCY && next < (end-hosts)/4) {
	        struct sockaddr_in s;
		extern int errno;
		int c;
		memset(&s, 0, sizeof(s));
		s.sin_family = AF_INET;
		s.sin_port = htons(ANARCAST_SERVER_PORT);
		memcpy(&s.sin_addr.s_addr, &hosts[next * 4], 4);
		if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
		    die("socket() failed");
		set_nonblock(c);
		if (connect(c, &s, sizeof(s)) == -1) {
		    if (errno == EINPROGRESS) {
			FD_SET(c, &w);
			a[c].type = 'v';
			a[c].peeraddy = s.sin_addr.s_addr;
			if (c >= m) m = c + 1;
			v_active++;
		    } else {
		        printf("%-15s Failed. (%s)\n", inet_ntoa(s.sin_addr), strerror(errno));
			if (close(c) == -1)
			    die("close() failed");
		    }
		} else { // success!
		    printf("%-15s Verified.\n", inet_ntoa(s.sin_addr));
		    memcpy(end0, &s.sin_addr.s_addr, 4);
		    end0 += 4;
		    if (close(c) == -1)
			die("close() failed");
		}
		next++;
	    }
	    if (next == (end-hosts)/4) // all done
		next = 0;
	}
	
	s = r, x = w;
	if (select(m, &s, &x, NULL, &tv) == -1)
	    die("select() failed");
	
	if (FD_ISSET(l, &s)) {
	    struct sockaddr_in s;
	    int c, b = sizeof(s);
	    // accept a connection
	    if ((c = accept(l, &s, &b)) == -1) {
		ioerror();
		continue;
	    }
	    set_nonblock(c);
	    FD_SET(c, &w);
	    active++;
	    if (c >= m) m = c + 1;
	    a[c].type = 'n';
	    a[c].count = (end-hosts)/4;
	    a[c].off = -4; // sizeof count value
	    if (c == m) m++;
	    if (!memmem(hosts, end-hosts, &s.sin_addr.s_addr, 4)) {
		memcpy(end, &s.sin_addr.s_addr, 4);
		end += 4;
		printf("%-15s Added.\n", inet_ntoa(s.sin_addr));
	    } else
		printf("%-15s Already known.\n", inet_ntoa(s.sin_addr));
	}

	for (n = 0 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) {
		if (a[n].type == 'v') {
		    int status, b = 4;
		    struct in_addr s = {a[n].peeraddy};
		    if (getsockopt(n, SOL_SOCKET, SO_ERROR, &status, &b) == -1)
			die("getsockopt() failed");
		    if (!status) { // success!
			printf("%-15s Verified.\n", inet_ntoa(s));
			memcpy(end0, &a[n].peeraddy, 4);
			end0 += 4;
		    } else
			printf("%-15s Failed. (%s)\n", inet_ntoa(s), strerror(status));
		    FD_CLR(n, &w);
		    v_active--;
		    if (close(n) == -1)
			die("close() failed");
		} else {
		    int c = a[n].off;
		    if (c < 0)
			c = write(n, &(&a[n].count)[4 + c], -c);
		    else
			c = write(n, &hosts[a[n].off], a[n].count * 4 - a[n].off);
		    if (c <= 0 || (a[n].off += c) == a[n].count * 4) {
			FD_CLR(n, &w);
			if (n+1 == m) m--;
			active--;
			if (close(n) == -1)
			    die("close() failed");
		    }
		}
	    }

	if (!active && !v_active && !next && !updated) {
	    int pre = (end-hosts)/4, post = (end0-hosts0)/4;
	    memset(end0, 0, 4); // terminator
	    memcpy(hosts, hosts0, post * 4 + 4);
	    end = hosts + (post * 4);
	    msync(hosts, post * 4 + 4, MS_SYNC);
	    updated = 1;
	    printf("Database updated. %d of %d servers removed.\n", pre-post, pre);
	}
    }
}

