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

#include "ez_sys.h"

/*
	fcpPutKeyFromFile()
	
	Parameters:

	- hFCP *hfcp
	Pointer to a created & initialized struct hFCP.
	
	- char *key_uri
	Key URI. Possible variants:

	1. CHK@
	2. KSK@<key name>
	3. SSK@<private key>[/<docname>]
	
	- char *key_filename
	Filename to insert as key data

	- char *meta_filename
	Filename to insert as metadata (or NULL for no metadata)

	Function returns:
	- Zero on success
	- Negative integer on error
*/

/* This function deletes the tmpfiles before exiting. The "_" version does not */

int fcpPutKeyFromFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	int rc;

	rc = _fcpPutKeyFromFile(hfcp, key_uri, key_filename, meta_filename);

	/* delete the tmpblocks before exiting */
	_fcpDeleteFile(hfcp->key->tmpblock);
	_fcpDeleteFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "deleted temp files");

	return rc;
}

int _fcpPutKeyFromFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	unsigned long ul;
	int rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpPutKeyFromFile()");
	_fcpLog(FCP_LOG_DEBUG, "Function parameters:");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s, key_filename: %s, meta_filename: %s",
					key_uri,
					key_filename,
					meta_filename
					);
	
	/* put the target uri away */
	fcpParseHURI(hfcp->key->target_uri, key_uri);

	if (key_filename) {
		if ((ul = _fcpFilesize(key_filename)) == (unsigned)~0L) { /* one's complement of zero is error indicator */

			_fcpLog(FCP_LOG_CRITICAL, "Key file not found: %s", key_filename);
			return -1;
		}
		else /* it's a valid file with non-zero length */
			hfcp->key->size = ul;
	}
	else
		hfcp->key->size = 0;

	if (meta_filename) {
		if ((ul = _fcpFilesize(meta_filename)) == (unsigned)~0L) {

			_fcpLog(FCP_LOG_CRITICAL, "Metadata file not found: %s", meta_filename);
			return -1;
		}
		else hfcp->key->metadata->size = ul;
	}
	else
		hfcp->key->metadata->size = 0;

	/* key_size and meta_size should be properly set at this point */

	/* set the mimetype if the key exists */
	if (hfcp->key->size) {
		if (hfcp->key->mimetype) free(hfcp->key->mimetype);
		hfcp->key->mimetype = strdup(_fcpGetMimetype(key_filename));
	}

	_fcpLog(FCP_LOG_DEBUG, "returned mimetype: %s", hfcp->key->mimetype);
	_fcpLog(FCP_LOG_DEBUG, "copying tmp files");

	if (hfcp->key->size)
		if (_fcpCopyFile(hfcp->key->tmpblock->filename, key_filename) < 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not copy internal tempfile key %s", key_filename);
			return -1;
		}

	if (hfcp->key->metadata->size) {
		if (_fcpCopyFile(hfcp->key->metadata->tmpblock->filename, meta_filename) < 0)
			_fcpLog(FCP_LOG_CRITICAL, "Could not copy internal tempfile metadata %s", meta_filename);
			return -1;
	}

	/* Now insert the key data as a CHK@, and later we'll insert a redirect
		 if necessary. If it's larger than L_BLOCK_SIZE, insert as an FEC
		 encoded splitfile. */

#ifdef DMALLOC
	dmalloc_verify(0);
	dmalloc_log_changed(_fcpDMALLOC, 1, 1, 1);
#endif

	if (hfcp->key->size > hfcp->options->splitblock) {
		_fcpLog(FCP_LOG_VERBOSE, "Starting FEC-Encoded insert");
		rc = put_fec_splitfile(hfcp);
	}
	
	else { /* Otherwise, insert as a normal key */
		_fcpLog(FCP_LOG_VERBOSE, "Starting single file insert");
		rc = put_file(hfcp, "CHK@");
	}

	if (rc) { /* bail after cleaning up */
		_fcpLog(FCP_LOG_VERBOSE, "Error inserting file");
		goto cleanup;
	}

	/* now check if it's KSK or SSK and insert redirect to hfcp->key->uri */
	/* create the final key as a re-direct to the inserted CHK@ */

	fcpParseHURI(hfcp->key->uri, hfcp->key->tmpblock->uri->uri_str);

	switch (hfcp->key->target_uri->type) {
	case KEY_TYPE_CHK: /* for CHK's */

		fcpParseHURI(hfcp->key->target_uri, hfcp->key->uri->uri_str);
		break;

	case KEY_TYPE_KSK:

			put_redirect(hfcp, hfcp->key->target_uri->uri_str, hfcp->key->uri->uri_str);
			break;

	case KEY_TYPE_SSK:
		{
			char *key;
			int   len;

			put_redirect(hfcp, hfcp->key->target_uri->uri_str, hfcp->key->uri->uri_str);

			strncpy(hfcp->key->private_key, hfcp->key->target_uri->keyid, L_KEY);
			fcpInvertPrivateKey(hfcp); 

			len = strlen(hfcp->key->target_uri->uri_str);

			/* 20 is at least 15 more than a safe buffer to account for optional "freenet:" */
			key = (char *)malloc(len+20);
			snprintf(key, len+19, "freenet:SSK@%s/%s//", hfcp->key->public_key, hfcp->key->target_uri->docname);

			fcpParseHURI(hfcp->key->target_uri, key);
			break;
		}
	}

	_fcpLog(FCP_LOG_DEBUG, "successfully inserted key %s from file %s", hfcp->key->target_uri->uri_str, key_filename);
	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpPutKeyFromFile()");

	return 0;

 cleanup: /* rc should be set to an FCP_ERR code */

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpPutKeyFromFile()");

	return rc;
}

