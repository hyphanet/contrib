#include "anarcast.h"
#include "sha.c"

struct state {
    char type, hash[HASH_LEN], *data;
    unsigned int off, len;
} a[FD_SETSIZE];

int
main (int argc, char **argv)
{
    int m, l;
    fd_set r, w;
    
    chdir_to_home();
    l = listening_socket(ANARCAST_SERVER_PORT);
    
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
	    else a[n].off = -4;
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
	    // read data
	    i = a[n].off;
	    if (!i) a[n].data = mbuf(a[n].len);
	    if ((c = read(n, &a[n].data[i], a[n].len - i)) <= 0) {
		ioerror();
		munmap(a[n].data, a[n].len);
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    a[n].off += c;
	    if (a[n].off == a[n].len) {
		char hex[HASH_LEN*2+1];
		sha_buffer(a[n].data, a[n].len, a[n].hash);
		bytestohex(hex, a[n].hash, HASH_LEN);
		printf("%s < %s\n", timestr(), hex);
		if (stat(hex, &st) == -1) {
		    if ((i = open(hex, O_WRONLY | O_CREAT, 0644)) == -1)
			err(1, "open(2) failed");
		    if (writeall(i, a[n].data, a[n].len) != a[n].len)
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
	    char hex[HASH_LEN*2+1];
	    // read hash
	    i = a[n].off;
	    if (i < 0) {
		if ((c = read(n, &a[n].hash[HASH_LEN+i], -i)) <= 0) {
		    ioerror();
		    FD_CLR(n, &r);
		    close(n);
		    continue;
		}
		a[n].off += c;
		if (a[n].off) continue;
	    }
	    bytestohex(hex, a[n].hash, HASH_LEN);
	    if (stat(hex, &st) == -1) {
		FD_CLR(n, &r);
		close(n);
		continue;
	    }
	    if ((c = open(hex, O_RDONLY)) == -1)
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
	    a[n].off += (c = write(n, &a[n].hash[i], HASH_LEN - i));
	    if (c <= 0 || a[n].off == HASH_LEN) {
		if (c <= 0) ioerror();
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
		    char hex[HASH_LEN*2+1];
		    bytestohex(hex, a[n].hash, HASH_LEN);
		    printf("%s > %s\n", timestr(), hex);
		} else ioerror();
	        FD_CLR(n, &w);
	        close(n);
	        munmap(a[n].data, a[n].len);
	    }
	}
    }
}

