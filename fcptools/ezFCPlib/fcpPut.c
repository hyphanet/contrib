/*
  This code is part of FCPTools - an FCP-based client library for Freenet

	Developers:
 	- David McNab <david@rebirthing.co.nz>
	- Jay Oliveri <ilnero@gmx.net>
	
  CopyLeft (c) 2001 by David McNab

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

#ifndef WINDOWS
#include <sys/socket.h>
#include <unistd.h>
#endif

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "ezFCPlib.h"


extern int   crSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);


static int   put_file(hFCP *hfcp);
static int   put_splitfile(hFCP *hfcp);
static int   put_fec_splitfile(hFCP *hfcp);


/*
	Returns 0 on success.
*/
int fcpPut(hFCP *hfcp)
{
	/* If multiple chunks are defined, it's a standard Splitfile insert. */
	if (hfcp->key->chunk_count > 1)
		return put_splitfile(hfcp);

	/* If one chunk is defined, and it's *big*, do an FEC encoded insert. */
	if (hfcp->key->size > CHUNK_BLOCK_SIZE)
		return put_fec_splitfile(hfcp);

	/* If it's small, do a quickie */
	return put_file(hfcp);
}	


/* ************************************************************** */
/* The rest of the functions in this file are effectively private */
/* ************************************************************** */


