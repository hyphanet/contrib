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
hFCP *fcpCreateHFCP(char *host, int port, int htl, int optmask)
{
  hFCP *h;

	h = malloc(sizeof (hFCP));
	memset(h, 0, sizeof (hFCP));

	if (!host) {
		h->host = malloc(sizeof(FCPT_DEF_HOST + 1));
		strcpy(h->host, FCPT_DEF_HOST);
	}
	else {
		h->host = malloc(strlen(host) + 1);
		strcpy(h->host, host);
	}

	h->port = (port == 0 ? FCPT_DEF_PORT : port );
	h->htl =  (htl  < 0 ? FCPT_DEF_HTL  : htl  );
	
	h->options = _fcpCreateHOptions();

	/* do the handle option mask */
	h->options->noredirect       = (optmask & FCPT_MODE_RAW ? FCPT_MODE_RAW : 0);
	h->options->remove_local  = (optmask & FCPT_MODE_REMOVE_LOCAL ? FCPT_MODE_REMOVE_LOCAL : 0);
	h->options->dbr           = (optmask & FCPT_MODE_DBR ? FCPT_MODE_DBR : 0);

	_fcpLog(FCPT_LOG_DEBUG, "noredirect: %u, remove_local: %u, dbr: %u",
					h->options->noredirect,
					h->options->remove_local,
					h->options->dbr);
	
	h->key    = _fcpCreateHKey();
	h->socket = FCPT_SOCKET_DISCONNECTED;
		
	return h;
}

/* like dup() for files; duplicates an hFCP struct */

hFCP *fcpInheritHFCP(hFCP *hfcp)
{
  hFCP *h;

	if (!hfcp) return 0;

	h = fcpCreateHFCP(hfcp->host, hfcp->port, hfcp->htl,
		                hfcp->options->noredirect |
										hfcp->options->remove_local |
		                hfcp->options->dbr);

	/* copy over any other options */
	h->options->timeout = hfcp->options->timeout;
	h->options->retry = hfcp->options->retry;
	h->options->min_timeout = hfcp->options->min_timeout;

	return h;
}

void fcpDestroyHFCP(hFCP *h)
{
	if (h) {

		if (h->socket != FCPT_SOCKET_DISCONNECTED) _fcpSockDisconnect(h);

		if (h->host) free(h->host);
		if (h->description) free(h->description);

		if (h->key) {
			_fcpDestroyHKey(h->key);
			free(h->key);
		}

		_fcpDestroyHOptions(h->options);
		free(h->options);

		_fcpDestroyResponse(h);

		h->socket = FCPT_SOCKET_DISCONNECTED;

		/* let caller free 'h' */
	}
}

hOptions *_fcpCreateHOptions(void)
{
	hOptions *h;

	h = malloc(sizeof (hOptions));
	memset(h, 0, sizeof (hOptions));

	h->logstream = FCPT_DEF_LOGSTREAM;		
	h->remove_local = FCPT_DEF_DELETELOCAL;
	h->regress = FCPT_DEF_REGRESS;
	h->retry = FCPT_DEF_RETRY;
	h->splitblock = FCPT_DEF_BLOCKSIZE;
	h->timeout = FCPT_DEF_TIMEOUT;
	h->verbosity = FCPT_DEF_VERBOSITY;

	return h;
}

void _fcpDestroyHOptions(hOptions *h)
{
	if (h) {
		if (h->homedir) free(h->homedir);
		if (h->tempdir) free(h->tempdir);
		
		if (h->logstream)
			if (h->logstream != stdout) fclose(h->logstream);
	}
}

hKey *_fcpCreateHKey(void)
{
	hKey *h;

	h = malloc(sizeof (hKey));
	memset(h, 0, sizeof (hKey));

	h->uri        = fcpCreateHURI();
	h->target_uri = fcpCreateHURI();

	h->tmpblock = _fcpCreateHBlock();

	/* binary_mode defaults to 0/text */
	h->tmpblock->binary_mode = 1;

	h->metadata = _fcpCreateHMetadata();

	return h;
}

void _fcpDestroyHKey(hKey *h)
{
	if (h) {

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

		if (h->segment_count)
			_fcpDestroyHSegments(h);
	}
}

static void _fcpDestroyResponse(hFCP *h)
{
	if (h) {
		if (h->response.success.uri) free(h->response.success.uri);
		if (h->response.datachunk.data) free(h->response.datachunk.data);
		if (h->response.keycollision.uri) free(h->response.keycollision.uri);
		if (h->response.pending.uri) free(h->response.pending.uri);
		if (h->response.failed.reason) free(h->response.failed.reason);
		if (h->response.urierror.reason) free(h->response.urierror.reason);
		if (h->response.routenotfound.reason) free(h->response.routenotfound.reason);
		if (h->response.formaterror.reason) free(h->response.formaterror.reason);

		/* don't forget the nodeinfo section */
		if (h->response.nodeinfo.architecture) free(h->response.nodeinfo.architecture);
		if (h->response.nodeinfo.operatingsystem) free(h->response.nodeinfo.operatingsystem);
		if (h->response.nodeinfo.operatingsystemversion) free(h->response.nodeinfo.operatingsystemversion);
		if (h->response.nodeinfo.nodeaddress) free(h->response.nodeinfo.nodeaddress);
		if (h->response.nodeinfo.javavendor) free(h->response.nodeinfo.javavendor);
		if (h->response.nodeinfo.javaname) free(h->response.nodeinfo.javaname);
		if (h->response.nodeinfo.javaversion) free(h->response.nodeinfo.javaversion);
	}
}

