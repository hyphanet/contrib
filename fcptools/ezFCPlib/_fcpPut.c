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
#include <math.h>

#include "ez_sys.h"

/* Private functions for internal use */
static int fec_segment_file(hFCP *hfcp);
static int fec_encode_segment(hFCP *hfcp, int segment);
static int fec_insert_data_blocks(hFCP *hfcp, int segment);
static int fec_insert_check_blocks(hFCP *hfcp, int segment);
static int fec_make_metadata(hFCP *hfcp);


/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	_fcpPutBlock()

	function creates a freenet CHK using the contents in 'key_filename'
	along with key metadata contained in 'meta_filename'.  Function will
	check	and validate the size of both file arguments.

	function expects the following members in hfcp to be set:

	function returns:
	- zero on success
	- non-zero on error.
*/
int _fcpPutBlock(hFCP *hfcp, hBlock *keyblock, hBlock *metablock, char *uri)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char put_command[L_FILE_BLOCKSIZE+1];

	int rc;
	int retry;

	unsigned long keysize;   /* local copy of key->size */
	unsigned long metasize;  /* local copy of metadata->size */

	int bytes;
	int byte_count;

	keysize = (keyblock ? hfcp->key->size : 0);
	metasize = (metablock ? hfcp->key->metadata->size : 0);

	/* now if key->size and metadata->size are both zero, bail */
	if ((keysize == 0) && (metasize == 0)) {
		_fcpLog(FCP_LOG_CRITICAL, "No data found to insert");
		return -1;
	}

	if (metasize == 0)
		rc = snprintf(put_command, L_FILE_BLOCKSIZE,
									"ClientPut\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nDataLength=%lx\nData\n",
									(hfcp->options->delete_local == 0 ? "false" : "true"),
									uri,
									hfcp->htl,
									keysize);
	else
		rc = snprintf(put_command, L_FILE_BLOCKSIZE,
									"ClientPut\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nDataLength=%lx\nMetadataLength=%lx\nData\n",
									(hfcp->options->delete_local == 0 ? "false" : "true"),
									uri,
									hfcp->htl,
									keysize + metasize,
									metasize);

	/********************************************************************/

	retry = hfcp->options->retry;
	fcpParseHURI(hfcp->key->tmpblock->uri, uri);
	
	do { /* let's loop this until we stop receiving Restarted messages */
		
		_fcpLog(FCP_LOG_VERBOSE, "%d retries left", retry);

		/* connect to Freenet FCP */
		if ((rc = _fcpSockConnect(hfcp)) != 0) goto cleanup;

		_fcpLog(FCP_LOG_VERBOSE, "Sending ClientPut message to %s:%u, htl=%u, delete_local=%s, meta-redirect=%s, dbr=%s",
						hfcp->host,
						hfcp->port,
						hfcp->htl,
						(hfcp->options->delete_local ? "Yes" : "No"),
						(hfcp->options->meta_redirect ? "Yes" : "No"),
						(hfcp->options->dbr ? "Yes" : "No"));
		
		_fcpLog(FCP_LOG_DEBUG, "other information.. regress=%u, keysize=%u, metasize=%u",
						hfcp->options->regress,
						keysize,
						metasize);
		
		/* Send ClientPut command */
		if ((rc = _fcpSend(hfcp->socket, put_command, strlen(put_command))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Error sending ClientPut message");
			goto cleanup;
		}

		/* now send any metadata that's available first.. */
		if (metasize > 0) {

			/* link to the metadata file (only if there's metadata) */
			_fcpBlockLink(metablock, _FCP_READ);
			bytes = metasize;
			
			while (bytes) {
				byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
				
				if ((rc = _fcpRead(metablock->fd, buf, byte_count)) == 0) {
					_fcpLog(FCP_LOG_CRITICAL, "Could not read metadata from internal tempfile");
					rc = -1;
					goto cleanup;
				}

				/* we read rc bytes, so send rc bytes and store new return value in rc */
				if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
					_fcpLog(FCP_LOG_CRITICAL, "Could not write metadata to node");
					rc = -1;
					goto cleanup;
				}
				
				/* decrement number of bytes written */
				bytes -= rc;

			} /* finished writing metadata (if any) */

			_fcpBlockUnlink(metablock);
			_fcpLog(FCP_LOG_VERBOSE, "Wrote metadata");
		}
		
		/* Here, all metadata has been written */
		
		/* now write key data
			 at this point, the socket *is* connected to the node and the ClientPut
			 command *has* been sent in either case (metadata, no-metadata) */
		
		if (keysize > 0) {

			_fcpBlockLink(keyblock, _FCP_READ);
			bytes = keysize;

			while (bytes) {

				byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

				/* read from source */
				if ((rc = _fcpRead(keyblock->fd, buf, byte_count)) < 0) {
					_fcpLog(FCP_LOG_CRITICAL, "Could not read key data from internal tempfile");
					rc = -1;
					goto cleanup;
				}

				/* write to socket */
				if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
					_fcpLog(FCP_LOG_CRITICAL, "Could not write key data to node");
					goto cleanup;
				}
				
				/* decrement by number of bytes written to the socket */
				bytes -= rc;
			}

			_fcpBlockUnlink(keyblock);
			_fcpLog(FCP_LOG_VERBOSE, "Wrote Key data");
		}

		/* both the meta and key blocks are "unlinked" */

		do {
			unsigned int minutes;
			unsigned int seconds;

			minutes = (int)(hfcp->options->timeout / 1000 / 60);
			seconds = ((hfcp->options->timeout / 1000) - (minutes * 60));

			_fcpLog(FCP_LOG_VERBOSE, "Waiting for response from node - timeout in %u minutes %u seconds)",
							minutes, seconds);
			
			/* expecting a success response */
			rc = _fcpRecvResponse(hfcp);
		
			switch (rc) {
			case FCPRESP_TYPE_SUCCESS:

				_fcpLog(FCP_LOG_VERBOSE, "Received Success message");
				fcpParseHURI(hfcp->key->tmpblock->uri, hfcp->response.success.uri);
				break;
				
			case FCPRESP_TYPE_KEYCOLLISION:

				_fcpLog(FCP_LOG_VERBOSE, "Received KeyCollision message (successful)");
				fcpParseHURI(hfcp->key->tmpblock->uri, hfcp->response.keycollision.uri);
				break;
				
			case FCPRESP_TYPE_RESTARTED:
				_fcpLog(FCP_LOG_VERBOSE, "Received Restarted message");
				_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));
				
				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* re-set retry count to initial value */
				retry = hfcp->options->retry;
				
				break;
				
			case FCPRESP_TYPE_PENDING:

				_fcpLog(FCP_LOG_VERBOSE, "Received Pending message");
				_fcpLog(FCP_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));

				break;
				
			case EZERR_SOCKET_TIMEOUT:
				retry--;

				_fcpLog(FCP_LOG_VERBOSE, "Received timeout waiting for response");

				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* this will route us to a restart */
				rc = FCPRESP_TYPE_RESTARTED;
				
				break;
				
			case FCPRESP_TYPE_ROUTENOTFOUND:
				retry--;

				_fcpLog(FCP_LOG_VERBOSE, "Received RoutNotFound message");
				_fcpLog(FCP_LOG_DEBUG, "unreachable: %u, restarted: %u, rejected: %u",
								hfcp->response.routenotfound.unreachable,
								hfcp->response.routenotfound.restarted,
								hfcp->response.routenotfound.rejected);

				/* now do the same routine as done for SOCKET_TIMEOUT */
				
				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* this will route us to a restart */
				rc = FCPRESP_TYPE_RESTARTED;
				
				break;

			case FCPRESP_TYPE_URIERROR:
				_fcpLog(FCP_LOG_CRITICAL, "Received UriError message: %s", hfcp->response.urierror.reason);
				break;
				
			case FCPRESP_TYPE_FORMATERROR:
				_fcpLog(FCP_LOG_CRITICAL, "Received FormatError message: %s", hfcp->response.formaterror.reason);
				break;
				
			case FCPRESP_TYPE_FAILED:
				_fcpLog(FCP_LOG_CRITICAL, "Received Failed message: %s", hfcp->response.failed.reason);
				break;
				
			default:
				_fcpLog(FCP_LOG_DEBUG, "_fcpPutBlock() - received unknown response code: %d", rc);
				break;
			}

		} while (rc == FCPRESP_TYPE_PENDING);
  } while ((rc == FCPRESP_TYPE_RESTARTED) && (retry >= 0));

	/* check to see which condition was reached (Restarted / Timeout) */

	/* if we exhausted our retries, then return a be-all Timeout error */
	if (retry < 0) {
		_fcpLog(FCP_LOG_CRITICAL, "Failed to insert file after %u retries", hfcp->options->retry);

		rc = EZERR_SOCKET_TIMEOUT;
		goto cleanup;
	}

  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION)) {
		_fcpLog(FCP_LOG_CRITICAL, "Failed to insert file");

		rc = -1;
    goto cleanup;
	}

  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "_fcpPutBlock() - inserted key: %s", hfcp->key->tmpblock->uri->uri_str);

	return 0;

 cleanup: /* this is called when there is an error above */

	/* make sure these files are closed.. don't worry (here)
	if the file descriptors *are* already closed */
	
  _fcpSockDisconnect(hfcp);

	if (keyblock) _fcpBlockUnlink(keyblock);
	if (metablock) _fcpBlockUnlink(metablock);

	return rc;
}


