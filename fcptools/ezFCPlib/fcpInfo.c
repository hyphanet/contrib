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
#include <stdlib.h>

#include "ez_sys.h"

int fcpClientHello(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpSendHello()");

	if (_fcpSockConnect(hfcp) != 0)	return -1;
	
	rc = snprintf(buf, L_FILE_BLOCKSIZE, "ClientHello\nEndMessage\n");
	
	_fcpLog(FCP_LOG_DEBUG, "sending ClientHello message");
	
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not send ClientHello message to node");
		_fcpSockDisconnect(hfcp);

		return -1;
	}
	
	/* expecting a NodeHello response */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_NODEHELLO) {
		_fcpLog(FCP_LOG_CRITICAL, "Did not receive expected NodeHello response");
		_fcpSockDisconnect(hfcp);

		return -1;
	}

	/* copy over the response fields */
	if (hfcp->description) free(hfcp->description);
	hfcp->description = strdup(hfcp->response.nodehello.description);

	if (hfcp->protocol) free(hfcp->protocol);
	hfcp->protocol = strdup(hfcp->response.nodehello.protocol);

	hfcp->highest_build = hfcp->response.nodehello.highest_build;
	hfcp->max_filesize = hfcp->response.nodehello.max_filesize;

	_fcpSockDisconnect(hfcp);
	return 0;
}


int fcpClientInfo(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpClientInfo()");

	if (_fcpSockConnect(hfcp) != 0)	return -1;
	
	rc = snprintf(buf, L_FILE_BLOCKSIZE, "ClientInfo\nEndMessage\n");
	
	_fcpLog(FCP_LOG_DEBUG, "sending ClientInfo message");
	
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not send ClientHello message to node");
		_fcpSockDisconnect(hfcp);
		return -1;
	}
	
	/* expecting a NodeInfo response */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_NODEINFO) {
		_fcpLog(FCP_LOG_CRITICAL, "Did not receive expected NodeInfo response");
		_fcpSockDisconnect(hfcp);
		return -1;
	}

	/* don't copy the fields, just print them from the response struct */
	
	_fcpSockDisconnect(hfcp);
	return 0;
}

int fcpInvertPrivateKey(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpInvertPrivateKey()");

	if (_fcpSockConnect(hfcp) != 0)	return -1;
	
	rc = snprintf(buf, L_FILE_BLOCKSIZE, "InvertPrivateKey\nPrivate=%s\nEndMessage\n",
		hfcp->key->private_key);
	
	_fcpLog(FCP_LOG_DEBUG, "sending InvertPrivateKey message");
	
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send InvertPrivateKey message");
		
		_fcpSockDisconnect(hfcp);
		return -1;
	}
	
	/* expecting a Success response */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_SUCCESS) {
		_fcpLog(FCP_LOG_VERBOSE, "fcpInvertPrivateKey(): error returned from node: %d", rc);

		_fcpSockDisconnect(hfcp);
		return -1;
	}
	
	_fcpSockDisconnect(hfcp);

	strncpy(hfcp->key->public_key, hfcp->response.success.pub, L_KEY);

	return 0;
}








