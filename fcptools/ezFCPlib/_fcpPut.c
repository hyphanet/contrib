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


extern int  _fcpSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);
extern char *crTmpFilename(void);

/* exported functions for fcptools codebase */

int put_file(hFCP *hfcp, char *key_filename, char *meta_filename);
int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);


/* Private functions for internal use */
static int send_clientput(hFCP *hfcp);

static int fec_segment_file(hFCP *hfcp, char *key_filename, char *meta_filename);
static int fec_encode_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_insert_segment(hFCP *hfcp, char *key_filename, int segment);
static int fec_make_metadata(hFCP *hfcp);

/* Log messages should be FCP_LOG_VERBOSE or FCP_LOG_DEBUG only in this module */


int put_file(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int kfd;
	int mfd;

	int bytes;
	int byte_count;

	FILE *kfile;
	FILE *mfile;

	/* let's loop this until we stop receiving Restarted messages */

	do {
		/* send the first part of the ClientPut message */
		rc = send_clientput(hfcp);
		
		/* Open metadata file */
		if (meta_filename) {
			if (!(mfile = fopen(meta_filename, "rb"))) {
				_fcpLog(FCP_LOG_DEBUG, "could not open metadata file for reading");
				return -1;
			}
			mfd = fileno(mfile);
			
			/* send metadata */
			while ((rc = read(mfd, buf, L_FILE_BLOCKSIZE)) > 0) {
				if (send(hfcp->socket, buf, rc, 0) < 0) {
					_fcpLog(FCP_LOG_DEBUG, "could not write metadata to socket");
					return -1;
				}
			}
			
			_fcpLog(FCP_LOG_VERBOSE, "Wrote metadata to socket");
			fclose(mfile);
		}
		
		/* now write key data */
		if (!(kfile = fopen(key_filename, "rb")))
			return -1;
		
		kfd = fileno(kfile);
		bytes = hfcp->key->size;
		
		while (bytes) {
			byte_count = (bytes > L_FILE_BLOCKSIZE ? L_FILE_BLOCKSIZE: bytes);
			
			/* read from source */
			if ((rc = read(kfd, buf, byte_count)) <= 0) {
				_fcpLog(FCP_LOG_DEBUG, "could not read key data from file \"%s\"", key_filename);
				return -1;
			}
			
			/* write to socket */
			if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
				_fcpLog(FCP_LOG_DEBUG, "could not write key data to socket");
				return -1;
			}
			
			/* decrement by number of bytes written to the socket */
			bytes -= byte_count;
		}
		
		/* expecting a success response */
		rc = _fcpRecvResponse(hfcp);
		
		switch (rc) {
		case FCPRESP_TYPE_SUCCESS:
			_fcpLog(FCP_LOG_DEBUG, "node returned Success message");
			_fcpParseURI(hfcp->key->uri, hfcp->response.success.uri);
			
			break;
			
		case FCPRESP_TYPE_KEYCOLLISION:
			_fcpLog(FCP_LOG_DEBUG, "node returned KeyCollision message");
			_fcpParseURI(hfcp->key->uri, hfcp->response.keycollision.uri);
			
			break;
			
		case FCPRESP_TYPE_RESTARTED:
			_fcpLog(FCP_LOG_DEBUG, "node returned Restarted message");

			fclose(mfile);
			fclose(kfile);			

			crSockDisconnect(hfcp);
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

  } while (rc == FCPRESP_TYPE_RESTARTED);

  /* finished with connection */
  crSockDisconnect(hfcp);
  
  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
    return -1;

	_fcpLog(FCP_LOG_DEBUG, "inserted key: %s", hfcp->key->uri->uri_str);

	return 0;
}


int put_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	int index;

	if (fec_segment_file(hfcp, key_filename, meta_filename) != 0)	return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_encode_segment(hfcp, key_filename, index) != 0) return -1;
	
	for (index = 0; index < hfcp->key->segment_count; index++)
		if (fec_insert_segment(hfcp, key_filename, index) != 0)	return -1;

	/* now that the data is inserted, generate and insert metadata for the
		 splitfile */
	if (fec_make_metadata(hfcp) != 0) return -1;

	return 0;
}

