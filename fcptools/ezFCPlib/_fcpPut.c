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


extern int    _fcpSockConnect(hFCP *hfcp);
extern void   _fcpSockDisconnect(hFCP *hfcp);
extern int    _fcpTmpfile(char **filename);

extern long   file_size(char *filename);
extern void   unlink_key(hKey *hKey);

/* exported functions for fcptools codebase */

/* the following two functions will set hfcp->error if one is encountered */
int put_file(hFCP *hfcp, char *key_filename, char *meta_filename, char *uri);
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);

int put_date_redirect(hFCP *hfcp, char *uri);
int put_redirect(hFCP *hfcp, char *uri_src, char *uri_dest);


/* Private functions for internal use */
static int fec_segment_file(hFCP *hfcp);
static int fec_encode_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_insert_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_make_metadata(hFCP *hfcp, char *meta_filename);

/* TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
+ Correct Restarted message handling to ensure seamless restarts.
  (_fcpPut.c:266(OK), _fcpPut.c:915, _fcpPut.c:1105(?), _fcpPut.c:1220).
+ Make sure all returns are prefixed with cleanup (use goto!)
***********************************************************************/

/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	put_file()

	function creates a freenet CHK using the contents in 'key_filename'
	along with key metadata contained in 'meta_filename'.  Function will
	check	and validate the size of both file arguments.

	function expects the following members in hfcp to be set:

	@@@ function returns:
	- zero on success
	- non-zero on error.
