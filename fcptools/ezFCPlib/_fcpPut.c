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


extern int   _fcpSockConnect(hFCP *hfcp);
extern void  _fcpSockDisconnect(hFCP *hfcp);
extern char *_fcpTmpFilename(void);

extern long  file_size(char *filename);

/* exported functions for fcptools codebase */

/* the following two functions will set hfcp->error if one is encountered */
int put_file(hFCP *hfcp, char *key_filename, char *meta_filename);
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);

int put_date_redirect(hFCP *hfcp, char *uri);
int put_redirect(hFCP *hfcp_root, char *uri_dest);


/* Private functions for internal use */
static int fec_segment_file(hFCP *hfcp);
static int fec_encode_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_insert_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_make_metadata(hFCP *hfcp, char *meta_filename);


/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */

/*
	put_file()

	function creates a freenet CHK using the contents in 'key_filename'
	along with key metadata contained in 'meta_filename'.  Function will
	check	and validate the size of both file arguments.

	function expects the following members in hfcp to be set:

	@@@ function may modify:
	- hfcp->error
	- hfcp->key

	@@@ function returns:
	- zero on success
	- non-zero on error.
*/
int put_file(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char msg[513];
	int rc;

	int kfd;
	int mfd;

	int bytes;
	int byte_count;
	int meta_bytes;

	FILE *kfile;
	FILE *mfile;

	_fcpLog(FCP_LOG_VERBOSE, "Entered put_file()");

	/* clear the error field */
	if (hfcp->error) {
		free(hfcp->error);
		hfcp->error = 0;
	}
	
	/* pre-handle the key and metadata filenames to simplify the logic here
		 if at all possible */

	if (key_filename) {
		if (!(kfile = fopen(key_filename, "rb"))) {
			hfcp->error = strdup("could not open key data file");
			return -1;
		}
	}
	else {
		hfcp->error = strdup("key data filename is NULL");
		return -1;
	}

	/* ok, the key filename looks valid */
	kfd = fileno(kfile);

	/* set the key data and metadata sizes*/
	if (hfcp->key) _fcpDestroyHKey(hfcp->key);

	hfcp->key = _fcpCreateHKey();
	hfcp->key->size = file_size(key_filename);

	/* and now the same for the metadata */
	if (meta_filename) {
		if (!(mfile = fopen(meta_filename, "rb"))) {
			hfcp->error = strdup("could not open metadata file");
			return -1;
		}
		mfd = fileno(mfile);
		
		hfcp->key->metadata = _fcpCreateHMetadata();
		hfcp->key->metadata->size = file_size(meta_filename);	

		/* re-set meta_bytes to metadata size */
		meta_bytes = hfcp->key->metadata->size;
	}
	else
		meta_bytes = 0;

	/* let's loop this until we stop receiving Restarted messages */
	
	do {
		/* @@@ perhaps perform lseeks() to handle Restarted messages.. */

		/* connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0)	return -1;
		
		/* create a put message (depending on existance of metadata) */
		if (meta_bytes) {
			rc = snprintf(buf, L_FILE_BLOCKSIZE,
										"ClientPut\nURI=CHK@\nHopsToLive=%x\nDataLength=%x\nMetadataLength=%x\nData\n",
										hfcp->htl,
										hfcp->key->size + meta_bytes,
										meta_bytes
										);
		}
		else {
			rc = snprintf(buf, L_FILE_BLOCKSIZE,
										"ClientPut\nURI=CHK@\nHopsToLive=%x\nDataLength=%x\nData\n",
										hfcp->htl,
										hfcp->key->size
										);
		}
		
		/* Send ClientPut command */
		if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not send ClientPut message");
			
			_fcpSockDisconnect(hfcp);
			return -1;
		}
		
		/* now send any metadata that's available first.. */
		if (meta_bytes) {
			while (meta_bytes) {
				byte_count = (meta_bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: meta_bytes);
				
				if ((rc = read(mfd, buf, byte_count)) <= 0) {
					hfcp->error = strdup("could not read metadata from file");
					
					_fcpSockDisconnect(hfcp);
					return -1;
				}
				
				if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
					hfcp->error = strdup("could not write metadata to socket");
					
					_fcpSockDisconnect(hfcp);
					return -1;
				}
				
				/* decrement number of bytes written */
				meta_bytes -= byte_count;
				
			} /* finished writing metadata (if any) */

			_fcpLog(FCP_LOG_VERBOSE, "Wrote metadata to socket");
			fclose(mfile);
		}
		
		/* @@@ perhaps we should make sure *all* the metadata was written? */

		/* now write key data
			 at this point, the socket *is* connected to the node and the ClientPut
			 command *has* been sent in either case (metadata, no-metadata) */

		bytes = hfcp->key->size;
		
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source */
			if ((rc = read(kfd, buf, byte_count)) <= 0) {
				hfcp->error = strdup("could not read key data from file");

				_fcpSockDisconnect(hfcp);
				return -1;
			}

			/* connect to the local socket (if not already connected
				 from metadata section */
			if (hfcp->socket < 0)
				if (_fcpSockConnect(hfcp)) {
					hfcp->error = strdup("could not connect to socket");

					_fcpSockDisconnect(hfcp);
					return -1;
				}
			
			/* write to socket */
			if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
				hfcp->error = strdup("could not write key data to socket");

				_fcpSockDisconnect(hfcp);
				return -1;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}
		
		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);
		
		switch (rc) {
		case FCPRESP_TYPE_SUCCESS:
			_fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);
			break;
			
		case FCPRESP_TYPE_KEYCOLLISION:
			_fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			fclose(mfile);
			fclose(kfile);			

			_fcpSockDisconnect(hfcp);
			break;

		/* for the error cases, set hfcp->error.. */

		case FCPRESP_TYPE_ROUTENOTFOUND:
			hfcp->error = strdup("route not found");
			break;
			
		case FCPRESP_TYPE_FORMATERROR:
			snprintf(msg, 512, "node returned format error; \"%s\"", hfcp->response.formaterror.reason);
			hfcp->error = strdup(msg);

			break;
			
		case FCPRESP_TYPE_FAILED:
			snprintf(msg, 512, "node returned failed; \"%s\"", hfcp->response.failed.reason);
			hfcp->error = strdup(msg);

			break;
			
		default:
			hfcp->error = strdup("unknown response code from node");
			break;
		}

  } while (rc == FCPRESP_TYPE_RESTARTED);

  /*
		finished with connection.
		
		note: on a Restarted message, hfcp is re-connected, so either way we
		must perform a disconnect here.
	*/
  _fcpSockDisconnect(hfcp);

  /* hfcp->error should be set already in switch..case block */
  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
    return -1;

	_fcpLog(FCP_LOG_DEBUG, "inserted key: %s", hfcp->key->uri->uri_str);

	return 0;
}


