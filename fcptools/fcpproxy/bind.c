char *bind_rcs = "$Id: bind.c,v 1.1 2001/09/29 01:30:53 heretic108 Exp $";
/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef _WIN32

#include <io.h>
#include <windows.h>

#else

#include <unistd.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <netdb.h> 
#include <sys/socket.h>
#ifndef __BEOS__
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <sys/signal.h>
#endif

#endif

extern int atoip();

long  remote_ip_long;
char *remote_ip_str;

/*
 * BIND-PORT (portnum)
 *  if success, return file descriptor
 *  if failure, returns -2 if address is in use, otherwise -1
 */
int	bind_port (hostnam, portnum)
char	*hostnam;
int	 portnum;
{
	struct sockaddr_in inaddr;
	int	fd;
	int     one = 1;

	memset ((char * ) &inaddr, '\0', sizeof inaddr);

	inaddr.sin_family      = AF_INET;
	inaddr.sin_addr.s_addr = atoip(hostnam);

	if(sizeof(inaddr.sin_port) == sizeof(short)) {
		inaddr.sin_port = htons(portnum);
	} else {
		inaddr.sin_port = htonl(portnum);
	}

	fd = socket(AF_INET, SOCK_STREAM, 0);

	if (fd < 0) {
		return(-1);
	}

	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char *)&one, sizeof(one));

	if (bind (fd, (struct sockaddr *)&inaddr, sizeof(inaddr)) < 0) {
		close (fd);
#ifdef _WIN32
		if (errno == WSAEADDRINUSE)
#else
		if (errno == EADDRINUSE)
#endif
		{
			return(-2);
		} else {
			return(-1);
		}
	}

	while (listen(fd, 5) == -1) {
		if (errno != EINTR) {
			return(-1);
		}
	}

	return fd;
}


/* 
 * ACCEPT-CONNECTION
 * the argument, fd, is the value returned from bind_port
 *
 * when a connection is accepted, it returns the file descriptor
 * for the connected port
 */
int	accept_connection (fd)
int	fd;
{
	struct sockaddr raddr;
	struct sockaddr_in *rap = (struct sockaddr_in *) &raddr;
	int	afd, raddrlen;

	raddrlen = sizeof raddr;
	do {
		afd = accept (fd, &raddr, &raddrlen);
	} while (afd < 1 && errno == EINTR);

	if (afd < 0) {
		return(-1);
	}

	remote_ip_str  = strdup(inet_ntoa(rap->sin_addr));
	remote_ip_long = ntohl(rap->sin_addr.s_addr);

	return afd;
}
