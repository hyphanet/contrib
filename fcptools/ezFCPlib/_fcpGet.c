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

static int read_metadata_line(hFCP *hfcp, char *line, int size);


/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	FUNCTION:_fcpGetBLock()

	PARAMETERS:

	- hfcp: A created and initialized hFCP struct.
	- keyblock: Points to an hBlock of keydata.
	- metablock: Points to an hBlock of metadata.
	- uri: Freenet URI to retrieve.

	IN:
	- hfcp->htl: Hops to live.
	- hfcp->host: Host of Freenet node.
	- hfcp->port: Port of Freenet node.

	- hfcp->options->delete_local: Send RemoveLocalKey?
	- hfcp->options->retry: Number of retries on socket timeouts and Restarts.
	- hfcp->options->meta_redirect: Insert metadata via redirect?
	- hfcp->options->dbr: Insert key as DBR?

	OUT:
	- hfcp->key->size: Number of bytes of keydata.
	- hfcp->key->metadata->size: Number of bytes of metadata.
	- hfcp->key->metadata->cdocs: Parsed metadata control doc information.

	- hfcp->key->tmpblock->uri: CHK of inserted block.

	RETURNS: Zero on success, <0 on error.
*/
int _fcpGetBLock(hFCP *hfcp, hBlock *keyblock, hBlock *metablock, char *uri)
{
	char get_command[L_FILE_BLOCKSIZE+1];  /* used *twice* in this function */

	int rc;
	int retry;

	unsigned long key_bytes;
	unsigned long key_count;

	unsigned long meta_bytes;

	int bytes;

	_fcpLog(FCP_LOG_DEBUG, "Entered _fcpGetBLock(key: \"%s\")", uri);

	/* Here we can be certain that the files have been properly initialized */

	rc = snprintf(get_command, L_FILE_BLOCKSIZE,
								"ClientGet\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nEndMessage\n",
								(hfcp->options->remove_local == 0 ? "false" : "true"),
								uri,
								hfcp->htl
								);

	retry = hfcp->options->retry;
	fcpParseHURI(keyblock->uri, uri);

	/********************************************************************/

	do {
		unsigned int minutes;
		unsigned int seconds;

		minutes = (int)(hfcp->options->timeout / 1000 / 60);
		seconds = ((hfcp->options->timeout / 1000) - (minutes * 60));

		/* connect to Freenet FCP */
		if ((rc = _fcpSockConnect(hfcp)) != 0) goto cleanup;

		_fcpLog(FCP_LOG_VERBOSE, "sending ClientGet message to %s:%u, htl=%u, remove_local=%s",
						hfcp->host,
						hfcp->port,
						hfcp->htl,
						(hfcp->options->remove_local ? "Yes" : "No"));

		
		_fcpLog(FCP_LOG_DEBUG, "other information.. regress=%u, keysize=%u, metasize=%u",
						hfcp->options->regress,
						hfcp->key->size,
						hfcp->key->metadata->size);

		/* Send ClientGet command */
		if ((rc = _fcpSend(hfcp->socket, get_command, strlen(get_command))) == -1) {
			_fcpLog(FCP_LOG_CRITICAL, "Error sending ClientGet message");
			goto cleanup;
		}
		
		_fcpLog(FCP_LOG_VERBOSE, "Waiting for response from node - timeout in %u minutes %u seconds)",
						minutes, seconds);

		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);
		
		switch (rc) {

		case FCPRESP_TYPE_DATAFOUND:
			_fcpLog(FCP_LOG_VERBOSE, "Received DataFound message");
			_fcpLog(FCP_LOG_DEBUG, "keysize: %u, metadata size: %u",
							hfcp->response.datafound.datalength - hfcp->response.datafound.metadatalength,
							hfcp->response.datafound.metadatalength
							);

			_fcpLog(FCP_LOG_DEBUG, "timeout value: %u seconds", (int)(hfcp->options->timeout / 1000));
			break;
			
		case FCPRESP_TYPE_URIERROR:
			_fcpLog(FCP_LOG_VERBOSE, "Received URIError message");
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			_fcpLog(FCP_LOG_VERBOSE, "Received Restarted message");
			_fcpLog(FCP_LOG_DEBUG, "timeout value: %u seconds", (int)(hfcp->options->timeout / 1000));
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* re-set retry count to initial value */
			retry = hfcp->options->retry;
			
			break;

		case FCPRESP_TYPE_DATANOTFOUND:
			_fcpLog(FCP_LOG_CRITICAL, "Received DataNotFound message");
			break;

		case FCPRESP_TYPE_ROUTENOTFOUND: /* Unreachable, Restarted, Rejected */
			_fcpLog(FCP_LOG_VERBOSE, "Received RouteNotFound message");

			_fcpLog(FCP_LOG_DEBUG, "unreachable: %u, restarted: %u, rejected: %u, backed off: %u",
							hfcp->response.routenotfound.unreachable,
							hfcp->response.routenotfound.restarted,
							hfcp->response.routenotfound.rejected,
							hfcp->response.routenotfound.backedoff);
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* re-set retry count to initial value */
			retry = hfcp->options->retry;
			
			/* this will route us to a restart */
			rc = FCPRESP_TYPE_RESTARTED;

			break;

		/* returned when there's an abnormal socket termination */
		case EZERR_SOCKET_TIMEOUT:
			retry--;
			
			_fcpLog(FCP_LOG_VERBOSE, "Received timeout waiting for response");
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* this will route us to a restart */
			rc = FCPRESP_TYPE_RESTARTED;
			
			break;
			
		case FCPRESP_TYPE_FORMATERROR:
			_fcpLog(FCP_LOG_CRITICAL, "Received FormatError message: %s", hfcp->response.formaterror.reason);
			break;
			
		case FCPRESP_TYPE_FAILED:
			_fcpLog(FCP_LOG_CRITICAL, "Received Failed message: %s", hfcp->response.failed.reason);
			break;
			
		default:
			_fcpLog(FCP_LOG_DEBUG, "_fcpGetBLock() - received unknown response code: %d", rc);
			break;

		} /* switch (rc) */			

	} while ((rc == FCPRESP_TYPE_RESTARTED) && (retry >= 0));

	/* if we exhauseted our retries, then return a be-all Timeout error */
	if (retry < 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Failed to retrieve file after %u retries", hfcp->options->retry);

		rc = EZERR_SOCKET_TIMEOUT;
		goto cleanup;
	}
	
	/* If data is not found, bail */
	
  if (rc != FCPRESP_TYPE_DATAFOUND) {
		_fcpLog(FCP_LOG_CRITICAL, "Failed to retrieve file");

		rc = -1;
    goto cleanup;
	}

	/* Now we have key data, and possibly meta data waiting for us */
	/* temp blocks are already linked */
	
	hfcp->key->metadata->size = meta_bytes = hfcp->response.datafound.metadatalength;
	hfcp->key->size = key_bytes = (hfcp->response.datafound.datalength - meta_bytes);

	/******************************************************************/

	/* only link if necessary */
	if (meta_bytes)	_fcpBlockLink(metablock, _FCP_WRITE);

	while (meta_bytes > 0) {

		/* re-use the get_command string */
		if ((bytes = read_metadata_line(hfcp, get_command, L_FILE_BLOCKSIZE)) <= 0) {
			_fcpLog(FCP_LOG_DEBUG, "read_metadata_line() returned zero");
			
			rc = -1;
			goto cleanup;
		}		

		_fcpWrite(metablock->fd, get_command, bytes);

		/* TODO write the raw metadata to a buf and pass into metaparse when done
			 (only the cdoc; don't parse the 'rest' piece */

		meta_bytes -= bytes;

		_fcpLog(FCP_LOG_DEBUG, "meta_bytes remaining: %d", meta_bytes);
	}

	/* if there was metadata, cleanup */
	if (hfcp->key->metadata->size > 0) {
		_fcpLog(FCP_LOG_VERBOSE, "Read %d bytes of metadata", hfcp->key->metadata->size);
		_fcpBlockUnlink(metablock);

		/* TODO parse into hfcp struct and save */
	}

	if (key_bytes) {
		_fcpLog(FCP_LOG_DEBUG, "link key file");
		_fcpBlockLink(keyblock, _FCP_WRITE);
	}

	/* if the DataChunk hasn't been received, do a NOP and let the loop
		 structure carry into the key section */
	if (hfcp->response.datachunk._index == -1);

	else if (hfcp->response.datachunk._index < hfcp->response.datachunk.length) {
		
		_fcpWrite(keyblock->fd,
							hfcp->response.datachunk.data + hfcp->response.datachunk._index,
							hfcp->response.datachunk.length - hfcp->response.datachunk._index);

		key_bytes -= (hfcp->response.datachunk.length - hfcp->response.datachunk._index);
	}

	/* this shouldn't happen */
	else if (hfcp->response.datachunk._index != hfcp->response.datachunk.length) {
		_fcpLog(FCP_LOG_DEBUG, "OUCH this shouldn't happen");

		rc = -1;
		goto cleanup;
	}

	/* at this point, we need to fetch a new DataChunk and dump em all
		 into the key file */

	while (key_bytes > 0) {
		
		/* the remaining data chunks should be just key data */
		if ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
			
			_fcpLog(FCP_LOG_DEBUG, "retrieved datachunk");

			key_count = hfcp->response.datachunk.length;
			_fcpWrite(keyblock->fd, hfcp->response.datachunk.data, key_count);
			
			key_bytes -= key_count;
		}
		else {
			_fcpLog(FCP_LOG_DEBUG, "expected missing datachunk message");

			rc = -1;
			goto cleanup;
		}
	}

	if (hfcp->key->size > 0) {
		
		_fcpLog(FCP_LOG_VERBOSE, "Read key data");
		_fcpBlockUnlink(keyblock);
	}

  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "_fcpGetBLock() - retrieved key: %s", uri);

	return 0;

 cleanup: /* this is called when there is an error above */

	/* unlink both.. not to worry if fd is -1 (it's checked in *Unlink()) */
	_fcpBlockUnlink(keyblock);
	_fcpBlockUnlink(metablock);

  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "abnormal termination");

	return rc;
}

