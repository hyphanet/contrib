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

	return h;
}

void _fcpDestroyHBlock(hBlock *h)
{
	if (h) {
		if (h->filename) free(h->filename);
		if (h->uri) _fcpDestroyHURI(h->uri);

		free(h);
	}
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

		if (h->uri) _fcpDestroyHURI(h->uri);
		if (h->mimetype) free(h->mimetype);
		if (h->tmpblock) free(h->tmpblock);
		if (h->metadata) free(h->metadata);

		/*
		while (i < h->segment_count)
			_fcpDestroyHBlock(h->chunks[i++]);
		*/

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

/*
	_fcpParseURI()

	This function parses a string containing a fully-qualified Freenet URI
	into simpler components.  It is written to be re-entrant on the same
	hURI pointer (it can be called repeatedly without being re-created.
*/
int _fcpParseURI(hURI *uri, char *key)
{
	int len;

	char *p;
	char *p2;

	char *p_key;

	p_key = key;

	/* clear out the dynamic arrays before attempting to parse a new uri */
	if (uri->uri_str) free(uri->uri_str);
  if (uri->keyid) free(uri->keyid);
	if (uri->path) free(uri->path);
	if (uri->file) free(uri->file);

	/* zero the block of memory */
	memset(uri, 0, sizeof (hURI));

  /* skip 'freenet:' */
  if (!strncmp(key, "freenet:", 8))
    key += 8;

  /* classify key header */
	/* MUST TEST SSK@ PARSING! */
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
		uri->uri_str = malloc(strlen(uri->keyid) + strlen(uri->path) + strlen(uri->file) + 10);
		sprintf(uri->uri_str, "SSK@%s/%s//%s", uri->keyid, uri->path, uri->file); 
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
			uri->uri_str = (char *)malloc(strlen(uri->keyid) + 5);
			sprintf(uri->uri_str, "CHK@%s", uri->keyid);
		}
		else {
			uri->uri_str = (char *)malloc(5);
			strcpy(uri->uri_str, "CHK@");
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

		uri->uri_str = (char *)malloc(strlen(uri->keyid) + 5);
		sprintf(uri->uri_str, "KSK@%s", uri->keyid);
  }
  
  else {
		_fcpLog(FCP_LOG_DEBUG, "error attempting to parse invalid key \"%s\"", p_key);
    return 1;
  }

  return 0;
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
	int i;

	if (h) {
		if (h->header_str) free(h->header_str);

		if (h->db_count > 0)
			for (i=0; i < h->db_count; _fcpDestroyHBlock(h->data_blocks[i++]));

		if (h->cb_count > 0)
			for (i=0; i < h->cb_count; _fcpDestroyHBlock(h->check_blocks[i++]));

		free(h);
	}
}

