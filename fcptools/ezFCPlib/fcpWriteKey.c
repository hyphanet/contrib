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


extern int    snprintf(char *str, size_t size, const char *format, ...);
extern int    crSockConnect(hFCP *hfcp);
extern void   crSockDisconnect(hFCP *hfcp);
extern char  *crTmpFilename(void);


static int _fcpInsertChunk(hChunk *chunk);


int fcpWriteKey(hFCP *hfcp, char *buf, int len)
{
	int chunk_avail;
	int count;
	int rc;

	hChunk *chunk;

	/* the first available chunk in the array */
	chunk = hfcp->key->chunks[hfcp->key->chunkCount - 1];

	chunk_avail = CHUNK_BLOCK_SIZE - chunk->size;
	count = (len > chunk_avail ? chunk_avail : len);

	/* While there's still data to write from caller.. */
	while (len) {

		rc = fwrite(buf, count, 1, chunk->file);
		if (rc == 0) {
			_fcpLog(FCP_LOG_DEBUG, "Error writing %d byte chunk to %s", count, chunk->filename);
			return -1;
		}

		/* Info was written.. update indexes */
		chunk->size += count;
		chunk_avail -= count;
		len -= count;
		buf += count;

		_fcpLog(FCP_LOG_DEBUG, "Wrote %d bytes to chunk %d", count, hfcp->key->chunkCount);

		if (chunk_avail == 0) {

			/* Close the file that should be exactly 256K in size.. */
			_fcpLog(FCP_LOG_DEBUG, "Closing chunk %d in file %s", hfcp->key->chunkCount, chunk->filename);

			fclose(chunk->file);
			chunk->file = 0;

			/* Go off an insert chunk; block till function returns */
			if ((rc = _fcpInsertChunk(chunk))) {
				_fcpLog(FCP_LOG_DEBUG, "error in inserting chunk %d from file %s", hfcp->key->chunkCount, chunk->filename);
				return -1;
			}

			hfcp->key->chunkCount++;

			hfcp->key->chunks = realloc(hfcp->key->chunks, sizeof(hChunk *) * hfcp->key->chunkCount);
			hfcp->key->chunks[hfcp->key->chunkCount - 1] = _fcpCreateHChunk();

			chunk = hfcp->key->chunks[hfcp->key->chunkCount - 1];

			chunk->filename = crTmpFilename();
			if (!(chunk->file = fopen(chunk->filename, "w")))
				return -1;

			chunk_avail = CHUNK_BLOCK_SIZE;
			count = (len > chunk_avail ? chunk_avail : len);
		}
	}
	
	return 0;
}


static int _fcpInsertChunk(hChunk *chunk)
{



	return 0;
}