*/
int put_file(hFCP *hfcp, char *key_filename, char *meta_filename, char *uri)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char put_command[L_FILE_BLOCKSIZE+1];

	int rc;

	int kfd = -1;
	int mfd = -1;

	int bytes;
	int byte_count;

	FILE *kfile;
	FILE *mfile;

	_fcpLog(FCP_LOG_DEBUG, "Entered put_file()\n  key: %s, metadata: %s, uri: %s", key_filename, meta_filename, uri);

	/* if the key_filename isn't there, or NULL assume no key to insert
		 (perhaps only metadata) */
	
	if (key_filename) {
		if ((hfcp->key->size = file_size(key_filename)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "file_size() returned %d for key file %s",
							hfcp->key->size, key_filename);
			
			hfcp->key->size = 0;
		}
	}
	else
		hfcp->key->size = 0;

	/* now metadata */

	if (meta_filename) {
		if ((hfcp->key->metadata->size = file_size(meta_filename)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "file_size() returned %d for metadata file %s",
							hfcp->key->metadata->size, meta_filename);
			
			hfcp->key->metadata->size = 0;
		}
	}
	else
		hfcp->key->metadata->size = 0;

	/* now if key->size and metadata->size are both zero, bail */
	if ((hfcp->key->size == 0) && (hfcp->key->metadata->size == 0))
		return -1;

	rc = snprintf(put_command, L_FILE_BLOCKSIZE,
								"ClientPut\nRemoveLocalKey=%s\nURI=%s\nHopsToLive=%x\nDataLength=%x\nMetadataLength=%x\nData\n",
								(hfcp->delete_local == 0 ? "false" : "true"),
								uri,
								hfcp->htl,
								hfcp->key->size + hfcp->key->metadata->size,
								hfcp->key->metadata->size
								);
	
	do { /* let's loop this until we stop receiving Restarted messages */
		
		_fcpLog(FCP_LOG_DEBUG, "put_file(): entered insert loop");

		if (hfcp->key->size > 0) {
			kfile = fopen(key_filename, "rb");
			kfd = fileno(kfile);
		}
		
		if (hfcp->key->metadata->size > 0) {
			mfile = fopen(meta_filename, "rb");
			mfd = fileno(mfile);
		}
		
		/* connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0)	return -1;
		
		_fcpLog(FCP_LOG_DEBUG, "sending ClientPut message; delete_local: %d, htl: %d, keysize: %d, metasize: %d",
						hfcp->delete_local, hfcp->htl, hfcp->key->size, hfcp->key->metadata->size);
		
		/* Send ClientPut command */
		if (send(hfcp->socket, put_command, strlen(put_command), 0) == -1) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not send ClientPut message");
			
			goto cleanup;
		}
		
		/* now send any metadata that's available first.. */
		if (hfcp->key->metadata->size > 0) {
			bytes = hfcp->key->metadata->size;
			
			while (bytes) {
				byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
				
				if ((rc = read(mfd, buf, byte_count)) <= 0) {
					_fcpLog(FCP_LOG_DEBUG, "could not read metadata from file");
					goto cleanup;
				}
				
				if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
					_fcpLog(FCP_LOG_DEBUG, "could not write metadata to socket");
					goto cleanup;
				}
				
				/* decrement number of bytes written */
				bytes -= byte_count;

			} /* finished writing metadata (if any) */
		}
		
		_fcpLog(FCP_LOG_DEBUG, "wrote metadata to socket");
		
		/* Here, all metadata has been written */
		
		/* now write key data
			 at this point, the socket *is* connected to the node and the ClientPut
			 command *has* been sent in either case (metadata, no-metadata) */
		
		bytes = hfcp->key->size;
		
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source */
			if ((rc = read(kfd, buf, byte_count)) <= 0) {
				_fcpLog(FCP_LOG_DEBUG, "could not read key data from file");
				goto cleanup;
			}

			/* write to socket */
			if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
				_fcpLog(FCP_LOG_DEBUG, "could not write key data to socket");
				goto cleanup;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}

		_fcpLog(FCP_LOG_DEBUG, "wrote key data to socket");
		
		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);

		_fcpLog(FCP_LOG_DEBUG, "put_file() handling response");
		
		switch (rc) {
		case FCPRESP_TYPE_SUCCESS:
			fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);
			break;
			
		case FCPRESP_TYPE_KEYCOLLISION:
			fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			_fcpLog(FCP_LOG_DEBUG, "received \"Restarted\"; cleaning up and re-entering the insert loop");

			/* close the key and metadata source files */
			close(mfd);
			close(kfd);			
			
			/* disconnect from the socket */
			_fcpSockDisconnect(hfcp);

			break;
			
		case FCPRESP_TYPE_ROUTENOTFOUND:
			_fcpLog(FCP_LOG_VERBOSE, "Received \"RouteNotFound\"; reason: %s", hfcp->response.routenotfound.reason);
			break;
			
		case FCPRESP_TYPE_FORMATERROR:
			_fcpLog(FCP_LOG_VERBOSE, "Received \"FormatError\"; reason: %s", hfcp->response.formaterror.reason);
			break;
			
		case FCPRESP_TYPE_FAILED:
			_fcpLog(FCP_LOG_VERBOSE, "Received \"Failed\"; reason: %s", hfcp->response.failed.reason);
			break;
			
		default:
			_fcpLog(FCP_LOG_VERBOSE, "Received unknown response code from node: %d", rc);
			break;
		}
		
  } while (rc == FCPRESP_TYPE_RESTARTED);

  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
    goto cleanup;

	/* do a 'local' cleanup here' */
	close(kfd);			
	if (hfcp->key->metadata->size) close(mfd);
	
  _fcpSockDisconnect(hfcp);
	_fcpLog(FCP_LOG_DEBUG, "put_file() was successful; inserted key: %s", hfcp->key->uri->uri_str);

	return 0;

 cleanup: /* this is called when there is an error above */
	close(kfd);			
	if (hfcp->key->metadata->size) close(mfd);
	
  _fcpSockDisconnect(hfcp);
	return -1;
}


