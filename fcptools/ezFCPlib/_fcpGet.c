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
#include <errno.h>

#include "ez_sys.h"


/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	get_file()

	function retrieves a freenet CHK via it's URI.

	function expects the following members in hfcp to be set:

	function returns:
	- zero on success
	- non-zero on error.
*/
int get_file(hFCP *hfcp, char *uri)
{
	char get_command[L_FILE_BLOCKSIZE+1];

	int rc;
	int retry;

	int key_bytes;
	int key_count;

	int meta_bytes;
	int meta_count;

	int index;

	_fcpLog(FCP_LOG_DEBUG, "Entered get_file(key: \"%s\")", uri);

	/* Here we can be certain that the files have been properly initialized */

	rc = snprintf(get_command, L_FILE_BLOCKSIZE,
								"ClientGet\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nEndMessage\n",
								(hfcp->options->skip_local == 0 ? "false" : "true"),
								uri,
								hfcp->htl
								);

	retry = hfcp->options->retry;
	fcpParseHURI(hfcp->key->tmpblock->uri, uri);

	/********************************************************************/

	do {
		int minutes;
		int seconds;

		minutes = (int)(hfcp->options->timeout / 1000 / 60);
		seconds = ((hfcp->options->timeout / 1000) - (minutes * 60));

		/* connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0)	return -1;

		_fcpLog(FCP_LOG_DEBUG, "sending ClientGet message - htl: %d, regress: %d, timeout: %d, keysize: n/a, metasize: n/a, skip_local: %s, rawmode: %d",
						hfcp->htl,
						hfcp->options->regress,
						hfcp->options->timeout,
						(hfcp->options->skip_local ? "Yes" : "No"),
						hfcp->options->rawmode);
		
		/* Send ClientGet command */
		if ((rc = _fcpSend(hfcp->socket, get_command, strlen(get_command))) == -1) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not send Get message");
			goto cleanup;
		}
		
		_fcpLog(FCP_LOG_VERBOSE, "Waiting for response from node - timeout in %d minutes %d seconds)",
						minutes, seconds);

		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);
		
		switch (rc) {

		case FCPRESP_TYPE_DATAFOUND:
			_fcpLog(FCP_LOG_VERBOSE, "Received DataFound message");
			_fcpLog(FCP_LOG_DEBUG, "keysize: %d, metadata size: %d",
							hfcp->response.datafound.datalength - hfcp->response.datafound.metadatalength,
							hfcp->response.datafound.metadatalength
							);

			_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));
			break;
			
		case FCPRESP_TYPE_URIERROR:
			_fcpLog(FCP_LOG_VERBOSE, "Received URIError message");
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			_fcpLog(FCP_LOG_VERBOSE, "Received restarted message");
			_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* unlink the temp files.. they'll be re-linked on look re-entry */
			_fcpUnlink(hfcp->key->tmpblock);
			_fcpUnlink(hfcp->key->metadata->tmpblock);

			/* re-set retry count to initial value */
			retry = hfcp->options->retry;
			
			break;

		case FCPRESP_TYPE_DATANOTFOUND:
			_fcpLog(FCP_LOG_CRITICAL, "Received DataNotFound message");
			break;

		case FCPRESP_TYPE_ROUTENOTFOUND: /* Unreachable, Restarted, Rejected */
			_fcpLog(FCP_LOG_VERBOSE, "Received 'route not found' message");
			_fcpLog(FCP_LOG_DEBUG, "unreachable: %d, restarted: %d, rejected: %d",
							hfcp->response.routenotfound.unreachable,
							hfcp->response.routenotfound.restarted,
							hfcp->response.routenotfound.rejected);
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* unlink the temp files.. they'll be re-linked on look re-entry */
			_fcpUnlink(hfcp->key->tmpblock);
			_fcpUnlink(hfcp->key->metadata->tmpblock);

			/* this will route us to a restart */
			rc = FCPRESP_TYPE_RESTARTED;

			break;

		/* returned when there's an abnormal socket termination */
		case EZERR_SOCKET_TIMEOUT:
			retry--;
			
			_fcpLog(FCP_LOG_VERBOSE, "Received timeout waiting for response");
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* unlink the temp files.. they'll be re-linked on look re-entry */
			_fcpUnlink(hfcp->key->tmpblock);
			_fcpUnlink(hfcp->key->metadata->tmpblock);

			/* this will route us to a restart */
			rc = FCPRESP_TYPE_RESTARTED;
			
			break;
			
		case FCPRESP_TYPE_FORMATERROR:
			_fcpLog(FCP_LOG_CRITICAL, "FormatError - reason: %s", hfcp->response.formaterror.reason);
			break;
			
		case FCPRESP_TYPE_FAILED:
			_fcpLog(FCP_LOG_CRITICAL, "Failed - reason: %s", hfcp->response.failed.reason);
			break;
			
		default:
			_fcpLog(FCP_LOG_DEBUG, "get_file() - received unknown response code: %d", rc);
			break;

		} /* switch (rc) */			

	} while ((rc == FCPRESP_TYPE_RESTARTED) && (retry >= 0));

	/* if we exhauseted our retries, then return a be-all Timeout error */
	if (retry < 0) {
		rc = EZERR_SOCKET_TIMEOUT;
		goto cleanup;
	}
	
	/* If data is not found, bail */
	
  if (rc != FCPRESP_TYPE_DATAFOUND) {
		rc = -1;
    goto cleanup;
	}
	
	/* Now we have key data, and possibly meta data waiting for us */
	
	hfcp->key->metadata->size = meta_bytes = hfcp->response.datafound.metadatalength;
	hfcp->key->size = key_bytes = hfcp->response.datafound.datalength - meta_bytes;
	
	if (meta_bytes > L_RAW_METADATA) {
		_fcpLog(FCP_LOG_DEBUG, "metadata size too large: %d", meta_bytes);
		rc = -1;
		goto cleanup;
	}

	/* initialize this so that later we can use it in *if* statement */
	meta_count = 0;

	if (meta_bytes) {

		hfcp->key->metadata->raw_metadata = (char *)malloc(meta_bytes+1);

		/* link to the metadata file (only if there's metadata) */
		_fcpLink(hfcp->key->metadata->tmpblock, _FCP_WRITE);

		/* keep writing metadata as long as there's metadata to write..
			 fetching more datachunks when required */
		
		_fcpLog(FCP_LOG_DEBUG, "retrieve metadata");
		
		/* index traverses through the raw metadata (char *) */
		index = 0;

		/* if there's no metadata, the respose will be retrieved and then the loop
			 is exited.. the next code block extracts the key data from the first
			 response retrieved */
	
		while ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
			
			_fcpLog(FCP_LOG_DEBUG, "retrieved datachunk");

			/* set meta_count below datachunk length */
			meta_count = (meta_bytes > hfcp->response.datachunk.length ? 
										hfcp->response.datachunk.length :
										meta_bytes);

			if (meta_count == 0) {
				_fcpLog(FCP_LOG_DEBUG, "no metadata to process");
				break;
			}

			_fcpLog(FCP_LOG_DEBUG, "writing metadata block");
			_fcpWrite(hfcp->key->metadata->tmpblock->fd, hfcp->response.datachunk.data, meta_count);

			/* copy over the raw metadata for handling later */
			memcpy(hfcp->key->metadata->raw_metadata + index,
						 hfcp->response.datachunk.data,
						 meta_count);
			
			meta_bytes -= meta_count;
			index += meta_count;

			/* if we're done, break out to avoid fetching another chunk prematurely */
			if (meta_bytes == 0) {

				_fcpLog(FCP_LOG_DEBUG, "finished metadata");

				hfcp->key->metadata->raw_metadata[index] = 0;
				_fcpMetaParse(hfcp->key->metadata, hfcp->key->metadata->raw_metadata);

				break;
			}
		}
	} /* at this point all the metadata has been written */

	/* check rc to see what caused loop exit */

	if (meta_bytes != 0) {
		_fcpLog(FCP_LOG_DEBUG, "loop exited while there was still metadata to process");
		
		rc = -1;
		goto cleanup;
	}

	/* check for trailing key data in most recent data chunk */

	_fcpLog(FCP_LOG_DEBUG, "meta_count: %d, hfcp->response.datachunk.length: %d",
		meta_count, hfcp->response.datachunk.length);

	/* link to the key file */
	_fcpLink(hfcp->key->tmpblock, _FCP_WRITE);

	if (meta_count < hfcp->response.datachunk.length) {

		_fcpLog(FCP_LOG_DEBUG, "processing %d/%d bytes of trailing key data",
						hfcp->response.datachunk.length - meta_count,
						hfcp->response.datachunk.length
						);

		/* key_count is the chunk length minus the metadata written within it */
		key_count = (hfcp->response.datachunk.length - meta_count);

		_fcpWrite(hfcp->key->tmpblock->fd, hfcp->response.datachunk.data + meta_count, key_count);
		
		key_bytes -= key_count;
		index += key_count;

		_fcpLog(FCP_LOG_DEBUG, "key_bytes remaining: %d", key_bytes);
	}

	/* here, all metadata has been written, and some key data has been written;
		 result is the most recent data chunk is exhausted of whatever key data
		 it had (both key and meta), so fetch more chunks and write them to the
		 keyfile */

	while (key_bytes > 0) {
		
		/* the remaining data chunks should be just key data */
		if ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
			
			_fcpLog(FCP_LOG_DEBUG, "retrieved datachunk");

			key_count = hfcp->response.datachunk.length;
			
			_fcpWrite(hfcp->key->tmpblock->fd, hfcp->response.datachunk.data, key_count);
			
			key_bytes -= key_count;
			index += key_count;
		}
		else {
			_fcpLog(FCP_LOG_DEBUG, "expected missing datachunk message");

			rc = -1;
			goto cleanup;
		}
	}

	/* all metadata and key data has been written.. yay! */
	_fcpUnlink(hfcp->key->tmpblock);
	_fcpUnlink(hfcp->key->metadata->tmpblock);

  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "get_file() - retrieved key: %s", uri);

	return 0;

 cleanup: /* this is called when there is an error above */

	/* unlink both.. not to worry if fd is -1 (it's checked in *Unlink()) */
	_fcpUnlink(hfcp->key->tmpblock);
	_fcpUnlink(hfcp->key->metadata->tmpblock);

  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "abnormal termination");

	return rc;
}

