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
	hURI *uri;

	_fcpLog(FCP_LOG_DEBUG, "Entered fcpGetKeyToFile()");
	_fcpLog(FCP_LOG_DEBUG, "Function parameters:");
	_fcpLog(FCP_LOG_DEBUG, "key_uri: %s, key_filename: %s, meta_filename: %s",
					key_uri,
					key_filename,
					meta_filename
					);

	hfcp->key = _fcpCreateHKey();
	hfcp->key->metadata = _fcpCreateHMetadata();

	uri = fcpCreateHURI();
	if (fcpParseURI(uri, key_uri)) goto cleanup;

	/* uri holds the passed in & parsed key uri */
	switch (uri->type) {

	case KEY_TYPE_CHK: /* for CHK's */

		fcpParseURI(hfcp->key->target_uri, uri->uri_str);
		fcpParseURI(hfcp->key->uri, uri->uri_str);
		break;

	case KEY_TYPE_SSK:
	case KEY_TYPE_KSK:

		/* uri is a target_uri, so store it */
		fcpParseURI(hfcp->key->target_uri, uri->uri_str);

		/* find the CHK that the target_uri is referring to */
		get_redirect(hfcp, hfcp->key->uri->uri_str, uri->uri_str);

		/* now in uri is the CHK that is the redirect target */
		break;
	}

	/* TODO: check metadata to detect splitfiles */

	_fcpLog(FCP_LOG_VERBOSE, "Start basic retrieve");
	rc = get_file(hfcp, hfcp->key->uri->uri_str, key_filename, meta_filename);

	if (rc) /* bail after cleaning up */
		goto cleanup;
	
	_fcpLog(FCP_LOG_VERBOSE, "Key: %s\n  Uri: %s", key_filename, hfcp->key->target_uri->uri_str);
	return 0;
	

 cleanup: /* rc should be set to an FCP_ERR code */

	_fcpLog(FCP_LOG_VERBOSE, "Error retrieving key: %s", hfcp->key->target_uri->uri_str);

	return rc;
}

