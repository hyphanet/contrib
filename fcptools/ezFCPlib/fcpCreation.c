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
		h->host = malloc(sizeof(EZFCP_DEFAULT_HOST + 1));
		strcpy(h->host, EZFCP_DEFAULT_HOST);
	}
	else {
		h->host = malloc(strlen(host) + 1);
		strcpy(h->host, host);
	}

	h->port = (port == 0 ? EZFCP_DEFAULT_PORT : port );
	h->htl =  (htl  < 0 ? EZFCP_DEFAULT_HTL  : htl  );
	
	h->options = _fcpCreateHOptions();

	/* do the handle option mask */
	h->options->noredirect       = (optmask & FCP_MODE_RAW ? FCP_MODE_RAW : 0);
	h->options->remove_local  = (optmask & FCP_MODE_REMOVE_LOCAL ? FCP_MODE_REMOVE_LOCAL : 0);
	h->options->dbr           = (optmask & FCP_MODE_DBR ? FCP_MODE_DBR : 0);
	h->options->meta_redirect = (optmask & FCP_MODE_REDIRECT_METADATA ? FCP_MODE_REDIRECT_METADATA : 0);

	_fcpLog(FCP_LOG_DEBUG, "noredirect: %u, remove_local: %u, dbr: %u, meta_redirect: %u",
					h->options->noredirect,
					h->options->remove_local,
					h->options->dbr,
					h->options->meta_redirect);
	
	h->key = _fcpCreateHKey();
	
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
		                hfcp->options->dbr |
		                hfcp->options->meta_redirect);

	/* copy over any other options */
	h->options->timeout = hfcp->options->timeout;
	h->options->retry = hfcp->options->retry;
	h->options->mintimeout = hfcp->options->mintimeout;

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

		if (h->_redirect) free(h->_redirect);

		_fcpDestroyHOptions(h->options);
		_fcpDestroyResponse(h);

		h->socket = FCP_SOCKET_DISCONNECTED;

		/* let caller free 'h' */
	}
}

hOptions *_fcpCreateHOptions(void)
{
	hOptions *h;

	h = malloc(sizeof (hOptions));
	memset(h, 0, sizeof (hOptions));

	h->logstream = EZFCP_DEFAULT_LOGSTREAM;		
	h->remove_local = EZFCP_DEFAULT_DELETELOCAL;
	h->regress = EZFCP_DEFAULT_REGRESS;
	h->retry = EZFCP_DEFAULT_RETRY;
	h->splitblock = EZFCP_DEFAULT_BLOCKSIZE;
	h->timeout = EZFCP_DEFAULT_TIMEOUT;
	h->verbosity = EZFCP_DEFAULT_VERBOSITY;

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

		if (h->segment_count) {

			for (i=0; (unsigned)i < h->segment_count; i++) {
				_fcpDestroyHSegment(h->segments[i]);
				free(h->segments[i]);
			}
			
			free(h->segments);
		}
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
		if (h->response.nodeinfo.istransient) free(h->response.nodeinfo.istransient);
	}
}

hBlock *_fcpCreateHBlock(void)
{
	hBlock *h;

	h = malloc(sizeof (hBlock)); /* 1st ! */
	memset(h, 0, sizeof (hBlock));

	h->uri = fcpCreateHURI();

	if (_fcpTmpfile(h->filename) != 0) {
		_fcpLog(FCP_LOG_DEBUG, "could not create temp file %s", h->filename);
		return 0;
	}		

	h->fd = -1;
	h->delete = 1;

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

		/*free(h->cdocs);*/
		h->cdoc_count = 0;
	}
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
		if (h->filename) free(h->filename);
		if (h->metastring) free(h->metastring);
	}
}

