/*
  This code is part of FCPTools - an FCP-based client library for Freenet

  CopyLeft (c) 2001 by David McNab

  Developers:
  - David McNab <david@rebirthing.co.nz>
  - Jay Oliveri <ilnero@gmx.net>
  
  Currently maintained by Jay Oliveri <ilnero@gmx.net>
  
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

#include "ezFCPlib.h"

#include <stdlib.h>
#include <string.h>

#include "ez_sys.h"

/* suppress a compiler warning */
/*char *strdup(const char *s); DELETE ? */

static int  getrespHello(hFCP *);
static int  getrespInfo(hFCP *);
static int  getrespSuccess(hFCP *);

static int  getrespDataFound(hFCP *);
static int  getrespDataChunk(hFCP *);
static int  getrespDataNotFound(hFCP *);
static int  getrespRouteNotFound(hFCP *);

static int  getrespUriError(hFCP *);
static int  getrespRestarted(hFCP *);
static int  getrespKeyCollision(hFCP *);
static int  getrespPending(hFCP *);

static int  getrespFailed(hFCP *);
static int  getrespFormatError(hFCP *);

/* FEC specific responses */
static int  getrespSegmentHeaders(hFCP *);
static int  getrespBlocksEncoded(hFCP *);
static int  getrespMadeMetadata(hFCP *);


int _fcpRecvResponse(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	while (1) {

		memset(resp, 0, 8193);
		rc = _fcpSockRecvln(hfcp, resp, 8192);

		/* return -1 on error, except if it's a TIMEOUT */
		if (rc <= 0)
			return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);

		else if (rc == 0)
			return 0;
	
		if (!strncmp(resp, "NodeHello", 9)) {
			hfcp->response.type = FCPRESP_TYPE_NODEHELLO;
			return getrespHello(hfcp);
		}
		else if (!strncmp(resp, "NodeInfo", 8)) {
			hfcp->response.type = FCPRESP_TYPE_NODEINFO;
			return getrespInfo(hfcp);
		}
		else if (!strcmp(resp, "Success")) {
			hfcp->response.type = FCPRESP_TYPE_SUCCESS;
			return getrespSuccess(hfcp);
		}
		
		else if (!strcmp(resp, "DataFound")) {
			hfcp->response.type = FCPRESP_TYPE_DATAFOUND;
			return getrespDataFound(hfcp);
		}
		else if (!strcmp(resp, "DataChunk")) {
			hfcp->response.type = FCPRESP_TYPE_DATACHUNK;
			return getrespDataChunk(hfcp);
		}
		else if (!strcmp(resp, "DataNotFound")) {
			hfcp->response.type = FCPRESP_TYPE_DATANOTFOUND;
			return getrespDataNotFound(hfcp);
		}
		else if (!strcmp(resp, "RouteNotFound")) {
			hfcp->response.type = FCPRESP_TYPE_ROUTENOTFOUND;
			return getrespRouteNotFound(hfcp);
		}
		
		else if (!strcmp(resp, "URIError")) {
			hfcp->response.type = FCPRESP_TYPE_URIERROR;
			return getrespUriError(hfcp);
		}
		else if (!strcmp(resp, "Restarted")) {
			hfcp->response.type = FCPRESP_TYPE_RESTARTED;
			return getrespRestarted(hfcp);
		}
		
		else if (!strcmp(resp, "KeyCollision")) {
			hfcp->response.type = FCPRESP_TYPE_KEYCOLLISION;
			return getrespKeyCollision(hfcp);
		}
		else if (!strcmp(resp, "Pending")) {
			hfcp->response.type = FCPRESP_TYPE_PENDING;
			return getrespPending(hfcp);
		}
		
		else if (!strcmp(resp, "FormatError")) {
			hfcp->response.type = FCPRESP_TYPE_FORMATERROR;
			return getrespFormatError(hfcp);
		}
		else if (!strcmp(resp, "Failed")) {
			hfcp->response.type = FCPRESP_TYPE_FAILED;
			return getrespFailed(hfcp);
		}
		
		/* FEC specific */
		else if (!strcmp(resp, "SegmentHeader")) {
			hfcp->response.type = FCPRESP_TYPE_SEGMENTHEADER;
			return getrespSegmentHeaders(hfcp);
		}
		else if (!strcmp(resp, "BlocksEncoded")) {
			hfcp->response.type = FCPRESP_TYPE_BLOCKSENCODED;
			return getrespBlocksEncoded(hfcp);
		}
		else if (!strcmp(resp, "MadeMetadata")) {
			hfcp->response.type = FCPRESP_TYPE_MADEMETADATA;
			return getrespMadeMetadata(hfcp);
		}
		
		/* Else, send a warning; a little loose, but this is still in development */
		else {
			_fcpLog(FCP_LOG_DEBUG, "_fcpRecvResponse() - received unknown message \"%s\"", resp);
		}
	}
	
	return 0;
}


