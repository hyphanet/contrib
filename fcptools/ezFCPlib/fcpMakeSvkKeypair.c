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
  IMPORTED DECLARATIONS
*/

extern char     _fcpID[];

/*
  Function:    fcpMakeSvkKeypair()
  
  Arguments:   hfcp    FCP handle
  pubkey  pointer to a buffer into which to write an SSK public key
  privkey pointer to a buffer into which to write an SSK private key
  
  Returns:     0 if successful, non-zero if failed
  
  Description:
*/

int fcpMakeSvkKeypair(HFCP *hfcp, char *pubkey, char *privkey)
{
  char *cmd = "GenerateSVKPair\nEndMessage\n";
  int  n;
  int  len;

	/* Should work cleanly, without the fcpKludge as it was so cleverly named */
  
  if (_fcpSockConnect(hfcp) != 0) return -1;
  
  len = strlen(cmd);
  _fcpSockSend(hfcp, _fcpID, 4);
  
  n = _fcpSockSend(hfcp, cmd, len);
  if (n < len) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }
  
  if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_SUCCESS) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }

  strcpy(pubkey, hfcp->pubkey);
  strcpy(privkey, hfcp->privkey);
  
  _fcpSockDisconnect(hfcp);
  return 0;
}
