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

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "ez_sys.h"

static void _fcpDestroyResponse(hFCP *h);

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
	h->timeout = EZFCP_DEFAULT_TIMEOUT;
	
	if (regress >= 0) h->regress = regress;
	
	/* do the handle option mask */
	h->rawmode =      (optmask & FCP_MODE_RAW ? 1 : 0);
	h->delete_local = (optmask & FCP_MODE_DELETE_LOCAL ? 1 : 0);
	h->skip_local =   (optmask & FCP_MODE_SKIP_LOCAL ? 1 : 0);
	
	h->key = _fcpCreateHKey();
	
	return h;
}

/* like dup() for files; duplicates an hFCP struct */

hFCP *fcpInheritHFCP(hFCP *hfcp)
{
  hFCP *h;

	if (!hfcp) return 0;

	h = fcpCreateHFCP(hfcp->host, hfcp->port, hfcp->htl, hfcp->regress,
		                hfcp->rawmode | hfcp->delete_local | hfcp->skip_local);

	h->timeout = hfcp->timeout;

	return h;
}

void fcpDestroyHFCP(hFCP *h)
{
	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpDestroyHFCP()");*/

	if (h) {

		if (h->socket != FCP_SOCKET_DISCONNECTED) _fcpSockDisconnect(h);

		if (h->host) free(h->host);
		if (h->description) free(h->description);

		if (h->key) {
			_fcpDestroyHKey(h->key);
			free(h->key);
		}

		_fcpDestroyResponse(h);

		h->socket = FCP_SOCKET_DISCONNECTED;

		/* let caller free 'h' */
	}
}

hKey *_fcpCreateHKey(void)
{
	hKey *h;

	h = (hKey *)malloc(sizeof (hKey));
	memset(h, 0, sizeof (hKey));

	h->uri        = fcpCreateHURI();
	h->target_uri = fcpCreateHURI();

	h->tmpblock = _fcpCreateHBlock();
	h->metadata = _fcpCreateHMetadata();

	return h;
}

void _fcpDestroyHKey(hKey *h)
{
	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpDestroyHKey()");*/

	if (h) {
		int i;

		if (h->uri) {
			fcpDestroyHURI(h->uri);
			free(h->uri);
		}

		if (h->target_uri) {
			fcpDestroyHURI(h->target_uri);
			free(h->target_uri);
		}

		if (h->mimetype) free(h->mimetype);

		if (h->tmpblock) {
			_fcpDestroyHBlock(h->tmpblock);
			free(h->tmpblock);
		}

		if (h->metadata) {
			_fcpDestroyHMetadata(h->metadata);
			free(h->metadata);
		}

		for (i=0; i < h->segment_count; i++) {
			_fcpDestroyHSegment(h->segments[i]);
			free(h->segments[i]);
		}
	}
}

static void _fcpDestroyResponse(hFCP *h)
{
	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpDestroyResponse()");*/

	if (h) {
		if (h->response.success.uri) free(h->response.success.uri);
		if (h->response.datachunk.data) free(h->response.datachunk.data);
		if (h->response.keycollision.uri) free(h->response.keycollision.uri);
		if (h->response.pending.uri) free(h->response.pending.uri);
		if (h->response.failed.reason) free(h->response.failed.reason);
		if (h->response.urierror.reason) free(h->response.urierror.reason);
		if (h->response.routenotfound.reason) free(h->response.routenotfound.reason);
		if (h->response.formaterror.reason) free(h->response.formaterror.reason);
	}
}

hBlock *_fcpCreateHBlock(void)
{
	hBlock *h;

	h = (hBlock *)malloc(sizeof (hBlock));
	memset(h, 0, sizeof (hBlock));

	h->uri = fcpCreateHURI();

	if ((h->fd = _fcpTmpfile(h->filename)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not create temp file %s", h->filename);
		return 0;
	}		

	return h;
}

void _fcpDestroyHBlock(hBlock *h)
{
	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpDestroyHBlock()");*/

	if (h) {
		
		/* close the file if it's open */

		if (h->fd != -1) {
			close(h->fd);
			h->fd = -1;
		}

		if (strlen(h->filename)) {

			/* delete the file */
			if (unlink(h->filename) == 0)
				_fcpLog(FCP_LOG_DEBUG, "deleted temp file %s", h->filename);

			else
				_fcpLog(FCP_LOG_DEBUG, "warning: could not delete temp file %s: \"%s\"",
								h->filename, strerror(errno));
		}
		
		if (h->uri) {
			fcpDestroyHURI(h->uri);
			free(h->uri);
		}
	}
}

hMetadata *_fcpCreateHMetadata(void)
{
	hMetadata *h;

	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpCreateHMetadata()");*/

	h = (hMetadata *)malloc(sizeof (hMetadata));
	memset(h, 0, sizeof (hMetadata));

	h->tmpblock = _fcpCreateHBlock();

	return h;
}

void _fcpDestroyHMetadata(hMetadata *h)
{
	/*_fcpLog(FCP_LOG_DEBUG, "Entered fcpDestroyHMetadata()");*/

	if (h) {

		if (h->tmpblock) {
			_fcpDestroyHBlock(h->tmpblock);
			free(h->tmpblock);
		}
		
		if (h->raw_metadata) free(h->raw_metadata);

		if (h->cdoc_count)
			_fcpDestroyHMetadata_cdocs(h);
	}
}

void _fcpDestroyHMetadata_cdocs(hMetadata *h)
{
	if (h->cdoc_count) {
		int i;
		int j;
		
		for (i=0; i < h->cdoc_count; i++) {
			
			if (h->cdocs[i]->name) free(h->cdocs[i]->name);
			if (h->cdocs[i]->field_count) {
				
				for (j=0; j < (h->cdocs[i]->field_count * 2); j += 2) {
					free(h->cdocs[i]->data[j]);
					free(h->cdocs[i]->data[j+1]);
				}
			}

			free(h->cdocs[i]);
		}

		h->cdoc_count = 0;
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
	char *p_key;

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
		_fcpLog(FCP_LOG_CRITICAL, "Error attempting to parse invalid key: %s", p_key);
    return -1;
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
	if (h) {
		int i;

		if (h->header_str) free(h->header_str);

		if (h->db_count)
			for (i=0; i < h->db_count; i++) {
				_fcpDestroyHBlock(h->data_blocks[i++]);
				free(h->data_blocks[i]);
			}
		
		if (h->cb_count)
			for (i=0; i < h->cb_count; i++) {
				_fcpDestroyHBlock(h->check_blocks[i++]);
				free(h->check_blocks[i++]);
			}
	}
}