/**********************************************************************/

static int send_clientput(hFCP *hfcp)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0)
    return -1;
	
  /* create a put message */

  if (hfcp->key->metadata) {
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

	/* Send ClientPut command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send ClientPut message");
		return -1;
	}

	return 0;
}


static int fec_segment_file(hFCP *hfcp, char *key_filename, char *meta_filename)
{
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;

	int index;
	int segment_count;

  /* connect to Freenet FCP */
  if (_fcpSockConnect(hfcp) != 0) return -1;

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

  switch (rc) {
  case FCPRESP_TYPE_SEGMENTHEADER:
    _fcpLog(FCP_LOG_DEBUG, "node returned SegmentHeader message");
		break;
		
  case FCPRESP_TYPE_FORMATERROR:
    _fcpLog(FCP_LOG_DEBUG, "node returned FormatError message");
    crSockDisconnect(hfcp);
		return -1;

  case FCPRESP_TYPE_FAILED:
    _fcpLog(FCP_LOG_DEBUG, "node returned Failed message: %s", hfcp->error);
    _fcpLog(FCP_LOG_DEBUG, "reason - %s", hfcp->error);

    crSockDisconnect(hfcp);
		return -1;

	default:
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

		_fcpLog(FCP_LOG_DEBUG, "got segment index %d", index);
	
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
	if (_fcpSockConnect(hfcp) != 0) return -1;
	
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
	char buf[L_FILE_BLOCKSIZE+1];
	int rc;
	int node_rc;

	int bi;   /* block index */

	int bytes;
	int byte_count;

	hSegment  *segment;

	FILE *kfile;
	hFCP *tmp_hfcp;

	int kfd;
	
	/* helper pointer */
	segment = hfcp->key->segments[index];
	
	/* start at the first block, of course */
	bi = 0;
	
	/* loop until we stop receiving Restarted messages */
	
	do { /* outer loop for re-transmitting in response to Restarted messages */
		
		/* open key file */
		if (!(kfile = fopen(key_filename, "rb"))) {
			_fcpLog(FCP_LOG_VERBOSE, "Could not open key data in file \"%s\"", key_filename);
			return -1;
		}			
		kfd = fileno(kfile);
		
		/* seek to the location relative to the segment (if needed) */
		if (segment->offset > 0)
			lseek(kfd, segment->offset, SEEK_SET);
		
		while (bi < segment->db_count) { /* insert all data blocks */

			tmp_hfcp = _fcpCreateHFCP();
			tmp_hfcp->key = _fcpCreateHKey();
			tmp_hfcp->key->size = segment->block_size;
			
			_fcpParseURI(tmp_hfcp->key->uri, "CHK@");
			
			/* now insert "block_size" bytes from current file pointer */
			/* on return, file pointer points to first byte to be written on
				 subsequent pass */
			
			_fcpLog(FCP_LOG_DEBUG, "inserting data block %d", bi);
			
			rc = send_clientput(tmp_hfcp);
			
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
					_fcpLog(FCP_LOG_DEBUG, "could not write key data to socket");
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
					_fcpLog(FCP_LOG_DEBUG, "could not write pad data to socket");
					return -1;
				}
				
				/* decrement by number of bytes written to the socket */
				bytes -= byte_count;
			}
			
			/* expecting a success response */
			node_rc = _fcpRecvResponse(tmp_hfcp);
			
			switch (node_rc) {
			case FCPRESP_TYPE_SUCCESS:
				_fcpLog(FCP_LOG_DEBUG, "node returned Success message");
				
				segment->data_blocks[bi] = _fcpCreateHBlock();
				_fcpParseURI(segment->data_blocks[bi]->uri, tmp_hfcp->response.success.uri);
				
				break;
				
			case FCPRESP_TYPE_KEYCOLLISION:
				_fcpLog(FCP_LOG_DEBUG, "node returned KeyCollision message");
				
				segment->data_blocks[bi] = _fcpCreateHBlock();
				_fcpParseURI(segment->data_blocks[bi]->uri, tmp_hfcp->response.keycollision.uri);
				
				break;
				
			case FCPRESP_TYPE_RESTARTED:
				_fcpLog(FCP_LOG_DEBUG, "node returned Restarted message");
				
				crSockDisconnect(tmp_hfcp);
				_fcpDestroyHFCP(tmp_hfcp);
				
				fclose(kfile);
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
				_fcpLog(FCP_LOG_DEBUG, "received unknown response code: %d", node_rc);
				
				crSockDisconnect(tmp_hfcp);
				_fcpDestroyHFCP(tmp_hfcp);

				fclose(kfile);
				return -1;
			}
			
			/* finished with connection */
			crSockDisconnect(tmp_hfcp);
			
			if ((node_rc != FCPRESP_TYPE_SUCCESS) && (node_rc != FCPRESP_TYPE_KEYCOLLISION))
				return -1;
			
			_fcpLog(FCP_LOG_VERBOSE, "Inserted data block %d: %s",
							bi, segment->data_blocks[bi]->uri->uri_str);
			_fcpDestroyHFCP(tmp_hfcp);
			
			/* before restarting the loop, adjust bi if we were successful */
			if (node_rc != FCPRESP_TYPE_RESTARTED) bi++;
		}
	} while (node_rc == FCPRESP_TYPE_RESTARTED);
	
	/* we're done with the key data */
	fclose(kfile);
	
	/* insert check blocks second */
	for (bi=0; bi < segment->cb_count; bi++) {
		tmp_hfcp = _fcpCreateHFCP();
		
		tmp_hfcp->key = _fcpCreateHKey();
		tmp_hfcp->key->size = segment->checkblock_size;

		_fcpParseURI(tmp_hfcp->key->uri, "CHK@");

		_fcpLog(FCP_LOG_DEBUG, "inserting check block %d", bi);
		rc = put_file(tmp_hfcp, segment->check_blocks[bi]->filename, 0);

		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "could not insert check block %d into Freenet", bi);
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