/*
	_fcpPutSplitfile()

	function creates a freenet FEC-Encoded CHK using the contents in
	'key_filename' along with key metadata contained in 'meta_filename'.
	Function will	check	and validate the size of both file arguments.

	The FEC logic is running within Fred and made accessable by Gianni's
	FEC-specific FCP commands.
	
	FEC spec: http://www.freenetproject.org/index.php?page=fec

	function expects the following members in hfcp to be set:

	function returns:
	- zero on success
	- non-zero on error.
	
	Any function calls here may set hfcp->error on return.
*/
int _fcpPutSplitfile(hFCP *hfcp)
{
	int rc;
	int index;

	/* now if key->size and metadata->size are both zero, bail */
	if ((hfcp->key->size == 0) && (hfcp->key->metadata->size == 0)) {

		_fcpLog(FCP_LOG_CRITICAL, "No data found to insert");
		return -1;
	}

	if ((rc = fec_segment_file(hfcp)) != 0) return rc;

	for (index = 0; index < hfcp->key->segment_count; index++)
		if ((rc = fec_encode_segment(hfcp, index)) != 0) return rc;

	for (index = 0; index < hfcp->key->segment_count; index++) {

		if ((rc = fec_insert_data_blocks(hfcp, index)) != 0) return rc;
		if ((rc = fec_insert_check_blocks(hfcp, index)) != 0) return rc;
	}

	/* TODO: now that the data is inserted, generate and insert metadata merged with
		 user-defined data for the splitfile */
	if ((rc = fec_make_metadata(hfcp)) != 0) return rc;

	return 0;
}

