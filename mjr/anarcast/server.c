#include "anarcast.h"
#include "sha.c"

struct state {
    char type, hash[HASH_LEN], *data;
    unsigned int off;
} a[FD_SETSIZE];

int
main (int argc, char **argv)
{
    int m, l;
    char b[1024];
    fd_set r, w;
    
    sprintf(b, "%s/.anarcast", getenv("HOME"));
    mkdir(b, 0755);
    if (chdir(b) == -1)
	err(1, "can't change to %s", b);
    
    if ((l = listening_socket(ANARCAST_SERVER_PORT)) == -1)
	err(1, "can't grab port %d", ANARCAST_SERVER_PORT);
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) {
	int i, n, c;
	struct stat st;
	fd_set s = r, x = w;

	i = select(m, &s, &x, NULL, NULL);
	if (i == -1) err(1, "select(2) failed");
	if (!i) continue;

	//=== read ==========================================================
	
	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) break;
	if (n == m) goto write;

	// accept connection
	if (n == l) {
	    if ((c = accept(n, NULL, 0)) != -1) {
		fcntl(c, F_SETFL, fcntl(c, F_GETFL) | O_NONBLOCK);
		FD_SET(c, &r);
		if (c == m) m++;
		memset(&a[c], 0, sizeof(a[c]));
	    }
	    continue;
	}
	
	// read type
	if (!a[n].type) {
	    read(n, &a[n].type, 1);
	    if (a[n].type != 'r' && a[n].type != 'i') {
		puts("Format Error: Unrecognized transaction type.");
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    if (a[n].type == 'r')
	        a[n].off = -HASH_LEN;
	    continue;
	}
	
	// insert
	if (a[n].type == 'i') {
	    // read data
	    i = a[n].off;
	    if (!i) a[n].data = mbuf(PART_SIZE);
	    if ((c = read(n, &a[n].data[i], PART_SIZE - i)) <= 0) {
		ioerror(c);
		munmap(a[n].data, PART_SIZE);
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    a[n].off += c;
	    if (a[n].off == PART_SIZE) {
		long t = time(NULL);
		struct tm *tm = localtime(&t);
		char ts[128];
		strftime(ts, 128, "%T", tm);
		sha_buffer(a[n].data, PART_SIZE, a[n].hash);
		bytestohex(b, a[n].hash, HASH_LEN);
		printf("%s < %s\n", ts, b);
		if (stat(b, &st) == -1) {
		    if ((i = open(b, O_WRONLY | O_CREAT, 0644)) == -1)
			err(1, "open(2) failed");
		    if (write(i, a[n].data, PART_SIZE) != PART_SIZE)
			err(1, "write(2) to file failed");
		    close(i);
		}
		munmap(a[n].data, PART_SIZE);
		FD_CLR(n, &r);
		FD_SET(n, &w);
		a[n].off = 0;
		continue;
	    }
	}
	
	// request
	if (a[n].type == 'r') {
	    // read hash
	    i = a[n].off;
	    if (i < 0) {
		if ((c = read(n, &a[n].hash[HASH_LEN+i], -i)) <= 0) {
		    ioerror(c);
		    FD_CLR(n, &r);
		    close(n);
		    continue;
		}
		a[n].off += c;
		if (a[n].off) continue;
	    }
	    bytestohex(b, a[n].hash, HASH_LEN);
	    if (stat(b, &st) == -1) {
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    if (st.st_size != PART_SIZE) {
		printf("%s: Bad file size! ", b);
		if (unlink(b) == 0) printf("Unlinked.\n");
		else printf("Unlinking failed!\n");
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    if ((c = open(b, O_RDONLY)) == -1)
		err(1, "open(2) failed");
	    a[n].data = mmap(0, st.st_size, PROT_READ, MAP_SHARED, c, 0);
	    if (a[n].data == MAP_FAILED)
		err(1, "mmap(2) failed");
	    close(c);
	    FD_CLR(n, &r);
	    FD_SET(n, &w);
	}

write:	//=== write =========================================================

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) break;
	if (n == m) continue;
	
	if (a[n].type == 'i') {
	    i = a[n].off;
	    a[n].off += (c = write(n, &a[n].hash[i], HASH_LEN - i));
	    if (c <= 0 || a[n].off == HASH_LEN) {
		if (c <= 0) ioerror(c);
		FD_CLR(n, &w);
		close(n);
	    }
	}
	
	if (a[n].type == 'r') {
	    i = a[n].off;
	    a[n].off += (c = write(n, &a[n].data[i], PART_SIZE - i));
	    if (c <= 0 || a[n].off == PART_SIZE) {
	        if (c > 0) {
		    long t = time(NULL);
		    struct tm *tm = localtime(&t);
		    char ts[128];
		    strftime(ts, 128, "%T", tm);
		    bytestohex(b, a[n].hash, HASH_LEN);
		    printf("%s > %s\n", ts, b);
		} else ioerror(c);
	        FD_CLR(n, &w);
	        close(n);
	        munmap(a[n].data, PART_SIZE);
	    }
	}
    }
}

