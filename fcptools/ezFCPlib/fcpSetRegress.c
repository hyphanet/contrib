
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

/* force cvs update */