/*
	getrespHello()

	Note this function copies the info directly into hfcp structure
*/
static int getrespHello(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received NodeHello response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "MaxFileSize=", 12)) {
			hfcp->response.nodehello.max_filesize = xtol(resp+12);
		}

		else if (!strncmp(resp, "HighestSeenBuild=", 17)) {
			hfcp->response.nodehello.highest_build = atoi(resp+17);
		}

		else if (!strncmp(resp, "Node=", 5)) {
			if (hfcp->response.nodehello.description) free(hfcp->response.nodehello.description);
			hfcp->response.nodehello.description = strdup(resp+5);
		}

		else if (!strncmp(resp, "Protocol=", 9)) {
			if (hfcp->response.nodehello.protocol) free(hfcp->response.nodehello.protocol);
			hfcp->response.nodehello.protocol = strdup(resp+9);
		}

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_NODEHELLO;

		else
		_fcpLog(FCP_LOG_DEBUG, "getrespHello() - received unhandled field \"%s\"", resp);
	}

	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}

static int getrespInfo(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	rc = 0;
	_fcpLog(FCP_LOG_DEBUG, "received NodeInfo response\n");

	if (hfcp->response.nodeinfo.architecture) free(hfcp->response.nodeinfo.architecture);
	if (hfcp->response.nodeinfo.operatingsystem) free(hfcp->response.nodeinfo.operatingsystem);
	if (hfcp->response.nodeinfo.operatingsystemversion) free(hfcp->response.nodeinfo.operatingsystemversion);
	if (hfcp->response.nodeinfo.nodeaddress) free(hfcp->response.nodeinfo.nodeaddress);
	if (hfcp->response.nodeinfo.javavendor) free(hfcp->response.nodeinfo.javavendor);
	if (hfcp->response.nodeinfo.javaname) free(hfcp->response.nodeinfo.javaname);
	if (hfcp->response.nodeinfo.javaversion) free(hfcp->response.nodeinfo.javaversion);

	memset(&hfcp->response.nodeinfo, 0, sizeof (FCPRESP_NODEINFO));
	
	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {
		
		if (!strncmp(resp, "Architecture=", 13)) {
			hfcp->response.nodeinfo.architecture = strdup(resp+13);
		}
		else if (!strncmp(resp, "OperatingSystem=", 16)) {
			hfcp->response.nodeinfo.operatingsystem= strdup(resp+16);
		}
		else if (!strncmp(resp, "OperatingSystemVersion=", 23)) {
			hfcp->response.nodeinfo.operatingsystemversion = strdup(resp+23);
		}
		else if (!strncmp(resp, "NodeAddress=", 12)) {
			hfcp->response.nodeinfo.nodeaddress = strdup(resp+12);
		}
		else if (!strncmp(resp, "NodePort=", 9)) {
			hfcp->response.nodeinfo.nodeport = xtol(resp+9);
		}
		else if (!strncmp(resp, "JavaVendor=", 11)) {
			hfcp->response.nodeinfo.javavendor = strdup(resp+11);
		}
		else if (!strncmp(resp, "JavaName=", 9)) {
			hfcp->response.nodeinfo.javaname = strdup(resp+9);
		}
		else if (!strncmp(resp, "JavaVersion=", 12)) {
			hfcp->response.nodeinfo.javaversion = strdup(resp+12);
		}
		else if (!strncmp(resp, "Processors=", 11)) {
			hfcp->response.nodeinfo.processors = xtol(resp+11);
		}
		else if (!strncmp(resp, "MaximumMemory=", 14)) {
			hfcp->response.nodeinfo.maximummemory = xtol(resp+14);
		}
		else if (!strncmp(resp, "AllocatedMemory=", 16)) {
			hfcp->response.nodeinfo.allocatedmemory = xtol(resp+16);
		}
		else if (!strncmp(resp, "FreeMemory=", 11)) {
			hfcp->response.nodeinfo.freememory = xtol(resp+11);
		}
		else if (!strncmp(resp, "EstimatedLoad=", 14)) {
			hfcp->response.nodeinfo.estimatedload = xtol(resp+14);
		}
		else if (!strncmp(resp, "EstimateRateLimitingLoad=", 25)) {
			hfcp->response.nodeinfo.estimateratelimitingload = xtol(resp+25);
		}
		else if (!strncmp(resp, "DatastoreMax=", 13)) {
			hfcp->response.nodeinfo.datastoremax = xtol(resp+13);
		}
		else if (!strncmp(resp, "DatastoreFree=", 14)) {
			hfcp->response.nodeinfo.datastorefree = xtol(resp+14);
		}
		else if (!strncmp(resp, "DatastoreUsed=", 14)) {
			hfcp->response.nodeinfo.datastoreused = xtol(resp+14);
		}
		else if (!strncmp(resp, "MaxFileSize=", 12)) {
			hfcp->response.nodeinfo.maxfilesize = xtol(resp+12);
		}
		else if (!strncmp(resp, "MostRecentTimestamp=", 20)) {
			hfcp->response.nodeinfo.mostrecenttimestamp = xtol(resp+20);
		}
		else if (!strncmp(resp, "LeastRecentTimestamp=", 21)) {
			hfcp->response.nodeinfo.leastrecenttimestamp = xtol(resp+21);
		}
		else if (!strncmp(resp, "RoutingTime=", 12)) {
			hfcp->response.nodeinfo.routingtime = xtol(resp+12);
		}
		else if (!strncmp(resp, "AvailableThreads=", 17)) {
			hfcp->response.nodeinfo.availablethreads = xtol(resp+17);
		}
		else if (!strncmp(resp, "ActiveJobs=", 11)) {
			hfcp->response.nodeinfo.activejobs = xtol(resp+11);
		}
		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_NODEINFO;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespInfo() - received unhandled field \"%s\"", resp);
	}

	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}