static int fec_make_metadata(hFCP *hfcp)
{
	char buf_s[L_FILE_BLOCKSIZE+1];
	char buf[32768]; /* large enough? */

	int rc;
	int fi;
	int bi;
	int meta_len;

	int byte_count;
	int segment_count;
	int index;

	hSegment *segment;

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
  if (_fcpSockConnect(hfcp) != 0)
    return -1;
	
	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1)
		 return -1;

	/* Send FECMakeMetadata command */
	sprintf(buf_s, "FECMakeMetadata\nDescription=file\nMimeType=text/plain\nDataLength=%x\nData\n",
					strlen(buf));

	if (send(hfcp->socket, buf_s, strlen(buf_s), 0) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send FECMakeMetadata message");
		return -1;
	}

	/* now send the segment headers and blockmap information */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_VERBOSE, "Could not send metadata information");
		return -1;
	}
	
	/* expecting a mademetadata response */
	rc = _fcpRecvResponse(hfcp);
	
	switch (rc) {
	case FCPRESP_TYPE_MADEMETADATA:
		_fcpLog(FCP_LOG_DEBUG, "node returned MadeMetadata message");

		meta_len = hfcp->response.mademetadata.datalength;
		break;

	case FCPRESP_TYPE_FAILED:
		_fcpLog(FCP_LOG_DEBUG, "revceived Failed message; %s", hfcp->error);

		crSockDisconnect(hfcp);
		return -1;

	default:
		_fcpLog(FCP_LOG_DEBUG, "received unknown response code: %d", rc);
		
		crSockDisconnect(hfcp);
		return -1;
	}

	for (fi=0; fi < meta_len; ) {

		/* We're expecting a DataChunk message */
		if ((rc = _fcpRecvResponse(hfcp)) != FCPRESP_TYPE_DATACHUNK) {
			_fcpLog(FCP_LOG_DEBUG, "did not receive expected DataChunk message");
			return -1;
		}

		snprintf(buf, hfcp->response.datachunk.length, "%s", hfcp->response.datachunk.data);
		_fcpLog(FCP_LOG_DEBUG, "%s", buf);

		fi += hfcp->response.datachunk.length;
	}

	return 0;
}

