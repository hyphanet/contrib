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


/* Log messages should be FCPT_LOG_VERBOSE or FCPT_LOG_DEBUG only in this module */

/*
	FUNCTION:_fcpPutBlock()

	Inserts a block (single piece of key/meta data) into Freenet and returns
	the URI for inserted block.  This function can be called for single blocks
	and for each block in a splitfile.

	PARAMETERS:

	- hfcp: A created and initialized hFCP struct.
	- keyblock: Points to an hBlock of keydata.
	- metablock: Points to an hBlock of metadata.
	- uri: Freenet URI to insert data under ("CHK@" to calculate).

	IN:
	- hfcp->htl: Hops to live.
	- hfcp->host: Host of Freenet node.
	- hfcp->port: Port of Freenet node.

	- hfcp->key->size: Number of bytes of keydata.
	- hfcp->key->metadata->size: Number of bytes of metadata.

	- hfcp->options->remove_local: Send RemoveLocalKey?
	- hfcp->options->retry: Number of retries on socket timeouts and Restarts.
	- hfcp->options->meta_redirect: Insert metadata via redirect?
	- hfcp->options->dbr: Insert key as DBR?

	OUT:
	- hfcp->key->tmpblock->uri: CHK of inserted block.

	RETURNS: Zero on success, <0 on error.
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

	/* grab the sizes and save them here */
	keysize = (keyblock ? hfcp->key->size : 0);
	metasize = (metablock ? hfcp->key->metadata->size : 0);

	/* now if key->size and metadata->size are both zero, bail */
	if ((keysize == 0) && (metasize == 0)) {
		_fcpLog(FCPT_LOG_CRITICAL, "No data found to insert");
		return -1;
	}

	/* build a ClientPut command, taking into account the possible existance
		 of metadata */
	if (metasize == 0)
		rc = snprintf(put_command, L_FILE_BLOCKSIZE,
									"ClientPut\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nDataLength=%lx\nData\n",
									(hfcp->options->remove_local == 0 ? "false" : "true"),
									uri,
									hfcp->htl,
									keysize);
	else
		rc = snprintf(put_command, L_FILE_BLOCKSIZE,
									"ClientPut\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nDataLength=%lx\nMetadataLength=%lx\nData\n",
									(hfcp->options->remove_local == 0 ? "false" : "true"),
									uri,
									hfcp->htl,
									keysize + metasize,
									metasize);

	/********************************************************************/

	/* set the retry count to full before entering loop */
	retry = hfcp->options->retry;

	/*fcpParseHURI(hfcp->key->tmpblock->uri, uri);*/
	
	do { /* let's loop this until we stop receiving Restarted messages */
		
		_fcpLog(FCPT_LOG_VERBOSE, "%d retries left", retry);

		/* connect to Freenet FCP */
		if ((rc = _fcpSockConnect(hfcp)) != 0) goto cleanup;

		_fcpLog(FCPT_LOG_VERBOSE, "Sending ClientPut message to %s:%u, htl=%u, remove_local=%s, meta-redirect=%s, dbr=%s",
						hfcp->host,
						hfcp->port,
						hfcp->htl,
						(hfcp->options->remove_local ? "Yes" : "No"),
						(hfcp->options->meta_redirect ? "Yes" : "No"),
						(hfcp->options->dbr ? "Yes" : "No"));
		
		_fcpLog(FCPT_LOG_DEBUG, "other information.. keysize=%u, metasize=%u",
						keysize,
						metasize);
		
		/* Send ClientPut command */
		if ((rc = _fcpSend(hfcp->socket, put_command, strlen(put_command))) == -1) {
		_fcpLog(FCPT_LOG_CRITICAL, "Error sending ClientPut message");
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
					_fcpLog(FCPT_LOG_CRITICAL, "Could not read metadata from internal tempfile");
					rc = -1;
					goto cleanup;
				}

				/* we read rc bytes, so send rc bytes and store new return value in rc */
				if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
					_fcpLog(FCPT_LOG_CRITICAL, "Could not write metadata to node");
					rc = -1;
					goto cleanup;
				}
				
				/* decrement number of bytes written */
				bytes -= rc;

			} /* finished writing metadata (if any) */

			_fcpBlockUnlink(metablock);
			_fcpLog(FCPT_LOG_VERBOSE, "Wrote metadata");
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
					_fcpLog(FCPT_LOG_CRITICAL, "Could not read key data from internal tempfile");
					rc = -1;
					goto cleanup;
				}

				/* write to socket */
				if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
					_fcpLog(FCPT_LOG_CRITICAL, "Could not write key data to node");
					goto cleanup;
				}
				
				/* decrement by number of bytes written to the socket */
				bytes -= rc;
			}

			_fcpBlockUnlink(keyblock);
			_fcpLog(FCPT_LOG_VERBOSE, "Wrote Key data");
		}

		/* both the meta and key blocks are "unlinked" */

		do {
			unsigned int minutes;
			unsigned int seconds;

			minutes = (int)(hfcp->options->timeout / 1000 / 60);
			seconds = ((hfcp->options->timeout / 1000) - (minutes * 60));

			_fcpLog(FCPT_LOG_VERBOSE, "Waiting for response from node - timeout in %u minutes %u seconds)",
							minutes, seconds);
			
			/* expecting a success response */
			rc = _fcpRecvResponse(hfcp);
		
			switch (rc) {
			case FCPT_RESPONSE_SUCCESS:

				_fcpLog(FCPT_LOG_VERBOSE, "Received Success message");
				fcpParseHURI(hfcp->key->tmpblock->uri, hfcp->response.success.uri);
				break;
				
			case FCPT_RESPONSE_KEYCOLLISION:

				_fcpLog(FCPT_LOG_VERBOSE, "Received KeyCollision message (Success)");
				fcpParseHURI(hfcp->key->tmpblock->uri, hfcp->response.keycollision.uri);
				break;
				
			case FCPT_RESPONSE_RESTARTED:
				_fcpLog(FCPT_LOG_VERBOSE, "Received Restarted message: %s", hfcp->response.restarted.reason);
				/*_fcpLog(FCPT_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));*/

				break;
				
				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* re-set retry count to initial value */
				retry = hfcp->options->retry;
				
				break;
				
			case FCPT_RESPONSE_PENDING:

				_fcpLog(FCPT_LOG_VERBOSE, "Received Pending message");
				/*_fcpLog(FCPT_LOG_DEBUG, "timeout value: %d seconds", (int)(hfcp->options->timeout / 1000));*/

				/* re-set retry count to initial value */
				retry = hfcp->options->retry;

				break;
				
			case FCPT_ERR_SOCKET_TIMEOUT:
				retry--;

				_fcpLog(FCPT_LOG_VERBOSE, "Received timeout waiting for response");
				
				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* this will route us to a restart */
				rc = FCPT_RESPONSE_RESTARTED;
				
				break;
				
			case FCPT_RESPONSE_ROUTENOTFOUND:
				retry--;

				_fcpLog(FCPT_LOG_VERBOSE, "Received RouteNotFound message");
				_fcpLog(FCPT_LOG_DEBUG, "unreachable: %u, restarted: %u, rejected: %u, backed off: %u",
								hfcp->response.routenotfound.unreachable,
								hfcp->response.routenotfound.restarted,
								hfcp->response.routenotfound.rejected,
								hfcp->response.routenotfound.backedoff);

				/* now do the same routine as done for SOCKET_TIMEOUT */
				
				/* disconnect from the socket */
				_fcpSockDisconnect(hfcp);

				/* re-set retry count to initial value */
				retry = hfcp->options->retry;

				/* this will route us to a restart */
				rc = FCPT_RESPONSE_RESTARTED;
				
				break;

			case FCPT_RESPONSE_URIERROR:
				_fcpLog(FCPT_LOG_CRITICAL, "Received UriError message: %s", hfcp->response.urierror.reason);
				break;
				
			case FCPT_RESPONSE_FORMATERROR:
				_fcpLog(FCPT_LOG_CRITICAL, "Received FormatError message: %s", hfcp->response.formaterror.reason);
				break;
				
			case FCPT_RESPONSE_FAILED:
				_fcpLog(FCPT_LOG_CRITICAL, "Received Failed message: %s", hfcp->response.failed.reason);
				break;
				
			default:
				_fcpLog(FCPT_LOG_DEBUG, "_fcpPutBlock() - received unknown response code: %d", rc);
				break;
			}

		} while (rc == FCPT_RESPONSE_PENDING);
  } while ((rc == FCPT_RESPONSE_RESTARTED) && (retry >= 0));

	/* check to see which condition was reached (Restarted / Timeout) */

	/* if we exhausted our retries, then return a be-all Timeout error */
	if (retry < 0) {
		_fcpLog(FCPT_LOG_CRITICAL, "Failed to insert file after %u retries", hfcp->options->retry);

		rc = FCPT_ERR_SOCKET_TIMEOUT;
		goto cleanup;
	}

  if ((rc != FCPT_RESPONSE_SUCCESS) && (rc != FCPT_RESPONSE_KEYCOLLISION)) {
		_fcpLog(FCPT_LOG_CRITICAL, "Failed to insert file");

		rc = -1;
    goto cleanup;
	}

	_fcpLog(FCPT_LOG_DEBUG, "_fcpPutBlock() - inserted key: %s", hfcp->key->tmpblock->uri->uri_str);

	rc = 0;

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

		_fcpLog(FCPT_LOG_CRITICAL, "No data found to insert");
		return -1;
	}

	if ((rc = fec_segment_file(hfcp)) != 0) return rc;

	for (index = 0; (unsigned)index < hfcp->key->segment_count; index++)
		if ((rc = fec_encode_segment(hfcp, index)) != 0) return rc;

	for (index = 0; (unsigned)index < hfcp->key->segment_count; index++) {

		if ((rc = fec_insert_data_blocks(hfcp, index)) != 0) return rc;
		if ((rc = fec_insert_check_blocks(hfcp, index)) != 0) return rc;
	}

	if ((rc = fec_make_metadata(hfcp)) != 0) return rc;

	/* destroy the segment block since it's not needed anymore */
	_fcpDestroyHSegments(hfcp->key);
	
	/* the CHK just inserted is in hfcp->key->uri */

