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
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#endif

#include <stdio.h>
#include <string.h>
#include <errno.h>

/* imports */
extern void _fcpSockDisconnect(hFCP *hfcp);

/* private helper functions */
static int host_is_numeric(char *host);


int _fcpSockConnect(hFCP *hfcp)
{
	char msg[513];
  int  rc;
	
  struct sockaddr_in sa_local_addr;
  struct sockaddr_in sa_serv_addr;
	
	struct hostent *host_ent;
  struct in_addr  in_address;

	char fcpID[4] = { 0, 0, 0, 2 };

	_fcpLog(FCP_LOG_DEBUG, "attempting socket connection");
	
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
		snprintf(msg, 512, "error binding to port %d: %s", hfcp->port, strerror(errno));

		_fcpLog(FCP_LOG_DEBUG, "%s", msg);
		hfcp->error = strdup(msg);

		_fcpSockDisconnect(hfcp);
    return -1;
  }
	
  /* connect to server */
  rc = connect(hfcp->socket, (struct sockaddr *) &sa_serv_addr, sizeof(struct sockaddr));
  if (rc < 0) {
		snprintf(msg, 512, "error connecting to server %s: %s", hfcp->host, strerror(errno));

		_fcpLog(FCP_LOG_DEBUG, "%s", msg);
		hfcp->error = strdup(msg);

		_fcpSockDisconnect(hfcp);
		return -1;
	}

	/* Send fcpID */
	send(hfcp->socket, fcpID, 4, 0);

  return 0;
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