/*
	On success, function set hfcp->key->uri with CHK of data.
 */
int _fcpGetFollowRedirects(hFCP *hfcp, char *uri)
{
	char  get_uri[L_URI+1];

	int   rc;
	int   depth;

	/* make calls to _fcpGetBLock() until we have exhausted any/all redirects */

	_fcpLog(FCP_LOG_DEBUG, "_fcpGetFollowRedirects()");

	strncpy(get_uri, uri, L_URI);
	depth = 0;

	/* this call will put metadata control doc info in
		 hfcp->key->metadata->dcocs */

	rc = _fcpGetBLock(hfcp,
										hfcp->key->tmpblock,
										hfcp->key->metadata->tmpblock,
										get_uri);

	_fcpLog(FCP_LOG_DEBUG, "_fcpGetBLock() returned as rc: %d", rc);
	
	while (rc == 0) {

		/* now we have the key data and perhaps metadata */

		/* TODO check for static redirects */
		/* check the hfcp->key->metadata->cdocs[] array */

		if (hfcp->_redirect) {
			_fcpLog(FCP_LOG_VERBOSE, "Following redirect to %s", hfcp->_redirect);

			strncpy(get_uri, hfcp->_redirect, L_URI);
			depth++;

			free(hfcp->_redirect); hfcp->_redirect = 0;
			
			rc = _fcpGetBLock(hfcp,
												hfcp->key->tmpblock,
												hfcp->key->metadata->tmpblock,
												get_uri);
		}

		/* TODO check for date-based redirects */
		/* calculate date target based on today's date + 'future_insert' parameter */

		else {
			
			_fcpLog(FCP_LOG_DEBUG, "got data!");
			fcpParseHURI(hfcp->key->tmpblock->uri, get_uri);

			/* TODO check metadata for meta_redirect flag; if true then
				 fetch the CHK@ referred and use that metadata
				 (use *tmp_hfcp)
			*/
			
			break;
		}
	}
	
	if (rc != 0) return -1;

	_fcpLog(FCP_LOG_DEBUG, "target: %s, chk: %s, recursions: %u",
					hfcp->key->target_uri->uri_str,
					hfcp->key->tmpblock->uri->uri_str,
					depth);

	return 0;
}


static int read_metadata_line(hFCP *hfcp, char *line, int size)
{
	int  i;

	i = 0;
	while (1) {

		/* check for case where datachunk needs to be 'refreshed' */
		if ((hfcp->response.datachunk._index == -1) ||
				(hfcp->response.datachunk._index == hfcp->response.datachunk.length)) {

			_fcpLog(FCP_LOG_DEBUG, "!! refreshing DataChunk");
			
			if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_DATACHUNK) {
				_fcpLog(FCP_LOG_DEBUG, "expected DataChunk");
				return -1;
			}
		}

		line[i++] = hfcp->response.datachunk.data[hfcp->response.datachunk._index++];

		if (i == size) {
			_fcpLog(FCP_LOG_DEBUG, "OOPS! truncating metadata line!!");
			line[i-1] = 0;

			return i-1;
		}

		if (line[i-1] == '\n') {
			line[i] = 0;

			/*_fcpLog(FCP_LOG_DEBUG, "I got %d bytes of metadata :%s:", i, line);*/
			return i;
		}
	}
}
