/*
  This code is part of FreeWeb - an FCP-based client for Freenet
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net
  
  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h>

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ezFCPlib.h"


extern int   snprintf(char *str, size_t size, const char *format, ...);

extern int   crSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);



int fcpOpenKeyRead(hFCP *hfcp, char *key, char *filename)
{
  return 0;
}


int fcpOpenKeyWrite(hFCP *hfcp, char *keyname)
{
  int   rc;
	char  buf[4096 + 1];
	int   len;

	hURI *uri = 0;
	
	/* Allocate hKey handle */
	if (hfcp->key) _fcpDestroyHKey(hfcp->key);
	hfcp->key = (hKey *)malloc(sizeof(hKey));

	hfcp->key->chunkCount = 1;
	hfcp->key->chunks = _fcpCreateHChunk();

	/* Initialize the first (and potentially only) chunk */
	hfcp->key->chunks[0]->filename = crTmpFilename();
	hfcp->key->chunks[0]->fd = open(hfcp->key->chunks[0]->filename, O_CREAT);
	
	uri = _fcpCreateHURI();

	if (_fcpParseURI(uri, keyname)) {
		_fcpDestroyHURI(uri);
		return -1;
	}
	hfcp->key->uri = uri;
	
	if (crSockConnect(hfcp)) return -1;
  if (send(hfcp->socket, _fcpID, 4, 0) != 4) return -1;

	strcpy(buf, "ClientHello\nEndMessage\n");
	len = strlen(buf);
	rc = send(hfcp->socket, buf, len, 0);

  /* If I couldn't say HELLO, bomb out */
	if (rc < len) return -1;

	if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_NODEHELLO) return -1;

	_fcpLog(FCP_LOG_DEBUG, "Confirmed hello response from host %s", hfcp->host);
	_fcpLog(FCP_LOG_DEBUG, "Node description: %s", hfcp->description);
		
  _fcpSockDisconnect(hfcp);


	/* Beginning of PUT successful */
  return 0;
}

