#include "anarcast.h"
#include <dirent.h>

#define BLOCKSIZE 512

struct {
    char name[100];
    char mode[8];
    char uid[8];
    char gid[8];
    char size[12];
    char mtime[12];
    char chksum[8];
    char link;
    char linkname[100];
    char padding[256];
} header;

int fd;
char zeros[BLOCKSIZE];
    
int
visible (const struct dirent *d)
{
    return d->d_name[0] != '.';
}

void
recurse (char *path)
{
    struct dirent **d;
    extern int errno;
    int n, i, tfd, chksum, m = scandir(path, &d, visible, alphasort);
    char tfn[] = "/tmp/le-XXXXXX";
    off_t o;
	
    if (m == -1) {
	printf("Cannot scan %s. Aborting. (%s)\n", path, strerror(errno));
	exit(1);
    }

    if (!m) {
	printf("Skipping empty directory %s.\n", path);
	return;
    }
    
    if ((tfd = mkstemp(tfn)) == -1)
	die("mkstemp() failed");
    unlink(tfn);

    for (n = 0 ; n < m ; n++) {
	struct stat s;
	char newpath[strlen(path) + strlen(d[n]->d_name) + 2];
	sprintf(newpath, "%s/%s", path, d[n]->d_name);
	if (stat(newpath, &s) == -1) {
	    printf("Cannot stat %s. Aborting. (%s)\n", newpath, strerror(errno));
	    exit(1);
        } else if (S_ISDIR(s.st_mode)) { // directory - recurse
	    recurse(newpath);
        } else if (S_ISREG(s.st_mode)) { // add keyfile to candidates file
	    int dfd;
	    if ((dfd = open(newpath, O_RDONLY)) == -1) {
		printf("Cannot open %s. Aborting. (%s)\n", newpath, strerror(errno));
		exit(1);
	    }
	    if (write(tfd, d[n]->d_name, strlen(d[n]->d_name)+1) != strlen(d[n]->d_name)+1) // write null-terminated file name
		die("write() failed");
	    if (write(tfd, &s.st_size, 4) != 4) // write key data length
		die("write() failed");
	    o = 0;
	    if (sendfile(tfd, dfd, &o, s.st_size) != s.st_size) // write key data
		die("sendfile() failed");
	    if (close(dfd) == -1)
		die("close() failed");
	} else {
	    printf("Weird file %s. Aborting.\n", newpath);
	    exit(1);
	}
    }

    if ((n = lseek(tfd, 0, SEEK_CUR)) == -1)
	die("lseek() failed");

    if (lseek(tfd, 0, SEEK_SET) == -1)
	die("lseek() failed");
    
    if (strlen(path) + strlen(".candidates") + 2 > 100) {
	printf("Path name is too long. Aborting.\n");
        exit(1);
    }
    
    sprintf(header.name, "%s/.candidates", path);
    snprintf(header.size, 12, "%011o ", n);
    memset(header.chksum, ' ', sizeof(header.chksum));
    chksum = 0;
    for (i = 0 ; i < sizeof(header) ; i++)
	chksum += (unsigned char) ((char *)&header)[i];
    sprintf(header.chksum, "%06o", chksum);
    
    if (write(fd, &header, BLOCKSIZE) != BLOCKSIZE)
	die("write() failed");
    
    o = 0;
    if (sendfile(fd, tfd, &o, n) != n)
	die("sendfile() failed");

    if (close(tfd) == -1)
	die("close() failed");

    if (write(fd, zeros, BLOCKSIZE - (n % BLOCKSIZE)) == -1)
	die("write() failed");
}

int
main (int argc, char **argv)
{
    int off;
    char temp[] = "/tmp/le-XXXXXX";

    if (argc != 3) {
	printf("Usage: %s <directory> <outfile>\n", argv[0]);
	exit(2);
    }

    if ((fd = mkstemp(temp)) == -1)
	die("mkstemp() failed");
    
    if (argv[1][strlen(argv[1])-1] == '/')
	argv[1][strlen(argv[1])-1] = 0;
    
    memset(zeros, 0, BLOCKSIZE);
    
    memset(&header, 0, BLOCKSIZE);
    snprintf(header.mode, 8, "%06o ", 0644);
    snprintf(header.mtime, 12, "%011lo ", time(NULL));
    header.link = '0';
    
    recurse(argv[1]);
    
    if ((off = lseek(fd, 0, SEEK_CUR)) == -1)
	die("lseek() failed");
    
    while (off / BLOCKSIZE % 20) {
        if (write(fd, zeros, BLOCKSIZE) != BLOCKSIZE)
	    die("write() failed");
	off += BLOCKSIZE;
    }

    if (close(fd) == -1)
	die("close() failed");
    
    sprintf(zeros, "gzip -cf %s > %s", temp, argv[2]);
    off = system(zeros);
    unlink(temp);
    return off;
}
