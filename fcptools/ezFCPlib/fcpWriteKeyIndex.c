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

extern char     *_fcpHost;
extern int      _fcpPort;
extern int      _fcpHtl;
extern int      _fcpRawMode;
extern char     _fcpID[];

/*
  Function:    fcpWriteKeyIndex()
  
  Arguments:   hfcp
  
  Description:
*/

int fcpWriteKeyIndex(HFCP *hfcp, char *data)
{
  char            *keydata;
  int             thiskeynum;
  char            keyname[128];
  char            fcpcmd[1024];
  unsigned int    datalen = strlen(data);
  unsigned int    n;
  
  /* skip past keys already registered */
  while ((thiskeynum = fcpReadKeyIndex(hfcp, &keydata, -1)) != -1) {
    _fcpLog(FCP_LOG_NORMAL, "Existing key %d:\n%s\n---------------------", thiskeynum, keydata);
    free(keydata);
  }
  
  /* insert new key */
  if (hfcp->keyindex.basedate[0])
    sprintf(keyname, "freenet:KSK@%s-%s-%d",
	    hfcp->keyindex.name, hfcp->keyindex.basedate, hfcp->keyindex.next_keynum);
  else
    sprintf(keyname, "freenet:KSK@%s-%d",
	    hfcp->keyindex.name, hfcp->keyindex.next_keynum);
  
  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0)
    return -1;
  
  /* write to the key index */
  sprintf(fcpcmd,
	  "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
	  keyname,
	  hfcp->htl,
	  datalen
	  );
  
  /* send off client put command */
  _fcpSockSend(hfcp, _fcpID, 4);
  n = _fcpSockSend(hfcp, fcpcmd, strlen(fcpcmd));

  if (n < strlen(fcpcmd)) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }
  
  /* send off the data itself */
  n = _fcpSockSend(hfcp, data, datalen);
  if (n < datalen) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }
  
  /* now hope for a successful response */
  if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_SUCCESS) {
    _fcpSockDisconnect(hfcp);
    return -1;
  }
  
  _fcpSockDisconnect(hfcp);
  return 0;
}
