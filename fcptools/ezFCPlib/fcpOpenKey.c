/*
  This code is part of FreeWeb - an FCP-based client for Freenet
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net
  
  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#ifndef WINDOWS
#include <unistd.h>
#endif

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#include "ezFCPlib.h"


hKey *fcpOpenKeyRead(hFCP *hfcp, char *key, int regress)
{
  return 0;
}


/*
	If filename is NULL, then set file description to read from STDIN.
*/
int fcpOpenKeyWrite(hFCP *hfcp, char *keyname);
{
  char *handshake = "ClientHello\nEndMessage\n";

  int   rc;
	int   fd;
	char  buf[4096 + 1];

	hURI *uri = 0;

	if (filename) {
		struct stat st;
		
		if (!stat(filename, &st)) return -1;
		
		if ((fd = open(filename, O_RDONLY)) == -1) return -1;

		/* Allocate hKey handle */
		if (hfcp->key) _fcpDestroyHKey(hfcp->key);
		hfcp->key = (hKey *)malloc(sizeof(hKey));
		
		hfcp->key->filename = malloc(strlen(filename) + 1);
		strcpy(hfcp->key->filename, filename);
		
		hfcp->key->size = st.st_size;
		hfcp->key->fd = fd;
		
	} else {
		/* Allocate hKey handle */
		if (hfcp->key) _fcpDestroyHKey(hfcp->key);
		hfcp->key = (hKey *)malloc(sizeof(hKey));
		
		hfcp->size = -1; /* Should handle this properly elsewhere */
		hfcp->key->fd = STDIN_FILENO;
	}
	
	if (hfcp->key->size > SPLIT_BLOCK_SIZE) {
		printf("Can't do splitfile!\n");
		
		return -1;
	}
	
	uri = _fcpCreateHURI();
	
	/* If the key uri isn't valid, we're gone */
	if (_fcpParseURI(uri, keyname)) {
		_fcpDestroyHURI(uri);
		return -1;
	}
	hfcp->key->uri = uri;
	
	if (crSockConnect(hfcp)) return -1;
  if (crSockSend(hfcp, _fcpID, 4) != 4) return -1;

	strcpy(buf, "ClientHello\nEndMessage\n");
	len = strlen(buf);
	rc = _fcpSockSend(hfcp, buf, len);

  /* If I couldn't say HELLO, bomb out */
	if (rc < len) return -1;

	if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_NODEHELLO) return -1;

	_fcpLog(FCP_LOG_DEBUG, "Confirmed hello response from host %s", hfcp->host);
	_fcpLog(FCP_LOG_DEBUG, "Node description: %s", hfcp->description);
		
  _fcpSockDisconnect(hfcp);

	snprintf(buf,
					 "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
					 4096,
					 hfcp->key->uri->uri_str,
					 hfcp->htl,
					 hfcp->key->size
					 );

	if (crSockConnect(hfcp)) return -1;
  if (crSockSend(hfcp, _fcpID, 4) != 4) return -1;

	len = strlen(buf);
	rc = _fcpSockSend(hfcp, buf, len);

  if (rc < len) {
    crSockDisconnect(hfcp);
		return -1;
	}

	/* Beginning of PUT successful */
  return 0;
}

