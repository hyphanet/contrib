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
#include <string.h>

#include "ez_sys.h"


static int fcpCloseKeyRead(hFCP *hfcp);
static int fcpCloseKeyWrite(hFCP *hfcp);


int fcpCloseKey(hFCP *hfcp)
{
  if (hfcp->key->openmode & FCP_MODE_O_READ)
	 return fcpCloseKeyRead(hfcp);

  else if (hfcp->key->openmode & FCP_MODE_O_WRITE)
	 return fcpCloseKeyWrite(hfcp);

  else
	 return -1;
}


static int fcpCloseKeyRead(hFCP *hfcp)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpCloseKeyRead()");

	/* unlink both files */
	_fcpBlockUnlink(hfcp->key->tmpblock);
	_fcpBlockUnlink(hfcp->key->metadata->tmpblock);

	/* delete the tmpblocks before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpCloseKeyRead()");

  return 0;
}

static int fcpCloseKeyWrite(hFCP *hfcp)
{
	/*
		- Metadata for redirect is no problem *unless* there's a un-named piece
		  of custom metadata; then the metadata will have 2 un-named docs;
			could be a problem.
	*/

	int rc;

	unsigned long key_size;
	unsigned long meta_size;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpCloseKeyWrite()");

	/* unlink both files */
	_fcpBlockUnlink(hfcp->key->tmpblock);
	_fcpBlockUnlink(hfcp->key->metadata->tmpblock);

	key_size  = hfcp->key->size;
	meta_size = hfcp->key->metadata->size;

	if (!hfcp->key->mimetype) fcpSetMimetype(hfcp->key, "application/octet-stream");

	_fcpLog(FCP_LOG_DEBUG, "mimetype: %s", hfcp->key->mimetype);

#ifdef DMALLOC
	dmalloc_verify(0);
	dmalloc_log_changed(_fcpDMALLOC, 1, 1, 1);
#endif

	if (key_size > hfcp->options->splitblock) {
		_fcpLog(FCP_LOG_VERBOSE, "Starting FEC-Encoded insert");
		
		rc = _fcpPutSplitfile(hfcp);
	}
	else {
		_fcpLog(FCP_LOG_VERBOSE, "Starting single file insert");
		
		rc = _fcpPutBlock(hfcp,
											hfcp->key->tmpblock,
											0,
											"CHK@");
	}
	
	if (rc) { /* bail after cleaning up */
		_fcpLog(FCP_LOG_VERBOSE, "Error inserting file");
		goto cleanup;
	}

	/* copy over the CHK uri */
	fcpParseHURI(hfcp->key->uri, hfcp->key->tmpblock->uri->uri_str);

	/* now the CHK has been inserted; check for metadata and insert a
		 re-direct if necessary */

	/* if it's a CHK with NO metadata, skip insertion of the root key */
	if ((hfcp->key->target_uri->type == KEY_TYPE_CHK) && (meta_size == 0)) {

		/* copy over new CHK */
		fcpParseHURI(hfcp->key->target_uri, hfcp->key->uri->uri_str);
	}
	else { /* for CHK's, SSK's, and KSK's that *have* metadata */
		
		if ((rc = _fcpInsertRoot(hfcp)) != 0) {

			_fcpLog(FCP_LOG_DEBUG, "could not insert root key/map file");

			rc = -1;
			goto cleanup;
		}
	}

	_fcpLog(FCP_LOG_DEBUG, "successfully inserted key %s", hfcp->key->target_uri->uri_str);
	rc = 0;

 cleanup: /* rc should be set to an FCP_ERR code (or zero) */

	/* delete the tmpblocks before exiting */
	_fcpDeleteBlockFile(hfcp->key->tmpblock);
	_fcpDeleteBlockFile(hfcp->key->metadata->tmpblock);

	_fcpLog(FCP_LOG_DEBUG, "Exiting fcpCloseKeyWrite()");
	return rc;
}