/*
	On success, function set hfcp->key->uri with CHK of data.
 */
int get_follow_redirects(hFCP *hfcp, char *uri)
{
	hDocument *doc;

	char      *key;
	char       get_uri[L_URI+1];

	int   rc;
	int   depth;

	/* make calls to get_file() until we have exhausted any/all redirects */

	_fcpLog(FCP_LOG_DEBUG, "get_follow_redirects()");

	strncpy(get_uri, uri, L_URI);
	depth = 0;

	rc = get_file(hfcp, get_uri);

	_fcpLog(FCP_LOG_DEBUG, "get_file() returned as rc: %d", rc);
	
	while (rc == 0) {

		/* now we have the key data and perhaps metadata */
		
		_fcpLog(FCP_LOG_DEBUG, "check metadata");
		
		/* if true, there's no metadata; we got data! */
		if (hfcp->key->metadata->size == 0) {
			
			_fcpLog(FCP_LOG_DEBUG, "no metadata?  got data!");
			fcpParseHURI(hfcp->key->tmpblock->uri, get_uri);

			break;
		}
		else { /* check for the case where there's metadata, but no redirect */
			
			_fcpLog(FCP_LOG_DEBUG, "there's metadata.. check for redirect key");

			doc = cdocFindDoc(hfcp->key->metadata, 0);
			key = (doc ? cdocLookupKey(doc, "Redirect.Target") : 0);

			if (!key) {
				
				_fcpLog(FCP_LOG_DEBUG, "metadata, but no redirect key.. got data");
				fcpParseHURI(hfcp->key->tmpblock->uri, get_uri);

				break;
			}
			else { /* key/val pair is redirect */

				_fcpLog(FCP_LOG_DEBUG, "key: %s", key);

				strncpy(get_uri, key, L_URI);
				depth++;

				rc = get_file(hfcp, get_uri);
			}
		}
	}

	if (rc != 0)
		_fcpLog(FCP_LOG_DEBUG, "get_file() returned: %d", rc);

	_fcpLog(FCP_LOG_DEBUG, "target: %s, chk: %s, recursions: %d",
					hfcp->key->target_uri->uri_str,
					hfcp->key->tmpblock->uri->uri_str,
					depth);

	return 0;
}

