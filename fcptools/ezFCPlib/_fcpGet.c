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
	char buf[L_FILE_BLOCKSIZE+1];  /* used *twice* in this function */

	int rc;
	int retry;

	unsigned long key_bytes;
	unsigned long key_count;

	unsigned long meta_count;
	unsigned long meta_bytes;
	unsigned long meta_str_len;

	char         *meta_str;

	_fcpLog(FCP_LOG_DEBUG, "Entered _fcpGetBLock(key: \"%s\")", uri);

	/* first thing, clear out the metadata structure
		 to make way for new and improved data ! */
	_fcpDestroyHMetadata_cdocs(hfcp->key->metadata);
	
	/* Here we can be certain that the files have been properly initialized */

	rc = snprintf(buf, L_FILE_BLOCKSIZE,
								"ClientGet\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nEndMessage\n",
								(hfcp->options->remove_local == 0 ? "false" : "true"),
								uri,
								hfcp->htl
								);

	meta_str = NULL;
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
		if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
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
	if (meta_bytes)
		_fcpBlockLink(metablock, _FCP_WRITE);
	
	meta_str_len = 0;

	/* setup the keydata here so that later we can just use it */
	if (key_bytes) {
		_fcpLog(FCP_LOG_DEBUG, "link key file");
		_fcpBlockLink(keyblock, _FCP_WRITE);
	}
	
	while (meta_bytes > 0) {
		meta_str = malloc(meta_bytes+1);
		
		if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_DATACHUNK) {
			_fcpLog(FCP_LOG_DEBUG, "expected DataChunk");
			
			rc = -1;
			goto cleanup;
		}		

		/* set count to the min(datachunk.length, metadata_remaining) */
		meta_count = (hfcp->response.datachunk.length > meta_bytes ?
									meta_bytes : hfcp->response.datachunk.length);
		
		_fcpWrite(metablock->fd, hfcp->response.datachunk.data, meta_count);
		memcpy(meta_str+meta_str_len, hfcp->response.datachunk.data, meta_count);

		/* adjust index over raw metadata buffer */
		meta_str_len += meta_count;

		/* and adjust count of remaining total metadata bytes to process */
		meta_bytes -= meta_count;

		_fcpLog(FCP_LOG_DEBUG, "meta_bytes remaining: %d", meta_bytes);
	}

	/* if there was metadata, cleanup */
	if (hfcp->key->metadata->size > 0) {
		
		/* null the buffer */
		meta_str[meta_str_len] = 0;
		/*_fcpLog(FCP_LOG_DEBUG, "raw metadata: %s", meta_str);*/
		
		_fcpLog(FCP_LOG_VERBOSE, "Read %d bytes of metadata", hfcp->key->metadata->size);
		_fcpBlockUnlink(metablock);

		if (!hfcp->key->metadata) {
			_fcpLog(FCP_LOG_CRITICAL, "hMetadata struct not malloc'ed");

			rc = -1;
			goto cleanup;
		}

		/* parse the metadata */
		_fcpLog(FCP_LOG_DEBUG, "parsing the metadata");

		_fcpMetaParse(hfcp->key->metadata, meta_str);

		/* handle the partial datachunk */
		if (meta_count < hfcp->response.datachunk.length) {
			
			/* the first 'meta_count' bytes are metadata, the remaining bytes in the
				 datachunk are key data */
			
			_fcpWrite(keyblock->fd,
								hfcp->response.datachunk.data + meta_count,
								hfcp->response.datachunk.length - meta_count);
			
			key_bytes -= (hfcp->response.datachunk.length - meta_count);
		}
	}

	/* at this point, we need to fetch a new DataChunk and dump em all
		 into the key file */

	while (key_bytes > 0) {
		
		/* the remaining data chunks should be just key data */
		if ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
			
			/*_fcpLog(FCP_LOG_DEBUG, "retrieved datachunk");*/

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


int _fcpGetSplitfile(hFCP *hfcp)
{
	hfcp = hfcp;

	return -1;
}

