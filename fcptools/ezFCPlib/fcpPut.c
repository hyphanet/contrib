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


#include <sys/types.h>
#include <sys/stat.h>

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "ezFCPlib.h"


extern int   crSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);
extern char *crTmpFilename(void);

/* exported functions for fcptools codebase */

int put_file(hFCP *hfcp, FILE *key_fp, int bytes, char *meta_filename);
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);


/* Private functions for internal use */
static int fec_segment_file(hFCP *hfcp, char *key_filename, char *meta_filename);
static int fec_encode_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_insert_segment(hFCP *hfcp, char *key_filename, int segment);

/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */


/*
	put_file()
*/
int put_file(hFCP *hfcp, FILE *key_fp, int bytes, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int kfd;
	int mfd;

	int rc;
	int byte_count;
	
	FILE *mfile;

  /* connect to Freenet FCP */
  if (crSockConnect(hfcp) != 0)
    return -1;
	
  /* create a put message */

  if (meta_filename) {
    snprintf(buf, L_FILE_BLOCKSIZE,
						 "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nMetadataLength=%x\nData\n",
						 hfcp->key->uri->uri_str,
						 hfcp->htl,
						 hfcp->key->size,
						 hfcp->key->metadata->size
						 );
	}
	else {
    snprintf(buf, L_FILE_BLOCKSIZE,
						 "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
						 hfcp->key->uri->uri_str,
						 hfcp->htl,
						 hfcp->key->size
						 );
  }

	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1)
		 return -1;

	_fcpLog(FCP_LOG_VERBOSE, "sent FCP id");

	/* Send ClientPut command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "could not send ClientPut message");
		return -1;
	}

	_fcpLog(FCP_LOG_VERBOSE, "sent ClientPut message");

	/* Open metadata file */
	if (meta_filename)
		if (!(mfile = fopen(meta_filename, "rb"))) {
			_fcpLog(FCP_LOG_DEBUG, "could not open metadata file for reading");
			return -1;
		}
	
	kfd = fileno(key_fp);

	if (meta_filename) mfd = fileno(mfile);

  /* send metadata */
	if (meta_filename) {
		while ((rc = read(mfd, buf, L_FILE_BLOCKSIZE)) > 0) {
			
			if (send(hfcp->socket, buf, rc, 0) < 0) {
				_fcpLog(FCP_LOG_DEBUG, "could not write metadata to socket");
				return -1;
			}
		}

		_fcpLog(FCP_LOG_VERBOSE, "wrote metadata to socket");
		fclose(mfile);
	}

	/* read from the parameter file pointer for key data; write to the
		 open socket */
	while (bytes) {
		byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);

		/* read from source */
		if ((rc = read(kfd, buf, L_FILE_BLOCKSIZE)) <= 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not read from pre-opened file pointer");
			return -1;
		}

		/* write to socket */
		if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not write key FILE * data to socket");
			return -1;
		}
		
		/* decrement by number of bytes written to the socket */
		bytes -= byte_count;
	}

	if (bytes != 0) {
		_fcpLog(FCP_LOG_VERBOSE, "did not write *all* data to socket!");
	}
	else
		_fcpLog(FCP_LOG_VERBOSE, "wrote key data to socket");

  /* expecting a success response */
  rc = _fcpRecvResponse(hfcp);
  
  switch (rc) {
  case FCPRESP_TYPE_SUCCESS:
    _fcpLog(FCP_LOG_DEBUG, "node returned Success message");

		if (hfcp->key->uri) _fcpDestroyHURI(hfcp->key->uri);

		hfcp->key->uri = _fcpCreateHURI();
		_fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);
		
		break;
		
  case FCPRESP_TYPE_KEYCOLLISION:
    _fcpLog(FCP_LOG_DEBUG, "node returned KeyCollision message");

		if (hfcp->key->uri) _fcpDestroyHURI(hfcp->key->uri);
		
		hfcp->key->uri = _fcpCreateHURI();
		_fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);

    break;

  case FCPRESP_TYPE_ROUTENOTFOUND:
    _fcpLog(FCP_LOG_DEBUG, "node returned RouteNotFound message");
    break;

  case FCPRESP_TYPE_FORMATERROR:
    _fcpLog(FCP_LOG_DEBUG, "node returned FormatError message");
    break;

  case FCPRESP_TYPE_FAILED:
    _fcpLog(FCP_LOG_DEBUG, "node returned Failed message");
    break;

  default:
    _fcpLog(FCP_LOG_DEBUG, "received unknown response code: %d", rc);

    crSockDisconnect(hfcp);
    return -1;
  }
  
  /* finished with connection */
  crSockDisconnect(hfcp);
  
  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
    return -1;

	_fcpLog(FCP_LOG_VERBOSE, "inserted key: %s", hfcp->key->uri->uri_str);
	return 0;
}


