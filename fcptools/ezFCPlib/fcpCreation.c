
/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
  CopyLeft (c) 2001 by David McNab
	
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


#include <stdio.h>
#include <stdlib.h>

#include "ezFCPlib.h"


hFCP *_fcpCreateHFCP(void)
{
  hFCP *h;

	h = (hFCP *)malloc(sizeof (hFCP));
  memset(h, 0, sizeof (hFCP));

	h->host = malloc(strlen(EZFCP_DEFAULT_HOST) + 1);
	strcpy(h->host, EZFCP_DEFAULT_HOST);

	h->port = EZFCP_DEFAULT_PORT;
	h->htl = EZFCP_DEFAULT_HTL;
	h->regress = EZFCP_DEFAULT_REGRESS;
	h->rawmode = EZFCP_DEFAULT_RAWMODE;

	h->socket = -1;

  return h;
}

void _fcpDestroyHFCP(hFCP *h)
{
	if (h) {
		if (h->host) free(h->host);
	}
	free(h);
}

hChunk *_fcpCreateHChunk(void)
{
	hChunk *h;

	h = (hChunk *)malloc(sizeof (hChunk));
	memset(h, 0, sizeof (hChunk));

	return h;
}

void _fcpDestroyHChunk(hChunk *h)
{
	if (h) {
		if (h->filename) free(h->filename);
		if (h->uri) _fcpDestroyHURI(h->uri);
	}
	free(h);
}

hKey *_fcpCreateHKey(void)
{
	hKey *h;

	h = (hKey *)malloc(sizeof (hKey));
	memset(h, 0, sizeof (hKey));

	h->uri = _fcpCreateHURI();

	return h;
}


void _fcpDestroyHKey(hKey *h)
{
	if (h) {
		int i = 0;

		if (h->metadata) {
		}
		if (h->uri) _fcpDestroyHURI(h->uri);
		if (h->mimetype) free(h->mimetype);

		while (i < h->chunkCount)
			_fcpDestroyHChunk(h->chunks[i++]);

		free(h);
	}
}

hURI *_fcpCreateHURI(void)
{
	hURI *h;

	h = (hURI *)malloc(sizeof (hURI));
	memset(h, 0, sizeof (hURI));
	
	return h;
}

void _fcpDestroyHURI(hURI *h)
{
	if (h) {
		if (h->uri_str) free(h->uri_str);
		if (h->keyid) free(h->keyid);
		if (h->path) free(h->path);
		if (h->file) free(h->file);

		free(h);
	}
}

int _fcpParseURI(hURI *uri, char *key)
{
	/* 1 of 3 possiblities:

	1) freenet:KSK@freetext.html
	2) freenet:SSK@chjgfklguhgwuiwo7895hgkdljdshfgd/SITE//fileinsite.html
	3) freenet:CHK@fkjdfjglsdjgslkgdjfghsdjkflgskdjfghdjfksl

	Caller must malloc area for *key !
	*/

	int len;

	char *p;
	char *p2;

  /* skip 'freenet:' */
  if (!strncmp(key, "freenet:", 8))
    key += 8;

	/* for "xxK@" initially.. */
	uri->uri_str = (char *)malloc(5);
  
  /* classify key header */
  if (!strncmp(key, "SSK@", 4)) {

    uri->type = KEY_TYPE_SSK;
		key += 4;

		/* Copy out the key id, up until the '/' char.*/
		for (p = key; *p != '/'; p++);
		len = p - key;

		uri->keyid = (char *)malloc(len + 1);

		strncpy(uri->keyid, key, len);
		p2 = uri->keyid + len;
		*p2 = 0;

		/* Make key point to the char after '/' */
		key = ++p;

		/* Calculate the length of the site name (malloc'ed) */
		for (; strncmp(p, "//", 2); p++);
		len = p - key;

		uri->path = (char *)malloc(len + 1); /* remember trailing 0 */

		/* Now copy sitename to malloc'ed area */
		strncpy(uri->path, key, len);
		*(uri->path + len) = 0;

		/* Make key point to the char after "//" */
		key = (p += 2);

		for (; *p; p++);
		len = p - key;

		uri->file = (char *)malloc(len + 1);

		strncpy(uri->file, key, len);
		*(uri->file + len) = 0;

		/* the +10 really is +8:  "SSK@ + / + //"  */
		realloc(uri->uri_str, strlen(uri->keyid) + strlen(uri->path) + strlen(uri->file) + 10);
		sprintf(uri->uri_str, "SSK@%s/%s//%s", uri->keyid, uri->path, uri->file); 
  }

	/* freenet:CHK@fkjdfjglsdjgslkgdjfghsdjkflgskdjfghdjfksl */

  else if (!strncmp(key, "CHK@", 4)) {

    uri->type = KEY_TYPE_CHK;
		strcpy(uri->uri_str, "CHK@");

		key += 4;
    
		for (p = key; *p; p++);
		len = p-key;

		_fcpLog(FCP_LOG_DEBUG, "length is: %d", len);

		if (len) {
			uri->keyid = (char *)malloc(len + 1);
			strncpy(uri->keyid, key, len);
			*(uri->keyid + len) = 0;

			realloc(uri->uri_str, strlen(uri->keyid) + 5);
			strcat(uri->uri_str, uri->keyid);
		}
  }
  
	/* freenet:KSK@freetext.html */

  else if (!strncmp(key, "KSK@", 4)) {

    uri->type = KEY_TYPE_KSK;
		strcpy(uri->uri_str, "KSK@");

    key += 4;

		for (p = key; *p; p++);
		len = p-key;

		uri->keyid = (char *)malloc(len + 1);
		strncpy(uri->keyid, key, len);
		*(uri->keyid + len) = 0;

		realloc(uri->uri_str, strlen(uri->keyid) + 5);
		strcat(uri->uri_str, uri->keyid);
  }
  
  else {
    return 1;
  }

	_fcpLog(FCP_LOG_DEBUG, "uri data follows");

	_fcpLog(FCP_LOG_DEBUG, "uri->keyid: %s",uri->keyid );
	_fcpLog(FCP_LOG_DEBUG, "uri->path: %s", uri->path);
	_fcpLog(FCP_LOG_DEBUG, "uri->file: %s", uri->file);
	_fcpLog(FCP_LOG_DEBUG, "uri->uri_str: %s", uri->uri_str);

	_fcpLog(FCP_LOG_DEBUG, "end of uri data");

  return 0;
}