static int getrespSuccess(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received Success response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "URI=", 4)) {
			if (hfcp->response.success.uri) free(hfcp->response.success.uri);
			hfcp->response.success.uri = strdup(resp+4);
		}
		
		else if (!strncmp(resp, "PublicKey=", 10)) {
			strncpy(hfcp->response.success.publickey, resp + 10, L_KEY);
		}

#   if 0 /* not sure why this is here */
		else if (!strncmp(resp, "Public=", 7)) {
			strncpy(hfcp->response.success.public, resp + 7, L_KEY);
		}
#   endif

		else if (!strncmp(resp, "PrivateKey=", 11)) {
			strncpy(hfcp->response.success.privatekey, resp + 11, L_KEY);
		}

		else if (!strncmp(resp, "CryptoKey=", 10)) {
			strncpy(hfcp->response.success.cryptokey, resp + 10, L_KEY);
		}

		else if (!strncmp(resp, "Length=", 7)) {
			hfcp->response.success.length = xtol(resp + 7);
		}
		
		else if (!strncmp(resp, "EndMessage", 10)) {
			return FCPRESP_TYPE_SUCCESS;
		}

		else
		_fcpLog(FCP_LOG_DEBUG, "getrespSuccess() - received unhandled field \"%s\"", resp);
  }
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int getrespDataFound(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received DataFound response");

	hfcp->response.datafound.datalength = 0;
	hfcp->response.datafound.metadatalength = 0;

	hfcp->key->size = 0;
	hfcp->key->metadata->size = 0;

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "DataLength=", 11)) {
			hfcp->key->size = hfcp->response.datafound.datalength = xtol(resp + 11);
		}
		
		else if (strncmp(resp, "MetadataLength=", 15) == 0) {
			hfcp->key->metadata->size = hfcp->response.datafound.metadatalength = xtol(resp + 15);
		}

		else if (strncmp(resp, "Timeout=", 8) == 0) {
			hfcp->options->timeout = hfcp->response.datafound.timeout = (unsigned short)xtol(resp + 8);
			/*_fcpLog(FCP_LOG_DEBUG, "Timeout: %u", hfcp->options->timeout);*/
		}
		
		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_DATAFOUND;

		else
		_fcpLog(FCP_LOG_DEBUG, "getrespDataFound() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int getrespDataChunk(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	unsigned long len;

	rc = _fcpSockRecvln(hfcp, resp, 8192);
	
	if (!strncmp(resp, "Length=", 7)) {
		
		len = xtol(resp + 7);

		if (hfcp->response.datachunk.length == 0) {

			_fcpLog(FCP_LOG_DEBUG, "initial allocation of data block");
			hfcp->response.datachunk.data = malloc(len+1);
		}

		else if (len > hfcp->response.datachunk.length) {

			hfcp->response.datachunk.data = realloc(hfcp->response.datachunk.data, len+1);
		}

		hfcp->response.datachunk.length = len;
	}
	else {
		_fcpLog(FCP_LOG_DEBUG, "did not receive expected Length property");
		return -1;
	}

	/* this line *should just be the word 'data' */
	rc = _fcpSockRecvln(hfcp, resp, 8192);

	if (strncmp(resp, "Data", 4)) {
		_fcpLog(FCP_LOG_DEBUG, "did not receive expected Data response line");
		return -1;
	}
	
	/* get len bytes of data */
	if ((rc = _fcpSockRecv(hfcp, hfcp->response.datachunk.data, len)) <= 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);

	return FCPRESP_TYPE_DATACHUNK;
}


