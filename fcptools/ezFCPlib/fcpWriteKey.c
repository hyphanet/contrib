/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
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
#include <sys/stat.h>
#include <sys/socket.h>

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ezFCPlib.h"

extern int   snprintf(char *str, size_t size, const char *format, ...);
extern int   crSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);

static int send_header(hfcp);


int fcpWriteKey(hFCP *hfcp, char *buf, int len)
{
	int index;
	int size;
	hChunk *chunk;

	int rc;

	/* the first available chunk in the array */
	index = hfcp->key->chunkCount - 1;

	chunk = hfcp->key->chunks[index];
	size = CHUNK_BLOCK_SIZE - chunk->file_size;
	rc = write(chunk->fd, buf, size);

	/* Problem if at least these two params don't equate */
	if (rc != size) return -1;

	/* If these 2 equate, then return w/ no errors immed */
	if (size == len) {
		chunk->file_size += size;
		chunk->file_offset += size;

		return size;
	}

	/* If we get here, then this chunk is full, start another one after firing
		 off an insertion thread for the current chunk */

	/* rc = _fcpInsertChunk(hfcp); */

	/* Now create a new chunk and start writing to that one.. */
	index++;
	hfcp->key->chunks = realloc(hfcp->key->chunks, sizeof(hChunk *) * (index + 1));
	hfcp->key->chunks[index] = _fcpCreateHChunk();

	chunk = hfcp->key->chunks[index];

	/* Initialize the new chunk */
	chunk->filename = crTmpFilename();
	chunk->fd = open(chunk->filename, O_CREAT);
	
  
	rc = send(hfcp->socket, buf, len, 0);
	if (rc > 0) {
		hfcp->key->offset += rc;
		return rc;
	}

  return 0;
}


static int send_header(hFCP *hfcp)
{
	char buf[4097];
	int len;
	int rc;

	/* uri_str should already be set to CHK@ via _fcpParseURI() */
	snprintf(buf,
					 4096,
					 "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
					 hfcp->key->uri->uri_str,
					 hfcp->htl,
					 hfcp->key->size
					 );

	if (crSockConnect(hfcp)) return -1;
  if (send(hfcp->socket, _fcpID, 4, 0) != 4) return -1;

	len = strlen(buf);
	rc = send(hfcp->socket, buf, len, 0);

  if (rc < len) {
    crSockDisconnect(hfcp);
		return -1;
	}
}