/*
	put_fec_splitfile()

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
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int kfd = -1;
	int mfd = -1;

	int index;

	FILE *kfile;
	FILE *mfile;

	_fcpLog(FCP_LOG_DEBUG, "Entered put_fec_splitfile()");

	if (key_filename) {
		if ((hfcp->key->size = file_size(key_filename)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "file_size() returned %d for key file %s",
							hfcp->key->size, key_filename);
			
			hfcp->key->size = 0;
		}
	}
	else
		hfcp->key->size = 0;

	/* now metadata */

	if (meta_filename) {
		if ((hfcp->key->metadata->size = file_size(meta_filename)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "file_size() returned %d for metadata file %s",
							hfcp->key->metadata->size, meta_filename);
			
			hfcp->key->metadata->size = 0;
		}
	}
	else
		hfcp->key->metadata->size = 0;

	/* now if key->size and metadata->size are both zero, bail */
	if ((hfcp->key->size == 0) && (hfcp->key->metadata->size == 0))
		return -1;

	if (hfcp->key->size > 0) {
		kfile = fopen(key_filename, "rb");
		kfd = fileno(kfile);
	}
	
	if (hfcp->key->metadata->size > 0) {
		mfile = fopen(meta_filename, "rb");
		mfd = fileno(mfile);
	}

	if (fec_segment_file(hfcp) != 0) return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_encode_segment(hfcp, key_filename, index) != 0) return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_insert_segment(hfcp, key_filename, index) != 0)	return -1;

	/* now that the data is inserted, generate and insert metadata merged with
		 user-defined data for the splitfile */
	if (fec_make_metadata(hfcp, meta_filename)) return -1;

	_fcpLog(FCP_LOG_DEBUG, "Exiting put_fec_splitfile(); inserted key: %s", hfcp->key->uri->uri_str);

	return 0;
}


#if 0
int put_date_redirect(hFCP *hfcp, char *uri)
{
	_fcpLog(FCP_LOG_DEBUG, "Entered put_date_redirect()");
}
#endif


int put_redirect(hFCP *hfcp, char *uri_src, char *uri_dest)
{
	hFCP *tmp_hfcp;

	char  buf[513];
	int   rc;
	
	rc = snprintf(buf, 512,
								"Version\nRevision=1\nEndPart\nDocument\nRedirect.Target=%s\nEnd\n",
								uri_dest
								);
	
	_fcpLog(FCP_LOG_DEBUG, "Metadata:\n%s", buf);
	
	tmp_hfcp = fcpInheritHFCP(hfcp);
	
	fcpOpenKey(tmp_hfcp, uri_src, _FCP_O_WRITE);
	fcpWriteMetadata(tmp_hfcp, buf, strlen(buf));
	
	close(tmp_hfcp->key->metadata->tmpblock->fd);
	tmp_hfcp->key->metadata->tmpblock->fd = -1;

	rc = 0;
	
	/* now insert the metadata which contains the redirect info */
	rc = put_file(tmp_hfcp, 0, tmp_hfcp->key->metadata->tmpblock->filename, uri_dest);
	
	_fcpLog(FCP_LOG_DEBUG, "put_file() - rc: %d, URI: %s", rc, tmp_hfcp->key->uri->uri_str);
	
	fcpParseURI(hfcp->key->target_uri, tmp_hfcp->key->uri->uri_str);
	
	/* call this function to close the block tempfiles
		 (for key and meta if necessary) */
	unlink_key(tmp_hfcp->key);
	
	fcpDestroyHFCP(tmp_hfcp);
	
	return rc;
}

/**********************************************************************/

