
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

extern char     _fcpID[];


//
// Function:    fcpPutKeyFromMem()
//
// Arguments:   fcpconn
//
// Description:
//
//

int fcpPutKeyFromMem(HFCP *hfcp, char *name, char *data, char *metadata, int datalen)
{
    int meta_len = 0;
    char buf[2048];
    int count;
    int status;

    // connect to Freenet FCP
    if (_fcpSockConnect(hfcp) != 0)
        return -1;

    // save the key
    if ((hfcp->wr_info.uri = _fcpParseUri(name)) == 0)
    {
        _fcpSockDisconnect(hfcp);
        return -1;
    }

    // create a put message
    if (metadata != NULL)
    {
        meta_len = strlen(metadata);
        sprintf(buf,
                "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nMetadataLength=%x\nData\n",
                name,
                hfcp->htl,
                datalen + meta_len,
                meta_len
                );
    }
    else
    {
        sprintf(buf,
                "ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
                name,
                hfcp->htl,
                datalen
                );
    }

    // send off client put command
    _fcpSockSend(hfcp, _fcpID, 4);
    count = strlen(buf);
    if (_fcpSockSend(hfcp, buf, count) < count)
    {
        // send of put command failed
        _fcpSockDisconnect(hfcp);
        return -1;
    }

    // Send metadata if there's any
    if (metadata)
    {
        if (_fcpSockSend(hfcp, metadata, meta_len) < meta_len)
        {
            _fcpSockDisconnect(hfcp);
            return -1;
        }
    }

    // Now send data
    if (datalen > 0)
        _fcpSockSend(hfcp, data, datalen);

	_fcpLog(FCP_LOG_DEBUG, "%d: fcpPutKeyFromMem: waiting for response", getpid());

    // expecting a success response
    status = _fcpRecvResponse(hfcp);

	_fcpLog(FCP_LOG_DEBUG, "%d: fcpPutKeyFromMem: got response", getpid());

    switch (status)
    {
    case FCPRESP_TYPE_SUCCESS:
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: SUCCESS");
        break;
    case FCPRESP_TYPE_KEYCOLLISION:
        // either of these are ok
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: KEYCOLLISON");
        break;
    case FCPRESP_TYPE_FORMATERROR:
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: FORMATERROR");
        break;
    case FCPRESP_TYPE_URIERROR:
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: URIERROR");
        break;
    case FCPRESP_TYPE_ROUTENOTFOUND:
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: ROUTENOTFOUND");
        break;
    case FCPRESP_TYPE_SIZEERROR:
        _fcpLog(FCP_LOG_NORMAL, "fcpPutKeyFromMem: SIZEERROR");
        break;
    case FCPRESP_TYPE_FAILED:
        _fcpLog(FCP_LOG_CRITICAL, "fcpPutKeyFromMem: FAILED");
        _fcpLog(FCP_LOG_CRITICAL, "Reason = ", hfcp->conn.response.body.failed.reason);
        break;
    }

    // finished with connection
    _fcpSockDisconnect(hfcp);

    // expecting a success response
    if (status != FCPRESP_TYPE_SUCCESS && status != FCPRESP_TYPE_KEYCOLLISION)
        return -1;

    // seems successful
/*** OLD_LEAKY - no longer needed
    if (hfcp->conn.response.body.keypair.pubkey != NULL)
        strcpy(hfcp->pubkey, hfcp->conn.response.body.keypair.pubkey);
    if (hfcp->conn.response.body.keypair.privkey != NULL)
        strcpy(hfcp->privkey, hfcp->conn.response.body.keypair.privkey);
    if (hfcp->conn.response.body.keypair.uristr != NULL)
        strcpy(hfcp->created_uri, hfcp->conn.response.body.keypair.uristr);
***/
    return 0;

}       // 'fcpPutKeyFromMem()'
