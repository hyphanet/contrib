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
	char buf[8193];
	int fd;
	int rc;
	int i;
	int count;
	
	hChunk *chunk;
	
  /* connect to Freenet FCP */
  if (crSockConnect(hfcp) != 0)
    return -1;
	
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
	if (send(hfcp->socket, _fcpID, 4, 0) == -1)
		 return -1;
		 
	/* Send FECSegmentFile command */
	if (send(hfcp->socket, buf, strlen(buf), 0) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "Could not send FECSegmentFile command");
		return -1;
	}

	rc = _fcpRecvResponse(hfcp);
	if (rc != FCPRESP_TYPE_SEGMENTHEADER) {
		_fcpLog(FCP_LOG_DEBUG, "Did not receive expected SegmentHeader response");
		return -1;
	}

	/* Allocate the area for all required segments */
	hfcp->key->segment_count = hfcp->response.segmentheader.segments;
	hfcp->key->segments = (hSegment **)malloc(sizeof (hSegment *) * hfcp->key->segment_count);

	/* Loop while there's more segments to receive */
	count = hfcp->key->segment_count;
	i = 0;

	while (i < count) {
		hfcp->key->segments[i] = (hSegment *)malloc(sizeof (hSegment));

		/* get counts of data and check blocks */
		hfcp->key->segments[i]->db_count = hfcp->response.segmentheader.block_count;
		hfcp->key->segments[i]->cb_count = hfcp->response.segmentheader.checkblock_count;

		/* allocate space for data and check block handles */
		hfcp->key->segments[i]->data_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[rc]->db_count);
		hfcp->key->segments[i]->check_blocks = (hBlock **)malloc(sizeof (hBlock *) * hfcp->key->segments[rc]->cb_count);

		/* Copy the entire SegmentHeader for re-use later */
		/* This is a shallow copy, but not a problem */

		memcpy(&hfcp->key->segments[i]->header, &hfcp->response.segmentheader, sizeof (FCPRESP_SEGMENTHEADER));

		i++;

		/* Only if we're expecting more SegmentHeader messages
			 should we attempt to retrieve one ! */
		if (i < count) rc = _fcpRecvResponse(hfcp);
	}			

	/* Open file we are about to send */
	chunk = hfcp->key->chunks[0];
	if (!(chunk->file = fopen(chunk->filename, "rb"))) {
		_fcpLog(FCP_LOG_DEBUG, "Could not open chunk for reading in order to insert into Freenet");
		return -1;
	}
	fd = fileno(chunk->file);

	/* Perform the writing in 8K blocks */

	i = hfcp->key->size;
	while (i) {
		
		/* How many bytes are we writing this pass? */
		count = (i > 8192 ? 8192: i);
		
		/* Now send data */
		rc = read(fd, buf, count);
			
		if (send(hfcp->socket, buf, count, 0) < 0) {
			_fcpLog(FCP_LOG_DEBUG, "Could not write key data to Freenet");
			return -1;
		}

		/* decrement i by number of bytes written to the socket */
		i -= count;
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