/*
	put_fec_splitfile()

	function creates a freenet FEC-Encoded CHK using the contents in
	'key_filename' along with key metadata contained in 'meta_filename'.
	Function will	check	and validate the size of both file arguments.

	The FEC logic is running within Fred and made accessable by Gianni's
	FEC-specific FCP commands. The initial proposal is at:
	
	http://hawk.freenetproject.org:8080/pipermail/devl/2002-September/001053.html

	function expects the following members in hfcp to be set:

	function returns:
	- zero on success
	- non-zero on error.
	
	Any function calls here may set hfcp->error on return.
*/
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int kfd;
	int mfd;

	int index;
	int meta_bytes;

	FILE *kfile;
	FILE *mfile;

	_fcpLog(FCP_LOG_VERBOSE, "Entered put_fec_splitfile()");

	/* clear the error field */
	if (hfcp->error) {
		free(hfcp->error);
		hfcp->error = 0;
	}

	if (key_filename) {
		if (!(kfile = fopen(key_filename, "rb"))) {
			hfcp->error = strdup("could not open key data file");
			return -1;
		}
	}
	else {
		hfcp->error = strdup("key data filename is NULL");
		return -1;
	}

	/* ok, the key filename looks valid */
	kfd = fileno(kfile);

	/* set the key data and metadata sizes*/
	if (hfcp->key) _fcpDestroyHKey(hfcp->key);

	hfcp->key = _fcpCreateHKey();
	hfcp->key->size = file_size(key_filename);

	/* and now the same for the metadata */
	if (meta_filename) {
		if (!(mfile = fopen(meta_filename, "rb"))) {
			hfcp->error = strdup("could not open metadata file");
			return -1;
		}
		mfd = fileno(mfile);
		
		hfcp->key->metadata = _fcpCreateHMetadata();
		hfcp->key->metadata->size = file_size(meta_filename);	

		/* re-set meta_bytes to metadata size */
		meta_bytes = hfcp->key->metadata->size;
	}

	if (fec_segment_file(hfcp) != 0) return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_encode_segment(hfcp, key_filename, index) != 0) return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_insert_segment(hfcp, key_filename, index) != 0)	return -1;

	/* now that the data is inserted, generate and insert metadata merged with
		 user-defined data for the splitfile */
	if (fec_make_metadata(hfcp, meta_filename)) return -1;

	/* now insert a redirect if the target uri is KSK or SSK */
	/* in hfcp->key->uri is the CHK@ that contains the splitfile metadata..
		 build any redirects to point to this key */


	return 0;
}

