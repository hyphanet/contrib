/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/


#include "ezFCPlib.h"

#include <stdio.h>
#include <stdlib.h>


extern int put_file(hFCP *hfcp, char *key_filename, char *meta_filename);
extern int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);

extern int put_date_redirect(hFCP *hfcp, char *uri);
extern int put_redirect(hFCP *hfcp, char *uri);

extern long file_size(char *filename);

/*
	fcpPutKeyFromFile()

	function will test the validity of the arguments from caller.

	function returns:
	- zero on success
	- non-zero on error
*/
int fcpPutKeyFromFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename)
{
	int key_size;
	int meta_size;
	int rc;

	/* clear the error string */
	if (hfcp->error) {
		free(hfcp->error);
		hfcp->error = 0;
	}

	if ((rc = file_size(key_filename)) < 0) {
		_fcpLog(FCP_LOG_VERBOSE, "Key data not found in file \"%s\"", key_filename);

		hfcp->error = strdup("could not open key file");
		return -1;
	}

	key_size = rc;

	if (meta_filename) {
		meta_size = file_size(meta_filename);

		if (meta_size < 0) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not open metadata file");
			
			/* set the proper error string always on return to non-ez code */
			hfcp->error = strdup("could not open metadata file");
			
			return -1;
		}
	}
	else /* there's no metadata filename; no metadata */
		meta_size = 0;

	/* meta_size should be properly set at this point */

	/* Now insert the key data as a CHK@, and later we'll insert a redirect
		 if necessary. If it's larger than L_BLOCK_SIZE, insert as an FEC
		 encoded splitfile. */

	if (key_size > L_BLOCK_SIZE)
		rc = put_fec_splitfile(hfcp, key_filename, meta_filename);
	
	else /* Otherwise, insert as a normal key */
		rc = put_file(hfcp, key_filename, meta_filename);
	
	if (rc) {
		_fcpLog(FCP_LOG_VERBOSE, "Insert error; \"%s\"", (hfcp->error ? hfcp->error: "unspecified error"));

		/* destroy the key since we didn't actually insert it properly */
		_fcpDestroyHKey(hfcp->key);
		hfcp->key = 0;

		return -1;
	}

	/* now check if it's KSK or SSK and insert redirect to hfcp->key->uri */
	/* create the final key as a re-direct to the inserted CHK@ */

	if (_fcpParseURI(hfcp->key->target_uri, key_uri)) {

		/* set the proper error string always on return to non-ez code */
		hfcp->error = strdup("target uri is invalid");
		return -1;
	}

	switch (hfcp->key->target_uri->type) {

	case KEY_TYPE_CHK: /* for CHK's */
		break;

	case KEY_TYPE_SSK:
	case KEY_TYPE_KSK:

		{ /* insert a redirect to point to hfcp->key->uri */
			/* code here is identical to code in fcpCloseKey.c:105 */
			
			hFCP *hfcp_meta;
			
			hfcp_meta = _fcpCreateHFCP();
			hfcp_meta->key = _fcpCreateHKey();
			
			/* uri was already checked above for validity */
			_fcpParseURI(hfcp_meta->key->uri, hfcp->key->target_uri->uri_str);
			
			if (put_redirect(hfcp_meta, hfcp->key->uri->uri_str)) {
				
				_fcpLog(FCP_LOG_VERBOSE, "Could not insert redirect \"%s\"", hfcp_meta->key->uri->uri_str);
				_fcpDestroyHFCP(hfcp_meta);
				
				return -1;
			}
			
			/* success inserting the re-direct */
			_fcpParseURI(hfcp->key->uri, hfcp_meta->key->uri->uri_str);
			_fcpDestroyHFCP(hfcp_meta);
			
			break;
		}
	}
	
	/* on exit, hfcp->key->uri holds the inserted final uri */
	return 0;
}

