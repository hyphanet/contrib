
//
//  This code is part of ezFCPlib - an FCP-based C client library for Freenet
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

#include <string.h>

#include "ezFCPlib.h"


extern char    _fcpHost[256];

void fcpSetHost(char *newHost)
{
    strcpy(_fcpHost, newHost);
}



//
// Function:    fcpSetHtl
//
// Arguments:   hfcp    pointer to FCP handle, created with fcpCreateHandle()
//				htl		hops to live value
//
// Description: Sets the default HTL for all future freenet operations on given handle
//

void fcpSetHtl(HFCP *hfcp, int htl)
{
    hfcp->htl = htl;

}       // 'fcpDestroyHandle()'


//
// Function:    fcpSetRegress
//
// Arguments:   hfcp    pointer to FCP handle, created with fcpCreateHandle()
//
// Description: Sets the number of days to regress in retrying failed date redirects
//

void fcpSetRegress(HFCP *hfcp, int regress)
{
    hfcp->regress = regress;

}       // 'fcpDestroyHandle()'