int _fcpInsertRoot(hFCP *hfcp)
{
	hFCP      *tmp_hfcp;
	hDocument *doc;

	char  buf[8193];
	char *metadata;
	char *metadata_uri;

	int  bytes;
	int  byte_count;
	int  rc;

	/* first let's be careful w/ our pointers, eh? */
	tmp_hfcp     = 0;
	metadata     = 0;
	metadata_uri = 0;
	doc          = 0;

	if (hfcp->options->meta_redirect) {
		
		/* insert the metadata and store the uri and use as redirect */
		tmp_hfcp = fcpInheritHFCP(hfcp);
		fcpOpenKey(tmp_hfcp, "CHK@", FCP_MODE_O_WRITE);

		_fcpBlockLink(hfcp->key->metadata->tmpblock, _FCP_READ);

		bytes = hfcp->key->metadata->size;
		while (bytes) {
			
			/* How many bytes are we writing this pass? */
			byte_count = (bytes > 8192 ? 8192: bytes);
			
			rc = _fcpRead(hfcp->key->metadata->tmpblock->fd, buf, byte_count);

			if ((rc = fcpWriteKey(tmp_hfcp, buf, rc)) < 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not write key data to node");
				goto cleanup;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= rc;
		}

		_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);
		_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
		
		/* now insert the metadata as a key */
		rc = _fcpPutBlock(tmp_hfcp,
											tmp_hfcp->key->tmpblock,
											0,
											"CHK@");
		
		if (rc != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not insert metadata as a redirect");
			goto cleanup;
		}

		/* store the uri and use as redirect target */
		metadata_uri = strdup(tmp_hfcp->key->tmpblock->uri->uri_str);
		snprintf(buf, 512, "Version\nRevision=1\nEndPart\nDocument\nName=meta-redirect\nRedirectTarget=%s\nEnd", metadata_uri);

		/* cleanup a little */
		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp); tmp_hfcp = 0;
		
		/* parse */
		if (_fcpMetaParse(hfcp->key->metadata, buf) != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Error parsing custom metadata");
			
			rc = -1;
			goto cleanup;
		}
	}
	else {

		/* read metadata on disk (if any) into buf */
		if (hfcp->key->metadata->size) {
			
			_fcpBlockLink(hfcp->key->metadata->tmpblock, _FCP_READ);
			
			/* allocate space for raw metadata */
			metadata = malloc(hfcp->key->metadata->size + 1);
			
			/* read it in one pass */
			_fcpRead(hfcp->key->metadata->tmpblock->fd, metadata, hfcp->key->metadata->size);
			
			/* make it a cstrz */
			metadata[hfcp->key->metadata->size] = 0;

			/* parse */
			if (_fcpMetaParse(hfcp->key->metadata, metadata) != 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Error parsing custom metadata");
				
				rc = -1;
				goto cleanup;
			}
			
			_fcpBlockUnlink(hfcp->key->metadata->tmpblock);
		}
	}

	/* in either case, a call to _fcpMetaParse() occurs exactly once */
	/* TODO do we need to check on Version? */
		 
	/* add the redirect key */
	doc = cdocAddDoc(hfcp->key->metadata, 0);
	cdocAddKey(doc, "Redirect.Target", hfcp->key->uri->uri_str);

	/* check on the dbr option */
	if (hfcp->options->dbr) {

		/* create a doc for the date-redirect and add the target */
		doc = cdocAddDoc(hfcp->key->metadata, "date-redirect");
		cdocAddKey(doc, "DateRedirect.Target", hfcp->key->target_uri->uri_str);

		_fcpLog(FCP_LOG_DEBUG, "inserted dbr key");
	}

	/* at this point, *all* metadata (if any) is in the hfcp->key->metadata struct */

	/* open another hfcp to write the metadata */
	tmp_hfcp = fcpInheritHFCP(hfcp);
	fcpOpenKey(tmp_hfcp, hfcp->key->target_uri->uri_str, FCP_MODE_O_WRITE);

	if (metadata) free(metadata);
	metadata = _fcpMetaString(hfcp->key->metadata);

	_fcpLog(FCP_LOG_DEBUG, "Metadata:\n%s", metadata);

	/* TODO: write the REST piece here, after we're sure we can read it in properly */
	fcpWriteMetadata(tmp_hfcp, metadata, strlen(metadata));

	_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
	_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);

	/* now insert the metadata (with no key data to insert) */
	rc = _fcpPutBlock(tmp_hfcp,
										0,
										tmp_hfcp->key->metadata->tmpblock,
										hfcp->key->target_uri->uri_str);

	/* copy the new uri */
	fcpParseHURI(hfcp->key->target_uri, tmp_hfcp->key->tmpblock->uri->uri_str);

	/* fall into cleanup and return rc, which on success == 0 */
	rc = 0;

 cleanup:

	/* Destroy ALL!!! (except for hfcp.. leave that for caller) */
	if (tmp_hfcp) {
		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
	}

	if (metadata) free(metadata);

	return rc;
}

