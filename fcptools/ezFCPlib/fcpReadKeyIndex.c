
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
// Function:    fcpReadKeyIndex()
//
// Arguments:   hfcp
//
// Description:
//
//

int fcpReadKeyIndex(HFCP *hfcp, char **pdata, int keynum)
{
    char keyname[128];
    int retval;

    if (keynum < 0)
        keynum = hfcp->keyindex.next_keynum;

    if (hfcp->keyindex.basedate[0])
        sprintf(keyname, "freenet:KSK@%s-%s-%d",
                hfcp->keyindex.name, hfcp->keyindex.basedate, keynum);
    else
        sprintf(keyname, "freenet:KSK@%s-%d",
                hfcp->keyindex.name, keynum);

    retval = fcpGetKeyToMem(hfcp, keyname, pdata, NULL);
    if (retval > 0)
        return hfcp->keyindex.next_keynum++;
    else
        return -1;

}       // 'fcpReadKeyIndex()'

/* force cvs update */
