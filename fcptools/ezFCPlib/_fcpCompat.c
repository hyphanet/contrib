/*
  This code is part of FCPTools - an FCP-based client library for Freenet

  CopyLeft (c) 2001 by David McNab

  Developers:
  - David McNab <david@rebirthing.co.nz>
  - Jay Oliveri <ilnero@gmx.net>
  
  Currently maintained by Jay Oliveri <ilnero@gmx.net>
  
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

#include "ezFCPlib.h"

#include <time.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>

#include "ez_sys.h"
				
extern char *_fcpTmpDir;

/*********************************************************************/

void _fcpSockDisconnect(hFCP *hfcp)
{
  if (hfcp->socket == FCP_SOCKET_DISCONNECTED)
		return;

#ifdef WIN32
	closesocket(hfcp->socket);
#else
	close(hfcp->socket);
#endif

	hfcp->socket = FCP_SOCKET_DISCONNECTED;
}



int _fcpTmpfile(char **filename)
{
	char s[513];
	int search = 1;

	struct stat st;
	time_t seedseconds;

	time(&seedseconds);
	srand((unsigned int)seedseconds);

	while (search) {

		snprintf(s, 512, "%s%ceztmp_%x", _fcpTmpDir, DIR_SEP, (unsigned int)rand());

		if (stat(s, &st))
			if (errno == ENOENT) search = 0;
	}

	/* set the filename parameter to the newly generated Tmp filename */
	*filename = strdup(s);

	/* I think creating the file right here is good in avoiding
		 race conditions.  Let the caller close the file (leaving a
		 zero-length file behind) if it needs to be re-opened
		 (ie when calling fcpGet()) */
	
	return creat(*filename, FCP_OPEN_FLAGS);
}

int _fcpRecv(int socket, char *buf, int len)
{
	int rc;

#ifdef WIN32
	rc = recv(socket, buf, len, 0);
#else
	rc = read(socket, buf, len);
#endif

	if (rc < 0)
		return -1;
	else
		return rc;
}

int _fcpSockRecv(hFCP *hfcp, char *buf, int len)
{
	int rc;

	struct timeval tv;
	fd_set readfds;

	tv.tv_usec = 0;
	tv.tv_sec  = hfcp->timeout / 1000;

	FD_ZERO(&readfds);
	FD_SET(hfcp->socket, &readfds);
	
	rc = select(hfcp->socket+1, &readfds, NULL, NULL, &tv);

	/* handle this popular case first */	
	if (rc == -1) {
		return EZERR_GENERAL;
	}
	/* check for socket timeout */
	else if (rc == 0) {
		return EZERR_SOCKET_TIMEOUT;
	}

	/* otherwise, rc *should* be 1, but any non-zero positive
	integer is acceptable, meaning all is well */

	/* grab the whole chunk */
	rc = _fcpRecv(hfcp->socket, buf, len);

	if (rc == -1) {
		_fcpLog(FCP_LOG_DEBUG, "unexpectedly lost connection to node");
		return -1;
	}
	else
		return rc;
}


int _fcpSockRecvln(hFCP *hfcp, char *buf, int len)
{
	int rc;
	int rcvd = 0;

	struct timeval tv;
	fd_set readfds;

	tv.tv_usec = 0;
	tv.tv_sec  = hfcp->timeout / 1000;

	FD_ZERO(&readfds);
	FD_SET(hfcp->socket, &readfds);

	buf[0] = 0;
	rc = select(hfcp->socket+1, &readfds, NULL, NULL, &tv);

	/* handle this popular case first */	
	if (rc == -1) {
		return EZERR_GENERAL;
	}
	/* check for socket timeout */
	else if (rc == 0) {
		return EZERR_SOCKET_TIMEOUT;
	}

	/* otherwise, rc *should* be 1, but any non-zero positive
	integer is acceptable, meaning all is well */

	while (1) {
		char s[41];

		rc = _fcpRecv(hfcp->socket, buf+rcvd, 1);

		if (rc == 0) {
			_fcpLog(FCP_LOG_DEBUG, "_fcpRecv() returned 0 (indicated an extraneous call)");
			return 0;
		}

		if (rc == -1) {
			_fcpLog(FCP_LOG_DEBUG, "unexpectedly lost connection to node");
			return -1;
		}

		if (buf[rcvd] == '\n') {
			buf[rcvd] = 0;
			return rcvd;
		}
		else if (rcvd >= len) {
			/* put the null bytes on the last char allocated in the array */
			buf[--rcvd] = 0;

			_fcpLog(FCP_LOG_DEBUG, "truncated line at %d bytes", rcvd);

			snprintf(s, 40, "*%s*", buf);
			_fcpLog(FCP_LOG_DEBUG, "1st 40 bytes: %s", s);
			return rcvd;
		}
		else {
			/* the char is already 'received' so increment the byte counter and
			fetch another */
			rcvd++;
		}
	}
}

long file_size(char *filename)
{
	int size;

	struct stat fstat;
	
	if (!filename) size = -1;
	else if (stat(filename, &fstat)) size = -1;
	else size = fstat.st_size;
	
	return size;
}

