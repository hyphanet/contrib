/*
  This code is part of FreeWeb - an FCP-based client for Freenet
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net
  
  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"

/*
  Function:    fcpReadKey()
  
  Arguments:   hfcp
  
  Description:
*/

int fcpReadKey(HFCP *hfcp, char *buf, int len)
{
  int n;
  int bytesLeft = hfcp->keysize - hfcp->bytesread;
  
  /* have we sucked the key dry? */
  if (bytesLeft == 0)
    return 0;
  
  /* if less data left in key than requested, truncate len */
  if (len > bytesLeft)
    len = bytesLeft;
  
  n = _fcpReadBlk(hfcp, buf, len);
  hfcp->bytesread += n;
  return n;
}
