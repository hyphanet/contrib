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
#include <stdlib.h>

extern void _fcpSockDisconnect(hFCP *hfcp);


/*
	This version requires certain variables to be specified as arguments.
*/
hFCP *fcpCreateHFCP(char *host, int port, int htl, int regress, int optmask)
{
  hFCP *h;

	h = (hFCP *)malloc(sizeof (hFCP));
	memset(h, 0, sizeof (hFCP));

	if (!host) {
		h->host = malloc(sizeof(EZFCP_DEFAULT_HOST + 1));
		strcpy(h->host, EZFCP_DEFAULT_HOST);
	}
	else {
		h->host = malloc(strlen(host) + 1);
		strcpy(h->host, host);
	}

	h->port = (port == 0 ? EZFCP_DEFAULT_PORT : port );
	h->htl =  (htl  < 0 ? EZFCP_DEFAULT_HTL  : htl  );
	
	if (regress >= 0) h->regress = regress;
	
	/* do the handle option mask */
	h->rawmode =       (optmask & HOPT_RAW ? 1 : 0);
	h->delete_local =  (optmask & HOPT_DELETE_LOCAL ? 1 : 0);
	
	_fcpLog(FCP_LOG_DEBUG, "created new HFCP structure\n     host: %s, port: %d, htl: %d",
					h->host, h->port, h->htl);

	return h;
}

void fcpDestroyHFCP(hFCP *h)
{
	if (h) {
		if (h->socket < 0) _fcpSockDisconnect(h);

		if (h->host) free(h->host);
		if (h->description) free(h->description);
		if (h->key) _fcpDestroyHKey(h->key);

		free(h);
	}
}

hBlock *_fcpCreateHBlock(void)
{
	hBlock *h;

	h = (hBlock *)malloc(sizeof (hBlock));
	memset(h, 0, sizeof (hBlock));

	h->uri = fcpCreateHURI();

	return h;
}

void _fcpDestroyHBlock(hBlock *h)
{
	if (h) {
		if (h->filename) free(h->filename);
		if (h->uri) fcpDestroyHURI(h->uri);

		free(h);
	}
}

hKey *_fcpCreateHKey(void)
{
	hKey *h;

	h = (hKey *)malloc(sizeof (hKey));
	memset(h, 0, sizeof (hKey));

	h->uri        = fcpCreateHURI();
	h->target_uri = fcpCreateHURI();

	return h;
}

void _fcpDestroyHKey(hKey *h)
{
	if (h) {
		int i;

		if (h->uri)   fcpDestroyHURI(h->uri);
		if (h->target_uri) fcpDestroyHURI(h->target_uri);

		if (h->mimetype) free(h->mimetype);
		if (h->tmpblock) free(h->tmpblock);

		for (i=0; i < h->segment_count; _fcpDestroyHSegment(h->segments[i++]));

		free(h);
	}
}

hURI *fcpCreateHURI(void)
{
	hURI *h;

	h = (hURI *)malloc(sizeof (hURI));
	memset(h, 0, sizeof (hURI));
	
	return h;
}

void fcpDestroyHURI(hURI *h)
{
	if (h) {
		if (h->uri_str) free(h->uri_str);
		if (h->keyid) free(h->keyid);
		if (h->docname) free(h->docname);
		if (h->metastring) free(h->metastring);

		free(h);
	}
}