#if 0
int put_date_redirect(hFCP *hfcp, char *uri)
{
	_fcpLog(FCP_LOG_VERBOSE, "Entered put_date_redirect()");
}
#endif

int put_redirect(hFCP *hfcp, char *uri_dest)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char meta_buf[L_FILE_BLOCKSIZE+1];

	int meta_bytes;
	int rc;

	_fcpLog(FCP_LOG_VERBOSE, "Entered put_redirect()");

	/* connect to Freenet FCP */
	if (_fcpSockConnect(hfcp) != 0)	return -1;

	rc = snprintf(meta_buf, L_FILE_BLOCKSIZE,
								"Version\nRevision=1\nEndPart\nDocument\nRedirect.Target=%s\nEnd\n",
								uri_dest
								);
	
	meta_bytes = strlen(meta_buf);
	
	/* create the put message */
	rc = snprintf(buf, L_FILE_BLOCKSIZE,
								"ClientPut\nURI=%s\n" \
								"HopsToLive=%x\n" \
								"DataLength=%x\n" \
								"MetadataLength=%x\n" \
								"Data\n" \
								"%s",
							
								hfcp->key->uri->uri_str,
								hfcp->htl,
								meta_bytes,
								meta_bytes,
								meta_buf
								);
	
	_fcpLog(FCP_LOG_DEBUG, "\n%s\n", buf);
	
	/* Send ClientPut command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send ClientPut message");
		
		_fcpSockDisconnect(hfcp);
		return -1;
	}
	
	/* expecting a success response */
	rc = _fcpRecvResponse(hfcp);
	
	switch (rc) {
	case FCPRESP_TYPE_SUCCESS:
		_fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);

		break;
		
	case FCPRESP_TYPE_KEYCOLLISION:
		_fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);

		_fcpLog(FCP_LOG_DEBUG, "keycollision on insert of redirect metadata");
		break;
		
	default:
		_fcpLog(FCP_LOG_DEBUG, "unhandled response from _fcpRecvResponse()");
	}

	_fcpSockDisconnect(hfcp);
	
	if ((rc == FCPRESP_TYPE_SUCCESS) || (rc == FCPRESP_TYPE_KEYCOLLISION))
		return 0;

	else
		return -1;
}

/**********************************************************************/

