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

/*#include <math.h> DEPRECATE */

#include "ez_sys.h"

/* Private functions for internal use */
/*
static int fec_segment_file(hFCP *hfcp);
static int fec_encode_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_retrieve_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_make_metadata(hFCP *hfcp, char *meta_filename);
*/


/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	get_file()

	function retrieves a freenet CHK via it's URI.

	function expects the following members in hfcp to be set:

	@@@ function returns:
	- zero on success
	- non-zero on error.
*/
int get_file(hFCP *hfcp, char *uri, char *key_filename, char *meta_filename)
{
	char get_command[L_FILE_BLOCKSIZE+1];

	int rc;
	int retry;

	int kfd = -1;
	int mfd = -1;

	int key_bytes;
	int key_count;
	int block_count;

	int meta_bytes;
	int meta_count;
	int chunk_written;


	FILE *kfile;
	FILE *mfile;

	if ((kfile = fopen(key_filename, "wb")) != 0)
		kfd = fileno(kfile);
	else {

		kfd = -1;
		_fcpLog(FCP_LOG_DEBUG, "Could not open temp file (%s) for writing key data", key_filename);
	}
	
	if ((mfile = fopen(meta_filename, "wb")) != 0)
		mfd = fileno(mfile);
	else {

		mfd = -1;
		_fcpLog(FCP_LOG_DEBUG, "Could not open temp file (%s) for writing meta data", meta_filename);
	}
	
	/* Here we can be certain that the files have been properly initialized */

	rc = snprintf(get_command, L_FILE_BLOCKSIZE,
								"ClientGet\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nEndMessage\n",
								(hfcp->delete_local == 0 ? "false" : "true"),
								uri,
								hfcp->htl
								);

	retry = _fcpRetry;

	/********************************************************************/

	do {
		int minutes;
		int seconds;

		minutes = (int)(hfcp->timeout / 1000 / 60);
		seconds = ((hfcp->timeout / 1000) - (minutes * 60));

		/* connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0)	return -1;

		_fcpLog(FCP_LOG_DEBUG, "sending ClientGet message - htl: %d, regress: %d, timeout: %d, keysize: n/a, metasize: n/a, delete_local: %d",
						hfcp->htl, hfcp->regress, hfcp->timeout, hfcp->delete_local);
		
		/* Send ClientGet command */
		if (send(hfcp->socket, get_command, strlen(get_command), 0) == -1) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not send Get message");

			rc = -1;
			goto cleanup;
		}
		
		_fcpLog(FCP_LOG_VERBOSE, "Waiting for response from node - timeout in %d minutes %d seconds)",
						minutes, seconds);
		
		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);
		
		switch (rc) {

		case FCPRESP_TYPE_DATAFOUND:
			_fcpLog(FCP_LOG_VERBOSE, "Received DataFound message");
			_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->timeout / 1000));
			break;
			
		case FCPRESP_TYPE_URIERROR:
			_fcpLog(FCP_LOG_VERBOSE, "Received URIError message");
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			_fcpLog(FCP_LOG_VERBOSE, "Received restarted message");
			_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->timeout / 1000));
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);
			
			/* re-set retry count to initial value */
			retry = _fcpRetry;
			
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

	key_bytes = hfcp->response.datafound.datalength;
	meta_bytes = hfcp->response.datafound.metadatalength;

	/* grab all the metadata from the datachunks first */

	_fcpLog(FCP_LOG_DEBUG, "retrieve metadata");

	while ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
		
		chunk_written = 0;

		/* set meta_count below datachunk length */
		meta_count = (meta_bytes > hfcp->response.datachunk.length ? 
									hfcp->response.datachunk.length :
									meta_bytes);
		
		while (meta_count > 0) {
			block_count = (meta_count > 8192 ? 8192 : meta_count);

			if (mfd != -1) write(mfd, hfcp->response.datachunk.data + chunk_written, block_count);
			
			/* decrement bytes to write, and increment count of bytes written within chunk */
			meta_count -= block_count;
			meta_bytes -= block_count;
			
			chunk_written += block_count;
		}

		/* entire chunk was metadata.. continue with the next datachunk */
		if (chunk_written == hfcp->response.datachunk.length) {
			_fcpLog(FCP_LOG_DEBUG, "entire chunk was metadata");
				continue;
		}

		_fcpLog(FCP_LOG_DEBUG, "all metadata written");

		/* all metadata has been written; dump key data in datachunk */
		if (meta_bytes == 0) {
			int key_trail;

			if (mfd != -1) fclose(mfile);
			key_trail = (hfcp->response.datachunk.length - chunk_written);

			while (key_trail > 0) {
				block_count = (key_trail > 8192 ? 8192 : key_trail);
				
				if (kfd != -1) write(kfd, hfcp->response.datachunk.data + chunk_written, block_count);
				
				/* decrement bytes to write, and increment count of bytes written within chunk */
				key_trail -= block_count;
				key_bytes -= block_count;
				
				chunk_written += block_count;
			}

			_fcpLog(FCP_LOG_DEBUG, "wrote data chunk");
		}
	} /* end metadata "while" loop */

	_fcpLog(FCP_LOG_DEBUG, "finished with partial key/meta data chunk");
	
	/* at this point, the remaining datachunks will all completely be key data */
	/* blindly dump them to the key file after closing the metafile */

	while ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {

		_fcpLog(FCP_LOG_DEBUG, "got data chunk");

		chunk_written = 0;

		while (key_bytes > 0) {
			key_count = (hfcp->response.datachunk.length > 8192 ? 8192 : hfcp->response.datachunk.length);
			
			if (kfd != -1) write(kfd, hfcp->response.datachunk.data + chunk_written, block_count);
			
			key_bytes -= block_count;
			chunk_written += block_count;
		}
	}

	/* do a 'local' cleanup here' */
	if (kfd != -1) fclose(kfile);			
	
  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "get_file() - retrieved key: %s", hfcp->key->uri->uri_str);

	return 0;


 cleanup: /* this is called when there is an error above */
	if (kfd != -1) fclose(kfile);			
	if (mfd != -1) fclose(mfile);
	
  _fcpSockDisconnect(hfcp);

	return rc;
}