#ifdef DMALLOC 
	dmalloc_vmessage("*** Exiting _fcpPutSplitfile\n", 0);
  dmalloc_verify(0); 
  dmalloc_log_changed(_fcpDMALLOC, 1, 0, 1); 
#endif 
 
	return 0;
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

	unsigned long index;
	unsigned long segment_count;

	_fcpLog(FCPT_LOG_DEBUG, "entered fec_segment_file()");

	/* connect to Freenet FCP */
	if ((rc = _fcpSockConnect(hfcp)) != 0) goto cleanup;
	
	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%lx\nEndMessage\n",
					 hfcp->key->size);

	/* Send FECSegmentFile command */
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCPT_LOG_VERBOSE, "Could not send FECSegmentFile message");
		goto cleanup;
	}

	_fcpLog(FCPT_LOG_DEBUG, "sent FECSegmentFile message");
	
	rc = _fcpRecvResponse(hfcp);

  switch (rc) {
  case FCPT_RESPONSE_SEGMENTHEADER: break;
		
  case FCPT_RESPONSE_FORMATERROR:
		_fcpLog(FCPT_LOG_CRITICAL, "Received FormatError message: %s", hfcp->response.formaterror.reason);

		rc = -1;
		goto cleanup;

  case FCPT_RESPONSE_FAILED:
		_fcpLog(FCPT_LOG_CRITICAL, "Received Failed message: %s", hfcp->response.failed.reason);

		rc = -1;
		goto cleanup;

	default:
		_fcpLog(FCPT_LOG_DEBUG, "* warning.. fec_segment_file() received unknown response code: %d", rc);
		break;
	}

	/* Allocate the area for all required segments (spaces for pointers to hSegment) */
	hfcp->key->segment_count = hfcp->response.segmentheader.segments;
	hfcp->key->segments = malloc(sizeof (hSegment *) * hfcp->key->segment_count);

	/* Loop while there's more segments to receive */
	segment_count = hfcp->key->segment_count;

	index = 0;

	_fcpLog(FCPT_LOG_DEBUG, "expecting %u segment(s)", segment_count);

	/* Loop through all segments and store information */
	for (rc = FCPT_RESPONSE_SEGMENTHEADER;
			((index < segment_count) && (rc == FCPT_RESPONSE_SEGMENTHEADER));) {

		_fcpLog(FCPT_LOG_DEBUG, "retrieving segment %d", index+1);

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

		_fcpLog(FCPT_LOG_DEBUG, "received segment %u/%u", index+1, segment_count);
	
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

	unsigned long segment_len;
	unsigned long file_len;
	unsigned long file_offset;
	unsigned long data_len;
	unsigned long metadata_len;
	unsigned long block_len;
	unsigned long pad_len;

	hSegment  *segment;

	_fcpLog(FCPT_LOG_DEBUG, "entered fec_encode_segment()");

	/* Helper pointer since we're encoding 1 segment at a time */
	segment      = hfcp->key->segments[index];
	
	segment_len  = segment->block_size * segment->block_count; /* */
	file_len     = segment->filelength;         /* total length of file */
	file_offset  = segment->offset;             /* offset from start of file */
	data_len     = file_len - file_offset;      /* total length of data left to write */
	metadata_len = strlen(segment->header_str); /* length of SegmentHeader */
	
	_fcpLog(FCPT_LOG_DEBUG, "block size: %lu, block_count: %lu, segment length: %lu",
		segment->block_size, segment->block_count, segment_len);
		
	if (_fcpSockConnect(hfcp) != 0) return -1;

	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECEncodeSegment\nDataLength=%lx\nMetadataLength=%lx\nData\n",
					 segment_len + metadata_len,
					 metadata_len
					 );
	
	/* Send FECEncodeSegment message */
	if ((rc = _fcpSend(hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCPT_LOG_CRITICAL, "Could not write FECEncodeSegment message to node");
		goto cleanup;
	}
	
	/* Send SegmentHeader */
	if ((rc = _fcpSend(hfcp->socket, segment->header_str, strlen(segment->header_str))) == -1) {
		_fcpLog(FCPT_LOG_CRITICAL, "Could not write segment header to node");
		goto cleanup;
	}

	_fcpLog(FCPT_LOG_DEBUG, "sent FECEncodeSegment message for segment");

	/* Open file we are about to send */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_READ);

	/* seek to the location relative to the segment (if needed) */
	if (file_offset > 0) lseek(hfcp->key->tmpblock->fd, file_offset, SEEK_SET);

	/* Write the data from the file, then write the pad blocks */
	/* set fi to the number of bytes to write in this segment */
	fi = (data_len > segment_len ? segment_len : data_len);
	
	/* pad_len is the length of the segment minus the data we're actually going to write */
	pad_len = segment_len - fi;

	_fcpLog(FCPT_LOG_DEBUG, "writing segment %d/%d to node", index+1, hfcp->key->segment_count);
			
	while (fi) {
		
		/* How many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		/* read byte_count bytes from the file we're inserting */
		rc = _fcpRead(hfcp->key->tmpblock->fd, buf, byte_count);
				
		if ((rc = _fcpSend(hfcp->socket, buf, rc)) < 0) {
			_fcpLog(FCPT_LOG_CRITICAL, "Could not write key data to node");
			goto cleanup;
		}
		
		/* decrement by number of bytes written to the socket */
		fi -= rc;
	}
	
	_fcpBlockUnlink(hfcp->key->tmpblock);

	if (pad_len) { /* now write the pad bytes and end transmission.. */
	
		/* set the buffer to all zeroes so we can send 'em */
		memset(buf, 0, L_FILE_BLOCKSIZE);
	
		_fcpLog(FCPT_LOG_DEBUG, "writing pad data");

		fi = pad_len;		
		while (fi) {
			
			/* how many bytes are we writing this pass? */
			byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
			
			if ((rc = _fcpSend(hfcp->socket, buf, byte_count)) < 0) {
				_fcpLog( FCPT_LOG_CRITICAL, "Could not write trailing bytes to node");
				goto cleanup;
			}
			
			/* decrement i by number of bytes written to the socket */
			fi -= rc;
		}
	}

	/* if the response isn't BlocksEncoded, we have a problem */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPT_RESPONSE_BLOCKSENCODED) {
		_fcpLog(FCPT_LOG_CRITICAL, "Did not receive expected BlocksEncoded message");
		rc = -1;

		goto cleanup;
	}
	
	/* it is a BlocksEncoded message.. get the check blocks */
	block_len = hfcp->response.blocksencoded.block_size;

	_fcpLog(FCPT_LOG_DEBUG, "expecting %u check blocks", segment->cb_count);

	for (bi=0; bi < segment->cb_count; bi++) {

		/* create the HBlock struct and link the temp file */
		segment->check_blocks[bi] = _fcpCreateHBlock();
		_fcpBlockLink(segment->check_blocks[bi], _FCP_WRITE);

		segment->check_blocks[bi]->size = block_len;
		
		/* We're expecting a DataChunk message */
		
		for (fi=0; fi < block_len; ) {

			if ((rc = _fcpRecvResponse(hfcp)) != FCPT_RESPONSE_DATACHUNK) {
				_fcpLog(FCPT_LOG_CRITICAL, "did not receive expected DataChunk message");
				rc = -1;
				
				goto cleanup;
			}

			/* Write it !! */
			rc = _fcpWrite(segment->check_blocks[bi]->fd,
										hfcp->response.datachunk.data,
										hfcp->response.datachunk.length);
			
			fi += rc;
		}
		
		/* Close the check block file */
		_fcpBlockUnlink(segment->check_blocks[bi]);

		_fcpLog(FCPT_LOG_DEBUG, "received check block %u/%u",
						bi+1, segment->cb_count);
	}
	
	_fcpLog(FCPT_LOG_VERBOSE, "Successfully received %u check blocks", bi);
	rc = 0;
	
 cleanup: /* this is called when there is an error above */
	
  _fcpSockDisconnect(hfcp);
	_fcpLog(FCPT_LOG_DEBUG, "exiting fec_encode_segment()");
	
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

	_fcpLog(FCPT_LOG_DEBUG, "entered fec_insert_data_blocks()");
	
	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	/* open key file */
	_fcpBlockLink(hfcp->key->tmpblock, _FCP_READ);

	while (bi < segment->db_count) { /* while (bi < segment->db_count) */

		tmp_hfcp = fcpInheritHFCP(hfcp);

		_fcpLog(FCPT_LOG_DEBUG, "inserting data block - segment: %u/%u, block %u/%u",
						index+1, hfcp->key->segment_count,
						bi+1, segment->db_count);

		fcpOpenKey(tmp_hfcp, "CHK@", FCPT_MODE_O_WRITE);

		/* seek to the location relative to the segment (if needed) */
		if (segment->offset > 0) lseek(hfcp->key->tmpblock->fd, segment->offset, SEEK_SET);

		bytes = segment->block_size;
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source; if 0, then send pad bytes */
			if ((rc = _fcpRead(hfcp->key->tmpblock->fd, buf, byte_count)) <= 0) break;

			if ((rc = fcpWriteKey(tmp_hfcp, buf, rc)) < 0) {
				_fcpLog(FCPT_LOG_CRITICAL, "Could not write key data to internal tempfile");
				goto cleanup;
			}
			
			/* decrement by number of bytes read from the socket */
			bytes -= rc;
		}
		
		if (bytes) _fcpLog(FCPT_LOG_DEBUG, "must send zero-padded data");
	
		/* check to see if there's pad bytes we have to retrieve */
		memset(buf, 0, L_FILE_BLOCKSIZE);
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

			if ((rc = fcpWriteKey(tmp_hfcp, buf, byte_count)) < 0) {
				_fcpLog(FCPT_LOG_CRITICAL, "Could not zero data to internal tempfile");
				goto cleanup;
			}

			/* decrement by number of bytes read from the socket */
			bytes -= byte_count;
		}

		_fcpLog(FCPT_LOG_DEBUG, "file to insert: %s", tmp_hfcp->key->tmpblock->filename);

		_fcpBlockUnlink(tmp_hfcp->key->tmpblock);
		_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);

		rc = _fcpPutBlock(tmp_hfcp,
											tmp_hfcp->key->tmpblock,
											0,
											"CHK@");

		if (rc != 0) goto cleanup;

		/* check blocks were created before, so now create the data blocks only */
		segment->data_blocks[bi] = _fcpCreateHBlock();
		fcpParseHURI(segment->data_blocks[bi]->uri, tmp_hfcp->key->tmpblock->uri->uri_str);

		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
		tmp_hfcp = 0;

		_fcpLog(FCPT_LOG_VERBOSE, "Inserted data block %u/%u",
						bi+1, segment->db_count);

		_fcpLog(FCPT_LOG_DEBUG, "segment %u/%u, block %u/%u, uri: %s",
						index+1, hfcp->key->segment_count,
						bi+1, segment->db_count,
						segment->data_blocks[bi]->uri->uri_str);

		bi++;
	}

	/* we're done with the key data */
	rc = 0;

 cleanup: /* this is called when there is an error above */

	_fcpBlockUnlink(hfcp->key->tmpblock);
	_fcpBlockUnlink(hfcp->key->metadata->tmpblock);

	if (tmp_hfcp) {
		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
	}

	_fcpLog(FCPT_LOG_DEBUG, "exiting fec_insert_data_blocks()");
	
	return rc;
}

