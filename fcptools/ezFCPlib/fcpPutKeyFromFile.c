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
	FUNCTION:fcpPutKeyFromFile()

	function creates a freenet CHK using the contents in 'key_filename'
	along with key metadata contained in 'meta_filename'.  Function will
	check	and validate the size of both file arguments.
	
	PARAMETERS:

	- hfcp: A created and initialized hFCP struct.
	- key_uri:
	- key_filename:	Filename to insert as key data.
	- meta_filename: Filename to insert as metadata (or NULL for no metadata).

	IN:

	OUT:

	RETURNS: Zero on success, <0 on error.
*/
int fcpPutKeyFromFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	unsigned long ul;
	unsigned long meta_size;

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

	if ((hfcp->key->target_uri->type == KEY_TYPE_CHK) && (hfcp->options->dbr)) {
		_fcpLog(FCP_LOG_CRITICAL, "Cannot insert a date-based redirect for a CHK");
		return -1;
	}

	/* get the vitals for the key data file */
	if (key_filename) {
		if ((ul = _fcpFilesize(key_filename)) == (unsigned)~0L) { /* one's complement of zero is error indicator */

			_fcpLog(FCP_LOG_CRITICAL, "Key file not found: %s", key_filename);
			rc = -1;
			goto cleanup;
		}
		else { /* it's a valid file with non-zero length */
			hfcp->key->size = ul;
			
			/* must call delete to first delete the file and then set the 'delete'
				 property back to zero */
			_fcpDeleteBlockFile(hfcp->key->tmpblock);
			
			if (_fcpBlockSetFilename(hfcp->key->tmpblock, key_filename) != 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not link to key file %s", key_filename);
				
				rc = -1;
				goto cleanup;
			}
		}
	}
	else
		hfcp->key->size = 0;

	if (meta_filename) {
		if ((ul = _fcpFilesize(meta_filename)) == (unsigned)~0L) { /* one's complement of zero is error indicator */
			
			_fcpLog(FCP_LOG_CRITICAL, "Metadata file not found: %s", meta_filename);
			rc = -1;
			goto cleanup;
		}
		else {
			hfcp->key->metadata->size = ul;

			/* must call delete (see above) */
			_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);
			
			if (_fcpBlockSetFilename(hfcp->key->metadata->tmpblock, meta_filename) != 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not link to metadata file %s", meta_filename);
				
				rc = -1;
				goto cleanup;
			}
		}
	}
	else
		meta_size = 0;
	
	/* key_size and meta_size should be properly set at this point */
	
	/* set the mimetype if the key exists */
	if (hfcp->key->size) {
		if (hfcp->key->mimetype) free(hfcp->key->mimetype);
		hfcp->key->mimetype = strdup(_fcpGetMimetype(key_filename));
	}

	_fcpLog(FCP_LOG_DEBUG, "returned mimetype: %s", hfcp->key->mimetype);

#ifdef DMALLOC
	dmalloc_verify(0);
	dmalloc_log_changed(_fcpDMALLOC, 1, 1, 1);
