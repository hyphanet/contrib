// ConfigFile.cpp: implementation of the CConfigFile class.
//
//////////////////////////////////////////////////////////////////////

#include "stdafx.h"

#include "NodeConfig.h"
#include "ConfigFile.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "PropGeek.h"
#include "PropFProxy.h"
#include "PropDiagnostics.h"


#ifdef _DEBUG
#undef THIS_FILE
static char THIS_FILE[]=__FILE__;
#define new DEBUG_NEW
#endif

extern char				progPath[256];

extern CPropNormal		*pNormal;
extern CPropAdvanced	*pAdvanced;
extern CPropGeek		*pGeek;
extern CPropFProxy		*pFProxy;
extern CPropDiagnostics *pDiagnostics;

//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////

CConfigFile::CConfigFile()
{
	 char name[255];
     //CString ip;
     PHOSTENT hostinfo;
	
	//Try to guess the own host name
	pNormal->m_ipAddress = "undefined";

	if(!gethostname ( name, sizeof(name)))
    {
		if(hostinfo = gethostbyname(name))
        {
			pNormal->m_ipAddress = hostinfo->h_name;
            //ip = inet_ntoa (*(struct in_addr *)*hostinfo->h_addr_list);
        }
    }

}


CConfigFile::~CConfigFile()
{

}

void CConfigFile::Load()
{
	FILE *fp;
	char buf[1024];
	char *pValue;

	/////////////////////////////
	//
	// Create default values

	// Normal tab
	//pNormal->m_importNewNodeRef.EnableWindow(false);
	// propose 20% of the free disk space, but min 10MB and max 2GB
	ULARGE_INTEGER FreeBytes,TotalBytes;
	GetDiskFreeSpaceEx(NULL,&FreeBytes,&TotalBytes,NULL);
	// our variable m_storeSize is in Megabytes so we need to divide by 2^20
	// i.e. shift FreeBytes right 20 bits
	pNormal->m_storeSize = __max(10,__min(2047,(DWORD)(Int64ShrlMod32(FreeBytes.QuadPart,20))/5));
	pNormal->m_storeFile = "";
	pNormal->m_useDefaultNodeRefs = FALSE; // this will be modified in the ctor of CPropNormal
	pNormal->m_transient = TRANSIENT;
	// the ipAddrress is determined in the constructor: pNormal->m_ipAddress;
	srand( (unsigned)time( NULL ) );
	pNormal->m_listenPort = rand() + 1024;	// random port number
	pNormal->warnPerm = TRUE;

	// Advanced tab
	pAdvanced->m_adminPassword = "";
	pAdvanced->m_bandwidthLimit = 0;
	pAdvanced->m_clientPort = 8481;
	pAdvanced->m_doAnnounce = false;
	pAdvanced->m_fcpHosts = "127.0.0.1,localhost";
	pAdvanced->m_initialRequestHTL = 15;
	pAdvanced->m_inputBandwidthLimit = 0;
	pAdvanced->m_maxHopsToLive = 25;
	pAdvanced->m_maximumThreads = 120;
	pAdvanced->m_maxNodeConnections = 60;
	pAdvanced->m_outputBandwidthLimit = 0;
	pAdvanced->m_seedFile = "seednodes.ref";

	// Geek tab
	pGeek->m_announcementAttempts = 10;
	pGeek->m_announcementDelay = 1800000;
	pGeek->m_announcementDelayBase = 2;
	pGeek->m_announcementPeers = 3;
	pGeek->m_authTimeout = 30000;
	pGeek->m_checkPointInterval = 1200;
	pGeek->m_connectionTimeout = 180000;
	pGeek->m_hopTimeDeviation = 12000;
	pGeek->m_hopTimeExpected = 12000;
	pGeek->m_initialRequests = 10;
	pGeek->m_localAnnounceTargets = "";
	pGeek->m_messageStoreSize = 50000;
	pGeek->m_blockSize = 4096;
	pGeek->m_maximumPadding = 65536;
	pGeek->m_routeConnectTimeout = 10000;
	pGeek->m_rtMaxNodes = 100;
	pGeek->m_rtMaxRefs = 1000;
	pGeek->m_storeDataFile = "";
	pGeek->m_streamBufferSize = 65536;
	pGeek->m_storeCipherName = "Twofish";
	pGeek->m_storeCipherWidth = 128;

	pFProxy->m_fproxyport = 8888;
	pFProxy->m_fproxyclass= "freenet.client.http.FproxyServlet";
	pFProxy->m_bfproxyfilter = TRUE;
	pFProxy->m_strfproxyallowedmime = "text/plain,image/jpeg,image/gif,image/png";
	pFProxy->m_bfproxyservice= TRUE;
	pFProxy->m_fproxyinserthtl = 15;
	pFProxy->m_fproxyrequesthtl = 15;
	pFProxy->m_fproxy_pollDroppedConnection = TRUE;
	pFProxy->m_fproxy_splitinchtl = 20;
	pFProxy->m_fproxy_splitretries = 1;
	pFProxy->m_fproxy_splitthreads = 10;

	pDiagnostics->m_diagnosticsPath = ".freenet/stats";
	pDiagnostics->m_doDiagnostics = TRUE;
	pDiagnostics->m_logFile = "freenet.log";
	pDiagnostics->m_logLevel = "normal";
	pDiagnostics->m_logFormat = "d (c, t): m";
	pDiagnostics->m_nodestatusservlet = true;
	pDiagnostics->m_nodestatusport = 8889;
	pDiagnostics->m_nodestatusclass = "freenet.client.http.NodeStatusServlet";
	
	// Reset unknown parameters container
	UnknownParms = "";


	/////////////////////////////////
	//
	// Try to open and read in a config file
	//

	if ((fp = fopen(FileName, "r")) == NULL)
		MessageBox(0, "Freenet config file 'freenet.ini' does not exist - creating one",
					"Freenet settings - config file missing",
					MB_SYSTEMMODAL | MB_ICONINFORMATION);
	else
	{
		while (fgets(buf, 1023, fp) != NULL)
		{
			// Split line into token and value
			if ((pValue = splitLine(buf)) == NULL)
				// line is empty, or a comment, or had no value
				continue;

			processItem(buf, pValue);
		}
		fclose(fp);
	}
}

