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


void unlink_key(hKey *hKey)
{
	/* close the temporary key file */
	if (hKey->tmpblock->fd != -1) close(hKey->tmpblock->fd);

	/* close the temporary metadata file */
	if (hKey->metadata->tmpblock->fd != -1) close(hKey->metadata->tmpblock->fd);

	hKey->tmpblock->fd = -1;
	hKey->metadata->tmpblock->fd = -1;
	
	_fcpLog(FCP_LOG_DEBUG, "unlinked key, closed temporary files");
}


static int fcpCloseKeyRead(hFCP *hfcp)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered fcpCloseKeyRead()");

	/* close the temporary files */
	unlink_key(hfcp->key);

  return 0;
}


static int fcpCloseKeyWrite(hFCP *hfcp)
{
	int rc;

	int key_size;
	int meta_size;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpCloseKeyWrite()");

	/* close the temporary files */
	unlink_key(hfcp->key);

	key_size  = file_size(hfcp->key->tmpblock->filename);
	meta_size = file_size(hfcp->key->metadata->tmpblock->filename);

	if (key_size > L_BLOCK_SIZE)
		rc = put_fec_splitfile(hfcp,
													 hfcp->key->tmpblock->filename,
													 (meta_size > 0 ? hfcp->key->metadata->tmpblock->filename: 0));

	else /* Otherwise, insert as a normal key */
		rc = put_file(hfcp, "CHK@", 
									hfcp->key->tmpblock->filename,
									(meta_size > 0 ? hfcp->key->metadata->tmpblock->filename: 0));

	if (rc) /* bail after cleaning up */
		goto cleanup;

	/* hfcp->key->uri is the CHK@ of the file we've just inserted */

	switch (hfcp->key->target_uri->type) {

	case KEY_TYPE_CHK: /* for CHK's, copy over the generated CHK to the target_uri field */

		/* copy it to the target */
		fcpParseURI(hfcp->key->target_uri, hfcp->key->uri->uri_str);

		break;

	case KEY_TYPE_SSK:
	case KEY_TYPE_KSK:

		put_redirect(hfcp, hfcp->key->target_uri->uri_str, hfcp->key->uri->uri_str);
		break;
	}
		
	_fcpLog(FCP_LOG_VERBOSE, "Uri: %s", hfcp->key->target_uri->uri_str);
	
	return 0;

 cleanup: /* rc should be set to an FCP_ERR code */
	
	_fcpLog(FCP_LOG_VERBOSE, "Error inserting file");

	return rc;
}

