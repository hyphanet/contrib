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

#include <stdio.h>
#include <string.h>

extern int   _fcpSockConnect(hFCP *hfcp);
extern void  _fcpSockDisconnect(hFCP *hfcp);


int fcpSendHello(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char msg[513];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpSendHello()");

	if (_fcpSockConnect(hfcp) != 0)	return -1;
	
	rc = snprintf(buf, L_FILE_BLOCKSIZE, "ClientHello\nEndMessage\n");
	
	_fcpLog(FCP_LOG_DEBUG, "sending ClientHello message");
	
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		snprintf(msg, 512, "Could not send ClientHello message");
		hfcp->error = strdup(msg);

		_fcpSockDisconnect(hfcp);
		return -1;
	}
	
	/* expecting a NodeHello response */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_NODEHELLO) {
		snprintf(msg, 512, "fcpSendHello(): error returned from node: %d", rc);
		hfcp->error = strdup(msg);

		return -1;
	}
	
	/* Note: inside getrespHello() the fields hfcp->node and hfcp->protocol
		 are set */

	return 0;
}

