char *conn_rcs = "$Id: conn.c,v 1.1 2001/09/29 01:30:53 heretic108 Exp $";
/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

/* This is fcpproxy, an http interface client for Freenet.
 * Adapted by David McNab for Freenet from the JunkBusters proxy engine
 * The latest version of this program is at the FreeWeb website,
 * http://freeweb.sourceforge.net, and can be downloaded as a tarball, or from
 * cvs, or as precompiled binaries
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>

#ifdef _WIN32

#include <windows.h>
#include <sys/timeb.h>
#include <io.h>

#else

#include <unistd.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <netdb.h> 
#include <sys/socket.h>

#ifndef __BEOS__
#include <netinet/tcp.h>
#include <arpa/inet.h>
#endif

#endif

#ifdef REGEX
#include "gnu_regex.h"
#endif

#include "fcpproxy.h"

int
direct_connect(struct gateway *gw, struct http_request *http, struct client_state *csp)
{
	if(gw->forward_host) {
		return(connect_to(gw->forward_host, gw->forward_port, csp));
	} else {
		return(connect_to(http->host, http->port, csp));
	}
}

int
atoip(char *host)
{
	struct sockaddr_in inaddr;
	struct hostent *hostp;

	if ((host == NULL) || (*host == '\0')) 
		return(INADDR_ANY);

	memset ((char * ) &inaddr, 0, sizeof inaddr);
	if ((inaddr.sin_addr.s_addr = inet_addr(host)) == -1) {
		if ((hostp = gethostbyname(host)) == NULL) {
			errno = EINVAL;
			return(-1);
		}
		if (hostp->h_addrtype != AF_INET) {
#ifdef _WIN32
			errno = WSAEPROTOTYPE;
#else
			errno = EPROTOTYPE;
#endif
			return(-1);
		}
		memcpy((char * ) &inaddr.sin_addr, (char * ) hostp->h_addr,
		    sizeof(inaddr.sin_addr));
	}
	return(inaddr.sin_addr.s_addr);
}


int
connect_to(char *host, int portnum, struct client_state *csp)
{
	struct sockaddr_in inaddr;
	int	fd, addr;
	fd_set wfds;
	struct timeval tv[1];
	int	flags;
	struct access_control_addr src[1], dst[1];

	memset ((char * ) &inaddr, 0, sizeof inaddr);

	if((addr = atoip(host)) == -1) return(-1);

	src->addr = csp->ip_addr_long;
	src->port = 0;

	dst->addr = ntohl(addr);
	dst->port = portnum;

	if(block_acl(src, dst, csp)) {
		errno = EPERM;
		return(-1);
	}

	inaddr.sin_addr.s_addr = addr;
	inaddr.sin_family      = AF_INET;

	if (sizeof(inaddr.sin_port) == sizeof(short)) {
		inaddr.sin_port = htons(portnum);
	} else {
		inaddr.sin_port = htonl(portnum);
	}

	if((fd = socket(inaddr.sin_family, SOCK_STREAM, 0)) < 0) {
		return(-1);
	}

#ifdef TCP_NODELAY
{	/* turn off TCP coalescence */
	int	mi = 1;
	setsockopt (fd, IPPROTO_TCP, TCP_NODELAY, (char * ) &mi, sizeof (int));
}
#endif

#ifndef _WIN32
#ifndef __BEOS__
	if ((flags = fcntl(fd, F_GETFL, 0)) != -1) {
		flags |= O_NDELAY;
		fcntl(fd, F_SETFL, flags);
	}
#endif
#endif

	while (connect(fd, (struct sockaddr *) & inaddr, sizeof inaddr) == -1) {

#ifdef _WIN32
		if (errno == WSAEINPROGRESS)
#else
		if (errno == EINPROGRESS)
#endif
		{
			break;
		}

		if (errno != EINTR) {
			(void) close (fd);
			return(-1);
		}
	}

#ifndef _WIN32
#ifndef __BEOS__
	if (flags != -1) {
		flags &= ~O_NDELAY;
		fcntl(fd, F_SETFL, flags);
	}
#endif
#endif

	/* wait for connection to complete */
	FD_ZERO(&wfds);
	FD_SET(fd, &wfds);

	tv->tv_sec  = 30;
	tv->tv_usec = 0;

	if (select(fd + 1, NULL, &wfds, NULL, tv) <= 0) {
		(void) close(fd);
		return(-1);
	}
	return(fd);
}