#endif

	/* Now insert the key data as a CHK@, and later we'll insert a redirect
		 if necessary. If it's larger than L_BLOCK_SIZE, insert as an FEC
		 encoded splitfile. */

	if (hfcp->key->size > hfcp->options->splitblock) {
		_fcpLog(FCP_LOG_VERBOSE, "Starting FEC-Encoded insert");

		rc = _fcpPutSplitfile(hfcp);

		/* CHK uri is already in proper location (hfcp->key->uri) */
	}
	
	else { /* Otherwise, insert as a normal key */
		_fcpLog(FCP_LOG_VERBOSE, "Starting single file insert");

		rc = _fcpPutBlock(hfcp,
											hfcp->key->tmpblock,
											0,
											"CHK@");

		/* tmpblocks will be deleted before function exit below */

		/* copy over the CHK uri */
		fcpParseHURI(hfcp->key->uri, hfcp->key->tmpblock->uri->uri_str);
	}

	if (rc) { /* bail after cleaning up */
		_fcpLog(FCP_LOG_VERBOSE, "Error inserting file");
		goto cleanup;
	}

	/* now the CHK has been inserted; check for metadata and insert a
		 re-direct if necessary */

	/* if it's a CHK with NO metadata, skip insertion of the root key */
	if ((hfcp->key->target_uri->type == KEY_TYPE_CHK) && (hfcp->key->metadata->size == 0)) {

		/* copy over new CHK */
		fcpParseHURI(hfcp->key->target_uri, hfcp->key->uri->uri_str);
	}
	else { /* for CHK's (with metadata) and all SSK's/KSK's */
		
		if ((rc = _fcpInsertRoot(hfcp)) != 0) {

			_fcpLog(FCP_LOG_DEBUG, "could not insert root key/map file");

			rc = -1;
			goto cleanup;
		}
	}

	/*fcpParseHURI(hfcp->key->target_uri, hfcp->key->uri->uri_str);*/

	_fcpLog(FCP_LOG_DEBUG, "successfully inserted key %s from file %s", hfcp->key->target_uri->uri_str, key_filename);
	rc = 0;

 cleanup: /* rc should be set to an FCP_ERR code (or zero) */

	/* delete the tmpblocks before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpPutKeyFromFile()");

	return rc;
}


/*
	FUNCTION _fcpInsertRoot()
	
	PARAMETERS:

	IN:
	- hfcp->key->uri: CHK@ uri.

	OUT:

	RETURNS: Zero on success, <0 on error.
*/
int _fcpInsertRoot(hFCP *hfcp)
{
	hFCP      *tmp_hfcp;
	hMetadata *meta;
	hDocument *doc;

	char  buf[8193];

	char      *metadata_raw;
	char      *uri;

	char      *dbr;
	int        rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered _fcpInsertRoot()");

	/* first let's be careful w/ our pointers, eh? */
	tmp_hfcp     = 0;
	metadata_raw = 0;
	uri          = 0;
	doc          = 0;
	dbr          = 0;

	meta = _fcpCreateHMetadata();
	
	/* read metadata on disk (if any) into buf */
	if (hfcp->key->metadata->size) {
		
		_fcpBlockLink(hfcp->key->metadata->tmpblock, _FCP_READ);
		
		/* allocate space for raw metadata */
		metadata_raw = malloc(hfcp->key->metadata->size + 1);
		
		/* read it in one pass */
		_fcpRead(hfcp->key->metadata->tmpblock->fd, metadata_raw, hfcp->key->metadata->size);
		
		/* make it a cstrz */
		metadata_raw[hfcp->key->metadata->size] = 0;
		
		/* parse */
		if (_fcpMetaParse(meta, metadata_raw) != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Error parsing custom metadata");
			
			rc = -1;
			goto cleanup;
		}
		
		_fcpBlockUnlink(hfcp->key->metadata->tmpblock);
	}
	
	/* this must happen for both DBR's and non-DBR's */
	doc = cdocAddDoc(meta, 0);
	cdocAddKey(doc, "Redirect.Target", hfcp->key->uri->uri_str);
	
	/* all the user metadata (and a meta-redirect if specified) is in the
		 'meta' struct */
		
	tmp_hfcp = fcpInheritHFCP(hfcp);
	
	/* check on the dbr option */

	if (hfcp->options->dbr) {
		
		if (!(dbr = _fcpDBRString(hfcp->key->target_uri, hfcp->options->future))) {
			rc = -1;
			goto cleanup;
		}

		_fcpLog(FCP_LOG_DEBUG, "dbr: %s", dbr);
		
		/* store the dbr uri over target_uri */
		uri = strdup(dbr);
		free(dbr); dbr = 0;
	}

	else {
		uri = strdup(hfcp->key->target_uri->uri_str);
	}
	
	/* open the regular *or* date-coded key (depends on above) */
	fcpOpenKey(tmp_hfcp, uri, FCP_MODE_O_WRITE);
	
	if (metadata_raw) free(metadata_raw);
	metadata_raw = _fcpMetaString(meta);
	
	fcpWriteMetadata(tmp_hfcp, metadata_raw, strlen(metadata_raw));
	
	_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
	_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);
	
	/* now insert the redirect key */
	rc = _fcpPutBlock(tmp_hfcp,
										0,
										tmp_hfcp->key->metadata->tmpblock,
										tmp_hfcp->key->target_uri->uri_str);

	_fcpLog(FCP_LOG_DEBUG, "inserted redirect key");
	
	/* for DBR's, need to insert root key with DateRedirect.Target cdoc */
	if (hfcp->options->dbr) {

		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);

		tmp_hfcp = fcpInheritHFCP(hfcp);
		
		fcpOpenKey(tmp_hfcp, hfcp->key->target_uri->uri_str, FCP_MODE_O_WRITE);
		
		snprintf(buf, 8192, "Version\nRevision=1\nEndPart\nDocument\nDateRedirect.Target=%s\nEnd", hfcp->key->target_uri->uri_str);
		fcpWriteMetadata(tmp_hfcp, buf, strlen(buf));
		
		_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
		_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);
		
		/* now insert the metadata (with no key data to insert) */
		rc = _fcpPutBlock(tmp_hfcp,
											0,
											tmp_hfcp->key->metadata->tmpblock,
											hfcp->key->target_uri->uri_str);
	}
	
	/* fall into cleanup and return rc, which on success == 0 */

	/* save the uri (it changes for SSK's and certain CHK's */
	fcpParseHURI(hfcp->key->target_uri, tmp_hfcp->key->tmpblock->uri->uri_str);

	rc = 0;

 cleanup:

	/* Destroy ALL!!! (except for hfcp.. leave that for caller) */

	if (tmp_hfcp) {
		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
	}

	if (doc) {
		_fcpDestroyHDocument(doc);
		free(doc);
	}

	if (meta) {
		_fcpDestroyHMetadata(meta);
		free(meta);
	}

	if (metadata_raw) free(metadata_raw);
	if (dbr) free(dbr);
	if (uri) free(uri);

	return rc;
}
