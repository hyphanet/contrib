
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


//static int    getrespchar(HFCP *hfcp);
static int  getrespHello(HFCP *hfcpconn);
static int  getrespSuccess(HFCP *hfcpconn);
static int  getrespFailed(HFCP *hfcp);
static int  getrespDatafound(HFCP *hfcpconn);
static int  getrespDataNotFound(HFCP *hfcp);
static int  getrespDatachunk(HFCP *hfcp);
static int  getrespFormaterror(HFCP *hfcp);
static int  getrespUrierror(HFCP *hfcpconn);
static int  getrespKeycollision(HFCP *hfcp);
static int  getrespRouteNotFound(HFCP *hfcp);
static int  getrespblock(HFCP *hfcp, char *respblock, int bytesreqd);
static int  getrespline(HFCP *hfcp, unsigned char *buf);
static int  htoi(char *s);


//
// Function:    _fcpRecvResponse
//
// Arguments:   fcpconn
//
//

int _fcpRecvResponse(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // Initialise the return fields
    hfcp->created_uri[0] = '\0';
    hfcp->privkey[0] = '\0';
    hfcp->pubkey[0] = '\0';

    //hfcp->conn.response.body.keypair.pubkey = NULL;
    //hfcp->conn.response.body.keypair.privkey = NULL;
    //hfcp->conn.response.body.keypair.uristr = NULL;
    hfcp->pubkey[0] = '\0';
    hfcp->privkey[0] = '\0';
    hfcp->created_uri[0] = '\0';

    // get first response line - loop until we don't get Restarted
    while (1)
    {
        if (getrespline(hfcp, respline) != 0)
            return -2;
        else if ((strcmp(respline, "Restarted") != 0)
                && (strcmp(respline, "Pending") != 0))
            // not a 'Restarted' and not an error - handle it below
            break;
        else
        {
            // read lines till we fail or get an 'EndMessage'
            int status;

            while ((status = getrespline(hfcp, respline)) == 0)
            {
                if (status != 0)
                {
                    _fcpLog(FCP_LOG_CRITICAL, "failed to read response");
                    return -2;
                }
                else if (!strcmp(respline, "EndMessage"))
                    break;
            }
        }
    }

    // categorise the response
    if (!strcmp(respline, "NodeHello"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_HELLO;
        return getrespHello(hfcp);
    }
    else if (!strcmp(respline, "Success"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_SUCCESS;
        return getrespSuccess(hfcp);
    }
    else if (!strcmp(respline, "DataFound"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_DATAFOUND;
        return getrespDatafound(hfcp);
    }
    else if (!strcmp(respline, "DataChunk"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_DATACHUNK;
        return getrespDatachunk(hfcp);
    }
    else if (!strcmp(respline, "FormatError"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_FORMATERROR;
        return getrespFormaterror(hfcp);
    }
    else if (!strcmp(respline, "URIError"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_URIERROR;
        return getrespUrierror(hfcp);
    }
    else if (!strcmp(respline, "DataNotFound"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_DATANOTFOUND;
        return getrespDataNotFound(hfcp);
    }
    else if (!strcmp(respline, "RouteNotFound"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_ROUTENOTFOUND;
        return getrespRouteNotFound(hfcp);
    }
    else if (!strcmp(respline, "KeyCollision"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_KEYCOLLISION;
        return getrespKeycollision(hfcp);
    }
    else if (!strcmp(respline, "Failed"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_FAILED;
        return getrespFailed(hfcp);
    }
    else if (!strcmp(respline, "SizeError"))
    {
        hfcp->conn.response.type = FCPRESP_TYPE_SIZEERROR;
        return FCPRESP_TYPE_SIZEERROR;
    }

    else
    {
        _fcpLog(FCP_LOG_CRITICAL, "_fcpRecvResponse: illegal reply header from node: '%s'", respline);
        return -3;      // got unknown response
    }

    return 0;

}       // '_fcpRecvResponse()'



//
// Function:    getrespHello()
//
// Arguments    hfcpconn - Freenet FCP handle
//
// Returns      FCPRESP_TYPE_HELLO if successful, -1 otherwise
//
// Description: reads in and processes details of NodeHello response
//

static int getrespHello(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "Protocol=", 9) == 0)
        {
            unsigned int max = sizeof(hfcp->protocol) - 1;

            if (strlen(respline+9) > max)
            {
                strncpy(hfcp->protocol, respline+9, max);
                hfcp->protocol[max] = '\0';
            }
            else
                strcpy(hfcp->protocol, respline+9);
        }

        else if (strncmp(respline, "Node=", 5) == 0)
        {
            unsigned int max = sizeof(hfcp->node) - 1;

            if (strlen(respline+5) > max)
            {
                strncpy(hfcp->node, respline+5, max);
                hfcp->node[max] = '\0';
            }
            else
                strcpy(hfcp->node, respline+5);
        }

        else if (strcmp(respline, "EndMessage") == 0)
            return FCPRESP_TYPE_HELLO;
    }

    // failed somewhere
    return -1;

}       // 'fcprespHello()'



//
// Function:    getrespSuccess()
//
// Arguments    hfcpconn - Freenet FCP handle
//
// Returns      FCPRESP_TYPE_SUCCESS if successful, -1 otherwise
//
// Description: reads in and processes details of NodeHello response
//

static int getrespSuccess(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "URI=", 4) == 0)
        {
            //hfcp->conn.response.body.keypair.uristr = strdup(respline + 4);
            strcpy(hfcp->created_uri, respline + 4);
        }

        else if (strncmp(respline, "PublicKey=", 10) == 0)
        {
            //hfcp->conn.response.body.keypair.pubkey = strdup(respline + 10);
            strcpy(hfcp->pubkey, respline + 10);
        }

        else if (strncmp(respline, "PrivateKey=", 11) == 0)
        {
            //hfcp->conn.response.body.keypair.privkey = strdup(respline + 11);
            strcpy(hfcp->privkey, respline + 11);
        }

        else if (strcmp(respline, "EndMessage") == 0)
            return FCPRESP_TYPE_SUCCESS;
    }

    // failed somewhere
    return -1;

}       // 'fcprespSuccess()'



//
// Function:    getrespFailed()
//
// Arguments    hfcpconn - Freenet FCP handle
//
// Returns      FCPRESP_TYPE_SUCCESS if successful, -1 otherwise
//
// Description: reads in and processes details of Failed response
//

static int getrespFailed(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "Reason=", 4) == 0)
        {
            //hfcp->conn.response.body.failed.reason = strdup(respline + 8);
            strcpy(hfcp->failReason, respline + 8);
        }

        if (strncmp(respline, "URI=", 4) == 0)
        {
            //hfcp->conn.response.body.keypair.uristr = strdup(respline + 4);
            strcpy(hfcp->created_uri, respline + 4);
        }

        else if (strcmp(respline, "EndMessage") == 0)
            return FCPRESP_TYPE_FAILED;
    }

    // failed somewhere
    return -1;

}       // 'fcprespFailed()'



//
// Function:    getrespDatafound()
//
// Arguments    fcpconn - connection block
//
// Returns      FCPRESP_TYPE_SUCCESS if successful, -1 otherwise
//
// Description: reads in and processes details of DataFound response
//

static int getrespDatafound(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // set zero metadata and reset data chunk pointers
    hfcp->conn.response.body.datafound.metaLength = 0;
    hfcp->conn.response.body.datachunk.dataptr = NULL;
    hfcp->conn.response.body.datachunk.length = 0;
    if (hfcp->conn.response.body.datachunk.data != NULL)
        hfcp->conn.response.body.datachunk.data = NULL;

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "DataLength=", 11) == 0)
            hfcp->conn.response.body.datafound.dataLength = htoi(respline + 11);

        else if (strncmp(respline, "MetadataLength=", 15) == 0)
            hfcp->conn.response.body.datafound.metaLength = htoi(respline + 15);

        else if (strcmp(respline, "EndMessage") == 0)
        {
            // make dataLength reflect size of data, NOT COUNTING METADATA
            hfcp->conn.response.body.datafound.dataLength -= hfcp->conn.response.body.datafound.metaLength;
            hfcp->keysize = hfcp->conn.response.body.datafound.dataLength;
            hfcp->bytesread = 0;
            return FCPRESP_TYPE_DATAFOUND;
        }
    }

    // failed somewhere
    return -1;

}       // 'fcprespDatafound()'




