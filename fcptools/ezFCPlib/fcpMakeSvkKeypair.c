/*
  This code is part of FreeWeb - an FCP-based client for Freenet
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net
  
  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"

#include <stdio.h>
#include <string.h>


/*
	fcpMakeSvkKeypair()

	Better allocate the parameters before calling this function.
*/

int fcpMakeSvkKeypair(hFCP *hfcp, char *pub_key, char *priv_key, char *entropy)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	_fcpLog(FCP_LOG_VERBOSE, "Entered GenerateSVKPair()");

	/* try to connect first.. bomb otherwise */
	if (_fcpSockConnect(hfcp) != 0)	return -1;

	strcpy(buf, "GenerateSVKPair\nEndMessage\n");
	
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
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
  
  _fcpSockDisconnect(hfcp);

  return 0;
}
