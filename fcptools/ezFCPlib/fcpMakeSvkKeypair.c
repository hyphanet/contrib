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
// IMPORTED DECLARATIONS
//

extern char     _fcpID[];


//
// Function:    fcpMakeSvkKeypair()
//
// Arguments:   hfcp    FCP handle
//              pubkey  pointer to a buffer into which to write an SSK public key
//              privkey pointer to a buffer into which to write an SSK private key
//
// Returns:     0 if successful, non-zero if failed
//
// Description:
//
//

int fcpMakeSvkKeypair(HFCP *hfcp, char *pubkey, char *privkey)
{
    char *cmd = "GenerateSVKPair\nEndMessage\n";
    ////char cmd_dummy[512];
    char key_dummy[256];
    int  n;
    int  len;
    int oldhtl;

    // temporary fudge - allows us to use old freenet
//  strcpy(pubkey, "uDT8iApCWc33MlNClfiMlIRHZOkQAgE");
//  strcpy(privkey, "GgyIDK5iFet2GOn-mm7~AkRl2JHlfdzxr2e0");
//  return 0;
    // delete these lines when new freenet is installed

    if (_fcpSockConnect(hfcp) != 0)
        return -1;

    len = strlen(cmd);
    _fcpSockSend(hfcp, _fcpID, 4);

    n = _fcpSockSend(hfcp, cmd, len);
    if (n < len)
    {
        _fcpSockDisconnect(hfcp);
        return -1;
    }

    if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_SUCCESS)
    {
        _fcpSockDisconnect(hfcp);
        return -1;
    }
    _fcpSockDisconnect(hfcp);

    // copy the keys
    //strcpy(pubkey, hfcp->conn.response.body.keypair.pubkey);
    //strcpy(privkey, hfcp->conn.response.body.keypair.privkey);
    strcpy(privkey, hfcp->privkey);

    // ugh!! noe we have to insert a 'junk key' under this private key and get back a
    // public key which will work!
    sprintf(key_dummy, "SSK@%s/fcpKludge", privkey);
    oldhtl = hfcp->htl;
    fcpSetHtl(hfcp, 0);
    fcpPutKeyFromMem(hfcp, key_dummy, "duhhhh", NULL, 3);
    fcpSetHtl(hfcp, oldhtl);
    //if (hfcp->conn.response.body.keypair.uristr == NULL)
    if (hfcp->created_uri[0] == '\0')
    {
        _fcpLog(FCP_LOG_CRITICAL, "fcpMakeSvkKeypair - failed");
        return -1;
    }
    //*strchr(hfcp->conn.response.body.keypair.uristr, '/') = '\0';
    //strcpy(pubkey, hfcp->conn.response.body.keypair.uristr + 12); // + 12 to skip 'freenet:SSK@'
    *strchr(hfcp->created_uri, '/') = '\0';
    strcpy(pubkey, hfcp->created_uri + 12); // + 12 to skip 'freenet:SSK@'

    _fcpSockDisconnect(hfcp);
    return 0;

}       // 'fcpMakeSvkKeypair()'
