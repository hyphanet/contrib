
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

extern long file_size(char *filename);

/*
	fcpPutKeyFromFile()

	function will test the validity of the arguments from caller.

	function may modify:
	- hfcp->error
	- hfcp->key->size
	- hfcp->key->uri

	function returns:
	- zero on success
	- non-zero on error
*/
int fcpPutKeyFromFile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int rc;

	if ((rc = file_size(key_filename)) < 0) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not open key file \"%s\"", key_filename);

		/* set the proper error string always on return to non-ez code */
		hfcp->error = str_reset(hfcp->error, "Could not open key file");

		return -1;
	}

	hfcp->key = _fcpCreateHKey();
	hfcp->key->size = rc;

	/* get the equivalent mimetype for this file we're inserting */
	hfcp->key->mimetype = str_reset(hfcp->key->mimetype, GetMimeType(key_filename));

	if (meta_filename) {
		if ((rc = file_size(meta_filename)) < 0) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not open metadata file");
			
			/* set the proper error string always on return to non-ez code */
			hfcp->error = str_reset(hfcp->error, "Could not open metadata file");
			
			_fcpDestroyHKey(hfcp->key);
			hfcp->key = 0;

			return -1;
		}

		/* otherwise, we've found a file supposed metadata in it */
		hfcp->key->metadata->size = rc;
	}

	/* key not specified, then generate a CHK */
	if (!hfcp->key->uri) _fcpParseURI(hfcp->key->uri, "CHK@");
	
	/* If it's larger than L_BLOCK_SIZE, insert as an FEC encoded splitfile */
	if (hfcp->key->size > L_BLOCK_SIZE) {
		rc = put_fec_splitfile(hfcp, key_filename, meta_filename);

		if (rc) {
			_fcpLog(FCP_LOG_VERBOSE, "FEC Insert error; \"%s\"", (hfcp->error ? hfcp->error: "unhandled error"));

			_fcpDestroyHKey(hfcp->key);
			hfcp->key = 0;
		}
	}
	else { /* Otherwise, insert as a normal key */
		rc = put_file(hfcp, key_filename, meta_filename);

		if (rc) {
			_fcpLog(FCP_LOG_VERBOSE, "Insert error; \"%s\"", (hfcp->error ? hfcp->error: "unhandled error"));

			_fcpDestroyHKey(hfcp->key);
			hfcp->key = 0;
		}
	}
	
	return rc;
}

