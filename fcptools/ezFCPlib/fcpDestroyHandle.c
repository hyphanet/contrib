
//
//  This code is part of FreeWeb - an FCP-based client for Freenet
//
//  Designed and implemented by David McNab, david@rebirthing.co.nz
//  CopyLeft (c) 2001 by David McNab
//
//  The FreeWeb website is at http://freeweb.sourceforge.net
//  The website for Freenet is at http://freenet.sourceforge.net
//
//  This code is distributed under the GNU Public Licence (GPL) version 2.
//  See http://www.gnu.org/ for further details of the GPL.
//


#include "ezFCPlib.h"


extern META04  *metaParse(char *buf);
extern void    metaFree(META04 *meta);


//
// Function:    fcpDestroyHandle()
//
// Arguments:   fcpconn
//
// Description: destroys a previously created FCP handle, and frees all the safeMalloc'ed
//              memory blocks it was using
//

void fcpDestroyHandle(HFCP *hfcp)
{
  if (hfcp) {
	 if (hfcp->meta)
		metaFree(hfcp->meta);
	 
	 if (hfcp->wr_info.uri != NULL)
		free(hfcp->wr_info.uri);

	 if (hfcp->malloced)
		free(hfcp);
  }
}       // 'fcpDestroyHandle()'
