
/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/


#include <sys/types.h>
#include <sys/stat.h>

#include <stdio.h>
#include <stdlib.h>

#include "ezFCPlib.h"


extern int put_file(hFCP *hfcp, char *key_filename, char *meta_filename);
extern int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);


int fcpPutKeyFromFile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int rc;
	struct stat fstat;

	hfcp->key = _fcpCreateHKey();

	if (stat(key_filename, &fstat)) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not open file \"%s\"", key_filename);
		return -1;
	}
	hfcp->key->size = fstat.st_size;

	if (meta_filename) {
		if (!(stat(meta_filename, &fstat)))
			hfcp->key->metadata->size = fstat.st_size;
	}

	/* If it's larger than L_BLOCK_SIZE, insert as an FEC encoded splitfile */
	if (hfcp->key->size > L_BLOCK_SIZE) {

		_fcpLog(FCP_LOG_VERBOSE, "Performing FEC-encoded insert");
		rc = put_fec_splitfile(hfcp, key_filename, meta_filename);
	}
	else { /* Otherwise, insert as a normal key */

		_fcpParseURI(hfcp->key->uri, "CHK@");

		_fcpLog(FCP_LOG_VERBOSE, "Performing basic insert (non-redundant)");
		rc = put_file(hfcp, key_filename, meta_filename);
	}
	
	if (rc)
		_fcpLog(FCP_LOG_CRITICAL, "Could not insert file \"%s\" into Freenet", key_filename);
	else
		_fcpLog(FCP_LOG_NORMAL, "%s", hfcp->key->uri->uri_str);

	return rc;
}