static int getrespDataNotFound(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received DataNotFound response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_DATANOTFOUND;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespDataNotFound() - received unhandled field \"%s\"", resp);
	}

	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int getrespRouteNotFound(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received RouteNotFound response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "Reason=", 7)) {
			if (hfcp->response.routenotfound.reason) free(hfcp->response.routenotfound.reason);
			hfcp->response.routenotfound.reason = strdup(resp + 7);
		}

		else if (!strncmp(resp, "Unreachable=", 12))
			hfcp->response.routenotfound.unreachable = (unsigned short)xtol(resp + 12);

		else if (!strncmp(resp, "Restarted=", 10))
			hfcp->response.routenotfound.restarted = (unsigned short)xtol(resp + 10);

		else if (!strncmp(resp, "Rejected=", 9))
			hfcp->response.routenotfound.rejected = (unsigned short)xtol(resp + 9);

		else if (!strncmp(resp, "BackedOff=", 10))
			hfcp->response.routenotfound.backedoff = (unsigned short)xtol(resp + 10);

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_ROUTENOTFOUND;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespRouteNotFound() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int getrespUriError(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received URIError response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "Reason=", 7)) {
			if (hfcp->response.urierror.reason) free(hfcp->response.urierror.reason);
			hfcp->response.urierror.reason = strdup(resp + 7);
		}

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_URIERROR;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespUriError() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}

/*
  Function:    getrespRestarted()

  Arguments    hfcpconn - Freenet FCP handle

	Notes:
	- Response will set hfcp->options->timeout before returning.
*/
static int getrespRestarted(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received Restarted response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "Timeout=", 8)) {
			hfcp->response.restarted.timeout = (unsigned short)xtol(resp + 8);
			hfcp->options->timeout = hfcp->response.restarted.timeout;

			/*_fcpLog(FCP_LOG_DEBUG, "Timeout: %u", hfcp->options->timeout);*/
		}

		if (!strncmp(resp, "Reason=", 7)) {
			if (hfcp->response.restarted.reason) free(hfcp->response.restarted.reason);
			
			hfcp->response.restarted.reason = strdup(resp + 7);
		}

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_RESTARTED;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespRestarted() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int getrespKeyCollision(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received KeyCollision response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "URI=", 4)) {
			if (hfcp->response.keycollision.uri) free(hfcp->response.keycollision.uri);
			hfcp->response.keycollision.uri = strdup(resp+4);
		}

		else if (!strncmp(resp, "PublicKey=", 10)) {
			strncpy(hfcp->response.keycollision.publickey, resp + 10, L_KEY);
		}
		
		else if (!strncmp(resp, "PrivateKey=", 11)) {
			strncpy(hfcp->response.keycollision.privatekey, resp + 11, L_KEY);
		}

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_KEYCOLLISION;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespKeyCollision() - received unhandled field \"%s\"", resp);
  }
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


/*
  Function:    getrespPending()

  Arguments    hfcpconn - Freenet FCP handle

	Notes:
	- Response will set hfcp->options->timeout before returning.
*/

static int getrespPending(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received Pending response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "URI=", 4)) {
			if (hfcp->response.pending.uri) free(hfcp->response.pending.uri);
			hfcp->response.pending.uri = strdup(resp+4);
		}

		else if (!strncmp(resp, "Timeout=", 8)) { /* milliseconds */
			hfcp->response.pending.timeout = (unsigned short)xtol(resp + 8);
			hfcp->options->timeout = hfcp->response.pending.timeout;
		}

		else if (!strncmp(resp, "PublicKey=", 10)) {
			strncpy(hfcp->response.pending.publickey, resp + 10, L_KEY);
		}
		
		else if (!strncmp(resp, "PrivateKey=", 11)) {
			strncpy(hfcp->response.pending.privatekey, resp + 11, L_KEY);
		}
		
		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_PENDING;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespPending() - received unhandled field \"%s\"", resp);
  }
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


