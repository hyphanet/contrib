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

#include "ez_sys.h"

/*
	fcpMakeSvkKeypair()

	Better allocate the parameters before calling this function.
*/

int fcpMakeSvkKeypair(hFCP *hfcp, char *pub_key, char *priv_key, char *crypt_key)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	_fcpLog(FCP_LOG_VERBOSE, "Entered GenerateSVKPair()");

	/* try to connect first.. bomb otherwise */
	if (_fcpSockConnect(hfcp) != 0)	return -1;

	strcpy(buf, "GenerateSVKPair\nEndMessage\n");
	
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send GenerateSVKPair message");
		_fcpSockDisconnect(hfcp);
		return -1;
	}
  
  if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_SUCCESS) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }

  strcpy(pub_key, hfcp->response.success.publickey);
  strcpy(priv_key, hfcp->response.success.privatekey);
  strcpy(crypt_key, hfcp->response.success.cryptokey);
  
  _fcpSockDisconnect(hfcp);

  return 0;
}
