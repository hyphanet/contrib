/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

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

#include <sys/types.h>

#ifndef WINDOWS
#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#endif

#include "ezFCPlib.h"

/* imports */
extern void crSockDisconnect(hFCP *hfcp);

/* private helper functions */
static int host_is_numeric(char *host);


int _fcpSockConnect(hFCP *hfcp)
{
  int rc;

  struct sockaddr_in localAddr, servAddr;
	struct in_addr host_addr;
	struct hostent *host_ent;

	/* this little code-tangent allows it to work on Windows correctly.
		 you cannot pass an ip address to gethostbyname() on windows as you
		 can on linux.  check the MSDN (keyword "gethostbyname") for the
		 relevant information.  if anyone has a better way, please show me. */
	
	if (host_is_numeric(hfcp->host)) {
		if (!(rc = inet_aton(hfcp->host, &host_addr))) {
			return -1;
		}
		
		host_ent = gethostbyaddr(&host_addr, sizeof(struct in_addr), AF_INET);
	}
	else /* host must be an alpha-numeric symbolic name */
		host_ent = gethostbyname(hfcp->host);
	
	if (!(host_ent)) {
		_fcpLog(FCP_LOG_DEBUG, "could not get host information for \"%s\"", hfcp->host);
		return -1;
	}
	
  servAddr.sin_family = host_ent->h_addrtype;
  memcpy((char *) &servAddr.sin_addr.s_addr, host_ent->h_addr_list[0], host_ent->h_length);
  servAddr.sin_port = htons(_fcpPort);

  /* create socket */
  hfcp->socket = socket(AF_INET, SOCK_STREAM, 0);

  if (hfcp->socket < 0) return -1;

  /* bind any port number */
  localAddr.sin_family = AF_INET;
  localAddr.sin_addr.s_addr = htonl(INADDR_ANY);
  localAddr.sin_port = htons(0);
	
  rc = bind(hfcp->socket, (struct sockaddr *) &localAddr, sizeof(localAddr));
  if (rc < 0) {
		_fcpLog(FCP_LOG_DEBUG, "error binding to port %d", hfcp->port);
		crSockDisconnect(hfcp);
    return -1;
  }

  /* connect to server */
  rc = connect(hfcp->socket, (struct sockaddr *) &servAddr, sizeof(servAddr));
  if (rc < 0) {
		_fcpLog(FCP_LOG_DEBUG, "error connecting to server %s", hfcp->host);
		crSockDisconnect(hfcp);
		return -1;
	}

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

