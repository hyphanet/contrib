
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

#ifndef WINDOWS
#include "unistd.h"
#endif

#include "stdlib.h"

#include "ezFCPlib.h"


//
// Function:    fcpWriteKey()
//
// Arguments:   hfcp
//              buf     pointer to buf containing data to write
//              len     number of bytes to write
//
// Returns:     number of bytes written, if successful
//              -1 if failed
//
// Description: writes a block of data to the temporary file created by
//              the prior call to fcpOpenKey()
//              Data gets sent to node via FCP on the call to fcpCloseKey()
//
//

int fcpWriteKey(HFCP *hfcp, char *buf, int len)
{
    int count;

    if (hfcp->wr_info.fd_data <= 0)
        return -1;                  // temp file isn't open

    count = write(hfcp->wr_info.fd_data, buf, len);
    if (count < 0)
        return count;

    hfcp->wr_info.num_data_wr += count;
    return count;

}       // 'fcpWriteKey()'


//
// Function:    fcpWriteKeyMeta()
//
// Arguments:   hfcp    standard HFCP handle
//              buf     pointer to buf containing metadata to write
//              len     number of bytes to write
//
// Description: writes a block of metadata to the temporary file created by
//              the prior call to fcpOpenKey()
//              Data gets sent to node via FCP on the call to fcpCloseKey()
//

int fcpWriteKeyMeta(HFCP *hfcp, char *buf, int len)
{
    int count;

    if (hfcp->wr_info.fd_meta <= 0)
        return -1;                  // temp file isn't open

    count = write(hfcp->wr_info.fd_meta, buf, len);
    if (count < 0)
        return count;

    hfcp->wr_info.num_meta_wr += count;
    return count;

}       // 'fcpWriteKeyMeta()'

/* force cvs update */
