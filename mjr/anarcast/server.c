#include "anarcast.h"
#include "crypt.c"

int
main (int argc, char **argv)
{
    int m, l;
    fd_set r, w;

    // states
    struct {
	char type, hash[HASHLEN], *data;
	unsigned int off, len;
    } a[FD_SETSIZE];
    
    if (argc != 1) {
	fprintf(stderr, "Usage: %s (no options)\n", argv[0]);
	exit(2);
    }
    
    chdir_to_home();
    l = listening_socket(ANARCAST_SERVER_PORT, INADDR_ANY);
    
    puts("Server started.");
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) { // and we're off!
	int i, n, c;
	struct stat st;
	fd_set s = r, x = w;
	
	i = select(m, &s, &x, NULL, NULL);
	if (i == -1) die("select() failed");
	if (!i) continue;
	
	for (n = 0 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) {
		if (n == l) { // accept connection
		    if ((c = accept(n, NULL, 0)) != -1) {
			set_nonblock(c);
			FD_SET(c, &r);
			if (c >= m) m = c + 1;
			memset(&a[c], 0, sizeof(a[c]));
		    }
		} else if (!a[n].type) { // read type
		    if (read(n, &a[n].type, 1) != 1 || (a[n].type != 'r' && a[n].type != 'i')) {
			FD_CLR(n, &r);
			if (n + 1 == m) m--;
			if (close(n) == -1)
			    die("close() failed");
		    } else {
		        if (a[n].type == 'r')
			    a[n].off = -HASHLEN;
		        else
			    a[n].off = -4;
		    }
		} else if (a[n].type == 'i') { // insert
		    i = a[n].off;
		    if (i < 0) { // read data length
			if ((c = read(n, &(&a[n].len)[4+i], -i)) <= 0) {
			    FD_CLR(n, &r);
			    if (n+1 == m) m--;
			    if (close(n) == -1)
				die("close() failed");
			} else 
			    a[n].off += c;
		    } else { // read data
			i = a[n].off;
			if (!i) a[n].data = mbuf(a[n].len);
			if ((c = read(n, &a[n].data[i], a[n].len - i)) <= 0) {
			    ioerror();
			    FD_CLR(n, &r);
			    if (n + 1 == m) m--;
			    if (munmap(a[n].data, a[n].len) == -1)
				die("munmap() failed");
			    if (close(n) == -1)
				die("close() failed");
			} else if ((a[n].off += c) == a[n].len) { // all done!
			    char hex[HASHLEN*2+1];
			    hashdata(a[n].data, a[n].len, a[n].hash);
			    bytestohex(hex, a[n].hash, HASHLEN);
			    printf("%s < %s\n", timestr(), hex);
			    if (stat(hex, &st) == -1) {
				if ((i = open(hex, O_WRONLY | O_CREAT, 0644)) == -1)
				    die("open() failed");
				if (writeall(i, a[n].data, a[n].len) != a[n].len)
				    die("write() to file failed");
				if (close(i) == -1)
				    die("close() failed");
			    }
			    if (munmap(a[n].data, a[n].len) == -1)
				die("munmap() failed");
			    FD_CLR(n, &r);
			    if (n + 1 == m) m--;
			    if (close(n) == -1)
				die("close() failed");
			}
		    }
		} else if (a[n].type == 'r') { // request
		    char hex[HASHLEN*2+1];
		    i = a[n].off;
		    if ((c = read(n, &a[n].hash[HASHLEN+i], -i)) <= 0) { // read hash
		        ioerror();
		        FD_CLR(n, &r);
		        if (n + 1 == m) m--;
		        if (close(n) == -1)
		   	    die("close() failed");
		    } else if (!(a[n].off += c)) { // hash is all read
			bytestohex(hex, a[n].hash, HASHLEN);
			if (stat(hex, &st) == -1) { // not found
			    FD_CLR(n, &r);
			    if (n + 1 == m) m--;
			    if (close(n) == -1)
				die("close() failed");
			} else { // data found
			    if ((c = open(hex, O_RDONLY)) == -1)
				die("open() failed");
			    a[n].data = mmap(0, st.st_size, PROT_READ, MAP_SHARED, c, 0);
			    if (a[n].data == MAP_FAILED)
				die("mmap() failed");
			    if (close(c) == -1)
				die("close() failed");
			    FD_CLR(n, &r); // no more reading...
			    FD_SET(n, &w); // we wanna write our data back to the client now!
			    a[n].len = st.st_size;
			    a[n].off = -4; // data length int
			}
		    }
		}
	    }

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) {
		i = a[n].off;
		if (i < 0) // write data length
		    c = write(n, &(&a[n].len)[4+i], -i);
		else // write the data!
		    c = write(n, &a[n].data[i], a[n].len - i);
		if (c <= 0 || (a[n].off += c) == a[n].len) {
		    if (c > 0) { // success!
			char hex[HASHLEN*2+1];
			bytestohex(hex, a[n].hash, HASHLEN);
			printf("%s > %s\n", timestr(), hex);
		    } else ioerror(); // error, yuck.
		    FD_CLR(n, &w);
		    if (n + 1 == m) m--;
		    if (close(n) == -1)
			die("close() failed");
		    if (munmap(a[n].data, a[n].len) == -1)
			die("munmap() failed");
		}
	    }
    }
}