/*
	fec_segment_file()

	function will set hfcp->error before returning to caller
*/
static int fec_segment_file(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int index;
	int segment_count;

	_fcpLog(FCP_LOG_DEBUG, "Entered fec_segment_file()");

  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not make socket connection to node");
		return -1;
	}

	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%x\nEndMessage\n",
					 hfcp->key->size
					 );

	/* Send FECSegmentFile command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not send FECSegmentFile command");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECSegmentFile message");

	rc = _fcpRecvResponse(hfcp);

  switch (rc) {
  case FCPRESP_TYPE_SEGMENTHEADER:
		break;
		
  case FCPRESP_TYPE_FORMATERROR:
		snprintf(hfcp->error, L_ERROR_STRING, "node returned format error; \"%s\"", hfcp->response.formaterror.reason);

    _fcpSockDisconnect(hfcp);
		return -1;

  case FCPRESP_TYPE_FAILED:
		snprintf(hfcp->error, L_ERROR_STRING, "node returned failed; \"%s\"", hfcp->response.failed.reason);

    _fcpSockDisconnect(hfcp);
		return -1;

	default:
		snprintf(hfcp->error, L_ERROR_STRING, "unknown response code from node: %d", rc);

    _fcpSockDisconnect(hfcp);
		return -1;
	}

	/* Allocate the area for all required segments */
	hfcp->key->segment_count = hfcp->response.segmentheader.segments;
	hfcp->key->segments = (hSegment **)malloc(sizeof (hSegment *) * hfcp->key->segment_count);

	/* Loop while there's more segments to receive */
	segment_count = hfcp->key->segment_count;
	index = 0;

	/* Loop through all segments and store information */
	while (index < segment_count) {
		hfcp->key->segments[index] = (hSegment *)malloc(sizeof (hSegment));

		/* get counts of data and check blocks */
		hfcp->key->segments[index]->db_count = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[index]->cb_count = hfcp->response.segmentheader.checkblock_count;

		/* allocate space for data and check block handles */
		hfcp->key->segments[index]->data_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[index]->db_count);
		hfcp->key->segments[index]->check_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[index]->cb_count);

		snprintf(buf, L_FILE_BLOCKSIZE,
						 "SegmentHeader\nFECAlgorithm=%s\nFileLength=%x\nOffset=%x\n" \
						 "BlockCount=%x\nBlockSize=%x\nDataBlockOffset=%x\nCheckBlockCount=%x\n" \
						 "CheckBlockSize=%x\nCheckBlockOffset=%x\nSegments=%x\nSegmentNum=%x\nBlocksRequired=%x\nEndMessage\n",

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

		hfcp->key->segments[index]->header_str = (char *)malloc(strlen(buf) + 1);
		strcpy(hfcp->key->segments[index]->header_str, buf);

		_fcpLog(FCP_LOG_DEBUG, "got segment index %d:\n%s", index, buf);
	
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
}


static int fec_encode_segment(hFCP *hfcp, char *key_filename, int index)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int fd;
	int rc;

	int fi;   /* file index */
	int bi;   /* block index */

	int byte_count;

	int data_len;
	int block_len;
	int metadata_len;
	int pad_len;

	hSegment  *segment;
	FILE      *file;

	_fcpLog(FCP_LOG_DEBUG, "Entered fec_encode_segment()");

	/* Helper pointer since we're encoding 1 segment at a time */
	segment = hfcp->key->segments[index];
	
	data_len     = segment->filelength;
	metadata_len = strlen(segment->header_str);
	pad_len      = (segment->block_count * segment->block_size) - data_len;
	
	/* new connection to Freenet FCP */
	if (_fcpSockConnect(hfcp) != 0) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not make socket connection to node");
		return -1;
	}
	
	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECEncodeSegment\nDataLength=%x\nMetadataLength=%x\nData\n",
					 data_len + metadata_len + pad_len,
					 metadata_len
					 );
	
	/* Send FECEncodeSegment message */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not write FECEncodeSegment message");
		return -1;
	}
	
	/* Send SegmentHeader */
	if (send(hfcp->socket, segment->header_str, strlen(segment->header_str), 0) == -1) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not write initial SegmentHeader message");
		return -1;
	}

	/* Open file we are about to send */
	if (!(file = fopen(key_filename, "rb"))) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not open file for reading");
		return -1;
	}
	fd = fileno(file);
	
	/* Write the data from the file, then write the pad blocks */
	/* data_len is the total length of the data file we're inserting */
	
	fi = data_len;
	while (fi) {
		int bytes;
		
		/* How many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		/* read byte_count bytes from the file we're inserting */
		bytes = read(fd, buf, byte_count);
		
		if ((rc = send(hfcp->socket, buf, bytes, 0)) < 0) {
			snprintf(hfcp->error, L_ERROR_STRING, "could not write bytes to socket");
			return -1;
		}
		
		/* decrement by number of bytes written to the socket */
		fi -= byte_count;
	}
	
	/* now write the pad bytes and end transmission.. */
	
	/* set the buffer to all zeroes so we can send 'em */
	memset(buf, 0, L_FILE_BLOCKSIZE);
	
	fi = pad_len;
	while (fi) {
		
		/* how many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
			snprintf(hfcp->error, L_ERROR_STRING, "could not write zero-padded data to socket");
			return -1;
		}
		
		/* decrement i by number of bytes written to the socket */
		fi -= byte_count;
	}
	
	/* if the response isn't BlocksEncoded, we have a problem */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_BLOCKSENCODED) {
		snprintf(hfcp->error, L_ERROR_STRING, "did not receive expected BlocksEncoded message");
		return -1;
	}
	
	/* it is a BlocksEncoded message.. get the check blocks */
	block_len = hfcp->response.blocksencoded.block_size;
	
	for (bi=0; bi < segment->cb_count; bi++) {

		/* We're expecting a DataChunk message */
		if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			snprintf(hfcp->error, L_ERROR_STRING, "did not receive expected DataChunk message");
			return -1;
		}
		
		segment->check_blocks[bi] = _fcpCreateHBlock();
		fd = _fcpTmpfile(&segment->check_blocks[bi]->filename);

		if (fd == -1) {
			snprintf(hfcp->error, L_ERROR_STRING, "could not open file for writing check block %d: %s",
							 bi,
							 segment->check_blocks[bi]->filename);

			return -1;
		}

		segment->check_blocks[bi]->size = block_len;

		for (fi=0; fi < block_len; ) {
			byte_count = write(fd, hfcp->response.datachunk.data, hfcp->response.datachunk.length);
			
			if (byte_count != hfcp->response.datachunk.length) {
				snprintf(hfcp->error, L_ERROR_STRING, "error writing check block %d", bi);
				return -1;
			}
			
			fi += byte_count;
			
			/* only get the next DataChunk message if we're expecting one */
			if (fi < block_len)
				if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
					snprintf(hfcp->error, L_ERROR_STRING, "did not receive expected DataChunk message (2)");
					return -1;
				}
		}
		
		if (fi != block_len) {
			snprintf(hfcp->error, L_ERROR_STRING, "bytes received for check block did not match with expected length");
			return -1;
		}

		/* Close the check block file */
		close(fd);
	}

	_fcpLog(FCP_LOG_DEBUG, "successfully received %d check blocks", bi);

	return 0;
}


