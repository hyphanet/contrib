#define DATABASE_SIZE (1024*1024)
#define WEED_INTERVAL (60*60)

#include "anarcast.h"
#include <pthread.h>

// thread-related filth
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
int waiting;

// this thread tries to connect to every server in the database. when it's
// done, it waits on our condition variable.  the main loop waits until there
// are no more connections and then broadcasts the condition, which causes our
// thread to grab the mutex, update the database, and start over after perhaps
// pausing a while
void * thread (void *arg);

char *hosts, // the start of our data. woohoo
     *end; // the end of our data. add new data here and move this up

struct {
    unsigned int count; // number of addresses to send
    unsigned int off; // offset in database
} a[FD_SETSIZE];

int
main (int argc, char **argv)
{
    int l, m, active;
    pthread_t t;
    fd_set r, w;
    
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
    
    l = listening_socket(INFORM_SERVER_PORT, INADDR_ANY);
    active = 0;
    
    if (pthread_create(&t, NULL, thread, NULL))
	die("pthread_create() failed");
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) {
	int n;
	fd_set s = r, x = w;
	struct timeval tv = {60, 0};
	
	// tell our thread to grab the mutex and update the database
	if (!active)
	    pthread_cond_broadcast(&cond);
	
	if ((n = select(m, &s, &x, NULL, &tv)) == -1)
	    die("select() failed");
	
	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) {
		struct sockaddr_in s;
		int c, b = sizeof(s);
		// accept a connection
		if ((c = accept(n, &s, &b)) == -1) {
		    ioerror();
		    continue;
		}
		set_nonblock(c);
		active++;
		FD_SET(c, &w);
		if (c >= m) m = c + 1;
		a[c].count = (end-hosts)/4;
		a[c].off = -4; // sizeof count value
		if (c == m) m++;
		if (!memmem(hosts, end-hosts, &s.sin_addr.s_addr, 4)) {
		    pthread_mutex_lock(&mutex);
		    memcpy(end, &s.sin_addr.s_addr, 4);
		    end += 4;
		    pthread_mutex_unlock(&mutex);
		    printf("%-15s Added.\n", inet_ntoa(s.sin_addr));
		} else
		    printf("%-15s Already known.\n", inet_ntoa(s.sin_addr));
	    }

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) {
		int c = a[n].off;
	        
		if (c < 0)
		    // write address count
		    c = write(n, &(&a[n].count)[4 + c], -c);
		else
		    // write addresses
		    c = write(n, &hosts[a[n].off], a[n].count * 4 - a[n].off);
		
		if (c <= 0) {
		    active--;
		    FD_CLR(n, &w);
		    if (n+1 == m) m--;
		    if (close(n) == -1)
			die("close() failed");
		} else if ((a[n].off += c) == a[n].count * 4) {
		    active--;
		    FD_CLR(n, &w);
		    if (n+1 == m) m--;
		    if (close(n) == -1)
			die("close() failed");
		}
	    }
    }
}

void *
thread (void *arg)
{
    int f;
    long l, lastweed = time(NULL);
    char *p, *q, *b = mbuf(DATABASE_SIZE);
    
    for (;;) {
	// try to connect to every host in our database. if we can, then stick
	// it in the new buffer and advance the new end pointer (q)
	for (p = q = hosts, f = 0 ; *(int*)p ; p += 4) {
	    int c;
	    struct sockaddr_in a;
	    
	    memset(&a, 0, sizeof(a));
	    a.sin_family = AF_INET;
	    a.sin_port = htons(ANARCAST_SERVER_PORT);
	    a.sin_addr.s_addr = *(int*)p;
	    
	    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
		die("socket() failed");
	    
	    if (connect(c, &a, sizeof(a)) != -1) {
		memcpy(q, p, 4);
		q += 4;
	    } else {
		printf("%s unreachable.\n", inet_ntoa(a.sin_addr));
		f++;
	    }
	    
	    if (close(c) == -1)
		die("close() failed");
	}
	
	printf("%d of %d total hosts unreachable.\n", f, (end-hosts)/4);
	
	if (!f) {
	    puts("No unreachable hosts. Not updating database.");
	} else if (f * 2 > (end-hosts)/4) {
	    puts("Too many unreachable hosts. Not updating database.");
	} else {
	    // wait for our chance and then update the database
	    puts("Waiting for current transfers to complete before updating database.");
	    pthread_mutex_lock(&mutex);
	    pthread_cond_wait(&cond, &mutex);
	    memcpy(hosts, b, q-hosts);
	    end = hosts + (q-hosts);
	    puts("Database updated.");
	    pthread_mutex_unlock(&mutex);
	}

	// wait for a while if we must
	if ((l = time(NULL)) < lastweed + WEED_INTERVAL) {
	    int i = lastweed + WEED_INTERVAL - l;
	    printf("Sleeping %d seconds before next verification run.\n", i);
	    sleep(i);
	}

	lastweed = l; // update our lastweed to the time when we finished
    }
}