/***********************************************************************/
/* static functions static functions static functions static functions */
/***********************************************************************/

/*
	fec_segment_file()
*/
static int fec_segment_file(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int index;
	int segment_count;

	_fcpLog(FCP_LOG_DEBUG, "entered fec_segment_file()");

	/* connect to Freenet FCP */
	if ((rc = _fcpSockConnect(hfcp)) != 0) goto cleanup;
	
	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%lx\nEndMessage\n",
					 hfcp->key->size);

	/* Send FECSegmentFile command */
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send FECSegmentFile message");
		goto cleanup;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECSegmentFile message");
	
	rc = _fcpRecvResponse(hfcp);

  switch (rc) {
  case FCPRESP_TYPE_SEGMENTHEADER: break;
		
  case FCPRESP_TYPE_FORMATERROR:
		_fcpLog(FCP_LOG_CRITICAL, "Received FormatError message: %s", hfcp->response.formaterror.reason);

		rc = -1;
		goto cleanup;

  case FCPRESP_TYPE_FAILED:
		_fcpLog(FCP_LOG_CRITICAL, "Received Failed message: %s", hfcp->response.failed.reason);

		rc = -1;
		goto cleanup;

	default:
		_fcpLog(FCP_LOG_DEBUG, "* warning.. fec_segment_file() received unknown response code: %d", rc);
		break;
	}

	/* Allocate the area for all required segments (spaces for pointers to hSegment) */
	hfcp->key->segment_count = (unsigned short)hfcp->response.segmentheader.segments;
	hfcp->key->segments = malloc(sizeof (hSegment *) * hfcp->key->segment_count);

	/* Loop while there's more segments to receive */
	segment_count = hfcp->key->segment_count;
	index = 0;

	_fcpLog(FCP_LOG_DEBUG, "expecting %u segment(s)", segment_count);

	/* Loop through all segments and store information */
	for (rc = FCPRESP_TYPE_SEGMENTHEADER;
			((index < segment_count) && (rc == FCPRESP_TYPE_SEGMENTHEADER));) {

		_fcpLog(FCP_LOG_DEBUG, "retrieving segment %d", index+1);

		hfcp->key->segments[index] = _fcpCreateHSegment();

		/* get counts of data and check blocks */
		hfcp->key->segments[index]->db_count = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[index]->cb_count = hfcp->response.segmentheader.checkblock_count;

		/* allocate space for data and check block handles */
		hfcp->key->segments[index]->data_blocks = malloc(sizeof (hBlock *) * hfcp->key->segments[index]->db_count);
		hfcp->key->segments[index]->check_blocks = malloc(sizeof (hBlock *) * hfcp->key->segments[index]->cb_count);

		snprintf(buf, L_FILE_BLOCKSIZE,
						 "SegmentHeader\nFECAlgorithm=%s\nFileLength=%lx\nOffset=%lx\n" \
						 "BlockCount=%lx\nBlockSize=%lx\nDataBlockOffset=%lx\nCheckBlockCount=%lx\n" \
						 "CheckBlockSize=%lx\nCheckBlockOffset=%lx\nSegments=%lx\nSegmentNum=%lx\nBlocksRequired=%lx\nEndMessage\n",

						 hfcp->response.segmentheader.fec_algorithm,
						 hfcp->response.segmentheader.filelength,
						 hfcp->response.segmentheader.offset,
						 hfcp->response.segmentheader.block_count,
						 hfcp->response.segmentheader.block_size,
						 hfcp->response.segmentheader.datablock_offset,
						 hfcp->response.segmentheader.checkblock_count,
						 hfcp->response.segmentheader.checkblock_size,
						 hfcp->response.segmentheader.checkblock_offset,
						 hfcp->response.segmentheader.segments,
						 hfcp->response.segmentheader.segment_num,
						 hfcp->response.segmentheader.blocks_required
						 );

		if (hfcp->key->segments[index]->header_str) free(hfcp->key->segments[index]->header_str);
		hfcp->key->segments[index]->header_str = strdup(buf);

		_fcpLog(FCP_LOG_DEBUG, "received segment %u/%u", index+1, segment_count);
	
		hfcp->key->segments[index]->filelength        = hfcp->response.segmentheader.filelength;
		hfcp->key->segments[index]->offset            = hfcp->response.segmentheader.offset;
		hfcp->key->segments[index]->block_count       = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[index]->block_size        = hfcp->response.segmentheader.block_size;
		hfcp->key->segments[index]->datablock_offset  = hfcp->response.segmentheader.datablock_offset;
		hfcp->key->segments[index]->checkblock_count  = hfcp->response.segmentheader.checkblock_count;
		hfcp->key->segments[index]->checkblock_size   = hfcp->response.segmentheader.checkblock_size;
		hfcp->key->segments[index]->checkblock_offset = hfcp->response.segmentheader.checkblock_offset;
		hfcp->key->segments[index]->segments          = hfcp->response.segmentheader.segments;
		hfcp->key->segments[index]->segment_num       = hfcp->response.segmentheader.segment_num;
		hfcp->key->segments[index]->blocks_required   = hfcp->response.segmentheader.blocks_required;
		
		index++;

		/* Only if we're expecting more SegmentHeader messages
			 should we attempt to retrieve one ! */
		if (index < segment_count) rc = _fcpRecvResponse(hfcp);

	} /* End While - all segments now in hfcp container */

	/* Disconnect this connection.. its outlived it's purpose */
	_fcpSockDisconnect(hfcp);

	return 0;
	
 cleanup: /* this is called when there is an error above */
	
  _fcpSockDisconnect(hfcp);
	return rc;
}