static int fec_insert_segment(hFCP *hfcp, char *key_filename, int index)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int bi;   /* block index */

	int bytes;
	int byte_count;

	hSegment  *segment;

	FILE *kfile;
	hFCP *tmp_hfcp;

	int kfd;

	_fcpLog(FCP_LOG_DEBUG, "Entered fec_insert_segment()");
	
	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	/* open key file */
	if (!(kfile = fopen(key_filename, "rb"))) {
		snprintf(hfcp->error, L_ERROR_STRING, "Could not open key data from file");
		return -1;
	}			
	kfd = fileno(kfile);
	
	while (bi < segment->db_count) { /* while (bi < segment->db_count) */
		
		/* seek to the location relative to the segment (if needed) */
		if (segment->offset > 0) lseek(kfd, segment->offset, SEEK_SET);
		
		tmp_hfcp = fcpInheritHFCP(hfcp);
		fcpOpenKey(tmp_hfcp, "CHK@", _FCP_O_WRITE);

		bytes = segment->block_size;
		
		_fcpLog(FCP_LOG_DEBUG, "bytes to write: %d", bytes);

		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source */
			if ((byte_count = read(kfd, buf, byte_count)) <= 0) break;

			if (fcpWriteKey(tmp_hfcp, buf, byte_count)) {
				snprintf(hfcp->error, L_ERROR_STRING, "could not write key data to temp file");
				return -1;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}
		
		if (bytes) _fcpLog(FCP_LOG_DEBUG, "must send zero-padded data");
		
		/* check to see if there's pad bytes we have to send */
		memset(buf, 0, L_FILE_BLOCKSIZE);
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

			if (fcpWriteKey(tmp_hfcp, buf, byte_count)) {
				snprintf(hfcp->error, L_ERROR_STRING, "could not write key data to temp file");
				return -1;
			}

			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}

		/* now that the block is written to a temp file, insert it as a CHK
			 (no metadata here). */
		
		rc = put_file(tmp_hfcp, tmp_hfcp->key->tmpblock->filename, 0, "CHK@");
		if (rc) return -1;

		segment->data_blocks[bi] = _fcpCreateHBlock();
		fcpParseURI(segment->data_blocks[bi]->uri, tmp_hfcp->key->uri->uri_str);

		_fcpLog(FCP_LOG_DEBUG, "Inserted data block %d: %s",
						bi, segment->data_blocks[bi]->uri->uri_str);
		
		bi++;
	}

	/* we're done with the key data */
	unlink_key(tmp_hfcp->key);
	fcpDestroyHFCP(tmp_hfcp);

	close(kfd);
	kfd = -1;

	/******************************************************************/
	/* insert check blocks next */

	for (bi=0; bi < segment->cb_count; bi++) {

		_fcpLog(FCP_LOG_DEBUG, "inserting check block %d", bi);

		tmp_hfcp = fcpInheritHFCP(hfcp);
		tmp_hfcp->key = _fcpCreateHKey();
		tmp_hfcp->key->metadata = _fcpCreateHMetadata();
		
		rc = put_file(tmp_hfcp, segment->check_blocks[bi]->filename, 0, "CHK@");

		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not insert check block %d into Freenet", bi);

			/* hfcp->error already set via call to put_file() */
			return -1;
		}		

		segment->check_blocks[bi] = _fcpCreateHBlock();
		fcpParseURI(segment->check_blocks[bi]->uri, tmp_hfcp->key->uri->uri_str);
	
		_fcpLog(FCP_LOG_DEBUG, "successfully inserted check block %d", bi);
		_fcpLog(FCP_LOG_DEBUG, "inserted check block %d: %s",
						bi, tmp_hfcp->key->uri->uri_str);

		fcpDestroyHFCP(tmp_hfcp);
	}

	return 0;
}