//
// Function:    getrespDatachunk()
//
// Arguments    fcpconn - connection block
//
// Returns      FCPRESP_TYPE_SUCCESS if successful, -1 otherwise
//
// Description: reads in and processes details of DataFound response
//

static int getrespDatachunk(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "Length=", 7) == 0)
            hfcp->conn.response.body.datachunk.length = htoi(respline + 7);

        else if (strncmp(respline, "Data", 4) == 0)
        {
            int numbytes;
            char *temp;

            // allocate buf for incoming data
            temp = safeMalloc(hfcp->conn.response.body.datachunk.length);
            hfcp->conn.response.body.datachunk.data = temp;

            // get n bytes of data
            numbytes = getrespblock(hfcp,
                                    hfcp->conn.response.body.datachunk.data,
                                    hfcp->conn.response.body.datachunk.length);
            hfcp->conn.response.body.datachunk.length = numbytes;
            hfcp->conn.response.body.datachunk.dataptr = hfcp->conn.response.body.datachunk.data;
            hfcp->conn.response.body.datachunk.dataend
                = hfcp->conn.response.body.datachunk.data + hfcp->conn.response.body.datachunk.length;
            return FCPRESP_TYPE_DATACHUNK;
        }
    }

    // failed somewhere
    return -1;

}       // 'fcprespDatachunk()'