hBlock *_fcpCreateHBlock(void)
{
	hBlock *h;

	h = malloc(sizeof (hBlock));
	memset(h, 0, sizeof (hBlock));

	h->uri = fcpCreateHURI();

	if (_fcpTmpfile(h->filename) != 0) {
		_fcpLog(FCPT_LOG_DEBUG, "could not create temp file %s", h->filename);
		return 0;
	}		

	h->fd = -1;
	h->m_delete = 1;

	return h;
}

void _fcpDestroyHBlock(hBlock *h)
{
	if (h) {
		
		/* close and delete the file */
		_fcpBlockUnlink(h);
		_fcpDeleteBlockFile(h);
		
		if (h->uri) {
			fcpDestroyHURI(h->uri);
			free(h->uri);
		}
	}
}

hMetadata *_fcpCreateHMetadata(void)
{
	hMetadata *h;

	h = malloc(sizeof (hMetadata));
	memset(h, 0, sizeof (hMetadata));

	h->tmpblock = _fcpCreateHBlock();

	return h;
}

void _fcpDestroyHMetadata(hMetadata *h)
{
	if (h) {

		if (h->tmpblock) {
			_fcpDestroyHBlock(h->tmpblock);
			free(h->tmpblock);
		}
		
		if (h->cdoc_count)
			_fcpDestroyHMetadata_cdocs(h);

		if (h->encoding) free(h->encoding);
		if (h->raw) free(h->raw);
		if (h->rest) free(h->rest);
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

				free(h->cdocs[i]->data);
			}

			free(h->cdocs[i]);
		}

		free(h->cdocs);
		h->cdoc_count = 0;
	}
	
	if (h->raw) free(h->raw); h->raw = NULL;
	if (h->rest) free(h->rest); h->rest = NULL;
}

hURI *fcpCreateHURI(void)
{
	hURI *h;

	h = malloc(sizeof (hURI));
	memset(h, 0, sizeof (hURI));

	return h;
}

void fcpDestroyHURI(hURI *h)
{
	if (h) {
		if (h->uri_str) free(h->uri_str);
		if (h->routingkey) free(h->routingkey);
		if (h->cryptokey) free(h->cryptokey);
		if (h->filename) free(h->filename);
		if (h->metastring) free(h->metastring);
	}
}

hDocument *_fcpCreateHDocument(void)
{
	hDocument *h;
	
	h = malloc(sizeof (hDocument));
	memset(h, 0, sizeof (hDocument));
	
	return h;
}

void _fcpDestroyHDocument(hDocument *h)
{
	if (h) {
		int i;
		
		if (h->field_count) {
			
			for (i=0; i < (h->field_count * 2); i += 2) {
				free(h->data[i]);
				free(h->data[i+1]);
			}

			free(h->data);
		}
		
		if (h->name) free(h->name);
		if (h->format) free(h->format);
		if (h->description) free(h->description);
	}
}


/*************************************************************************/
/* FEC specific */
/*************************************************************************/


hSegment *_fcpCreateHSegment(void)
{
  hSegment *h;

	h = malloc(sizeof (hSegment));
  memset(h, 0, sizeof (hSegment));

  return h;
}

void _fcpDestroyHSegment(hSegment *h)
{
	if (h) {
		int i;

		if (h->header_str) free(h->header_str);

		if (h->db_count) {
			for (i=0; i < (unsigned short)h->db_count; i++) {
				_fcpDestroyHBlock(h->data_blocks[i]);
				free(h->data_blocks[i]);

				h->data_blocks[i] = 0;
			}
			
			free(h->data_blocks);
			h->data_blocks = 0;
		}			
		
		if (h->cb_count) {
			for (i=0; i < (unsigned short)h->cb_count; i++) {
				_fcpDestroyHBlock(h->check_blocks[i]);
				free(h->check_blocks[i]);

				h->check_blocks[i] = 0;
			}
			
			free(h->check_blocks);
			h->check_blocks = 0;
		}
	}
}

void _fcpDestroyHSegments(hKey *key)
{
	int i;

	for (i=0; (unsigned)i < key->segment_count; i++) {
		_fcpDestroyHSegment(key->segments[i]);
		free(key->segments[i]);

		key->segments[i] = 0;
	}

	if (key->segment_count) {
		free(key->segments);
		key->segments = 0;
		
		key->segment_count = 0;
	}
}

#ifdef fcpCreationTEST

int main(int c, char *argv[])
{
	hURI *uri;

	if (c != 2) {
		printf("Include a Freenet URI\n");
		return -1;
	}

	_fcpOpenLog(stdout, 4);

	uri = fcpCreateHURI();
	printf("\nURI :%s:\n\n", argv[1]);
	
	fcpParseHURI(uri, argv[1]);

	switch (uri->type) {
	case FN_KEY_CHK:

		printf("Type CHK\n\n");
		break;

	case FN_KEY_SSK:
		
		printf("Type SSK\n\n");
		printf("*   metastring :%s:\n", uri->metastring);
		
		break;
		
	case FN_KEY_KSK:

		printf("Type KSK\n");
		printf("*   metastring :%s:\n", uri->metastring);

		break;
	}
	
	printf("*   routingkey :%s:\n", uri->routingkey);
	printf("*   cryptokey :%s:\n", uri->cryptokey);
	printf("*   filename :%s:\n", uri->filename);
	
	printf("*   uri_str :%s:\n", uri->uri_str);
	printf("\n");
	
	return 0;
}

#endif
