#define LISTEN_PORT           6666
#define HASH_LEN              20

#include <err.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include "sha.c"

struct state {
    char type, hash[HASH_LEN], *data;
    unsigned int len, off;
} a[FD_SETSIZE];

int listening_socket ();
void bytestohex (char *hex, char *bytes, int blen);

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
    
    if ((l = listening_socket()) == -1)
	err(1, "can't grab port %d", LISTEN_PORT);
    
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
	    if (a[n].type == 'r') {
	        a[n].off = -HASH_LEN;
	    } else if (a[n].type == 'i') {
		a[n].off = -4;
	    } else {
		FD_CLR(n, &r);
		close(n);
	    }
	    continue;
	}
	
	// insert
	if (a[n].type == 'i') {
	    // read length
	    i = a[n].off;
	    if (i < 0) {
		if ((c = read(n, &(&a[n].len)[4+i], -i)) <= 0) {
		    FD_CLR(n, &r);
		    close(n);
		    continue;
		}
		a[n].off += c;
		continue;
	    }
	    // map temp file
	    if (!i) {
		strcpy(b, "/tmp/anarcast-XXXXXX");
		if ((c = mkstemp(b)) == -1)
		    err(1, "mkstemp(3) failed");
		if (ftruncate(c, a[n].len) == -1)
		    err(1, "ftruncate(2) failed");
		a[n].data = mmap(0, a[n].len, PROT_READ | PROT_WRITE, MAP_SHARED, c, 0);
		if (a[n].data == MAP_FAILED)
		    err(1, "mmap(2) failed");
		close(c);
	    }
	    // read data
	    if ((c = read(n, &a[n].data[i], a[n].len - i)) <= 0) {
		munmap(a[n].data, a[n].len);
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    a[n].off += c;
	    if (a[n].off == a[n].len) {
		long t = time(NULL);
		struct tm *tm = localtime(&t);
		char ts[128];
		strftime(ts, 128, "%T", tm);
		sha_buffer(a[n].data, a[n].len, a[n].hash);
		bytestohex(b, a[n].hash, HASH_LEN);
		printf("%s < %s (%d bytes)\n", ts, b, a[n].len);
		if (stat(b, &st) == -1) {
		    if ((i = open(b, O_WRONLY | O_CREAT, 0644)) == -1)
			err(1, "open(2) failed");
		    if (write(i, a[n].data, a[n].len) != a[n].len)
			err(1, "write(2) to file failed");
		    close(i);
		}
		munmap(a[n].data, a[n].len);
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
	    if ((c = open(b, O_RDONLY)) == -1)
		err(1, "open(2) failed");
	    a[n].data = mmap(0, st.st_size, PROT_READ, MAP_SHARED, c, 0);
	    if (a[n].data == MAP_FAILED)
		err(1, "mmap(2) failed");
	    close(c);
	    FD_CLR(n, &r);
	    FD_SET(n, &w);
	    a[n].len = st.st_size;
	    a[n].off = -4;
	}

write:	//=== write =========================================================

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) break;
	if (n == m) continue;
	
	if (a[n].type == 'i') {
	    i = a[n].off;
	    c = write(n, &a[n].hash[i], HASH_LEN - i);
	    a[n].off += c;
	    if (c <= 0 || a[n].off == HASH_LEN) {
		FD_CLR(n, &w);
		close(n);
	    }
	}
	
	if (a[n].type == 'r') {
	    i = a[n].off;
	    if (i < 0) c = write(n, &(&a[n].len)[4+i], -i);
	    else c = write(n, &a[n].data[i], a[n].len - i);
	    a[n].off += c;
	    if (c <= 0 || a[n].off == a[n].len) {
	        if (c > 0) {
		    long t = time(NULL);
		    struct tm *tm = localtime(&t);
		    char ts[128];
		    strftime(ts, 128, "%T", tm);
		    bytestohex(b, a[n].hash, HASH_LEN);
		    printf("%s > %s (%d bytes)\n", ts, b, a[n].len);
		}
	        FD_CLR(n, &w);
	        close(n);
	        munmap(a[n].data, a[n].len);
	    }
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
    a.sin_port = htons(LISTEN_PORT);
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

void
bytestohex (char *hex, char *bytes, int blen)
{
    static char hextable[] = "0123456789ABCDEF";
    for ( ; blen-- ; bytes++) {
	*hex++ = hextable[*bytes >> 4 & 0x0f];
	*hex++ = hextable[*bytes & 0x0f];
    }
    *hex = 0;
}

