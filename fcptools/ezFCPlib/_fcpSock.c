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

#ifndef WINDOWS
#include <sys/select.h>

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#endif

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

#include "ez_sys.h"

/* private helper functions */
static int host_is_numeric(char *host);


int _fcpSockConnect(hFCP *hfcp)
{
  int  rc;
	
  struct sockaddr_in sa_local_addr;
  struct sockaddr_in sa_serv_addr;
	
	struct hostent *host_ent;
  struct in_addr  in_address;

	char fcpID[4] = { 0, 0, 0, 2 };

	if (host_is_numeric(hfcp->host)) {
    in_address.s_addr = inet_addr(hfcp->host);
	}
  else {
    host_ent = gethostbyname(hfcp->host);
		if (!host_ent) return -1;

		in_address.s_addr = ((struct in_addr *)host_ent->h_addr_list[0])->s_addr;
  }

	/* now (struct in_addr)in_address is set properly in either
		 name case or dotted-IP */
	sa_serv_addr.sin_addr.s_addr = in_address.s_addr;
  sa_serv_addr.sin_port = htons(hfcp->port);
  sa_serv_addr.sin_family = AF_INET;
	
  /* create socket */
  hfcp->socket = socket(AF_INET, SOCK_STREAM, 0);
	
  if (hfcp->socket < 0) return -1;
	
  /* bind to any port number on local machine */
  sa_local_addr.sin_addr.s_addr = htonl(INADDR_ANY);
  sa_local_addr.sin_port = htons(0);
  sa_local_addr.sin_family = AF_INET;
	
  rc = bind(hfcp->socket, (struct sockaddr *) &sa_local_addr, sizeof(struct sockaddr));

  if (rc < 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Error binding to port %d: %s", hfcp->port, strerror(errno));
		_fcpSockDisconnect(hfcp);

    return -1;
  }
	
  /* connect to server */
  rc = connect(hfcp->socket, (struct sockaddr *) &sa_serv_addr, sizeof(struct sockaddr));
  if (rc < 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Error connecting to server %s: %s", hfcp->host, strerror(errno));
		_fcpSockDisconnect(hfcp);

		return -1;
	}

	/* Send fcpID */
	send(hfcp->socket, fcpID, 4, 0);

	_fcpLog(FCP_LOG_DEBUG, "_fcpSockConnect() - host: %s:%d", hfcp->host, hfcp->port);

  return 0;
}

/*
	We need *both* Recv() and Recvln(); per arch WINDOWS/NON-WIN.
*/
int _fcpSockRecv(hFCP *hfcp, char *buf, int len)
{
	int rc;
	int rcvd = 0;

	struct timeval tv;
	fd_set readfds;

	tv.tv_usec = 0;
	tv.tv_sec  = hfcp->timeout / 1000;
	
	FD_ZERO(&readfds);
	FD_SET(hfcp->socket, &readfds);
	
	rc = select(hfcp->socket+1, &readfds, NULL, NULL, &tv);
	if (rc < 0) return -1;
	
	if (!FD_ISSET(hfcp->socket, &readfds)) {
		return EZERR_SOCKET_TIMEOUT;
	}

	for (rc = 1; rc > 0; rcvd += rc) {
		rc = read(hfcp->socket, buf + rcvd, len - rcvd);
	}

	if (rc < 0) { /* bad */
		_fcpLog(FCP_LOG_DEBUG, "_fcpSockRecv() - read operation returned error code %d", rc);
		return -1;
	}
	
	return rcvd;
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
	
	rc = select(hfcp->socket+1, &readfds, NULL, NULL, &tv);
	if (rc < 0) return rc;
	
	if (!FD_ISSET(hfcp->socket, &readfds))
		return EZERR_SOCKET_TIMEOUT;

	while (1) {
		rc = read(hfcp->socket, buf + rcvd, 1);

		if (rc <= 0) break;
		else if (buf[rcvd] == '\n') break;
		else if (rcvd >= len) break;

		rcvd++;
	};

	if (rc < 0) { /* bad */
		return -1;
	}

	buf[rcvd] = 0;
	return rcvd;
}


/*********************************************************************/

static int host_is_numeric(char *host)
{
	while (*host != '.') {

		/* if there's a character not between 0 -> 9, return with "false" */
		if ((*host < '0') || (*host > '9')) return 0;

		host++;
	}
	
	/* return "true"; there are no alpha characters in the hostname
		up to the first "." */
	return 1;
}

