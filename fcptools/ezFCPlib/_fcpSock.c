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

#ifndef WIN32
#include <sys/select.h>

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#endif

#include <time.h>

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

#include "ez_sys.h"

/* private helper functions */
static int host_is_numeric(char *host);

/*******************************************************************/
/* Socket Connect/Disconnect functions */
/*******************************************************************/

int _fcpSockConnect(hFCP *hfcp)
{
	int   rc;
	
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
	
	/* translate the return code */
#ifdef WIN32
	if (hfcp->socket == INVALID_SOCKET) hfcp->socket = -1;
#endif
	
	if (hfcp->socket < 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not create socket");
		
		return -1;
	}
	
	/* bind to any port number on local machine */
	sa_local_addr.sin_addr.s_addr = htonl(INADDR_ANY);
	sa_local_addr.sin_port = htons(0);
	sa_local_addr.sin_family = AF_INET;
	
	rc = bind(hfcp->socket, (struct sockaddr *) &sa_local_addr, sizeof(struct sockaddr));
	
	/* translate the return code */
#ifdef WIN32
	if (rc ==	SOCKET_ERROR) rc = -1;
#endif
	
	if (rc != 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not bind to port %u", hfcp->port);
		_fcpSockDisconnect(hfcp);
		
		return -1;
	}
	
	/* connect to server */
	rc = connect(hfcp->socket, (struct sockaddr *) &sa_serv_addr, sizeof(struct sockaddr));
	
	/* translate the return code */
#ifdef WIN32
	if (rc ==	SOCKET_ERROR) rc = -1;
#endif
	
	if (rc != 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not connect to server %s:%u", hfcp->host, hfcp->port);
		_fcpSockDisconnect(hfcp);
		
		return -1;
	}
	
	/* Send fcpID */
	if ((rc = _fcpSend(hfcp->socket, fcpID, 4)) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not send 4-byte ID");
		_fcpSockDisconnect(hfcp);
		
		return -1;
	}
	
	_fcpLog(FCP_LOG_DEBUG, "_fcpSockConnect() - host: %s:%u", hfcp->host, hfcp->port);

	/* reset the timeout to the default to avoid waiting on the same interval
	 over and over and over and over again.. */
	hfcp->options->timeout = EZFCP_DEFAULT_TIMEOUT;

	return 0;
}

void _fcpSockDisconnect(hFCP *hfcp)
{
	if (hfcp->socket == FCP_SOCKET_DISCONNECTED) return;
	
#ifdef WIN32
	shutdown(hfcp->socket, 2);
	closesocket(hfcp->socket);
	
#else
	close(hfcp->socket);
#endif
	
	hfcp->socket = FCP_SOCKET_DISCONNECTED;
	_fcpLog(FCP_LOG_DEBUG, "_fcpSockDisconnect()");
}

/*******************************************************************/
/* Substitutes for recv() and send() */
/*******************************************************************/

/* This recv() handles cases where returned byte count is less than
	the expected count */

int _fcpRecv(FCPSOCKET fcpsock, char *buf, int len)
{
	int rc;
	int bs;

	bs = 0;
	while (len) {

#ifdef WIN32
		rc = recv(fcpsock, buf+bs, len, 0);
#else
		rc = read(fcpsock, buf+bs, len);
#endif

		len -= rc;
		bs += rc;
	}

	if (rc < 0) return -1;
	else return bs;
}

int _fcpSend(FCPSOCKET fcpsock, char *buf, int len)
{
	int rc;
	int bs;

	bs = 0;
	while (len) {

		/* this function is the same on win and BSD-systems */
		rc = send(fcpsock, buf+bs, len, 0);

		if (rc < 0) {

#ifdef WIN32
			_fcpLog(FCP_LOG_DEBUG, "send() returned error code: %d", WSAGetLastError());
#else
			_fcpLog(FCP_LOG_DEBUG, "send() returned error");
#endif

			return -1;
		}

		len -= rc;
		bs += rc;
	}

	if (rc < 0) return -1;
	else return bs;
}

int _fcpSockRecv(hFCP *hfcp, char *buf, int len)
{
	int rc;

	struct timeval tv;
	fd_set readfds;

	tv.tv_usec = 0;

	if (hfcp->options->timeout < (hfcp->options->min_timeout * 1000)) {
		tv.tv_sec = hfcp->options->min_timeout;
		_fcpLog(FCP_LOG_DEBUG, "raised timeout to mininum value: %u seconds", hfcp->options->min_timeout);
	}
	else
		tv.tv_sec = hfcp->options->timeout / 1000;

	FD_ZERO(&readfds);
	FD_SET(hfcp->socket, &readfds);
	
	/* 1st param is ignored by Winsock */
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

#ifdef DMALLOC
	dmalloc_verify(0);
#endif

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

	if (hfcp->options->timeout < (hfcp->options->min_timeout * 1000)) {
		tv.tv_sec = hfcp->options->min_timeout;
		_fcpLog(FCP_LOG_DEBUG, "raised timeout to mininum value: %u seconds", hfcp->options->min_timeout);
	}
	else
		tv.tv_sec = hfcp->options->timeout / 1000;

	FD_ZERO(&readfds);
	FD_SET(hfcp->socket, &readfds);

	buf[0] = 0;

	/* 1st param is ignored by Winsock */
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

			_fcpLog(FCP_LOG_DEBUG, "truncated line at %u bytes", rcvd);

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

/*******************************************************************/

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

