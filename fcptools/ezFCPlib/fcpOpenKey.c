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


/*
	If filename is NULL, then set file description to read from STDIN.
*/
int fcpOpenKeyWrite(hFCP *hfcp, char *keyname, char *filename)
{
  int   rc;
	int   fd;
	char  buf[4096 + 1];
	int   len;

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
		
		hfcp->key->size = -1; /* Should handle this properly elsewhere */
		hfcp->key->fd = -1; /* @@@ FIX @@@ */
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

	snprintf(buf,
					 4096,
					 "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
					 hfcp->key->uri->uri_str,
					 hfcp->htl,
					 hfcp->key->size
					 );

	if (crSockConnect(hfcp)) return -1;
  if (send(hfcp->socket, _fcpID, 4, 0) != 4) return -1;

	len = strlen(buf);
	rc = send(hfcp->socket, buf, len, 0);

  if (rc < len) {
    crSockDisconnect(hfcp);
		return -1;
	}

	/* Beginning of PUT successful */
  return 0;
}

