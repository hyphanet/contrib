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
// Function:    fcpGetKeyToMem()
//
// Arguments:   fcpconn
//
// Description: destroys a previously created FCP handle, and frees all the safeMalloc'ed
//              memory blocks it was using
//

int fcpGetKeyToMem(HFCP *hfcp, char *key, char **pData, char **pMetadata)
{
    int size_expected;
    int size_received = 0;
    char *data_recvd;

    // try to get the key open
    if (fcpOpenKey(hfcp, key, (_FCP_O_READ | (hfcp->raw ? _FCP_O_RAW : 0))) != 0)
        return -1;

    // snarf key's metadata, if needed
	if (pMetadata)			// (thanks Jason Smith for bug fix here)
	    *pMetadata = NULL;

    // do we also want key's data?
    if (pData != NULL)
    {
        // how big is this key?
        size_expected = hfcp->conn.response.body.datafound.dataLength;
        if (size_expected > 0)
        {
            // try to allocate a sufficiently large chunk of memory
            data_recvd = safeMalloc(size_expected + 1);

            if (data_recvd != NULL)
            {
                // safeMalloc successful - now suck it into memory
                size_received = fcpReadKey(hfcp, data_recvd, size_expected);
                data_recvd[size_received] = '\0';
                *pData = data_recvd;
            }
        }
        else
            // no data in key
            size_received = 0;
    }
    else
    {
        char buf[4096];
        int keysize = hfcp->conn.response.body.datafound.dataLength;

        // suck in all the data and throw it away as we go
        while (keysize > 0)
        {
            size_received = fcpReadKey(hfcp,
                           buf,
                           (keysize > 4096 ? 4096 : keysize));
            if (size_received <= 0)
                break;
            else
                keysize -= size_received;
        }
    }

    // all done
    fcpCloseKey(hfcp);
    return size_received;

}       // 'fcpGetKeyToMem()'

/* force cvs update */