/*
	fcpParseURI()

	This function parses a string containing a fully-qualified Freenet URI
	into simpler components.  It is written to be re-entrant on the same
	hURI pointer (it can be called repeatedly without being re-created.
*/
int fcpParseURI(hURI *uri, char *key)
{
	int len;
	
	char *p;
	char *p2;

	char *p_key;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpParseURI()");
	_fcpLog(FCP_LOG_DEBUG, "uri: %s", key);

	p_key = key;

	/* clear out the dynamic arrays before attempting to parse a new uri */
	if (uri->uri_str) free(uri->uri_str);
  if (uri->keyid) free(uri->keyid);
	if (uri->docname) free(uri->docname);
	if (uri->metastring) free(uri->metastring);

	/* zero the block of memory */
	memset(uri, 0, sizeof (hURI));

  /* skip 'freenet:' */
  if (!strncmp(key, "freenet:", 8))
    key += 8;
	
  /* classify key header */
  if (!strncmp(key, "SSK@", 4)) {
		char *string_end;
		
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
		
		/* handle rest of key, depending on what's included and what's implied */
		
		/* if the '//' sequence isn't in the uri.. */
		if (!(string_end = strstr(p, "//"))) {
			
			uri->docname = strdup(p);

			uri->uri_str = malloc(strlen(uri->keyid) + strlen(uri->docname) + 20);
			sprintf(uri->uri_str, "freenet:SSK@%s/%s//", uri->keyid, uri->docname); 
		}
		else { /* there's a "//"; must use that as the next border */
			
			string_end = strstr(p, "//");
			len = string_end - p;
			
			uri->docname = malloc(len + 1);
			strncpy(uri->docname, p, len);
		
			/* set key to first char after "//" */
			key = string_end + 1;
			
			/* now set the remaining part to metastring */
			uri->metastring = strdup(key);
			
			uri->uri_str = malloc(strlen(uri->keyid) + strlen(uri->docname) + strlen(uri->metastring) + 20);
			
			/* @@@ TODO: yes we're ignoring metastring for now.. */
			sprintf(uri->uri_str, "freenet:SSK@%s/%s//", uri->keyid, uri->docname); 
		}
  }

  else if (!strncmp(key, "CHK@", 4)) {
		
    uri->type = KEY_TYPE_CHK;
		key += 4;
    
		len = strlen(key);

		if (len) {
			uri->keyid = (char *)malloc(len + 1);
			strcpy(uri->keyid, key);
		}
		
		if (uri->keyid) {
			uri->uri_str = (char *)malloc(strlen(uri->keyid) + 13);
			sprintf(uri->uri_str, "freenet:CHK@%s", uri->keyid);
		}
		else {
			uri->uri_str = (char *)malloc(13);
			strcpy(uri->uri_str, "freenet:CHK@");
		}
  }
  
	/* freenet:KSK@freetext.html */

  else if (!strncmp(key, "KSK@", 4)) {

    uri->type = KEY_TYPE_KSK;

    key += 4;

		len = strlen(key);

		uri->keyid = (char *)malloc(len + 1);
		strcpy(uri->keyid, key);
		*(uri->keyid + len) = 0;

		uri->uri_str = (char *)malloc(strlen(uri->keyid) + 13);
		sprintf(uri->uri_str, "freenet:KSK@%s", uri->keyid);
  }
  
  else {
		_fcpLog(FCP_LOG_DEBUG, "error attempting to parse invalid key: %s", p_key);
    return 1;
  }

  _fcpLog(FCP_LOG_DEBUG, "parsed uri into: %s", uri->uri_str);
	
  return 0;
}

hMetadata *_fcpCreateHMetadata(void)
{
	hMetadata *h;

	h = (hMetadata *)malloc(sizeof (hMetadata));
	memset(h, 0, sizeof (hMetadata));

	return h;
}

void _fcpDestroyHMetadata(hMetadata *h)
{
	if (h) {
	}

	return;
}


/*************************************************************************/
/* FEC specific */
/*************************************************************************/


hSegment *_fcpCreateHSegment(void)
{
  hSegment *h;

	h = (hSegment *)malloc(sizeof (hSegment));
  memset(h, 0, sizeof (hSegment));

  return h;
}

void _fcpDestroyHSegment(hSegment *h)
{
	if (h) {
		int i;

		if (h->header_str) free(h->header_str);

		if (h->db_count)
			for (i=0; i < h->db_count; _fcpDestroyHBlock(h->data_blocks[i++]));

		if (h->cb_count)
			for (i=0; i < h->cb_count; _fcpDestroyHBlock(h->check_blocks[i++]));

		free(h);
	}
}

