
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


extern char _fcpHost[];
extern int  _fcpPort;
extern int  _fcpHtl;
extern int  _fcpRawMode;
extern int  _fcpRegress;

//
// Function:    fcpInitHandle
//
// Arguments:   none
//
// Returns:     pointer to created handle, if successful
//              NULL if failed
//

void fcpInitHandle(HFCP *hfcp)
{
    hfcp->malloced = 0;

    hfcp->created_uri[0] = '\0';
    hfcp->privkey[0] = '\0';
    hfcp->pubkey[0] = '\0';
    hfcp->failReason[0] = '\0';

    hfcp->meta = NULL;
    hfcp->fields = NULL;

    hfcp->htl = _fcpHtl;
    hfcp->raw = _fcpRawMode;
    hfcp->openmode = 0;
    hfcp->regress = _fcpRegress;
    hfcp->keyindex.basedate[0] = '\0';
    hfcp->fields = NULL;
    hfcp->node[0] = '\0';
    hfcp->protocol[0] = '\0';

    // initialise write status block
    hfcp->wr_info.fd_data = -1;
    hfcp->wr_info.fd_meta = -1;
    hfcp->wr_info.num_data_wr = 0;
    hfcp->wr_info.num_meta_wr = 0;
    hfcp->wr_info.uri = NULL;

}       // 'fcpInitHandle()'