/*
	fec_segment_file()

	function will set hfcp->error before returning to caller
*/
static int fec_segment_file(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char msg[513];
	int rc;

	int index;
	int segment_count;

	_fcpLog(FCP_LOG_VERBOSE, "Entered fec_segment_file()");

  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0) {
		hfcp->error = strdup("could not make socket connection to node");
		return -1;
	}

	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%x\nEndMessage\n",
					 hfcp->key->size
					 );

	/* Send FECSegmentFile command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		hfcp->error = strdup("could not send FECSegmentFile command");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECSegmentFile message");

	rc = _fcpRecvResponse(hfcp);

  switch (rc) {
  case FCPRESP_TYPE_SEGMENTHEADER:
		break;
		
  case FCPRESP_TYPE_FORMATERROR:
		snprintf(msg, 512, "node returned format error; \"%s\"", hfcp->response.formaterror.reason);
		hfcp->error = strdup(msg);

    _fcpSockDisconnect(hfcp);
		return -1;

  case FCPRESP_TYPE_FAILED:
		snprintf(msg, 512, "node returned failed; \"%s\"", hfcp->response.failed.reason);
		hfcp->error = strdup(msg);

    _fcpSockDisconnect(hfcp);
		return -1;

	default:
		hfcp->error = strdup("unknown response code from node");
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
						 "BlockCount=%x\nBlockSize=%x\nCheckBlockCount=%x\n" \
						 "CheckBlockSize=%x\nSegments=%x\nSegmentNum=%x\nBlocksRequired=%x\nEndMessage\n",

						 hfcp->response.segmentheader.fec_algorithm,
						 hfcp->response.segmentheader.filelength,
						 hfcp->response.segmentheader.offset,
						 hfcp->response.segmentheader.block_count,
						 hfcp->response.segmentheader.block_size,
						 hfcp->response.segmentheader.checkblock_count,
						 hfcp->response.segmentheader.checkblock_size,
						 hfcp->response.segmentheader.segments,
						 hfcp->response.segmentheader.segment_num,
						 hfcp->response.segmentheader.blocks_required
						 );

		hfcp->key->segments[index]->header_str = (char *)malloc(strlen(buf) + 1);
		strcpy(hfcp->key->segments[index]->header_str, buf);

		_fcpLog(FCP_LOG_DEBUG, "got segment index %d:\n%s", index, buf);
	
		hfcp->key->segments[index]->filelength       = hfcp->response.segmentheader.filelength;
		hfcp->key->segments[index]->offset           = hfcp->response.segmentheader.offset;
		hfcp->key->segments[index]->block_count      = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[index]->block_size       = hfcp->response.segmentheader.block_size;
		hfcp->key->segments[index]->checkblock_count = hfcp->response.segmentheader.checkblock_count;
		hfcp->key->segments[index]->checkblock_size  = hfcp->response.segmentheader.checkblock_size;
		hfcp->key->segments[index]->segments         = hfcp->response.segmentheader.segments;
		hfcp->key->segments[index]->segment_num      = hfcp->response.segmentheader.segment_num;
		hfcp->key->segments[index]->blocks_required  = hfcp->response.segmentheader.blocks_required;
		
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
	char msg[513];
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

	_fcpLog(FCP_LOG_VERBOSE, "Entered fec_encode_segment()");

	/* Helper pointer since we're encoding 1 segment at a time */
	segment = hfcp->key->segments[index];
	
	data_len     = segment->filelength;
	metadata_len = strlen(segment->header_str);
	pad_len      = (segment->block_count * segment->block_size) - data_len;
	
	/* new connection to Freenet FCP */
	if (_fcpSockConnect(hfcp) != 0) {
		hfcp->error = strdup("could not make socket connection to node");
		return -1;
	}
	
	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECEncodeSegment\nDataLength=%x\nMetadataLength=%x\nData\n",
					 data_len + metadata_len + pad_len,
					 metadata_len
					 );
	
	/* Send FECEncodeSegment message */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		hfcp->error = strdup("could not write FECEncodeSegment message");
		return -1;
	}
	
	/* Send SegmentHeader */
	if (send(hfcp->socket, segment->header_str, strlen(segment->header_str), 0) == -1) {
		hfcp->error = strdup("could not write initial SegmentHeader .essage");
		return -1;
	}

	/* Open file we are about to send */
	if (!(file = fopen(key_filename, "rb"))) {
		hfcp->error = strdup("could not open file for reading");
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
			hfcp->error = strdup("could not write bytes to socket");
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
			hfcp->error = strdup("could not write zero-padded data to socket");
			return -1;
		}
		
		/* decrement i by number of bytes written to the socket */
		fi -= byte_count;
	}
	
	/* if the response isn't BlocksEncoded, we have a problem */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_BLOCKSENCODED) {
		hfcp->error = strdup("did not receive expected BlocksEncoded message");
		return -1;
	}
	
	/* it is a BlocksEncoded message.. get the check blocks */
	block_len = hfcp->response.blocksencoded.block_size;
	
	for (bi=0; bi < segment->cb_count; bi++) {
		
		/* We're expecting a DataChunk message */
		if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			hfcp->error = strdup("did not receive expected DataChunk message");
			return -1;
		}
		
		segment->check_blocks[bi] = _fcpCreateHBlock();
		segment->check_blocks[bi]->filename = _fcpTmpFilename();
		segment->check_blocks[bi]->size = block_len;
		
		if (!(file = fopen(segment->check_blocks[bi]->filename, "wb"))) {
			snprintf(msg, 512, "could not open file for writing check block %d", bi);
			hfcp->error = strdup(msg);
			return -1;
		}
		fd = fileno(file);
		
		for (fi=0; fi < block_len; ) {
			byte_count = write(fd, hfcp->response.datachunk.data, hfcp->response.datachunk.length);
			
			if (byte_count != hfcp->response.datachunk.length) {
				snprintf(msg, 512, "error writing check block %d", bi);
				hfcp->error = strdup(msg);
				return -1;
			}
			
			fi += byte_count;
			
			/* only get the next DataChunk message if we're expecting one */
			if (fi < block_len)
				if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
					hfcp->error = strdup("did not receive expected DataChunk message (2)");
					return -1;
				}
		}
		
		if (fi != block_len) {
			hfcp->error = strdup("bytes received for check block did not match with expected length");
			return -1;
		}

		/* Close the check block file */
		fclose(file);
	}

	_fcpLog(FCP_LOG_DEBUG, "successfully received %d check blocks", bi);

	return 0;
}