static int put_file(hFCP *hfcp)
{
	char buf[8193];
	int fd;
	int rc;
	
	hChunk *chunk;
	
  /* connect to Freenet FCP */
  if (crSockConnect(hfcp) != 0)
    return -1;
	
  /* create a put message */
  if (hfcp->key->metadata != NULL) {
		/* Code for inserting with metadata */

  }
  else {
    snprintf(buf, FCPRESP_BUFSIZE,
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
		_fcpLog(FCP_LOG_DEBUG, "Could not send ClientPut command");
		return -1;
	}

	/* Open file we are about to send */
	chunk = hfcp->key->chunks[0];
	if (!(chunk->file = fopen(chunk->filename, "rb"))) {
		_fcpLog(FCP_LOG_DEBUG, "Could not open chunk for reading in order to insert into Freenet");
		return -1;
	}

  /* Now send data */
	fd = fileno(chunk->file);
	while ((rc = read(fd, buf, 8192)) > 0) {

    if (send(hfcp->socket, buf, rc, 0) < 0) {
      _fcpLog(FCP_LOG_DEBUG, "Could not write key data to Freenet");
      return -1;
		}
	}

  fclose(chunk->file);
	chunk->file = 0;
  
  /* expecting a success response */
  rc = _fcpRecvResponse(hfcp);
  
  switch (rc) {
  case FCPRESP_TYPE_SUCCESS:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: SUCCESS: %s", chunk->filename);
    break;

  case FCPRESP_TYPE_KEYCOLLISION:
    /* either of these are ok */
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: KEYCOLLISION: %s", chunk->filename);
    break;

  case FCPRESP_TYPE_ROUTENOTFOUND:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: ROUTENOTFOUND: %s", chunk->filename);
    break;

  case FCPRESP_TYPE_FORMATERROR:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: FORMATERROR: %s", chunk->filename);
    break;

  case FCPRESP_TYPE_FAILED:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: FAILED: %s", chunk->filename);
    break;

  default:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: unknown response from node for %s", chunk->filename);
    crSockDisconnect(hfcp);
    return -1;
  }
  
  /* finished with connection */
  crSockDisconnect(hfcp);
  
  if ((rc != FCPRESP_TYPE_SUCCESS) && (rc != FCPRESP_TYPE_KEYCOLLISION))
    return -1;

	_fcpLog(FCP_LOG_NORMAL, "Inserted key: %s", hfcp->response.success.uri);

	/* Before returning, set all the hfcp-> fields proper, so that the response
		 struct can be re-used on next call to _fcpRecvResponse(). */
	return 0;
}


static int put_splitfile(hFCP *hfcp)
{
	return 0;
}


static int put_fec_splitfile(hFCP *hfcp)
{
	/* Some slightly redundant declarations for readability sake
		 (which in C is a sometimes a ridiculous goal */

	char buf[8193];
	int fd;
	int rc;
	int i, j, k, l;  /* remember FORTRAN? */

	int byte_count;
	int segment_count;

	int data_len;
	int metadata_len;
	int pad_len;
	
	hChunk *chunk;

  /* connect to Freenet FCP */
  if (crSockConnect(hfcp) != 0) return -1;
	
  if (hfcp->key->metadata != NULL) {
		/* Code for inserting with metadata */

  }
  else {
    snprintf(buf, 8192,
						 "FECSegmentFile\nAlgoName=OnionFEC_a_1_2\nFileLength=%x\nEndMessage\n",
						 hfcp->key->size
						 );
  }

	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1) return -1;
		 
	/* Send FECSegmentFile command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "Could not send FECSegmentFile command");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sent FECSegmentFile message");

	rc = _fcpRecvResponse(hfcp);
	if (rc != FCPRESP_TYPE_SEGMENTHEADER) {
		_fcpLog(FCP_LOG_DEBUG, "Did not receive expected SegmentHeader response");
		return -1;
	}

	/* Allocate the area for all required segments */
	hfcp->key->segment_count = hfcp->response.segmentheader.segments;
	hfcp->key->segments = (hSegment **)malloc(sizeof (hSegment *) * hfcp->key->segment_count);

	/* Loop while there's more segments to receive */
	segment_count = hfcp->key->segment_count;
	i = 0;

	_fcpLog(FCP_LOG_DEBUG, "retrieving %d segments..", segment_count);

	/* Loop through all segments and store information */
	while (i < segment_count) {
		hfcp->key->segments[i] = (hSegment *)malloc(sizeof (hSegment));

		/* get counts of data and check blocks */
		hfcp->key->segments[i]->db_count = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[i]->cb_count = hfcp->response.segmentheader.checkblock_count;

		/* allocate space for data and check block handles */
		hfcp->key->segments[i]->data_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[i]->db_count);
		hfcp->key->segments[i]->check_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[i]->cb_count);

		snprintf(buf, 8192,
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

		hfcp->key->segments[i]->header_str = (char *)malloc(strlen(buf) + 1);
		strcpy(hfcp->key->segments[i]->header_str, buf);
		
		hfcp->key->segments[i]->filelength       = hfcp->response.segmentheader.filelength;
		hfcp->key->segments[i]->offset           = hfcp->response.segmentheader.offset;
		hfcp->key->segments[i]->block_count      = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[i]->block_size       = hfcp->response.segmentheader.block_size;
		hfcp->key->segments[i]->checkblock_count = hfcp->response.segmentheader.checkblock_count;
		hfcp->key->segments[i]->checkblock_size  = hfcp->response.segmentheader.checkblock_size;
		hfcp->key->segments[i]->segments         = hfcp->response.segmentheader.segments;
		hfcp->key->segments[i]->segment_num      = hfcp->response.segmentheader.segment_num;
		hfcp->key->segments[i]->blocks_required  = hfcp->response.segmentheader.blocks_required;
		
		_fcpLog(FCP_LOG_DEBUG, "got segment %d", i+1);

		i++;

		/* Only if we're expecting more SegmentHeader messages
			 should we attempt to retrieve one ! */
		if (i < segment_count) rc = _fcpRecvResponse(hfcp);

	} /* End While - all segments now in hfcp container */

	/* Disconnect this connection.. its outlived it's purpose */
	crSockDisconnect(hfcp);

	/***********************************************************************/
	/* Begin phase 2: Liquid Goo Phase (only handles the first segment) */
	/***********************************************************************/

	_fcpLog(FCP_LOG_DEBUG, "preparing %d segments to encode", segment_count);

	data_len = hfcp->key->segments[0]->filelength;
	metadata_len = strlen(hfcp->key->segments[0]->header_str);
	pad_len = (hfcp->key->segments[0]->block_count * hfcp->key->segments[0]->block_size) - data_len;

	_fcpLog(FCP_LOG_DEBUG, "status; data_len=%d, metadata_len=%d, pad_len=%d, blocksize*count=%d",
					data_len,
					metadata_len,
					pad_len,
					hfcp->key->segments[0]->block_count * hfcp->key->segments[0]->block_size
					);

	/* new connection to Freenet FCP */
	if (crSockConnect(hfcp) != 0) return -1;
	
	/* Send fcpID */
	if (send(hfcp->socket, _fcpID, 4, 0) == -1) return -1;

	snprintf(buf, 8192,
					 "FECEncodeSegment\nDataLength=%x\nMetadataLength=%x\nData\n",
					 data_len + metadata_len + pad_len,
					 metadata_len
					 );

	/* Send FECEncodeSegment message */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not write FECEncodeSegment message");

		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "sending %s..", buf);
	
	/* Send SegmentHeader */
	if (send(hfcp->socket, hfcp->key->segments[0]->header_str, strlen(hfcp->key->segments[0]->header_str), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not write initial SegmentHeader message");

		return -1;
	}
	
	_fcpLog(FCP_LOG_DEBUG, "sent initial SegmentHeader");
	
	/* Open file we are about to send */
	chunk = hfcp->key->chunks[0];
	if (!(chunk->file = fopen(chunk->filename, "rb"))) {

		_fcpLog(FCP_LOG_DEBUG, "Could not open chunk for reading in order to insert into Freenet");
		return -1;
	}
	fd = fileno(chunk->file);
	
	/* Write the data from the file, then write the pad blocks */
	/* data_len is the total length of the data file we're inserting */
	
	i = data_len;
	while (i) {
		int bytes;
		
		/* How many bytes are we writing this pass? */
		byte_count = (i > 8192 ? 8192: i);

		/* read byte_count bytes from the file we're inserting */
		bytes = read(fd, buf, byte_count);

		if ((rc = send(hfcp->socket, buf, bytes, 0)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "Could not write key data to Freenet: %s", strerror(errno));
			return -1;
		}
		_fcpLog(FCP_LOG_DEBUG, "wrote %d bytes this pass; %d/%d bytes left to write", bytes, i, data_len);

		/* decrement i by number of bytes written to the socket */
		i -= byte_count;
	}

	/* now write the pad bytes and end transmission.. */

	/* set the buffer to all zeroes so we can send 'em */
	memset(buf, 0, 8192);

	i = pad_len;
	while (i) {
		/* how many bytes are we writing this pass? */
		byte_count = (i > 8192 ? 8192: i);

		if ((rc = send(hfcp->socket, buf, byte_count, 0)) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "Could not write zero-padded data to Freenet: %s", strerror(errno));
			return -1;
		}
		_fcpLog(FCP_LOG_DEBUG, "wrote %d zero bytes this pass; %d/%d bytes left to write", byte_count, i, pad_len);

		/* decrement i by number of bytes written to the socket */
		i -= byte_count;
	}

  rc = _fcpRecvResponse(hfcp);
  
  switch (rc) {
  case FCPRESP_TYPE_BLOCKSENCODED:
    _fcpLog(FCP_LOG_DEBUG, "fcpPut: BLOCKSENCODED");
    break;
	}

	crSockDisconnect(hfcp);
	return 0;
}