//
// Function:    getrespFormaterror()
//
// Arguments:   fcpconn - connection block
//
// Returns:     0 if successful, -1 otherwise
//
// Description: Gets the details of a format error
//

static int getrespFormaterror(HFCP *hfcp)
{
    return FCPRESP_TYPE_FORMATERROR;

}       // 'getrespFormaterror()'


//
// Function:    getrespRouteNotFound()
//
// Arguments:   fcpconn - connection block
//
// Returns:     0 if successful, -1 otherwise
//
// Description: Gets the details of a RouteNotFound
//

static int getrespRouteNotFound(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (!strcmp(respline, "EndMessage"))
            return FCPRESP_TYPE_ROUTENOTFOUND;
        else
            _fcpLog(FCP_LOG_NORMAL, "RouteNotFound: %s", respline);
    }

    // failed somewhere
    return -1;

}       // 'getrespFormaterror()'

//
// Function:    getrespDataNotFound()
//
// Arguments:   fcpconn - connection block
//
// Returns:     0 if successful, -1 otherwise
//
// Description: Gets the details of a RouteNotFound
//

static int getrespDataNotFound(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (!strcmp(respline, "EndMessage"))
            return FCPRESP_TYPE_DATANOTFOUND;
        else
            _fcpLog(FCP_LOG_NORMAL, "DataNotFound: %s", respline);
    }

    // failed somewhere
    return -1;

}       // 'getrespDataNotFound()'



//
// Function:    getrespUrierror()
//
// Arguments:   fcpconn - connection block
//
// Returns:     number of bytes read if successful, -1 otherwise
//
// Description: Reads an arbitrary number of bytes from connection
//

static int getrespUrierror(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (!strcmp(respline, "EndMessage"))
            return FCPRESP_TYPE_ROUTENOTFOUND;
        else
            _fcpLog(FCP_LOG_NORMAL, "URIerror: %s", respline);
    }

    // failed somewhere
    return FCPRESP_TYPE_URIERROR;
}