static int fec_insert_segment(hFCP *hfcp, char *key_filename, int index)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char msg[513];
	int rc;
	int node_rc;

	int bi;   /* block index */

	int bytes;
	int byte_count;

	hSegment  *segment;

	FILE *kfile;
	hFCP *tmp_hfcp;

	int kfd;

	_fcpLog(FCP_LOG_VERBOSE, "Entered fec_insert_segment()");
	
	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;

	/* open key file- NEED TO HANDLE RESTARTS PROPERLY HERE! */
	if (!(kfile = fopen(key_filename, "rb"))) {
		hfcp->error = strdup("Could not open key data from file");
		return -1;
	}			
	kfd = fileno(kfile);
	
	while (bi < segment->db_count) { /* while (bi < segment->db_count) */
		
		/* seek to the location relative to the segment (if needed) */
		if (segment->offset > 0) lseek(kfd, segment->offset, SEEK_SET);
		
		tmp_hfcp = _fcpCreateHFCP();
		tmp_hfcp->key = _fcpCreateHKey();
		tmp_hfcp->key->size = segment->block_size;
		
		_fcpParseURI(tmp_hfcp->key->uri, "CHK@");
		
		_fcpLog(FCP_LOG_DEBUG, "inserting data block %d", bi);
		
		/* connect to Freenet FCP */
		if (_fcpSockConnect(tmp_hfcp) != 0)
			return -1;
		
		/* create a put message */
		
		rc = snprintf(buf, L_FILE_BLOCKSIZE,
									"ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
									tmp_hfcp->key->uri->uri_str,
									tmp_hfcp->htl,
									tmp_hfcp->key->size
									);

		_fcpLog(FCP_LOG_DEBUG, "sending message..\n%s", buf);
		
		/* Send ClientPut command */
		if (send(tmp_hfcp->socket, buf, strlen(buf), 0) == -1) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not send ClientPut message");
			return -1;
		}

		/* now insert "block_size" bytes from current file pointer */
		/* on return, file pointer points to first byte to be written on
			 subsequent pass */
		
		/* read from the parameter file pointer for key data; write to the
			 open socket */
		bytes = segment->block_size;
		
		_fcpLog(FCP_LOG_DEBUG, "bytes to write: %d", bytes);
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source */
			if ((byte_count = read(kfd, buf, byte_count)) <= 0) break;
			
			/* write to socket */
			if ((rc = send(tmp_hfcp->socket, buf, byte_count, 0)) < 0) {
				hfcp->error = strdup("could not write key data to socket");
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
			
			/* write to socket */
			if ((rc = send(tmp_hfcp->socket, buf, byte_count, 0)) < 0) {
				hfcp->error = strdup("could not write pad data to socket");
				return -1;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}
		
		/* expecting a success response */
		node_rc = _fcpRecvResponse(tmp_hfcp);
		
		switch (node_rc) {
		case FCPRESP_TYPE_SUCCESS:
			segment->data_blocks[bi] = _fcpCreateHBlock();
			_fcpParseURI(segment->data_blocks[bi]->uri, tmp_hfcp->response.success.uri);
			
			break;
			
		case FCPRESP_TYPE_KEYCOLLISION:
			segment->data_blocks[bi] = _fcpCreateHBlock();
			_fcpParseURI(segment->data_blocks[bi]->uri, tmp_hfcp->response.keycollision.uri);
			
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			/* clean up for loop re-entry, captain over, over */
			_fcpSockDisconnect(tmp_hfcp);
			_fcpDestroyHFCP(tmp_hfcp);
			
			fclose(kfile);
			break;
			
		case FCPRESP_TYPE_ROUTENOTFOUND:
			hfcp->error = strdup("route not found");
			break;
			
		case FCPRESP_TYPE_FORMATERROR:
			snprintf(msg, 512, "node returned format error; \"%s\"", tmp_hfcp->response.formaterror.reason);
			hfcp->error = strdup(msg);
			
			break;
			
		case FCPRESP_TYPE_FAILED:
			snprintf(msg, 512, "node returned failed; \"%s\"", tmp_hfcp->response.failed.reason);
			hfcp->error = strdup(msg);
			
			break;
			
		default:
			hfcp->error = strdup("unknown response code from node");
			break;
		}
		
		/* now handle everything smoothly, if perhaps overkill.. */

		/* re-do the current segment is node sends us Restarted */
		if (node_rc == FCPRESP_TYPE_RESTARTED) continue;
		
		/* if there's an error that causes us to halt.. */
		if ((node_rc != FCPRESP_TYPE_SUCCESS) && (node_rc != FCPRESP_TYPE_KEYCOLLISION)) break;
		
		/* here, node_rc is either success or collision, both which are ok */
		bi++;
	}

	/* finished with connection */
	_fcpSockDisconnect(tmp_hfcp);
	_fcpDestroyHFCP(tmp_hfcp);

	/* we're done with the key data */
	fclose(kfile);

	/* hfcp->error should already be set with the great news */
	if ((node_rc != FCPRESP_TYPE_SUCCESS) && (node_rc != FCPRESP_TYPE_KEYCOLLISION)) {
		return -1;
	}

	/* print status info about the last block.. */
	_fcpLog(FCP_LOG_VERBOSE, "Inserted data block %d: %s",
					bi, segment->data_blocks[segment->db_count - 1]->uri->uri_str);
	
	/******************************************************************/
	/* insert check blocks next */

	for (bi=0; bi < segment->cb_count; bi++) {
		tmp_hfcp = _fcpCreateHFCP();
		
		_fcpLog(FCP_LOG_DEBUG, "inserting check block %d", bi);
		rc = put_file(tmp_hfcp, segment->check_blocks[bi]->filename, 0);

		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not insert check block %d into Freenet", bi);

			/* hfcp->error already set via call to put_file() */
			return -1;
		}		

		segment->check_blocks[bi] = _fcpCreateHBlock();
		_fcpParseURI(segment->check_blocks[bi]->uri, tmp_hfcp->key->uri->uri_str);
	
		_fcpLog(FCP_LOG_DEBUG, "successfully inserted check block %d", bi);
		_fcpLog(FCP_LOG_VERBOSE, "Inserted check block %d: %s",
						bi, tmp_hfcp->key->uri->uri_str);

		_fcpDestroyHFCP(tmp_hfcp);
	}

	return 0;
}