/*
  Function:    getrespFailed()

  Arguments    hfcpconn - Freenet FCP handle

  Returns      FCPRESP_TYPE_SUCCESS if successful, -1 otherwise

  Description: reads in and processes details of Failed response
*/

static int getrespFailed(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received Failed response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "Reason=", 7)) {
			if (hfcp->response.failed.reason) free(hfcp->response.failed.reason);
			
			hfcp->response.failed.reason = strdup(resp + 7);
		}
		
		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_FAILED;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespFailed() - received unhandled field \"%s\"", resp);
  }
  
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


/*
	Function:    getrespFormatError()
	
	Arguments:   fcpconn - connection block
	
	Returns:     0 if successful, -1 otherwise
	
	Description: Gets the details of a format error
*/

static int getrespFormatError(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received FormatError response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "Reason=", 7)) {
			if (hfcp->response.formaterror.reason) free(hfcp->response.formaterror.reason);
			hfcp->response.formaterror.reason = strdup(resp + 7);
		}
		
		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_FORMATERROR;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespFormatError() - received unhandled field \"%s\"", resp);
  }
  
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}


static int  getrespSegmentHeaders(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received SegmentHeader response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "FECAlgorithm=", 13))
			strncpy(hfcp->response.segmentheader.fec_algorithm, resp + 13, L_KEY);

		else if (!strncmp(resp, "FileLength=", 11))
			hfcp->response.segmentheader.filelength = xtol(resp + 11);

		else if (!strncmp(resp, "Offset=", 7))
			hfcp->response.segmentheader.offset = xtol(resp + 7);

		else if (!strncmp(resp, "BlockCount=", 11))
			hfcp->response.segmentheader.block_count = xtol(resp + 11);

		else if (!strncmp(resp, "BlockSize=", 10))
			hfcp->response.segmentheader.block_size = xtol(resp + 10);

		else if (!strncmp(resp, "DataBlockOffset=", 16))
			hfcp->response.segmentheader.datablock_offset = xtol(resp + 16);

		else if (!strncmp(resp, "CheckBlockCount=", 16))
			hfcp->response.segmentheader.checkblock_count = xtol(resp + 16);

		else if (!strncmp(resp, "CheckBlockSize=", 15))
			hfcp->response.segmentheader.checkblock_size = xtol(resp + 15);

		else if (!strncmp(resp, "CheckBlockOffset=", 17))
			hfcp->response.segmentheader.checkblock_offset = xtol(resp + 17);

		else if (!strncmp(resp, "Segments=", 9))
			hfcp->response.segmentheader.segments = xtol(resp + 9);

		else if (!strncmp(resp, "SegmentNum=", 11))
			hfcp->response.segmentheader.segment_num = xtol(resp + 11);

		else if (!strncmp(resp, "BlocksRequired=", 15))
			hfcp->response.segmentheader.blocks_required = xtol(resp + 15);

 		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_SEGMENTHEADER;
		else
			_fcpLog(FCP_LOG_DEBUG, "getrespSegmentHeaders() - received unhandled field \"%s\"", resp);
  }
	
  /* oops.. there's been a socket error of sorts */
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	
	else
		return 0;
}


static int getrespBlocksEncoded(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received BlocksEncoded response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "BlockCount=", 11))
			hfcp->response.blocksencoded.block_count = (unsigned short)xtol(resp + 11);

		else if (!strncmp(resp, "BlockSize=", 10))
			hfcp->response.blocksencoded.block_size = xtol(resp + 10);

 		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_BLOCKSENCODED;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespBlocksEncoded() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}

static int getrespMadeMetadata(hFCP *hfcp)
{
	char resp[8193];
	int  rc;

	_fcpLog(FCP_LOG_DEBUG, "received MadeMetadata response");

	while ((rc = _fcpSockRecvln(hfcp, resp, 8192)) > 0) {

		if (!strncmp(resp, "DataLength=", 11))
			hfcp->response.mademetadata.datalength = xtol(resp + 11);

		else if (!strncmp(resp, "EndMessage", 10))
			return FCPRESP_TYPE_MADEMETADATA;

		else
			_fcpLog(FCP_LOG_DEBUG, "getrespMadeMetadata() - received unhandled field \"%s\"", resp);
	}
	
	if (rc < 0)
		return (rc == EZERR_SOCKET_TIMEOUT ? EZERR_SOCKET_TIMEOUT : -1);
	else
		return 0;
}