static int fec_encode_segment(hFCP *hfcp, int index)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	unsigned long fi;   /* file index */
	unsigned long bi;   /* block index */

	unsigned long byte_count;

	unsigned long data_len;
	unsigned long block_len;
	unsigned long metadata_len;
	unsigned long pad_len;

	hSegment  *segment;

	_fcpLog(FCP_LOG_DEBUG, "entered fec_encode_segment()");

	/* Helper pointer since we're encoding 1 segment at a time */
	segment = hfcp->key->segments[index];
	
	data_len     = segment->filelength;
	metadata_len = strlen(segment->header_str);
	pad_len      = (segment->block_count * segment->block_size) - data_len;
	
	if (_fcpSockConnect(hfcp) != 0) return -1;

	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECEncodeSegment\nDataLength=%lx\nMetadataLength=%lx\nData\n",
					 data_len + metadata_len + pad_len,
					 metadata_len
					 );
	
	/* Send FECEncodeSegment message */
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not write FECEncodeSegment message to node");
		goto cleanup;
	}
	
	/* Send SegmentHeader */
	if ((rc = _fcpSend(hfcp->socket, segment->header_str, strlen(segment->header_str))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not write segment header to node");
		goto cleanup;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECEncodeSegment message");

	/* Open file we are about to send */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_READ);

	/* Write the data from the file, then write the pad blocks */
	/* data_len is the total length of the data file we're inserting */
	
	fi = data_len;
	while (fi) {
		
		/* How many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		/* read byte_count bytes from the file we're inserting */
		rc = _fcpRead(hfcp->key->tmpblock->fd, buf, byte_count);
		
		if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not write key data to node");
			goto cleanup;
		}
		
		/* decrement by number of bytes written to the socket */
		fi -= rc;
	}
	
	_fcpBlockUnlink(hfcp->key->tmpblock);

	/* now write the pad bytes and end transmission.. */
	
	/* set the buffer to all zeroes so we can send 'em */
	memset(buf, 0, L_FILE_BLOCKSIZE);
	
	fi = pad_len;
	while (fi) {
		
		/* how many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		if ((rc = _fcpSend(hfcp->socket, buf, byte_count)) < 0) {
			_fcpLog( FCP_LOG_CRITICAL, "Could not write trailing bytes to node");
			goto cleanup;
		}
		
		/* decrement i by number of bytes written to the socket */
		fi -= rc;
	}

	/* if the response isn't BlocksEncoded, we have a problem */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_BLOCKSENCODED) {
		_fcpLog(FCP_LOG_CRITICAL, "Did not receive expected BlocksEncoded message");
		rc = -1;

		goto cleanup;
	}
	
	/* it is a BlocksEncoded message.. get the check blocks */
	block_len = hfcp->response.blocksencoded.block_size;

	_fcpLog(FCP_LOG_DEBUG, "expecting %u check blocks", segment->cb_count);

	for (bi=0; bi < segment->cb_count; bi++) {

		/* create the HBlock struct and link the temp file */
		segment->check_blocks[bi] = _fcpCreateHBlock();
		_fcpBlockLink(segment->check_blocks[bi], _FCP_WRITE);

		segment->check_blocks[bi]->size = block_len;
		
		/* We're expecting a DataChunk message */
		
		for (fi=0; fi < block_len; ) {

			if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
				_fcpLog(FCP_LOG_CRITICAL, "did not receive expected DataChunk message");
				rc = -1;
				
				goto cleanup;
			}

			rc = _fcpWrite(segment->check_blocks[bi]->fd,
										hfcp->response.datachunk.data,
										hfcp->response.datachunk.length);
			
			fi += rc;
		}
		
		/* Close the check block file */
		_fcpBlockUnlink(segment->check_blocks[bi]);

		_fcpLog(FCP_LOG_DEBUG, "received check block %u/%u",
						bi+1, segment->cb_count);
	}
	
	_fcpLog(FCP_LOG_VERBOSE, "Successfully received %u check blocks", bi);
	
  _fcpSockDisconnect(hfcp);
	return 0;
	
 cleanup: /* this is called when there is an error above */
	
  _fcpSockDisconnect(hfcp);
	return rc;
}


