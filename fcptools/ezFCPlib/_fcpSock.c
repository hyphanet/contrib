/*
	This code is part of FreeWeb - an FCP-based client for Freenet

	sockadDesigned and implemented by David McNab, david@rebirthing.co.nz
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
#include <sys/time.h>
#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include "ezFCPlib.h"


int crSockConnect(hFCP *hfcp)
{
  int rc, i;

  struct sockaddr_in localAddr, servAddr;
  struct hostent *h;

  if (!(h = gethostbyname(hfcp->host))) return -1;

  servAddr.sin_family = h->h_addrtype;
  memcpy((char *) &servAddr.sin_addr.s_addr, h->h_addr_list[0], h->h_length);
  servAddr.sin_port = htons(_fcpPort);

  /* create socket */
  hfcp->socket = socket(AF_INET, SOCK_STREAM, 0);

  if (hfcp->socket < 0) return -1;

  /* bind any port number */
  localAddr.sin_family = AF_INET;
  localAddr.sin_addr.s_addr = htonl(INADDR_ANY);
  localAddr.sin_port = htons(0);
	
	_fcpLog(FCP_LOG_DEBUG, "binding to TCP port: %d", _fcpPort);

  rc = bind(hfcp->socket, (struct sockaddr *) &localAddr, sizeof(localAddr));
  if (rc < 0) {
		_fcpLog(FCP_LOG_DEBUG, "crSockConnect(): error binding to port %d", _fcpPort);
		_fcpLog(FCP_LOG_DEBUG, strerror(errno));

		crSockDisconnect(hfcp);
    return -1;
  }

  /* connect to server */
  rc = connect(hfcp->socket, (struct sockaddr *) &servAddr, sizeof(servAddr));
  if (rc < 0) {
		_fcpLog(FCP_LOG_DEBUG, "crSockConnect(): error connecting to server %s", _fcpHost);
		_fcpLog(FCP_LOG_DEBUG, strerror(errno));
		
		crSockDisconnect(hfcp);
		return -1;
	}

  /* OK - we're in :) */
  _fcpNumOpenSockets++;

	_fcpLog(FCP_LOG_DEBUG, "Connected to server %s:%d", _fcpHost, _fcpPort);
  return 0;
}