void CConfigFile::Save()
{
	FILE *fp;
	char datestr[128];

	if ((fp = fopen(FileName, "w")) == NULL)
	{
		MessageBox(0, 
			"Can't write freenet.ini",
			"Freenet Config - fatal error",
			MB_SYSTEMMODAL | MB_ICONEXCLAMATION);
		return;
	}

	fprintf(fp, "[Freenet node]\n");
	fprintf(fp, "# Freenet configuration file\n");
	fprintf(fp, "# This file was automatically generated by WinConfig on %s\n", _strdate(datestr));
	fprintf(fp, "\n");
	fprintf(fp, "# Note that all properties may be overridden from the command line,\n");
	fprintf(fp, "# so for example, java Freenet.Node --listenPort 10000 will cause\n");
	fprintf(fp, "# the setting in this file to be ignored\n");
	fprintf(fp, "\n");
	fprintf(fp, "\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "# Normal entries\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "\n");
	fprintf(fp, "# The byte size of the datastore cache file.  Note that it will maintain\n");
	fprintf(fp, "# a fixed size. If you change this or the storePath field following,\n");
	fprintf(fp, "# your entire datastore will be wiped and replaced with a blank one\n");

	// storeSize = size in bytes ... our variable m_storeCacheSize is in Megabytes so
	// multiply by 2^20, i.e. shift left 20 bits, before writing to conf file
	char szStoreSize[35];

	fprintf(fp, "storeSize=%s\n", _ui64toa(Int64ShllMod32(pNormal->m_storeSize,20), szStoreSize, 10) );
	fprintf(fp, "\n");
	fprintf(fp, "# The path to a single file (including file name, or a comma-separated list of files,\n");
	fprintf(fp, "# containing the data store.  The size of each file is given by <storeSize>.\n");
	fprintf(fp, "# Defaults to cache_<port> in the main freenet directory.\n");
	fprintf(fp, "%sstoreFile=%s\n",pNormal->m_storeFile.GetLength()?"":"#", pNormal->m_storeFile);
	fprintf(fp, "\n");
	fprintf(fp, "# Transient nodes do not give out references to themselves, and should\n");
	fprintf(fp, "# therefore not receive any requests.  Set this to yes only if you are\n");
	fprintf(fp, "# on a slow, non-permanent connection.\n");
	fprintf(fp, "transient=%s\n", (pNormal->m_transient == TRANSIENT) ? "true" : "false");
	fprintf(fp, "\n");
	fprintf(fp, "# The port to listen for incoming FNP (Freenet Node Protocol) connections on.\n");
	fprintf(fp, "listenPort=%d\n", pNormal->m_listenPort);
	fprintf(fp, "\n");
	fprintf(fp, "# The I.P. address of this node as seen by the public internet.\n");
	fprintf(fp, "# This is needed in order for the node to determine its own\n");
	fprintf(fp, "# NodeReference.\n");
	if (pNormal->m_ipAddress.GetLength() == 0)
		fprintf(fp, "#ipAddress=\n");
	else
		fprintf(fp, "ipAddress=%s\n", pNormal->m_ipAddress.GetBuffer(1));
	fprintf(fp, "\n");
	fprintf(fp, "# This is used only by Windows configurator, not by node\n");
	fprintf(fp, "warnPerm=%s\n", pNormal->warnPerm ? "true" : "false");
	fprintf(fp, "\n");
	fprintf(fp, "\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "# Advanced Entries\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "\n");
	fprintf(fp, "# set to yes if you want your node to announce itself to other nodes\n");
	fprintf(fp, "doAnnounce=%s\n", pAdvanced->m_doAnnounce ? "yes" : "no");
	fprintf(fp, "\n");
	fprintf(fp, "# file containing initial node references\n");
	fprintf(fp, "seedFile=%s\n", pAdvanced->m_seedFile);
	fprintf(fp, "\n");
	fprintf(fp, "# The port to listen for local FCP (Freenet Client Protocol) connections on.\n");
	fprintf(fp, "clientPort=%d\n", pAdvanced->m_clientPort);
	fprintf(fp, "\n");
	fprintf(fp, "# The maximum number of bytes per second to transmit, totaled between\n");
	fprintf(fp, "# incoming and outgoing connections.  Ignored if either inputBandwidthLimit\n");
	fprintf(fp, "# or outputBandwidthLiit is nonzero.\n");
	if (pAdvanced->m_inputBandwidthLimit == 0 && pAdvanced->m_outputBandwidthLimit == 0)
		fprintf(fp, "bandwidthLimit=%d\n", pAdvanced->m_bandwidthLimit);
	else
		fprintf(fp, "#bandwidthLimit=%d\n", pAdvanced->m_bandwidthLimit);
	fprintf(fp, "\n");
	fprintf(fp, "# If nonzero, specifies an independent limit for outgoing data only.\n");
	fprintf(fp, "# (overrides bandwidthLimit if nonzero)\n");
	fprintf(fp, "outputBandwidthLimit=%d\n", pAdvanced->m_outputBandwidthLimit);
	fprintf(fp, "inputBandwidthLimit=%d\n", pAdvanced->m_inputBandwidthLimit);
	fprintf(fp, "\n");
	fprintf(fp, "#A comma-separated list of hosts which are allowed to talk to node via FCP\n");
	fprintf(fp, "fcpHosts=%s\n", pAdvanced->m_fcpHosts.GetBuffer(0));
	fprintf(fp, "\n");
	fprintf(fp, "# The hops that initial requests should make.\n");
	fprintf(fp, "initialRequestHTL=%d\n", pAdvanced->m_initialRequestHTL);
	fprintf(fp, "\n");
	fprintf(fp, "# If this is set then users that can provide the password can\n");
	fprintf(fp, "# can have administrative access. It is recommended that\n");
	fprintf(fp, "# you do not use this without also using adminPeer below\n");
	fprintf(fp, "# in which case both are required.\n");
	if (pAdvanced->m_adminPassword.GetLength() == 0)
		fprintf(fp, "#adminPassword=\n");
	else
		fprintf(fp, "adminPassword=%s\n", pAdvanced->m_adminPassword.GetBuffer(1));
	fprintf(fp, "\n");
	fprintf(fp, "# If this is set, then users that are authenticated owners\n");
	fprintf(fp, "# of the given PK identity can have administrative access.\n");
	fprintf(fp, "# If adminPassword is also set both are required.\n");
	fprintf(fp, "\n");
	fprintf(fp, "# When forwarding a request, the node will reduce the HTL to this value\n");
	fprintf(fp, "# if it is found to be in excess.\n");
	fprintf(fp, "maxHopsToLive=%d\n", pAdvanced->m_maxHopsToLive);
	fprintf(fp, "\n");
	fprintf(fp, "# Should we use thread-management?  If this number is defined and non-zero,\n");
	fprintf(fp, "# this specifies how many inbound connections can be active at once.\n");
	fprintf(fp, "maximumThreads=%d\n", pAdvanced->m_maximumThreads);
	fprintf(fp, "\n");
	fprintf(fp, "# The number of connections that a node can keep open at the same time\n");
	fprintf(fp, "maxNodeConnections=%d\n", pAdvanced->m_maxNodeConnections);
	fprintf(fp, "\n");
	fprintf(fp, "\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "# Geek Settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "\n");
	fprintf(fp, "# The number of attempts to make at announcing this node per\n");
	fprintf(fp, "# initial peer. Zero means the node will not announce itself\n");
	fprintf(fp, "announcementAttempts=%d\n", pGeek->m_announcementAttempts);
	fprintf(fp, "\n");
	fprintf(fp, "# The amount of time to wait before initially announcing the node,\n");
	fprintf(fp, "# and to base the time the time between retries on. In milliseconds.\n");
	fprintf(fp, "announcementDelay=%d\n", pGeek->m_announcementDelay);
	fprintf(fp, "\n");
	fprintf(fp, "# The value to mutliply the last delay time with for each retry.\n");
	fprintf(fp, "# That is, for try N, we weight <announcementDelay>*<announcementDelay>^N\n");
	fprintf(fp, "# before starting.\n");
	fprintf(fp, "announcementDelayBase=%d\n", pGeek->m_announcementDelayBase);
	fprintf(fp, "\n");
	fprintf(fp, "# announcementPeers: undocumented.\n");
	fprintf(fp, "announcementPeers=%d\n", pGeek->m_announcementPeers);
	fprintf(fp, "\n");
	fprintf(fp, "# How long to wait for authentication before giving up (in milliseconds)\n");
	fprintf(fp, "authTimeout=%d\n", pGeek->m_authTimeout);
	fprintf(fp, "\n");
	fprintf(fp, "# The interval at which to write out the node's data file\n");
	fprintf(fp, "# (the store_<port> file, *not* the cache_<port> file).\n");
	fprintf(fp, "checkPointInterval=%d\n", pGeek->m_checkPointInterval);
	fprintf(fp, "\n");
	fprintf(fp, "# How long to listen on an inactive connection before closing\n");
	fprintf(fp, "# (if reply address is known)\n");
	fprintf(fp, "connectionTimeout=%d\n", pGeek->m_connectionTimeout);
	fprintf(fp, "\n");
	fprintf(fp, "# The expected standard deviation in hopTimeExpected.\n");
	fprintf(fp, "hopTimeDeviation=%d\n", pGeek->m_hopTimeDeviation);
	fprintf(fp, "\n");
	fprintf(fp, "# The expected time it takes a Freenet node to pass a message.\n");
	fprintf(fp, "# Used to calculate timeout values for requests.\n");
	fprintf(fp, "hopTimeExpected=%d\n", pGeek->m_hopTimeExpected);
	fprintf(fp, "\n");
	fprintf(fp, "# The number of keys to request from the returned close values\n");
	fprintf(fp, "# after an Announcement (this is per announcement made).\n");
	fprintf(fp, "initialRequests=%d\n", pGeek->m_initialRequests);
	fprintf(fp, "\n");
	fprintf(fp, "# localAnnounceTargets: undocumented.\n\n");
	fprintf(fp, "# The number of outstanding message replies the node will\n");
	fprintf(fp, "# wait for before it starts to abandon them.\n");
	fprintf(fp, "messageStoreSize=%d\n", pGeek->m_messageStoreSize);
	fprintf(fp, "\n");
	fprintf(fp, "# What size should the blocks have when moving data?\n");
	fprintf(fp, "blockSize=%d\n", pGeek->m_blockSize);
	fprintf(fp, "\n");
	fprintf(fp, "# The maximum number of bytes of padding to allow between messages\n");
	fprintf(fp, "# and in Void messages.\n");
	fprintf(fp, "maximumPadding=%d\n", pGeek->m_maximumPadding);
	fprintf(fp, "\n");
	fprintf(fp, "# The time to wait for connections to be established and \n");
	fprintf(fp, "# authenticated before passing by a node while routing out.\n");
	fprintf(fp, "# Connections that are by passed are still finished and cached \n");
	fprintf(fp, "# for the time set by ConnectionTimeout (in milliseconds).\n");
	fprintf(fp, "routeConnectTimeout=%d\n", pGeek->m_routeConnectTimeout);
	fprintf(fp, "\n");
	fprintf(fp, "# The number of unique nodes that can be contained in the routing table.\n");
	fprintf(fp, "rtMaxNodes=%d\n", pGeek->m_rtMaxNodes);
	fprintf(fp, "\n");
	fprintf(fp, "# The number of references allowed in the routing table.  This should not\n");
	fprintf(fp, "# be set too high.  It is suggested to leave it at 1000 for now.\n");
	fprintf(fp, "rtMaxRefs=%d\n", pGeek->m_rtMaxRefs);
	fprintf(fp, "\n");
	fprintf(fp, "# The path to the file containing the node's reference to itself, its\n");
	fprintf(fp, "# routing table, and the datastore directory.  Defaults to store_<port>\n");
	fprintf(fp, "# in the storePath directory.\n");
	if (pGeek->m_storeDataFile.GetLength() == 0)
		fprintf(fp, "#storeDataFile=\n");
	else
		fprintf(fp, "storeDataFile=store_%d\n", pNormal->m_listenPort);
	fprintf(fp, "\n");
	fprintf(fp, "# The name of a symmetric cipher algorithm to encrypt the datastore\n");
	fprintf(fp, "# contents with.  Supported algorithms are \"Twofish\", \"Rijndael\",\n");
	fprintf(fp, "# and \"null\", \"none\", or \"void\" (for no encryption).\n");
	fprintf(fp, "storeCipherName=%s\n", pGeek->m_storeCipherName);
	fprintf(fp, "\n");
	fprintf(fp, "# The width in bits of the cipher key to use for the datastore.\n");
	fprintf(fp, "# The allowed values for this will depend on the cipher algorithm.\n");
	fprintf(fp, "# Twofish allows 64, 128, 192, or 256, while Rijndael allows\n");
	fprintf(fp, "# 128, 192, or 256.\n");
	fprintf(fp, "storeCipherWidth=%d\n", pGeek->m_storeCipherWidth);
	fprintf(fp, "\n\n");

	fprintf(fp, "########################\n");
	fprintf(fp, "# Diagnostics Settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "# The name of the log file (`NO' to log to standard out)\n");
	fprintf(fp, "logFile=%s\n", pDiagnostics->m_logFile);
	fprintf(fp, "\n");
	fprintf(fp, "# The error reporting threshold, one of:\n");
	fprintf(fp, "#   Error:   Errors only\n");
	fprintf(fp, "#   Normal:  Report significant events\n");
	fprintf(fp, "#   Minor:   Report minor events\n");
	fprintf(fp, "#   Debug:   Report events only of relevance when debugging\n");
	fprintf(fp, "logLevel=%s\n", pDiagnostics->m_logLevel.GetBuffer(0));
	fprintf(fp, "\n");
	fprintf(fp, "#A template string for log messages.  All non-alphabet characters are\n");
	fprintf(fp, "# reproduced verbatim.  Alphabet characters are substituted as follows:\n");
	fprintf(fp, "# d = date (timestamp), c = class name of the source object,\n");
	fprintf(fp, "# h = hashcode of the object, t = thread name, p = priority,\n");
	fprintf(fp, "# m = the actual log message\n");
	fprintf(fp, "logFormat=%s\n", pDiagnostics->m_logFormat);
	fprintf(fp, "\n");
	fprintf(fp, "# The directory in which to cache diagnostics data.\n");
	fprintf(fp, "diagnosticsPath=%s\n", pDiagnostics->m_diagnosticsPath.GetBuffer(0));
	fprintf(fp, "\n");
	fprintf(fp, "# The diagnostics module receives and aggregates statistics aboutFreenet's performance.\n");
	fprintf(fp, "# This will eat some gratuitous memory and cpubut may let you provide valuable data to the project.\n");
	fprintf(fp, "doDiagnostics=%s\n", pDiagnostics->m_doDiagnostics ? "yes" : "no");
	fprintf(fp, "\n");
	fprintf(fp, "\n\n");

	fprintf(fp, "########################\n");
	fprintf(fp, "# Services & Servlets\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "services=%s%s\n",pFProxy->m_bfproxyservice?"fproxy,":"",
								pDiagnostics->m_nodestatusservlet?"nodestatus,":"");
	fprintf(fp, "\n");

	// FProxy settings
	fprintf(fp, "########################\n");
	fprintf(fp, "# FProxy settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "fproxy.class=%s\n",pFProxy->m_fproxyclass);
	fprintf(fp, "fproxy.port=%d\n",pFProxy->m_fproxyport);
	fprintf(fp, "fproxy.params.insertHtl=%d\n",pFProxy->m_fproxyinserthtl);
	fprintf(fp, "fproxy.params.requestHtl=%d\n",pFProxy->m_fproxyrequesthtl);
	fprintf(fp, "fproxy.params.filter=%s\n",pFProxy->m_bfproxyfilter?"true":"false");
	fprintf(fp, "fproxy.params.passThroughMimeTypes=%s\n",pFProxy->m_strfproxyallowedmime);
	fprintf(fp, "fproxy.params.pollForDroppedConnection=%s\n",pFProxy->m_fproxy_pollDroppedConnection?"true":"false");
	fprintf(fp, "fproxy.params.splitFileRetryHtlIncrement=%d\n",pFProxy->m_fproxy_splitinchtl);
	fprintf(fp, "fproxy.params.splitFileRetries=%d\n",pFProxy->m_fproxy_splitretries);
	fprintf(fp, "fproxy.params.splitFileThreads=%d\n",pFProxy->m_fproxy_splitthreads);
	fprintf(fp, "\n");

		// FProxy settings
	fprintf(fp, "########################\n");
	fprintf(fp, "# Nodestatus servlet settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "nodestatus.class=%s\n",pDiagnostics->m_nodestatusclass);
	fprintf(fp, "nodestatus.port=%d\n",pDiagnostics->m_nodestatusport);
	fprintf(fp, "\n");

	// Write out unknown parameters
	if (UnknownParms.GetLength() > 0)
	{
		fprintf(fp, "# Unknown parameters - these are not yet known or handled by the NodeConfig\n");
		fprintf(fp, "# utility, but are assumed to be valid and understandable to the node\n");
		fprintf(fp, "# if you see this in the file, then please email the parameters following\n");
		fprintf(fp, "# this comment header to devl@freenetproject.org, to prompt the developers\n");
		fprintf(fp, "# into updating this configuration utility - thanks\n");
		fprintf(fp, "\n");
		fprintf(fp, "%s\n", UnknownParms.GetBuffer(0));
	}

	fclose(fp);
}


//
// big ugly mother of a routine which assigns config parameters to
// property page member variables
//

void CConfigFile::processItem(char *tok, char *val)
{
	if (!strcmp(tok, "[Freenet node]\n"))
		return;
	else if (!strcmp(tok, "storeSize"))
	//only if we did not set 0 as disk cache size (means we should propose our own default value)
	{
		if(_atoi64(val) != 0)
		{
			// storeSize = size in bytes ... our variable m_storeSize is in Megabytes
			// so divide what we read from conf file by 2^20, i.e. shift right 20 bits
			pNormal->m_storeSize = (DWORD)(Int64ShrlMod32(_atoi64(val),20));
		}
	}
	else if (!strcmp(tok, "storeFile"))
		pNormal->m_storeFile = val;
	else if (!strcmp(tok, "transient"))
	{
		pNormal->m_transient = atobool(val)?TRANSIENT:NOT_TRANSIENT;
	}
	else if (!strcmp(tok, "listenPort"))
		pNormal->m_listenPort = atoi(val);
	else if (!strcmp(tok, "ipAddress"))
		pNormal->m_ipAddress = val;
	else if (!strcmp(tok, "warnPerm"))
		pNormal->warnPerm = atobool(val);
	else if (!strcmp(tok, "doAnnounce"))
		pAdvanced->m_doAnnounce = atobool(val);
	else if (!strcmp(tok, "seedFile"))
		pAdvanced->m_seedFile = val;
	else if (!strcmp(tok, "clientPort"))
		pAdvanced->m_clientPort = atoi(val);
	else if (!strcmp(tok, "bandwidthLimit"))
		pAdvanced->m_bandwidthLimit = atoi(val);
	else if (!strcmp(tok, "outputBandwidthLimit"))
		pAdvanced->m_outputBandwidthLimit = atoi(val);
	else if (!strcmp(tok, "inputBandwidthLimit"))
		pAdvanced->m_inputBandwidthLimit = atoi(val);
	else if (!strcmp(tok, "fcpHosts"))
		pAdvanced->m_fcpHosts = val;
	else if (!strcmp(tok, "initialRequestHTL"))
		pAdvanced->m_initialRequestHTL = atoi(val);
	else if (!strcmp(tok, "adminPassword"))
		pAdvanced->m_adminPassword = val;
	else if (!strcmp(tok, "maxHopsToLive"))
		pAdvanced->m_maxHopsToLive = atoi(val);
	else if (!strcmp(tok, "maximumThreads"))
		pAdvanced->m_maximumThreads = atoi(val);
	else if (!strcmp(tok, "maxNodeConnections"))
		pAdvanced->m_maxNodeConnections = atoi(val);
	else if (!strcmp(tok, "announcementAttempts"))
		pGeek->m_announcementAttempts = atoi(val);
	else if (!strcmp(tok, "announcementDelay"))
		pGeek->m_announcementDelay = atoi(val);
	else if (!strcmp(tok, "announcementDelayBase"))
		pGeek->m_announcementDelayBase = atoi(val);
	else if (!strcmp(tok, "announcementPeers"))
		pGeek->m_announcementPeers = atoi(val);
	else if (!strcmp(tok, "authTimeout"))
		pGeek->m_authTimeout = atoi(val);
	else if (!strcmp(tok, "checkPointInterval"))
		pGeek->m_checkPointInterval = atoi(val);
	else if (!strcmp(tok, "connectionTimeout"))
		pGeek->m_connectionTimeout = atoi(val);
	else if (!strcmp(tok, "hopTimeDeviation"))
		pGeek->m_hopTimeDeviation = atoi(val);
	else if (!strcmp(tok, "hopTimeExpected"))
		pGeek->m_hopTimeExpected = atoi(val);
	else if (!strcmp(tok, "initialRequests"))
		pGeek->m_initialRequests = atoi(val);
	else if (!strcmp(tok, "messageStoreSize"))
		pGeek->m_messageStoreSize = atoi(val);
	else if (!strcmp(tok, "blockSize"))
		pGeek->m_blockSize = atoi(val);
	else if (!strcmp(tok, "maximumPadding"))
		pGeek->m_maximumPadding = atoi(val);
	else if (!strcmp(tok, "routeConnectTimeout"))
		pGeek->m_routeConnectTimeout = atoi(val);
	else if (!strcmp(tok, "rtMaxNodes"))
		pGeek->m_rtMaxNodes = atoi(val);
	else if (!strcmp(tok, "rtMaxRefs"))
		pGeek->m_rtMaxRefs = atoi(val);
	else if (!strcmp(tok, "storeDataFile"))
		pGeek->m_storeDataFile = val;
	else if (!strcmp(tok, "streamBufferSize"))
		pGeek->m_streamBufferSize = atoi(val);
	else if (!strcmp(tok, "storeCipherName"))
		pGeek->m_storeCipherName = val;
	else if (!strcmp(tok, "storeCipherWidth"))
		pGeek->m_storeCipherWidth = atoi(val);
	// Servlets & FProxy
	else if (!strcmp(tok, "services"))
	{
		pFProxy->m_bfproxyservice = (strstr(_strupr(val),"FPROXY"))?true:false;
		pDiagnostics->m_nodestatusservlet = (strstr(_strupr(val),"NODESTATUS"))?TRUE:FALSE;
	}
	else if (!strcmp(tok, "fproxy.class"))
		pFProxy->m_fproxyclass = val;
	else if (!strcmp(tok, "fproxy.port"))
		pFProxy->m_fproxyport = atoi(val);
	else if (!strcmp(tok, "fproxy.params.insertHtl"))
		pFProxy->m_fproxyinserthtl = atoi(val);
	else if (!strcmp(tok, "fproxy.params.requestHtl"))
		pFProxy->m_fproxyrequesthtl = atoi(val);
	else if (!strcmp(tok, "fproxy.params.filter"))
		pFProxy->m_bfproxyfilter = atobool(val);
	else if (!strcmp(tok, "fproxy.params.passThroughMimeTypes"))
		pFProxy->m_strfproxyallowedmime = val;
	else if (!strcmp(tok, "fproxy.params.pollForDroppedConnection"))
		pFProxy->m_fproxy_pollDroppedConnection = atobool(val);
	else if (!strcmp(tok, "fproxy.params.splitFileRetryHtlIncrement"))
		pFProxy->m_fproxy_splitinchtl = atoi(val);
	else if (!strcmp(tok, "fproxy.params.splitFileRetries"))
		pFProxy->m_fproxy_splitretries = atoi(val);
	else if (!strcmp(tok, "fproxy.params.splitFileThreads"))
		pFProxy->m_fproxy_splitthreads = atoi(val);
	else if (!strcmp(tok, "logFile"))
		pDiagnostics->m_logFile = val;
	else if (!strcmp(tok, "logLevel"))
		pDiagnostics->m_logLevel = val;
	else if (!strcmp(tok, "logFormat"))
		pDiagnostics->m_logFormat = val;
	else if (!strcmp(tok, "diagnosticsPath"))
		pDiagnostics->m_diagnosticsPath = val;
	else if (!strcmp(tok, "doDiagnostics"))
		pDiagnostics->m_doDiagnostics = atobool(val);
	else if (!strcmp(tok, "nodestatus.port"))
		pDiagnostics->m_nodestatusport = atoi(val);
	else if (!strcmp(tok, "nodestatus.class"))
		pDiagnostics->m_nodestatusclass = val;

	else
	{
		// Add to 'unknown parameters' list
		UnknownParms += tok;
		UnknownParms += "=";
		UnknownParms += val;
		UnknownParms += "\n";
	}
}


BOOL CConfigFile::atobool(char *buf)
{
	if (!stricmp(buf, "true") || !stricmp(buf, "yes"))
		return TRUE;
	else
		return FALSE;
}


// split a line of the form 'key [= value] into the key/value pair

char *CConfigFile::splitLine(char *buf)
{
    char *eq;
    char *s, *s1;

	// delete leading whitespace
	for (s = buf; *s; s++)
	{
		if (*s == '#')
			return NULL;
		else if (*s == ' ' || *s == '\t')
			continue;
		else
			break;
	}
	if (strlen(s) > 0)
		strcpy(buf, s);
	else
		return NULL;

	// delete trailing line terminators
	s = buf + strlen(buf) - 1;
	while (*s == '\r' || *s == '\n')
		*s-- = '\0';

	// bail if no value given
    if ((eq = strchr(buf, '=')) == NULL)
        return NULL;

	// split line
    *eq = '\0';

    // delete whitespace after key
    for (s = eq - 1; strchr(" \t", *s) != NULL; s--)
        *s = '\0';

    // delete whitespace before value
    for (s = eq + 1; strchr(" \t", *s) != NULL; s++)
        ;

    // bail if nothing left in value
    if (*s == '\0')
        return NULL;

    // delete whitespace after value
    for (s1 = s + strlen(s) - 1; strchr(" \t", *s1) != NULL; s1--)
        *s1 = '\0';

	// found a value assigned to keyword - return it
    return (strlen(s1) > 0) ? s : NULL;
}