static int fec_make_metadata(hFCP *hfcp, char *meta_filename)
{
	char buf[513];
	char block[L_FILE_BLOCKSIZE];

	FILE *mfile;
	char *mfilename;

	FILE *kfile;

	int   kfd = -1;
	int   mfd = -1;

	int rc;
	int bi;
	int meta_len;

	int segment_count;
	int index;

	int bytes;
	int byte_count;

	hSegment *segment;
	hFCP     *tmp_hfcp;

	_fcpLog(FCP_LOG_DEBUG, "Entered fec_make_metadata()");

	/* TODO: heh.. */
	meta_filename = meta_filename;

	segment_count = hfcp->key->segment_count;
	index = 0;

	tmp_hfcp = fcpInheritHFCP(hfcp);
	fcpOpenKey(tmp_hfcp, "CHK@", _FCP_O_WRITE);
	
	while (index < segment_count) {
		/* build SegmentHeader and BlockMap pairs */
		
		/* helper pointer */
		segment = hfcp->key->segments[index];

		snprintf(buf, 512,
						 "SegmentHeader\nFECAlgorithm=OnionFEC_a_1_2\nFileLength=%x\nOffset=%x\n" \
						 "BlockCount=%x\nBlockSize=%x\nDataBlockOffset=%x\nCheckBlockCount=%x\n" \
						 "CheckBlockSize=%x\nSegments=%x\nSegmentNum=%x\nBlocksRequired=%x\nEndMessage\n",
						 
						 segment->filelength,
						 segment->offset,
						 segment->block_count,
						 segment->block_size,
						 segment->datablock_offset,
						 segment->checkblock_count,
						 segment->checkblock_size,
						 segment->segments,
						 segment->segment_num,
						 segment->blocks_required);
		
		/* copy the segment header; storing it there turned out to be a good idea :) */
		fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		
		fcpWriteKey(tmp_hfcp, "BlockMap\n", strlen("BlockMap\n"));
		
		/* concatenate data block map */
		for (bi=0; bi < segment->db_count; bi++) {

			snprintf(buf, 512, "Block.%x=%s\n", bi, segment->data_blocks[bi]->uri->uri_str);
			fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		}
		
		/* now for check block map */
		for (bi=0; bi < segment->cb_count; bi++) {

			snprintf(buf, 512, "Check.%x=%s\n", bi, segment->check_blocks[bi]->uri->uri_str);
			fcpWriteKey(tmp_hfcp, buf, strlen(buf));
		}
		
		fcpWriteKey(tmp_hfcp, "EndMessage\n", strlen("EndMessage\n"));

		/* done with this segment.. */
		index++;
	}

	_fcpLog(FCP_LOG_DEBUG, "write FECMakeMetadata message to temporary file");

	unlink_key(tmp_hfcp->key);
	meta_len = tmp_hfcp->key->size;
	
  /* connect to Freenet FCP */
  if (_fcpSockConnect(tmp_hfcp) != 0) {
		snprintf(hfcp->error, L_ERROR_STRING, "could not make socket connection to node");
    return -1;
	}

	/* Send FECMakeMetadata command */
	snprintf(buf, 512, "FECMakeMetadata\nDescription=file\nMimeType=%s\nDataLength=%x\nData\n",
					 hfcp->key->mimetype,
					 meta_len);

	_fcpLog(FCP_LOG_DEBUG, "\n%s", buf);
	
	if (send(tmp_hfcp->socket, buf, strlen(buf), 0) == -1) {
		snprintf(hfcp->error, L_ERROR_STRING, "Could not send FECMakeMetadata message");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECMakeMetadata command to node");

	/* TODO: use fcpReadKey() */

	if (!(kfile = fopen(tmp_hfcp->key->tmpblock->filename, "rb"))) {
		return -1;
	}

	kfd = fileno(kfile);
	bytes = meta_len;

	while (bytes) {
		int i;

		byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
		byte_count = read(kfd, block, byte_count);

		for (i=0; i < byte_count; i++) {
			printf("%c", block[i]);
		}
	
		if (send(tmp_hfcp->socket, block, byte_count, 0) == -1) {
			snprintf(hfcp->error, L_ERROR_STRING, "Could not send metadata information");
			return -1;
		}
		
		/* decrement by number of bytes written to the socket */
		bytes -= byte_count;
	}

	_fcpLog(FCP_LOG_DEBUG, "ready to receive FEC valid metadata from node");
	
	/* expecting a mademetadata response */
	rc = _fcpRecvResponse(tmp_hfcp);
	
	switch (rc) {
	case FCPRESP_TYPE_MADEMETADATA:
		meta_len = tmp_hfcp->response.mademetadata.datalength;
		_fcpLog(FCP_LOG_DEBUG, "bytes of metadata to process: %d", meta_len);

		break;

	default:
		snprintf(hfcp->error, L_ERROR_STRING, "unknown response code from node: %d", rc);

		_fcpSockDisconnect(tmp_hfcp);
		return -1;
	}


	/* now read metadata from freenet and write to tmp file,
		 and then finally write the metadata into Freenet (via put_file) */

	/*
	while (bytes < meta_len) {
		char *p;

		if ((rc = _fcpRecvResponse(tmp_hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			_fcpSockDisconnect(tmp_hfcp);
			return -1;
		}

		write(mfd, tmp_hfcp->response.datachunk.data, tmp_hfcp->response.datachunk.length);
		bytes += tmp_hfcp->response.datachunk.length;

		p = (char *)malloc(tmp_hfcp->response.datachunk.length + 1);
		memcpy(p, tmp_hfcp->response.datachunk.data, tmp_hfcp->response.datachunk.length);
		p[hfcp->response.datachunk.length] = 0;

		_fcpLog(FCP_LOG_DEBUG, "DataChunk follows:");
		_fcpLog(FCP_LOG_DEBUG, "%s\n", p);

		free(p);
	}
	close(mfd);

	if (rc < 0) _fcpLog(FCP_LOG_DEBUG, "DataChunk returned an error");

	_fcpLog(FCP_LOG_DEBUG, "read %d metadata bytes from socket and wrote locally", bytes);
	_fcpSockDisconnect(tmp_hfcp);
*/

	return 0;
}