static int fec_insert_data_blocks(hFCP *hfcp, int index)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	unsigned long bi;   /* block index */

	int bytes;
	int byte_count;

	hSegment  *segment;

	hFCP *tmp_hfcp;

	_fcpLog(FCP_LOG_DEBUG, "entered fec_insert_data_blocks()");
	
	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	/* open key file */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_READ);

	tmp_hfcp = fcpInheritHFCP(hfcp);

	while (bi < segment->db_count) { /* while (bi < segment->db_count) */

		_fcpLog(FCP_LOG_DEBUG, "inserting data block - segment: %u/%u, block %u/%u",
						index+1, hfcp->key->segment_count,
						bi+1, segment->db_count);

		fcpOpenKey(tmp_hfcp, "CHK@", FCP_MODE_O_WRITE);

		/* seek to the location relative to the segment (if needed) */
		if (segment->offset > 0) lseek(hfcp->key->tmpblock->fd, segment->offset, SEEK_SET);

		bytes = segment->block_size;
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source; if 0, then send pad bytes */
			if ((rc = _fcpRead(hfcp->key->tmpblock->fd, buf, byte_count)) <= 0) break;

			if ((rc = fcpWriteKey(tmp_hfcp, buf, rc)) < 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not write key data to internal tempfile");
				goto cleanup;
			}
			
			/* decrement by number of bytes read from the socket */
			bytes -= rc;
		}
		
		if (bytes) _fcpLog(FCP_LOG_DEBUG, "must send zero-padded data");
	
		/* check to see if there's pad bytes we have to retrieve */
		memset(buf, 0, L_FILE_BLOCKSIZE);
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

			if ((rc = fcpWriteKey(tmp_hfcp, buf, byte_count)) < 0) {
				_fcpLog(FCP_LOG_CRITICAL, "Could not zero data to internal tempfile");
				goto cleanup;
			}

			/* decrement by number of bytes read from the socket */
			bytes -= byte_count;
		}

		_fcpLog(FCP_LOG_DEBUG, "file to insert: %s", tmp_hfcp->key->tmpblock->filename);

		/** TODO ***/