static int fec_make_metadata(hFCP *hfcp, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	char buf_s[L_FILE_BLOCKSIZE+1];

	char msg[513];

	FILE *mfile;
	char *mfilename;
	int   mfd;	

	int rc;
	int bi;
	int meta_len;

	int segment_count;
	int index;

	int bytes;
	int byte_count;

	hSegment *segment;

	_fcpLog(FCP_LOG_VERBOSE, "Entered fec_make_metadata()");

	/* heh.. */
	meta_filename = meta_filename;

	segment_count = hfcp->key->segment_count;
	index = 0;

	while (index < segment_count) {
		/* build SegmentHeader and BlocMap pairs */

		/* helper pointer */
		segment = hfcp->key->segments[index];

		/* copy the segment header; storing it there turned out to be a good idea :) */
		strcpy(buf, segment->header_str);
		strcat(buf, "BlockMap\n");
	
		/* concatenate data block map */
		for (bi=0; bi < segment->db_count; bi++) {
			sprintf(buf_s, "Block.%x=%s\n", bi, segment->data_blocks[bi]->uri->uri_str);
			strcat(buf, buf_s);
		}

		/* now for check block map */
		for (bi=0; bi < segment->cb_count; bi++) {
			sprintf(buf_s, "Check.%x=%s\n", bi, segment->check_blocks[bi]->uri->uri_str);
			strcat(buf, buf_s);
		}

		strcat(buf, "EndMessage\n");

		/* done with this segment.. */
		index++;
	}

	/* send it to the node */

  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0) {
		hfcp->error = strdup("could not make socket connection to node");
    return -1;
	}
	
	/* Send FECMakeMetadata command */
	sprintf(buf_s, "FECMakeMetadata\nDescription=file\nMimeType=%s\nDataLength=%x\nData\n",
					hfcp->key->mimetype,
					strlen(buf));

	if (send(hfcp->socket, buf_s, strlen(buf_s), 0) == -1) {
		hfcp->error = strdup("Could not send FECMakeMetadata message");
		return -1;
	}

	/* now send the segment headers and blockmap information */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		hfcp->error = strdup("Could not send metadata information");
		return -1;
	}
	
	/* expecting a mademetadata response */
	rc = _fcpRecvResponse(hfcp);
	
	switch (rc) {
	case FCPRESP_TYPE_MADEMETADATA:
		meta_len = hfcp->response.mademetadata.datalength;
		break;

	case FCPRESP_TYPE_FAILED:
		snprintf(msg, 512, "node returned failed; \"%s\"", hfcp->response.failed.reason);
		hfcp->error = strdup(msg);

		_fcpSockDisconnect(hfcp);
		return -1;

	default:
		hfcp->error = strdup("unknown response code from node");
		_fcpSockDisconnect(hfcp);

		return -1;
	}

	/* now read metadata from freenet and write to tmp file */
	mfilename = _fcpTmpFilename();
	mfile = fopen(mfilename, "wb");
	mfd = fileno(mfile);
	
	/* barf all the data into the file */
	while ((rc = _fcpRecvResponse(hfcp)) == FCPRESP_TYPE_DATACHUNK) {
		write(mfd, hfcp->response.datachunk.data, hfcp->response.datachunk.length);
	}
	fclose(mfile);

	/* now re-open the file and re-barf it back into freenet as actual
		 key metadata */

	mfile = fopen(mfilename, "rb");
	mfd = fileno(mfile);

	/* connect to Freenet FCP */
	if (_fcpSockConnect(hfcp) != 0)	return -1;

	/* create the put message */
	rc = snprintf(buf, L_FILE_BLOCKSIZE,
								"ClientPut\nURI=CHK@\n" \
								"HopsToLive=%x\n" \
								"DataLength=%x\n" \
								"MetadataLength=%x\n" \
								"Data\n",
								
								hfcp->htl,
								meta_len,
								meta_len
								);

	_fcpLog(FCP_LOG_DEBUG, "\n%s\n", buf);
	
	bytes = meta_len;
	if (bytes) {

		/* send initial ClientPut message */
		send(hfcp->socket, buf, strlen(buf), 0);
		
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			if ((rc = read(mfd, buf, byte_count)) <= 0) {
				hfcp->error = strdup("could not read metadata from file");
				
				_fcpSockDisconnect(hfcp);
				return -1;
			}
			
			if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
				hfcp->error = strdup("could not write metadata to socket");
				
				_fcpSockDisconnect(hfcp);
				return -1;
			}
			
			/* decrement number of bytes written */
			bytes -= byte_count;
			
		} /* finished writing metadata (if any) */

		/* close file we were reading from */
		fclose(mfile);
	}
	
	/* expecting a success response */
	rc = _fcpRecvResponse(hfcp);

	switch (rc) {
	case FCPRESP_TYPE_SUCCESS:
		_fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);
		break;
		
	case FCPRESP_TYPE_KEYCOLLISION:
		_fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);
		break;
		
	case FCPRESP_TYPE_FORMATERROR:
		_fcpLog(FCP_LOG_DEBUG, "keycollision on insert of redirect metadata");
		break;

	default:
		_fcpLog(FCP_LOG_DEBUG, "weird error");
	}

	_fcpSockDisconnect(hfcp);

	/* on any error, return here before setting the uri */
	if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
		return -1;

	return 0;
}