static int fec_insert_check_blocks(hFCP *hfcp, int index)
{
	int rc;
	unsigned long bi;   /* block index */

	hSegment *segment;
	hFCP     *tmp_hfcp;

	_fcpLog(FCPT_LOG_DEBUG, "entered fec_encode_check_blocks()");

	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	for (bi=0; bi < segment->cb_count; bi++) {

		tmp_hfcp = fcpInheritHFCP(hfcp);
		
		_fcpLog(FCPT_LOG_DEBUG, "inserting check block - segment: %u/%u, block %u/%u",
						index+1, hfcp->key->segment_count,
						bi+1, segment->cb_count);
		
		rc = _fcpBlockSetFilename(tmp_hfcp->key->tmpblock,
															segment->check_blocks[bi]->filename);

		/* must set so that _fcpPutBlock() works properly */
		tmp_hfcp->key->size = _fcpFilesize(segment->check_blocks[bi]->filename);
		
		rc = _fcpPutBlock(tmp_hfcp,
											tmp_hfcp->key->tmpblock,
											0,
											"CHK@");

		if (rc < 0) {
			_fcpLog(FCPT_LOG_CRITICAL, "Could not insert check block %u into Freenet", bi);
			goto cleanup;
		}		
		
		fcpParseHURI(segment->check_blocks[bi]->uri, tmp_hfcp->key->tmpblock->uri->uri_str);

		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
		tmp_hfcp = 0;

		_fcpLog(FCPT_LOG_VERBOSE, "Inserted check block %u/%u",
						bi+1, segment->cb_count);

		_fcpLog(FCPT_LOG_DEBUG, "segment %u/%u, block %u/%u, uri: %s",
						index+1, hfcp->key->segment_count,
						bi+1, segment->cb_count,
						segment->check_blocks[bi]->uri->uri_str);
	}
	
	rc = 0;

 cleanup: /* this is called when there is an error above */
	
	if (tmp_hfcp) {
		fcpDestroyHFCP(tmp_hfcp);
		free(tmp_hfcp);
	}
	
	_fcpLog(FCPT_LOG_DEBUG, "exiting fec_insert_check_blocks()");
	
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

	_fcpLog(FCPT_LOG_DEBUG, "entered fec_make_metadata()");

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
	_fcpLog(FCPT_LOG_DEBUG, "wrote FECMakeMetadata message to temporary file");

	meta_len = tmp_hfcp->key->size;

  if (_fcpSockConnect(tmp_hfcp) != 0) return -1;

	/* Send FECMakeMetadata command */
	snprintf(buf, 512, "FECMakeMetadata\nDescription=file\nMimeType=%s\nDataLength=%lx\nData\n",
					 hfcp->key->mimetype,
					 meta_len);

	if ((rc = _fcpSend(tmp_hfcp->socket, buf, strlen(buf))) == -1) {
		_fcpLog(FCPT_LOG_CRITICAL, "Could not send FECMakeMetadata message");
		goto cleanup;
	}

	_fcpLog(FCPT_LOG_DEBUG, "sent FECMakeMetadata command to node");

	bytes = meta_len;
	_fcpBlockLink(tmp_hfcp->key->tmpblock, _FCP_READ);

	while (bytes) {
		byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

		rc = _fcpRead(tmp_hfcp->key->tmpblock->fd, block, byte_count);

		if ((rc = _fcpSend(tmp_hfcp->socket, block, rc)) == -1) {
			_fcpLog(FCPT_LOG_CRITICAL, "Could not send metadata");
			goto cleanup;
		}
		
		/* decrement by number of bytes written to the socket */
		bytes -= rc;
	}

	_fcpBlockUnlink(tmp_hfcp->key->tmpblock);

	/* expecting a mademetadata response */
	rc = _fcpRecvResponse(tmp_hfcp);
	
	switch (rc) {
	case FCPT_RESPONSE_MADEMETADATA:
		meta_len = tmp_hfcp->response.mademetadata.datalength;
		_fcpLog(FCPT_LOG_DEBUG, "bytes of metadata to process: %u", meta_len);

		break;

	default:
		_fcpLog(FCPT_LOG_CRITICAL, "Unknown response code from node: %d", rc);
		rc = -1;
		
		goto cleanup;
	}

	_fcpLog(FCPT_LOG_DEBUG, "reading prepared metadata");

	_fcpBlockLink(tmp_hfcp->key->metadata->tmpblock, _FCP_WRITE);
	bytes = meta_len;

	while (bytes) {

		if ((rc = _fcpRecvResponse(tmp_hfcp)) != FCPT_RESPONSE_DATACHUNK) {
			_fcpLog(FCPT_LOG_CRITICAL, "Did not receive expected DataChunk message");
			rc = -1;

			goto cleanup;
		}

		fcpWriteMetadata(tmp_hfcp, 
										 tmp_hfcp->response.datachunk.data,
										 tmp_hfcp->response.datachunk.length);
		
		bytes -= tmp_hfcp->response.datachunk.length;
	}

	_fcpBlockUnlink(tmp_hfcp->key->metadata->tmpblock);
  _fcpSockDisconnect(tmp_hfcp);
	
	_fcpLog(FCPT_LOG_DEBUG, "wrote metadata to tempfile successfully");

	rc = _fcpPutBlock(tmp_hfcp,
										0,
										tmp_hfcp->key->metadata->tmpblock,
										"CHK@");

	if (rc != 0) {
		_fcpLog(FCPT_LOG_CRITICAL, "Could not insert splitfile metadata");
		goto cleanup;
	}

	fcpParseHURI(hfcp->key->uri, tmp_hfcp->key->tmpblock->uri->uri_str);
	rc = 0;

 cleanup: /* this is called when there is an error above */
	
  _fcpSockDisconnect(tmp_hfcp);

	fcpDestroyHFCP(tmp_hfcp);
	free(tmp_hfcp);

	return rc;
}

