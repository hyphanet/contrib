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
#include <string.h>

#include "ez_sys.h"


/*
	fcpGetKeyToFile()
*/
int fcpGetKeyToFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	int rc;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpGetKeyToFile()");
	_fcpLog(FCP_LOG_DEBUG, "Function parameters:");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s, key_filename: %s, meta_filename: %s",
					key_uri,
					key_filename,
					meta_filename
					);
	
	/* function must get the key, then determine if it's a normal
	key or a manifest for a splitfile */

	fcpParseHURI(hfcp->key->target_uri, key_uri);

	/* get the vitals for the key data file */
	if (key_filename) {
		
		if (_fcpBlockSetFilename(hfcp->key->tmpblock, key_filename) != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not link to key file %s", key_filename);
			
			rc = -1;
			goto cleanup;
		}
	}
	else {
		hfcp->key->tmpblock->filename[0] = 0;
	}
	
	if (meta_filename) {
		
		if (_fcpBlockSetFilename(hfcp->key->metadata->tmpblock, meta_filename) != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not link to metadata file %s", meta_filename);
			
			rc = -1;
			goto cleanup;
		}
	}
	else {
		hfcp->key->metadata->tmpblock->filename[0] = 0;
	}

	rc = _fcpGetKeyToFile(hfcp, key_uri, key_filename, meta_filename);

cleanup:

	/* delete temp files before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpGetKeyToFile()");

	return rc;
}

#if 0
int _fcpGetFollowRedirects(hFCP *hfcp, char *uri)
{
	char  get_uri[L_URI+1];

	int   rc;
	int   depth;

	/* make calls to _fcpGetBLock() until we have exhausted any/all redirects */

	_fcpLog(FCP_LOG_DEBUG, "_fcpGetFollowRedirects()");

	strncpy(get_uri, uri, L_URI);
	depth = 0;
	rc = 0;

	while (rc == 0) {
		
		/*  we have the key data and perhaps metadata from prior call
				(both from GetKeyToFile and OpenKeyRead) */
		
		/* TODO check for static redirects */
		/* check the hfcp->key->metadata->cdocs[] array */

		if (hfcp->key->db_redirect) {

			/*CalculateDBRTarget(with egress param);*/

			_fcpLog(FCP_LOG_VERBOSE, "Following redirect to..?");

			/*hfcp->key->static_redirect = 0;*/

			/* get the DBR target (either KSK or SSK) */
			rc = _fcpGetBLock(hfcp,
												hfcp->key->tmpblock,
												hfcp->key->metadata->tmpblock,
												get_uri);

			strncpy(get_uri, hfcp->key->tmpblock->uri->uri_str, L_URI);
			depth++;
		}

		/* now handle the KSK/SSK@abd & KSK/SSK@abc-dbr123 */
		if ((hfcp->key->uri->type == KEY_TYPE_KSK) || (hfcp->key->uri->type == KEY_TYPE_SSK)) {

			/* get the key; save *custom* metadata and use cdoc info to
				 follow for key data in CHK (normal or FEC) */
			
			rc = _fcpGetBLock(hfcp,
												hfcp->key->tmpblock,
												hfcp->key->metadata->tmpblock,
												get_uri);

			strncpy(get_uri, hfcp->key->tmpblock->uri->uri_str, L_URI);
			depth++;
		}
		
		/* assume it's either a normal CHK or a splitfile */
		
		if (hfcp->key->splitfile) {

			rc = _fcpGetSplitfile(hfcp);
			
			/* copy over the new uri? */
		}
		
		else {
			
			rc = _fcpGetBLock(hfcp,
												hfcp->key->tmpblock,
												hfcp->key->metadata->tmpblock,
												get_uri);

			strncpy(get_uri, hfcp->key->tmpblock->uri->uri_str, L_URI);
			depth++;
		}

		/* TODO: only break from while loop if we don't need another redirect */
		break;
	}
	
	if (rc != 0) goto cleanup;

	_fcpLog(FCP_LOG_DEBUG, "target: %s, chk: %s, recursions: %u",
					hfcp->key->target_uri->uri_str,
					hfcp->key->tmpblock->uri->uri_str,
					depth);

	rc = 0;

 cleanup:

	return rc;
}
#endif


int _fcpGetKeyToFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	int         rc;

	hDocument *doc;
	char      *next_uri;
	int        get_next;
	
	doc      = 0;
	next_uri = 0;

	_fcpLog(FCP_LOG_VERBOSE, "Fetching first block");

	/* this isn't funcional but more informational */
	if (hfcp->options->noredirect != 0)
		_fcpLog(FCP_LOG_VERBOSE, "Starting single retrieve (no redirects)");
	
	rc = _fcpGetBLock(hfcp,
										hfcp->key->tmpblock,
										hfcp->key->metadata->tmpblock,
										key_uri);
	
	if (rc) { /* bail after cleaning up */
		_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key");

		rc = -1;
		goto cleanup;
	}

	_fcpLog(FCP_LOG_DEBUG, "successfully retrieved the 1st block..");
	
	/* Here, the key and meta data is within the tmpblocks */
	
	/* check the metadata structures for any redirects */

	if (hfcp->options->noredirect)
		get_next = 0;
	else
		get_next = 1;

	while (get_next) {

		/*if (doc) free(doc);*/
		doc = cdocFindDoc(hfcp->key->metadata, hfcp->key->target_uri->metastring);

		if (!doc)
			get_next = 0;

		else {
			if (next_uri) {
				free(next_uri);
				next_uri = 0;
			}

			switch (doc->type) {

			case META_TYPE_REDIRECT:
				next_uri = strdup(cdocLookupKey(doc, "Redirect.Target"));
				break;
				
			case META_TYPE_DBR:
				next_uri = _fcpDBRString(hfcp->key->target_uri, hfcp->options->future);
				break;
				
			case META_TYPE_SPLITFILE:
				rc = _fcpGetSplitfile(hfcp);
				break;
				
			default:
				_fcpLog(FCP_LOG_DEBUG, "note: unhandled doctype: %d", doc->type);
				get_next = 0;
				
				break;
			}
		}
		
		/* get the "next" block */
		if (get_next) {
			rc = _fcpGetBLock(hfcp,
												hfcp->key->tmpblock,
												hfcp->key->metadata->tmpblock,
												next_uri);
			
			if (rc) { /* bail after cleaning up */
				_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key");
				
				rc = -1;
				goto cleanup;
			}
		}
	}		
	
	rc = 0;

cleanup:

	if (next_uri) free(next_uri);
	/*if (doc) _fcpDestroyHDocument(doc);*/

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpGetKeyToFile()");
	return rc;
}