int get_size(hFCP *hfcp, char *uri)
{
	hFCP *tmp_hfcp;

	char  buf[513];
	int   rc;

	rc = snprintf(buf, 512, "GetSize\nURI=%s\nEndMessage\n", uri);
	
	tmp_hfcp = fcpInheritHFCP(hfcp);

	/* new connection to Freenet FCP */
	if (_fcpSockConnect(tmp_hfcp) != 0) return -1;

	/* Send GetSize message */
	if (send(tmp_hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not send GetSize message");
		rc = -1;
		
		goto cleanup;
	}

	rc = _fcpRecvResponse(tmp_hfcp);

	switch (rc) {
		
	case FCPRESP_TYPE_SUCCESS:
		_fcpLog(FCP_LOG_VERBOSE, "Received success message");
		break;
		
	case FCPRESP_TYPE_FORMATERROR:
		_fcpLog(FCP_LOG_CRITICAL, "FormatError - reason: %s", tmp_hfcp->response.formaterror.reason);
		break;
		
	}
		
	/* expecting a success response */
	if (rc != FCPRESP_TYPE_SUCCESS) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not retrieve Success message");
		rc = -1;

		goto cleanup;
	}
	
	_fcpLog(FCP_LOG_VERBOSE, "Received Success message");
		
	rc = tmp_hfcp->response.success.length;
	fcpDestroyHFCP(tmp_hfcp);
	
	return rc;

 cleanup:
	fcpDestroyHFCP(tmp_hfcp);

	return rc;
}


int get_redirect(hFCP *hfcp, char *uri_chk, char *uri_redirect)
{
	hfcp = hfcp;
	uri_chk = uri_chk;
	uri_redirect = uri_redirect;
	
	return 0;
}