/*
	fcpParseHURI()

	This function parses a string containing a fully-qualified Freenet URI
	into simpler components.  It is written to be re-entrant on the same
	hURI pointer (it can be called repeatedly without being re-created.
	
	FreenetURI handles parsing and creation of the Freenet URI format, defined
	as follows:
	
	freenet:[KeyType@]RoutingKey[,CryptoKey][,n1=v1,n2=v2,...][/docname][//metastring]
	
	where KeyType is the TLA of the key (currently SVK, SSK, KSK, or CHK). If
	omitted, KeyType defaults to KSK.
	
	For KSKs, the string keyword (docname) takes the RoutingKey position and the
	remainder of the fields are inapplicable (except metastring). Examples:
	freenet:KSK@foo//bar freenet:KSK@test.html freenet:test.html
	
	RoutingKey is the modified Base64 encoded key value. CryptoKey is the
	modified Base64 encoded decryption key.
	
	Following the RoutingKey and CryptoKey there may be a series of
	name=value pairs representing URI meta-information.
	
	The docname is only meaningful for SSKs, and is hashed with the PK
	fingerprint to get the key value.
	
	The metastring is meant to be passed to the metadata processing systems that
	act on the retrieved document.
*/
int fcpParseHURI(hURI *uri, char *key)
{
	int rc;
	int len;

	char *p_key;
	char *uri_s;

	uri_s = malloc(strlen(key)+128);

	/* clear out the dynamic arrays before attempting to parse a new uri */
  if (uri->routingkey) free(uri->routingkey);
  if (uri->cryptokey) free(uri->cryptokey);
	if (uri->filename) free(uri->filename);
	if (uri->metastring) free(uri->metastring);
	if (uri->uri_str) free(uri->uri_str);
	
	/* save the key root */
	p_key = key;
	
	/* zero the block of memory */
	memset(uri, 0, sizeof (hURI));

  /* skip 'freenet:' */
  if (!strncmp(key, "freenet:", 8))
    key += 8;
	
  /* classify key header */
  if (!strncmp(key, "SSK@", 4)) {
		int i;
		
    uri->type = KEY_TYPE_SSK;
		strcpy(uri_s, "freenet:SSK@");

		key += 4;

		/* next character can be {',', '/', '//', '\0'} */

		/* copy all the characters in the RoutingKey section */
		uri->routingkey = malloc(L_KEY+1);

		for (i=0; (*key != ',') && (*key != '/') && (*key != 0); key++)
			uri->routingkey[i++] = *key;

		/* grab the routingkey/RoutingKey */
		uri->routingkey[i] = 0;
		strcat(uri_s, uri->routingkey);

		/* if we're on a ',', then we either next have to parse a
			 CryptoKey or n1=v1 pair */
		if (*key == ',') {
			int doNameValPairs;

			key++;
			doNameValPairs = 0;

			/* test the current section to see if it's a CryptoKey */
			for (i=0; key[i] != '\0'; i++) {

				if (key[i] == ',') { /* we have a CryptoKey, *and* n1=v1 pairs */

					uri->cryptokey = malloc(i+1);
					memcpy(uri->cryptokey, key, i);
					uri->cryptokey[i] = 0;

					/* build the uri_s version */
					strcat(uri_s, ",");
					strcat(uri_s, uri->cryptokey);

					key += i;

					/* set so that in next section we look for the name/val pairs */
					doNameValPairs = 1;

					break;
				}
				
				if (key[i] == '/') { /* we have a CryptoKey, and we have *no* n1=v1 pairs */

					uri->cryptokey = malloc(i+1);
					
					memcpy(uri->cryptokey, key, i);
					uri->cryptokey[i] = 0;

					/* build the uri_s version */
					strcat(uri_s, ",");
					strcat(uri_s, uri->cryptokey);

					key += i;

					break;
				}

				if (key[i] == '=') { /* we have *no* CryptoKey, but only n1=v1 pairs */

					/* set so that in next section we look for the name/val pairs */
					doNameValPairs = 1;

					break;
				}
			}

			/* if we hit a NULL char then we have a crypto key and that's it! */
			if (*key=='\0') {
				
				uri->cryptokey = malloc(i+1);
				memcpy(uri->cryptokey, key, i);
				uri->cryptokey[i] = '\0';

				/* build the uri_s version */
				strcat(uri_s, ",");
				strcat(uri_s, uri->cryptokey);
				
				goto success;
			}

			/* we have the crypto key (if there was one); process the name/val
				 pairs*/

			if (doNameValPairs) {

				/* parse em */
				/* ensure key points to a '/' or '\0' */
			}
		}

		/* here, key points to the start of the docname section, the start of the
			 metastring section or '\0' */

		/* check if key is a '/' */
		if ((*key == '/') && (*(key+1) != '/')) {

			key++;
			if (*key == '\0') {

				rc = -1;
				goto cleanup;
			}

			/* iterate through docname and stop at '//' or '\0' */
			for (i=0; key[i] != '\0'; i++) {

				/* if key is a '//' then break */
				if ((key[i]=='/') && (key[i+1]== '/')) break;
			}					

			uri->filename = malloc(i+1);
			memcpy(uri->filename, key, i);
			uri->filename[i] = '\0';

			/* build the uri_s version */
			strcat(uri_s, "/");
			strcat(uri_s, uri->filename);
		}
		
		/* advance past the actual filename part */
		key += i;
		
		/* if we also have a metastring.. */
		if ((*key=='/') && (*(key+1)=='/')) {

			/* position after the '//' */
			key += 2;

			/* copy over the metastring */
			uri->metastring = strdup(key);
			
			/* build the uri_s version */
			strcat(uri_s, "//");
			strcat(uri_s, uri->metastring);
		}
	}
	
  else if (!strncmp(key, "CHK@", 4)) {
	
		uri->type = KEY_TYPE_CHK;

		strcpy(uri_s, "freenet:CHK@");
		key += 4;

		len = strlen(key);

		if (len) {
			int i;

			for (i=0; (key[i]!='\0') && (key[i]!=',') && (key[i]!='/'); i++);
			
			uri->routingkey = malloc(i+1);
			memcpy(uri->routingkey, key, i);
			uri->routingkey[i] = '\0';

			strcat(uri_s, uri->routingkey);
			
			if (key[i]=='\0') goto success;
			
			/* get the CryptoKey if it exists */
			if (key[i]==',') {
				
				key += i+1;
				
				for (i=0; (key[i]!='\0') && (key[i]!='/'); i++);

				uri->cryptokey = malloc(i+1);
				memcpy(uri->cryptokey, key, i);
				uri->cryptokey[i] = '\0';				

				strcat(uri_s, ",");
				strcat(uri_s, uri->cryptokey);
			}
			
			if (key[i]=='\0') goto success;
			
			key += i;
			uri->filename = strdup(key);
			
			strcat(uri_s, "/");
			strcat(uri_s, uri->filename);
		}
	}
	
	/* freenet:KSK@freetext.html */
  else if (!strncmp(key, "KSK@", 4)) {

    uri->type = KEY_TYPE_KSK;

		strcpy(uri_s, "freenet:KSK@");
    key += 4;

		uri->routingkey = strdup(key);
		strcat(uri_s, uri->routingkey);

		/* TODO add logic for handling 'metastring' */
  }
  
  else {
		_fcpLog(FCP_LOG_CRITICAL, "Error attempting to parse invalid key: %s", p_key);
    rc = -1;
		goto cleanup;
  }

 success:

	uri->uri_str = strdup(uri_s);
	_fcpLog(FCP_LOG_DEBUG, "uri: %s", uri->uri_str);

	rc = 0;
	
 cleanup:

	free(uri_s);
  return rc;
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
			}
			
			free(h->data_blocks);
		}			
		
		if (h->cb_count) {
			for (i=0; i < (unsigned short)h->cb_count; i++) {
				_fcpDestroyHBlock(h->check_blocks[i]);
				free(h->check_blocks[i]);
			}
			
			free(h->check_blocks);
		}
	}
}