//
// Function:    getrespKeycollision()
//
// Arguments:   fcpconn - connection block
//
// Returns:     number of bytes read if successful, -1 otherwise
//
// Description: Reads an arbitrary number of bytes from connection
//

static int getrespKeycollision(HFCP *hfcp)
{
    char respline[RECV_BUFSIZE];

    // get protocol field
    while (getrespline(hfcp, respline) == 0)
    {
        if (strncmp(respline, "URI=", 4) == 0)
        {
            //hfcp->conn.response.body.keypair.uristr = strdup(respline + 4);
            strcpy(hfcp->created_uri, respline + 4);
        }

        else if (strncmp(respline, "PublicKey=", 10) == 0)
        {
            //hfcp->conn.response.body.keypair.pubkey = strdup(respline + 10);
            strcpy(hfcp->pubkey, respline + 10);
        }

        else if (strncmp(respline, "PrivateKey=", 11) == 0)
        {
            //hfcp->conn.response.body.keypair.privkey = strdup(respline + 11);
            strcpy(hfcp->privkey, respline + 11);
        }

        else if (strcmp(respline, "EndMessage") == 0)
            return FCPRESP_TYPE_KEYCOLLISION;
    }

    // failed somewhere
    return -1;
}





//
// Function:    getrespblock()
//
// Arguments:   fcpconn - connection block
//
// Returns:     number of bytes read if successful, -1 otherwise
//
// Description: Reads an arbitrary number of bytes from connection
//

static int getrespblock(HFCP *hfcp, char *respblock, int bytesreqd)
{
    int     charsfound = 0;
    char    *ptr = respblock;

    while (bytesreqd > 0)
    {
        // now, try to get and return desired number of bytes
        charsfound = _fcpSockReceive(hfcp, ptr, bytesreqd);
        if (charsfound > 0)
        {
            ptr += charsfound;
            bytesreqd -= charsfound;
        }
        else if (charsfound == 0)
            break;          // connection closed - got all we're gonna get
        else
            return -1;      // connection failure
    }

    return ptr - respblock; // got all we want

}       // 'getrespblock()'



//
// Function:    getrespline()
//
// Arguments:   fcpconn - connection block
//              buf - pointer to a buffer into which to receive line
//
// Returns:     safeMalloc'ed string, or NULL if none found
//
// Description: Reads a single line of text from response buffer

static int getrespline(HFCP *hfcp, unsigned char *buf)
{
    unsigned char *cp = buf;

    // get chars one by one till newline or till we run out
    while (_fcpSockReceive(hfcp, cp, 1) > 0)
    {
        if (*cp == '\n')
        {
            *cp = '\0';
            _fcpLog(FCP_LOG_DEBUG, "From node: %s", buf);
            return 0;
        }
        else
		{
			cp++;
            if ((cp - buf) >= (RECV_BUFSIZE - 1))
			{
				_fcpLog(FCP_LOG_CRITICAL, "*** PANIC - BUFFER OVERFLOW IN NODE RESPONSE LINE");
	            _fcpLog(FCP_LOG_DEBUG, "From node: %s", buf);
				*cp = '\0';
				return 0;
			}
		}
    }

    // incomplete line
    *cp = '\0';
    return -1;

}       // 'getrespline()'


//
//  Function:       htoi
//
//  Argument:       pointer to string
//
//  Returns:        int value of string
//
//  Description:    converts a string to its hex numerical value

static int htoi(char *s)
{
    int val = 0;

    while (*s)
    {
        if (isdigit(*s))
            val = val * 16 + (*s - '0');
        else if (*s >= 'a' && *s <= 'f')
            val = val * 16 + (*s - 'a' + 10);
        else if (*s >= 'A' && *s <= 'F')
            val = val * 16 + (*s - 'A' + 10);
        else
            return val;

        s++;
    }

    return val;

}       // 'htoi()'

/* force cvs update */
