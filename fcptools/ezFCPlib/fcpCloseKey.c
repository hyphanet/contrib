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

#include "ezFCPlib.h"

#include <sys/types.h>
#include <sys/stat.h>

#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>


extern int fcpPut(hFCP *hfcp);


static int fcpCloseKeyRead(hFCP *hfcp);
static int fcpCloseKeyWrite(hFCP *hfcp);

static int test(hFCP *hfcp);


int fcpCloseKey(hFCP *hfcp)
{
  if (hfcp->key->openmode & _FCP_O_READ)
	 return fcpCloseKeyRead(hfcp);

  else if (hfcp->key->openmode & _FCP_O_WRITE)
	 return fcpCloseKeyWrite(hfcp);

  else
	 return -1;
}


static int fcpCloseKeyRead(hFCP *hfcp)
{
  return 0;
}


static int fcpCloseKeyWrite(hFCP *hfcp)
{
	int index;

	index = hfcp->key->chunkCount - 1;
	_fcpLog(FCP_LOG_DEBUG, "Closing chunk %d in file %s", index+1, hfcp->key->chunks[index]->filename);

	if (hfcp->key->chunks[index]->size) {

		/* Close the last chunk in the array */
		fclose(hfcp->key->chunks[index]->file);	
		hfcp->key->chunks[index]->file = 0;
	}
	else {

		/* If the last chunk has zero bytes, then nuke it */
		fclose(hfcp->key->chunks[index]->file);	
		remove(hfcp->key->chunks[index]->filename);

		/* ok, well now *really* nuke it */
		_fcpDestroyHChunk(hfcp->key->chunks[index]);

		/* Decrement the chunk count; leave it allocated since it will only be
			 realloc'ed later */
		hfcp->key->chunkCount--;
	}

	/* Here, we're ready to insert! */

	return fcpPut(hfcp);
}