void _fcpDestroyHSegments(hKey *key)
{
	int i;

	for (i=0; (unsigned)i < key->segment_count; i++) {
		_fcpDestroyHSegment(key->segments[i]);
	}

	key->segment_count = 0;
}

#ifdef fcpCreationTEST

int main(int c, char *argv[])
{
	hURI *uri;

	if (c != 2) {
		printf("Include a Freenet URI\n");
		return -1;
	}

	_fcpOpenLog(stdout, 2);

	uri = fcpCreateHURI();
	printf("URI:%s\n", argv[1]);
	
	fcpParseHURI(uri, argv[1]);

	switch (uri->type) {
		
	case KEY_TYPE_CHK:

		printf("*   routingkey:%s:\n", uri->routingkey);
		printf("*   cryptokey:%s:\n", uri->cryptokey);
		printf("*   filename:%s:\n", uri->filename);
		printf("*   uri_str:%s:\n", uri->uri_str);

		break;

	case KEY_TYPE_SSK:
		
		printf("*   routingkey:%s:\n", uri->routingkey);
		printf("*   cryptokey:%s:\n", uri->cryptokey);
		printf("*   filename:%s:\n", uri->filename);
		printf("*   metastring:%s:\n", uri->metastring);
		printf("*   uri_str:%s:\n", uri->uri_str);
		
		break;
		
	case KEY_TYPE_KSK:

		printf("*   routingkey:%s:\n", uri->routingkey);
		printf("*   metastring:%s:\n", uri->metastring);
		printf("*   uri_str:%s:\n", uri->uri_str);

		break;
	}
		
	return 0;
}

#endif