#if 0
		if (fcpCloseKey(tmp_hfcp) != 0) {
			rc = -1;
			goto cleanup;
		}
#endif

		_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
		_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);

		rc = _fcpPutBlock(tmp_hfcp,
											hfcp->key->tmpblock,
											0,
											"CHK@");

		if (rc != 0) goto cleanup;

		/* check blocks were created before, so now create the data blocks only */
		segment->data_blocks[bi] = _fcpCreateHBlock();
		fcpParseHURI(segment->data_blocks[bi]->uri, tmp_hfcp->key->tmpblock->uri->uri_str);

		_fcpLog(FCP_LOG_VERBOSE, "Inserted data block %u/%u",
						bi+1, segment->db_count);

		_fcpLog(FCP_LOG_DEBUG, "segment %u/%u, block %u/%u, uri: %s",
						index+1, hfcp->key->segment_count,
						bi+1, segment->db_count,
						segment->data_blocks[bi]->uri->uri_str);

		bi++;
	}

	/* we're done with the key data */
	rc = 0;

 cleanup: /* this is called when there is an error above */

	_fcpBlockUnlink(hfcp->key->tmpblock);

	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);
	
	return rc;
}

static int fec_insert_check_blocks(hFCP *hfcp, int index)
{
	int rc;
	unsigned long bi;   /* block index */

	hSegment *segment;
	hFCP     *tmp_hfcp;

	_fcpLog(FCP_LOG_DEBUG, "entered fec_encode_check_blocks()");

	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	tmp_hfcp = fcpInheritHFCP(hfcp);

	for (bi=0; bi < segment->cb_count; bi++) {

		_fcpLog(FCP_LOG_DEBUG, "inserting check block - segment: %u/%u, block %u/%u",
						index+1, hfcp->key->segment_count,
						bi+1, segment->cb_count);

		/**** TODO ****/
		/*rc = fcpPutKeyFromFile(tmp_hfcp, "CHK@", segment->check_blocks[bi]->filename, 0);*/

		/* use _fcpSetFilename()...
		rc = _fcpPutBlock(tmp_hfcp,
											tmp_hfcp->
		*/

		if (rc < 0) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not insert check block %u into Freenet", bi);
			goto cleanup;
		}		
		
		fcpParseHURI(segment->check_blocks[bi]->uri, tmp_hfcp->key->uri->uri_str);

		_fcpLog(FCP_LOG_VERBOSE, "Inserted check block %u/%u",
						bi+1, segment->cb_count);

		_fcpLog(FCP_LOG_DEBUG, "segment %u/%u, block %u/%u, uri: %s",
						index+1, hfcp->key->segment_count,
						bi+1, segment->cb_count,
						segment->check_blocks[bi]->uri->uri_str);
	}
	
	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);
	
	return 0;

 cleanup: /* this is called when there is an error above */
	
	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);
	
	return rc;
}


