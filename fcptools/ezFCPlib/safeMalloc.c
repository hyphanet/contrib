
//
//  This code is part of FCPtools - a multiplatform SDK for Freenet apps in C
//
//  Designed and implemented by David McNab, david@rebirthing.co.nz
//  CopyLeft (c) 2001 by David McNab
//
//  The website for Freenet is at http://freenet.sourceforge.net
//
//  This code is distributed under the GNU Public Licence (GPL) version 2.
//  See http://www.gnu.org/ for further details of the GPL.
//

#include "stdio.h"
#include "stdlib.h"

#include "ezFCPlib.h"


/*
  safeMalloc()

  a malloc() wrapper which waits until the requested amount of memory
  becomes available
*/
void *safeMalloc(int nbytes)
{
	void *blk;
	unsigned long int delay = 500;

	while ((blk = malloc(nbytes)) == NULL)
	{
		_fcpLog(FCP_LOG_CRITICAL, "safeMalloc: req for %d bytes failed, waiting %d ms",
				nbytes, delay);
		sleep(delay * 1000);

		// increase the delay for next time, max 1 hour (hahaha)
		if (delay < 3600000)
			delay = delay * 2;
	}

	return blk;
}
