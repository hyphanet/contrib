/*
  This code is part of FCPtools - a multiplatform SDK for Freenet apps in C

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"


/*
  safeMalloc()

  a malloc() wrapper which waits until the requested amount of memory
  becomes available
*/
void *safeMalloc(int nbytes)
{
  void *blk;
  unsigned int delay_s = 1;
  
  while ((blk = malloc(nbytes)) == NULL) {
	 _fcpLog(FCP_LOG_CRITICAL, "safeMalloc: req for %d bytes failed, waiting %d seconds", nbytes, delay_s);

	 // Sleep for delay_s seconds, 0 nanoseconds.
	 _fcpSleep(delay_s, 0);

	 // Double the delay for next time, max 1 hour (hahaha)
	 if (delay_s < 3600) delay_s = delay_s << 1;
  }
  
  return blk;
}
