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
#include <stdlib.h>
#include <stdio.h>


extern int put_file(hFCP *hfcp, char *key_filename, char *meta_filename);
extern int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);

extern int put_redirect(hFCP *hfcp_root, char *uri_dest);

extern long file_size(char *filename);

static int fcpCloseKeyRead(hFCP *hfcp);
static int fcpCloseKeyWrite(hFCP *hfcp);

int fcpCloseKey(hFCP *hfcp)
{
  if (hfcp->key->openmode & _FCP_O_READ)
	 return fcpCloseKeyRead(hfcp);

  else if (hfcp->key->openmode & _FCP_O_WRITE)
	 return fcpCloseKeyWrite(hfcp);

  else
	 return -1;
}


static int fcpCloseKeyRead(hFCP *hfcp)
{
	hfcp = hfcp;
  return 0;
}


static int fcpCloseKeyWrite(hFCP *hfcp)
{
	hFCP *tmp_hfcp;

	int rc;
	int size;

	/* close the temporary key file */
	fclose(hfcp->key->tmpblock->file);

	/* close the temporary metadata file */
	fclose(hfcp->key->metadata->tmpblock->file);

	tmp_hfcp = _fcpCreateHFCP();
	size = file_size(hfcp->key->tmpblock->filename);

	if (size > L_BLOCK_SIZE)
		rc = put_fec_splitfile(tmp_hfcp,
													 hfcp->key->tmpblock->filename,
													 hfcp->key->metadata->tmpblock->filename);

	else /* Otherwise, insert as a normal key */
		rc = put_file(tmp_hfcp,
									hfcp->key->tmpblock->filename,
									hfcp->key->metadata->tmpblock->filename);

	if (rc) {
		_fcpLog(FCP_LOG_VERBOSE, "Insert error; \"%s\"", (hfcp->error ? hfcp->error: "unspecified error"));

		_fcpDestroyHFCP(tmp_hfcp);
		return -1;
	}

	/* tmp_hfcp->key->uri is the CHK@ of the file we've just inserted */
	/* the target_uri was set in fcpOpenKeyRead() */

	switch (hfcp->key->target_uri->type) {

	case KEY_TYPE_CHK: /* for CHK's, copy over the generated CHK to the target_uri field */

		/* re-parse the CHK into target_uri (it only contains CHK@ currently) */
		_fcpParseURI(hfcp->key->uri, tmp_hfcp->key->uri->uri_str);
		break;

	case KEY_TYPE_SSK:
	case KEY_TYPE_KSK:
		
		{ /* insert a redirect to point to hfcp->key->uri */
			/* code here is identical to code in fcpPutKeyFromFile.c:111 */
			
			hFCP *hfcp_meta;
			
			hfcp_meta = _fcpCreateHFCP();
			hfcp_meta->key = _fcpCreateHKey();
			
			/* uri was already checked above for validity */
			_fcpParseURI(hfcp_meta->key->uri, hfcp->key->target_uri->uri_str);
			
			if (put_redirect(hfcp_meta, tmp_hfcp->key->uri->uri_str)) {
				
				_fcpLog(FCP_LOG_VERBOSE, "Could not insert redirect \"%s\"", hfcp_meta->key->uri->uri_str);
				_fcpDestroyHFCP(hfcp_meta);
				
				return -1;
			}
			else { /* congrads.. successful insert into Freenet */
				_fcpLog(FCP_LOG_NORMAL, "%s", hfcp_meta->key->uri->uri_str);
			}
			
			_fcpParseURI(hfcp->key->uri, hfcp_meta->key->uri->uri_str);
			_fcpDestroyHFCP(hfcp_meta);
			
			break;
		}
	}
		
	_fcpLog(FCP_LOG_VERBOSE, "Successfully inserted key \"%s\"", hfcp->key->uri->uri_str);
	
	return 0;
}