static int fec_make_metadata(hFCP *hfcp)
{
	char buf[513];
	char block[L_FILE_BLOCKSIZE];

	int rc;

	int segment_count;
	int index;

	int bytes;
	int byte_count;

	unsigned long meta_len;
	unsigned long bi;

	hSegment *segment;
	hFCP     *tmp_hfcp;

	_fcpLog(FCP_LOG_DEBUG, "entered fec_make_metadata()");

	segment_count = hfcp->key->segment_count;
	index = 0;

	tmp_hfcp = fcpInheritHFCP(hfcp);
	
	/* erm do this since we're not using fcpOpenKey() */
	_fcpBlockLink(tmp_hfcp->key->tmpblock, _FCP_WRITE);

	while (index < segment_count) {
		/* build SegmentHeader and BlockMap pairs */
		
		/* helper pointer */
		segment = hfcp->key->segments[index];

		snprintf(buf, 512,
						 "SegmentHeader\nFECAlgorithm=OnionFEC_a_1_2\nFileLength=%lx\nOffset=%lx\n" \
						 "BlockCount=%lx\nBlockSize=%lx\nDataBlockOffset=%lx\nCheckBlockCount=%lx\n" \
						 "CheckBlockSize=%lx\nCheckBlockOffset=%lx\nSegments=%lx\nSegmentNum=%lx\nBlocksRequired=%lx\nEndMessage\n",
						 
						 segment->filelength,
						 segment->offset,
						 segment->block_count,
						 segment->block_size,
						 segment->datablock_offset,
						 segment->checkblock_count,
						 segment->checkblock_size,
						 segment->checkblock_offset,
						 segment->segments,
						 segment->segment_num,
						 segment->blocks_required);

		/* copy the segment header */
		fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		fcpWriteKey(tmp_hfcp, "BlockMap\n", strlen("BlockMap\n"));
		
		/* concatenate data block map */
		for (bi=0; bi < segment->db_count; bi++) {

			snprintf(buf, 512, "Block.%lx=%s\n", bi, segment->data_blocks[bi]->uri->uri_str);
			fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		}
		
		/* now for check block map */
		for (bi=0; bi < segment->cb_count; bi++) {

			snprintf(buf, 512, "Check.%lx=%s\n", bi, segment->check_blocks[bi]->uri->uri_str);
			fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		}
		
		fcpWriteKey(tmp_hfcp, "EndMessage\n", strlen("EndMessage\n"));

		/* done with this segment.. */
		index++;
	}

	_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
	_fcpLog(FCP_LOG_DEBUG, "wrote FECMakeMetadata message to temporary file");

	meta_len = tmp_hfcp->key->size;

  if (_fcpSockConnect(tmp_hfcp) != 0) return -1;

	/* Send FECMakeMetadata command */
	snprintf(buf, 512, "FECMakeMetadata\nDescription=file\nMimeType=%s\nDataLength=%lx\nData\n",
					 hfcp->key->mimetype,
					 meta_len);

	if ((rc = _fcpSend(tmp_hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCP_LOG_CRITICAL, "Could not send FECMakeMetadata message");
		goto cleanup;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECMakeMetadata command to node");

	bytes = meta_len;
	_fcpBlockLink(tmp_hfcp->key->tmpblock, _FCP_READ);

	while (bytes) {
		byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

		rc = _fcpRead(tmp_hfcp->key->tmpblock->fd, block, byte_count);

		if ((rc = _fcpSend(tmp_hfcp->socket, block, rc)) == -1) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not send metadata");
			goto cleanup;
		}
		
		/* decrement by number of bytes written to the socket */
		bytes -= rc;
	}

	_fcpBlockUnlink(tmp_hfcp->key->tmpblock);

	/* expecting a mademetadata response */
	rc = _fcpRecvResponse(tmp_hfcp);
	
	switch (rc) {
	case FCPRESP_TYPE_MADEMETADATA:
		meta_len = tmp_hfcp->response.mademetadata.datalength;
		_fcpLog(FCP_LOG_DEBUG, "bytes of metadata to process: %u", meta_len);

		break;

	default:
		_fcpLog(FCP_LOG_CRITICAL, "Unknown response code from node: %d", rc);
		rc = -1;
		
		goto cleanup;
	}

	/* TODO *********** insert the custom metadata here!!!!!!!! */

	/* now read metadata from freenet and write to tmp file,
		 and then finally write the metadata into Freenet (via put_file) */

	/* write metadata to tmp file */
	_fcpLog(FCP_LOG_DEBUG, "writing prepared metadata to temporary file for insertion");

	_fcpBlockLink(tmp_hfcp->key->metadata->tmpblock, _FCP_WRITE);
	bytes = meta_len;

	while (bytes) {

		if ((rc = _fcpRecvResponse(tmp_hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			_fcpLog(FCP_LOG_CRITICAL, "Did not receive expected DataChunk message");
			rc = -1;

			goto cleanup;
		}

		if (fcpWriteMetadata(tmp_hfcp,
												 tmp_hfcp->response.datachunk.data,
												 tmp_hfcp->response.datachunk.length) < 0) {
			
			_fcpLog(FCP_LOG_DEBUG, "fcpWriteMetadata() returned < 0");
			rc = -1;
			
			goto cleanup;
		}
		
		bytes -= tmp_hfcp->response.datachunk.length;
	}

	_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);
	_fcpLog(FCP_LOG_DEBUG, "metadata written to temporary file");

	/* needed to force _fcpPutBlock() to only insert metadata for splitfile manifest */
	tmp_hfcp->key->size = 0;

	/************ TODO ************/
	
	/*if ((rc = _fcpPutBlock(tmp_hfcp, "CHK@")) < 0) goto cleanup;*/
	
	_fcpLog(FCP_LOG_DEBUG, "fec_make_metadata() was successful; inserted key: %s", tmp_hfcp->key->tmpblock->uri->uri_str);

	fcpParseHURI(hfcp->key->tmpblock->uri, tmp_hfcp->key->tmpblock->uri->uri_str);

  _fcpSockDisconnect(tmp_hfcp);

	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);
	
	return 0;

 cleanup: /* this is called when there is an error above */
	
  _fcpSockDisconnect(tmp_hfcp);

	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);

	return rc;
}
