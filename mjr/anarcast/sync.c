#include "anarcast.h"

int
lookup_host (char *host)
{
    struct hostent *h;
    extern int h_errno;

    if (!(h = gethostbyname(host))) {
	printf("%s: %s.\n", host, hstrerror(h_errno));
	exit(1);
    }

    return ((struct in_addr *)h->h_addr)->s_addr;
}

int
main (int argc, char **argv)
{
    struct sockaddr_in a;
    int address = 0, count, fd, c;
    char *data;
    pid_t pid;

    if (argc > 2) {
	printf("Usage: %s <optional database server address>\n", argv[0]);
	exit(2);
    }
    
    chdir_to_home();
    
    if ((fd = open("address_database", O_RDWR)) == -1) {
	extern int errno;
	
	if (errno != ENOENT)
	    die("open() of address_database failed");
	
	if (argc == 1) {
	    puts("Database server address is not configured.");
	    puts("Specify it as your first argument.");
	    exit(2);
	}

	address = lookup_host(argv[1]);

	if ((fd = open("address_database", O_RDWR|O_CREAT, 0644)) == -1)
	    die("open() of address_database failed");
    } else {
	if (read(fd, &address, 4) != 4) {
	    puts("Database is corrupt. Specify a new database server address.");
	    exit(1);
	}
    }
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = argv[1] ? lookup_host(argv[1]) : address;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	die("connect to server failed");
    
    if (readall(c, &count, 4) != 4)
	die("error reading from server");
    
    data = mbuf(count * 4);

    if (readall(c, data, count * 4) != count * 4)
	die("error reading from server");
    
    if (lseek(fd, 0, SEEK_SET) == -1)
	die("lseek() failed");

    if (write(fd, &a.sin_addr.s_addr, 4) != 4
     || write(fd, &count, 4) != 4
     || write(fd, data, count * 4) != count * 4)
	die("write to address_database failed");
    
    if (close(fd) == -1 || close(c) == -1)
	die("close() failed");

    printf("Loaded %d addresses.\n", count);
    
    if ((fd = open(".anarcast.pid", O_RDONLY)) == -1
     || read(fd, &pid, sizeof(pid)) != sizeof(pid)
     || kill(pid, SIGHUP) == -1)
	puts("Proxy was not signaled because it is not running.");

    return 0;
}

