#include "anarcast.h"
#include "sha.c"

void touch_inform_server (char *server);

struct state {
    char type, hash[HASHLEN], *data;
    unsigned int off, len;
} a[FD_SETSIZE];

int
main (int argc, char **argv)
{
    int m, l;
    fd_set r, w;
    
    if (argc != 2) {
	fprintf(stderr, "Usage: %s <inform server>\n", argv[0]);
	exit(2);
    }
    
    chdir_to_home();
    touch_inform_server(argv[1]);
    l = listening_socket(ANARCAST_SERVER_PORT);
    
    puts("Server started.");
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    FD_SET(l, &r);
    m = l + 1;
    
    for (;;) {
	int i, n, c;
	struct stat st;
	fd_set s = r, x = w;

	i = select(m, &s, &x, NULL, NULL);
	if (i == -1) die("select() failed");
	if (!i) continue;

	//=== read ==========================================================
	
	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &s)) break;
	if (n == m) goto write;

	// accept connection
	if (n == l) {
	    if ((c = accept(n, NULL, 0)) != -1) {
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
		if (close(n) == -1)
		    die("close() failed");
		continue;
	    }
	    if (a[n].type == 'r')
	        a[n].off = -HASHLEN;
	    else
		a[n].off = -4;
	    continue;
	}
	
	// insert
	if (a[n].type == 'i') {
            // read length
            i = a[n].off;
            if (i < 0) {
                if ((c = read(n, &(&a[n].len)[4+i], -i)) <= 0) {
                    FD_CLR(n, &r);
		    if (close(n) == -1)
		        die("close() failed");
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
		FD_CLR(n, &r);
		if (munmap(a[n].data, a[n].len) == -1)
		    die("munmap() failed");
		if (close(n) == -1)
		    die("close() failed");
		continue;
	    }
	    a[n].off += c;
	    if (a[n].off == a[n].len) {
		char hex[HASHLEN*2+1];
		sha_buffer(a[n].data, a[n].len, a[n].hash);
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
		if (close(n) == -1)
		    die("close() failed");
		continue;
	    }
	}
	
	// request
	if (a[n].type == 'r') {
	    char hex[HASHLEN*2+1];
	    // read hash
	    i = a[n].off;
	    if (i < 0) {
		if ((c = read(n, &a[n].hash[HASHLEN+i], -i)) <= 0) {
		    ioerror();
		    FD_CLR(n, &r);
		    if (close(n) == -1)
		        die("close() failed");
		    continue;
		}
		a[n].off += c;
		if (a[n].off) continue;
	    }
	    bytestohex(hex, a[n].hash, HASHLEN);
	    if (stat(hex, &st) == -1) {
		FD_CLR(n, &r);
		if (close(n) == -1)
		    die("close() failed");
		continue;
	    }
	    if ((c = open(hex, O_RDONLY)) == -1)
		die("open() failed");
	    a[n].data = mmap(0, st.st_size, PROT_READ, MAP_SHARED, c, 0);
	    if (a[n].data == MAP_FAILED)
		die("mmap() failed");
	    if (close(c) == -1)
	        die("close() failed");
	    FD_CLR(n, &r);
	    FD_SET(n, &w);
	    a[n].len = st.st_size;
	    a[n].off = -4;
	}

write:	//=== write =========================================================

	for (n = 3 ; n < m ; n++)
	    if (FD_ISSET(n, &x)) break;
	if (n == m) continue;
	
	i = a[n].off;
	if (i < 0) c = write(n, &(&a[n].len)[4+i], -i);
	else c = write(n, &a[n].data[i], a[n].len - i);
	a[n].off += c;
	if (c <= 0 || a[n].off == a[n].len) {
	    if (c > 0) {
		char hex[HASHLEN*2+1];
		bytestohex(hex, a[n].hash, HASHLEN);
		printf("%s > %s\n", timestr(), hex);
	    } else ioerror();
	    FD_CLR(n, &w);
	    if (close(n) == -1)
		die("close() failed");
	    if (munmap(a[n].data, a[n].len) == -1)
		die("munmap() failed");
	}
    }
}

void
touch_inform_server (char *server)
{
    struct sockaddr_in a;
    struct hostent *h;
    int c;

    if (!(h = gethostbyname(server))) {
	printf("Warning: %s: %s.\n", server, hstrerror(h_errno));
	return;
    }
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = ((struct in_addr *)h->h_addr)->s_addr;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	printf("Warning: connect() to %s failed.\n", server);
    
    if (close(c) == -1)
	die("close() failed");
}

