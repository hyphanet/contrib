#define DATABASE_SIZE (1024*1024)
#define WEED_INTERVAL (60*60)

#include "anarcast.h"
#include <pthread.h>

// thread-related filth
pthread_mutex_t mutex;
pthread_cond_t cond;
int waiting;

// this thread tries to connect to every server in the database. when it's
// done, it waits on our condition variable.  the main loop waits until there
// are no more connections and then broadcasts the condition, which causes our
// thread to grab the mutex, update the database, and start over after perhaps
// pausing a while
void * thread (void *arg);

char *hosts, // the start of our data. woohoo
     *end, // the end of our data. add new data here and move this up
     *off[FD_SETSIZE]; // the offsets of our current connections in the data

int
main (int argc, char **argv)
{
    unsigned int l, m, active;
    long last_weeding;
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
    
    l = listening_socket(INFORM_SERVER_PORT);
    last_weeding = time(NULL);
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
	struct timeval tv = {2,0};
	
	if ((n = select(m, &s, &x, NULL, &tv)) == -1)
	    die("select() failed");
	
	// tell our thread to grab the mutex and update the database
	if (!n) pthread_cond_broadcast(&cond);

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) {
		struct sockaddr_in a;
		int c, b = sizeof(a);
		// accept a connection
		// I NEED TO CONNECT BACK TO THE HOST BECAUSE IT MIGHT BE SPOOFED!!!
		if ((c = accept(n, &a, &b)) == -1) {
		    ioerror();
		    continue;
		}
		set_nonblock(c);
		active++;
		FD_SET(c, &w);
		off[c] = hosts;
		if (c == m) m++;
		if (!memmem(hosts, end-hosts, &a.sin_addr.s_addr, 4)) {
		    memcpy(end, &a.sin_addr.s_addr, 4);
		    end += 4;
		    printf("%-15s Added.\n", inet_ntoa(a.sin_addr));
		} else
		    printf("%-15s Already known.\n", inet_ntoa(a.sin_addr));
	    }

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) {
		int c = write(n, off[n], end-off[n]);
		if (c <= 0) {
		    if (off[n] != hosts) ioerror();
		    active--;
		    FD_CLR(n, &w);
		    if (close(n) == -1)
			die("close() failed");
		} else if ((off[n] += c) == end) {
		    active--;
		    FD_CLR(n, &w);
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
	    printf("Sleeping %d seconds before smoking up again.\n", i);
	    sleep(i);
	}

	lastweed = l; // update our lastweed to the time when we finished
    }
}