int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int index;
	int rc;

	rc = fec_segment_file(hfcp, key_filename, meta_filename);
	_fcpLog(FCP_LOG_DEBUG, "fec_segment_file returned %d", rc);

	for (index = 0; index < hfcp->key->segment_count; index++) {
		rc = fec_encode_segment(hfcp, key_filename, index);

		_fcpLog(FCP_LOG_DEBUG, "fec_encode_segment returned %d", rc);
	}
	_fcpLog(FCP_LOG_DEBUG, "fec_encode_segment loop returned");
	
	for (index = 0; index < hfcp->key->segment_count; index++) {
		rc = fec_insert_segment(hfcp, key_filename, index);

		_fcpLog(FCP_LOG_DEBUG, "fec_insert_segment returned %d", rc);
	}	
	_fcpLog(FCP_LOG_DEBUG, "fec_insert_segment loop returned");
	
	
	return 0;
}


/**********************************************************************/

static int fec_segment_file(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int index;
	int segment_count;

  /* connect to Freenet FCP */
  if (crSockConnect(hfcp) != 0) return -1;

	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%x\nEndMessage\n",
					 hfcp->key->size
					 );

	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1) return -1;
		 
	/* Send FECSegmentFile command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not send FECSegmentFile command");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECSegmentFile message");

	rc = _fcpRecvResponse(hfcp);
	if (rc != FCPRESP_TYPE_SEGMENTHEADER) {
		_fcpLog(FCP_LOG_DEBUG, "did not receive expected SegmentHeader response");
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

		_fcpLog(FCP_LOG_DEBUG, "got segment %d (index=%d); total %d", index+1, index, segment_count);
	
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
	crSockDisconnect(hfcp);

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

	/* Helper pointer since we're encoding 1 segment at a time */
	segment = hfcp->key->segments[index];
	
	data_len     = segment->filelength;
	metadata_len = strlen(segment->header_str);
	pad_len      = (segment->block_count * segment->block_size) - data_len;
	
	/* new connection to Freenet FCP */
	if (crSockConnect(hfcp) != 0) return -1;
	
	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1) return -1;
	
	snprintf(buf, L_FILE_BLOCKSIZE,
					 "FECEncodeSegment\nDataLength=%x\nMetadataLength=%x\nData\n",
					 data_len + metadata_len + pad_len,
					 metadata_len
					 );
	
	/* Send FECEncodeSegment message */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not write FECEncodeSegment message");
		return -1;
	}
	
	/* Send SegmentHeader */
	if (send(hfcp->socket, segment->header_str, strlen(segment->header_str), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not write initial SegmentHeader message");
		return -1;
	}
	
	/* Open file we are about to send */
	if (!(file = fopen(key_filename, "rb"))) {
		_fcpLog(FCP_LOG_DEBUG, "could not open file for reading");
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
			return -1;
		}
		
		/* decrement by number of bytes written to the socket */
		fi -= byte_count;
	}
	_fcpLog(FCP_LOG_DEBUG, "wrote %d bytes to socket", data_len);
	
	/* now write the pad bytes and end transmission.. */
	
	/* set the buffer to all zeroes so we can send 'em */
	memset(buf, 0, L_FILE_BLOCKSIZE);
	
	fi = pad_len;
	while (fi) {
		
		/* how many bytes are we writing this pass? */
		byte_count = (fi > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: fi);
		
		if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not write zero-padded data to socket: %s", strerror(errno));
			return -1;
		}
		
		/* decrement i by number of bytes written to the socket */
		fi -= byte_count;
	}
	
	/* if the response isn't BlocksEncoded, we have a problem */
	if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_BLOCKSENCODED) {
		_fcpLog(FCP_LOG_DEBUG, "did not receive expected BlocksEncoded message");
		return -1;
	}
	_fcpLog(FCP_LOG_DEBUG, "wrote %d zero-bytes to socket", pad_len);
	
	/* it is a BlocksEncoded message.. get the check blocks */
	block_len = hfcp->response.blocksencoded.block_size;
	
	for (bi=0; bi < segment->cb_count; bi++) {
		
		/* We're expecting a DataChunk message */
		if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			_fcpLog(FCP_LOG_DEBUG, "did not receive expected DataChunk message");
			return -1;
		}
		
		segment->check_blocks[bi] = _fcpCreateHBlock();
		segment->check_blocks[bi]->filename = crTmpFilename();
		segment->check_blocks[bi]->size = block_len;
		
		if (!(file = fopen(segment->check_blocks[bi]->filename, "wb"))) {
			
			_fcpLog(FCP_LOG_DEBUG, "could not open file \"%s\" for writing check block %d",
							segment->check_blocks[bi]->filename, bi);
			return -1;
		}
		fd = fileno(file);
		
		for (fi=0; fi < block_len; ) {
			byte_count = write(fd, hfcp->response.datachunk.data, hfcp->response.datachunk.length);
			
			if (byte_count != hfcp->response.datachunk.length) {
				_fcpLog(FCP_LOG_DEBUG, "error writing check block %d", bi);
				return -1;
			}
			
			fi += byte_count;
			
			/* only get the next DataChunk message if we're expecting one */
			if (fi < block_len)
				if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
					_fcpLog(FCP_LOG_DEBUG, "did not receive expected DataChunk message (2)");
					return -1;
				}
		}
		
		if (fi != block_len) {
			_fcpLog(FCP_LOG_DEBUG, "bytes received for check block did not match with expected length");
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
	int rc;
	int bi;   /* block index */

	hSegment  *segment;

	FILE *fp;
	hFCP *tmp_hfcp;

	/* helper pointer */
	segment = hfcp->key->segments[index];

	if (!(fp = fopen(key_filename, "rb"))) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not open key data in file \"%s\"", key_filename);
		return -1;
	}			

	/* seek to the location relative to the segment (if needed) */
	if (segment->offset > 0) {
		fseek(fp, segment->offset, SEEK_SET);
	}

	/* insert data blocks first */
	for (bi=0; bi < segment->db_count; bi++) {
		tmp_hfcp = _fcpCreateHFCP();

		tmp_hfcp->key = _fcpCreateHKey();
		tmp_hfcp->key->size = segment->block_size;

		_fcpParseURI(tmp_hfcp->key->uri, "CHK@");

		/* now insert "block_size" bytes from current file pointer */
		/* on return, file pointer points to first byte to be written on
			subsequent pass */
		rc = put_file(tmp_hfcp, fp, segment->block_size, 0);

		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not insert data block %d (0->size-1) into Freenet", bi);
			return -1;
		}		

		_fcpLog(FCP_LOG_DEBUG, "put_file returned %d fo data block %d (0->size-1)", rc, bi);
		_fcpLog(FCP_LOG_VERBOSE, "inserted uri: %s", tmp_hfcp->key->uri->uri_str);

		_fcpDestroyHFCP(tmp_hfcp);
	}

	/* if we're not at EOF, then not all the key data has been written */
	if (!feof(fp)) {
		_fcpLog(FCP_LOG_DEBUG, "did not write all key data to socket");
		return -1;
	}

	/* we're done with the key data */
	fclose(fp);

	/* insert check blocks second */
	for (bi=0; bi < segment->cb_count; bi++) {
		tmp_hfcp = _fcpCreateHFCP();

		tmp_hfcp->key = _fcpCreateHKey();
		tmp_hfcp->key->size = segment->checkblock_size;

		_fcpParseURI(tmp_hfcp->key->uri, "CHK@");

		if (!(fp = fopen(segment->check_blocks[bi]->filename, "rb"))) {
			_fcpLog(FCP_LOG_VERBOSE,
							"Could not open key data in file \"%s\"",
							segment->check_blocks[bi]->filename);
			return -1;
		}			

		rc = put_file(tmp_hfcp, fp, segment->check_blocks[bi]->size, 0);
		fclose(fp);

		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not insert check block %d (0->size-1) into Freenet", bi);
			return -1;
		}		

		_fcpLog(FCP_LOG_DEBUG, "put_file returned %d for check block %d", rc, bi);
		_fcpLog(FCP_LOG_VERBOSE, "inserted uri: %s", tmp_hfcp->key->uri->uri_str);
	}

	return 0;
}

