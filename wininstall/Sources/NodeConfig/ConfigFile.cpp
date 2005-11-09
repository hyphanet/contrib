// ConfigFile.cpp: implementation of the CConfigFile class.
//
//////////////////////////////////////////////////////////////////////

#include "stdafx.h"
#include "NodeConfig.h"
#include "ConfigFile.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "PropGeek.h"
#include "PropFproxy.h"
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

CString getTempDir()
{
	TCHAR tchBuf[MAX_PATH];
	CString dir;
	if(!GetTempPath(MAX_PATH, tchBuf))
		return ""; // it failed (how?)
	dir = tchBuf;

	// add a "\freenet" to the end of the dir
	dir += "freenet\\";

	return dir;
}	

//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////

CConfigFile::CConfigFile()
{
	 char name[255];
     //CString ip;
     PHOSTENT hostinfo;

	//Try to guess the own host name
	pNormal->m_strHiddenNodeAddress = "localhost";

	if(!gethostname ( name, sizeof(name)))
    {
		if(hostinfo = gethostbyname(name))
        {
			pNormal->m_strHiddenNodeAddress = hostinfo->h_name;
            //ip = inet_ntoa (*(struct in_addr *)*hostinfo->h_addr_list);
        }
    }

	if (!IsValidNodeAddress(pNormal->m_strHiddenNodeAddress))
	{
		pNormal->m_strHiddenNodeAddress = "localhost";
	}
}


CConfigFile::~CConfigFile()
{

}

bool CConfigFile::IsValidNodeAddress(CString & strNodeAddress)
{
	if(strNodeAddress != "localhost" && strNodeAddress.Find('.') == -1)
	{
		return false;
	}
	return true;
}

void CConfigFile::Load()
{
	FILE *fp;
	char buf[4096];
	char *pValue;

	OSVERSIONINFOEX os_version;	// For OS version detection
	BOOL bOsVersionInfoEx;

	/////////////////////////////
	//
	// Create default values

	// Normal tab
	// pNormal->m_importNewNodeRef.EnableWindow(false);
	// propose 20% of the free disk space, but min 256MB
	ULARGE_INTEGER FreeBytes,TotalBytes;

	// first calculate disk space -
	// - use obvious GetDiskFreeSpaceEx if available, else fall back
	//   to GetDiskFreeSpace for compatibility with Win95 original versions
	HINSTANCE hKernel32 = LoadLibrary("kernel32.dll");
	GETDISKFREESPACEEX_ *pGetDiskFreeSpaceEx = NULL;
	if (hKernel32 != NULL)
	{
		// got run time link to kernel32 (I can't see this ever failing!)
		pGetDiskFreeSpaceEx = (GETDISKFREESPACEEX_ *)(GetProcAddress(hKernel32,"GetDiskFreeSpaceExA"));
	}
	if (pGetDiskFreeSpaceEx != NULL)
	{
		// Have win95 osr2 or newer - can use GetDiskFreeSpaceEx function
		pGetDiskFreeSpaceEx(NULL,&FreeBytes,&TotalBytes,NULL);
	}
	else
	{
		DWORD dwSectorsPerCluster,dwBytesPerSector,dwNumFreeClusters,dwTotalClusters;
		GetDiskFreeSpace(NULL,&dwSectorsPerCluster,&dwBytesPerSector,&dwNumFreeClusters,&dwTotalClusters);
		// Following calculations are guaranteed to never overflow 32 bits
		// (GetDiskFreeSpace returns values deliberately fiddled so that below calculations
		//  never yield more than 2GB disk size)
		FreeBytes.QuadPart = dwBytesPerSector * dwSectorsPerCluster;
		TotalBytes.QuadPart = FreeBytes.QuadPart * dwTotalClusters;
		FreeBytes.QuadPart *= dwNumFreeClusters;
	}
	if (hKernel32 != NULL)
	{
		FreeLibrary(hKernel32);
		hKernel32=NULL;
	}
	
	// our variable m_storeSize is in Megabytes so we need to divide by 2^20
	// i.e. shift FreeBytes right 20 bits
	pNormal->m_storeSize = __max(256,(DWORD)(Int64ShrlMod32(FreeBytes.QuadPart,20))/5);
	pNormal->m_storeFile = "";

	pNormal->m_tempFile = getTempDir();
	pNormal->m_useDefaultNodeRefs = FALSE; // this will be modified in the ctor of CPropNormal
	pNormal->m_transient = NOT_TRANSIENT;
	// the ipAddrress is determined in the constructor: pNormal->m_ipAddress;
	srand(time(NULL));
	pNormal->m_listenPort = rand() + 1024;	// random port number

	// Advanced tab
	pAdvanced->m_adminPassword = "";
	pAdvanced->m_adminPeer = "";
	pAdvanced->m_bandwidthLimit = 0;
	pAdvanced->m_clientPort = 8481;
	pAdvanced->m_doAnnounce = false;
	pAdvanced->m_fcpHosts = "127.0.0.1,localhost";
	pAdvanced->m_initialRequestHTL = 15;
	pAdvanced->m_inputBandwidthLimit = 0;
	pAdvanced->m_maxHopsToLive = 25;
	pAdvanced->m_maximumThreads = 120;


	// Bob H 1/06/05: See what kind of windows we're on to decide on maxNodeConnections. Previously
	// we always defaulted to 60 even on NT / 2000 / XP which have considerably better networking.
	//
	// Init OS version info structure
	ZeroMemory( &os_version, sizeof(OSVERSIONINFOEX) );
	os_version.dwOSVersionInfoSize = sizeof( OSVERSIONINFOEX );
	
	// GetVersionEx can only handle an OSVERSIONINFOEX structure on NT 4 SP6 and later, so see if
	// call worked and fall back to OSVERSIONINFO if not.
	if( !(bOsVersionInfoEx = GetVersionEx ((OSVERSIONINFO *) &os_version)) )
	{
		os_version.dwOSVersionInfoSize = sizeof (OSVERSIONINFO);	// failed, try OSVERSIONINFO
		if (! GetVersionEx ( (OSVERSIONINFO *) &os_version) )
			os_version.dwPlatformId = -1;			// if even _that_ doesn't work who knows what we're running on,
	}												// maybe a broken WINE :) Set version to -1 to indicate error ...

	CString msg = "";
	switch ( os_version.dwPlatformId )	// What "family" of windows are we on?
	{
		// NT, 2000, XP, Server 2003. 
		// OSVERSIONEX documentation implies this may also apply for "and later" versions too
		// e.g. Longhorn. Anyway, all these ought to be able to handle 200 connections.
		// Apparently this should work on XP Pro 64 bit too (untested.)
		case VER_PLATFORM_WIN32_NT:
			pAdvanced->m_maxNodeConnections = 200;
			break;

		// 95, 98, Me. These have crappy stacks so use old limit of 60.
		case VER_PLATFORM_WIN32_WINDOWS:
			pAdvanced->m_maxNodeConnections = 60;
			break;

		// wtf, they seem to be kicking it old skool with Win32s ?!?
		// If someone actually manages to get Freenet working on Win 3.1 I will be impressed :)
		case VER_PLATFORM_WIN32s:
	pAdvanced->m_maxNodeConnections = 60;
			msg = "Warning, you appear to be trying to run Freenet on a 16-bit version of Windows,\n";
			msg += "such as Windows 3.11.\n\n";
			msg += "This is VERY unlikely to work!\n\n";
			msg += "We recommend you upgrade to a modern operating system, such as Windows XP or Linux.\n";
			AfxMessageBox( msg );
			break;

		// Get here if both GetVersionEx calls failed or we otherwise get an unknown platform ID
		default:
			pAdvanced->m_maxNodeConnections = 200;
			msg = "I couldn't tell what version of Windows you are using.\n";
			msg	+= "I will set maximum node connections to 200.\n\n";
			msg	+= "This will probably be OK, but please report this message and what kind of Windows\n";
			msg	+= "you are using to devl@freenetproject.org so we can fix the installer. Thanks!\n";
			AfxMessageBox( msg, MB_OK|MB_ICONINFORMATION );
			break;
	}


	pAdvanced->m_outputBandwidthLimit = 0;
	pAdvanced->m_seedFile = "seednodes.ref";
	pAdvanced->SetCPUPrioritySlider(THREAD_PRIORITY_IDLE, IDLE_PRIORITY_CLASS);

	// Geek tab
	pGeek->m_announcementAttempts = 10;
	pGeek->m_announcementDelay = 1800000;
	pGeek->m_announcementDelayBase = 2;
	pGeek->m_announcementPeers = 3;
	pGeek->m_authTimeout = 30000;
	pGeek->m_checkPointInterval = 1200;
	pGeek->m_connectionTimeout = 600000;
	pGeek->m_hopTimeDeviation = 4000;
	pGeek->m_hopTimeExpected = 7000;
	pGeek->m_initialRequests = 10;
	pGeek->m_localAnnounceTargets = "";
	pGeek->m_messageStoreSize = 50000;
	pGeek->m_blockSize = 4096;
	pGeek->m_maximumPadding = 65536;
	pGeek->m_routeConnectTimeout = 10000;
	pGeek->m_rtMaxNodes = 50;
	pGeek->m_rtMaxRefs = 50;
	pGeek->m_storeType = "freenet";
	pGeek->m_storeDataFile = "";
	pGeek->m_streamBufferSize = 16384;
	pGeek->m_storeCipherName = "Twofish";
	pGeek->m_storeCipherWidth = 128;
	pGeek->m_bAutoIP = TRUE;
	pGeek->m_bAllowNodeAddressChanges = FALSE;

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
	pFProxy->m_bShowNewBuildWarning = TRUE;

	pDiagnostics->m_diagnosticsPath = ".freenet/stats";
	pDiagnostics->m_doDiagnostics = TRUE;
	pDiagnostics->m_logFile = "freenet.log";
	pDiagnostics->m_logLevel = "normal";
	pDiagnostics->m_logFormat = "d (c, t): m";
	pDiagnostics->m_bLogInboundContacts = FALSE;
	pDiagnostics->m_bLogInboundRequests = FALSE;
	pDiagnostics->m_bLogOutboundContacts = FALSE;
	pDiagnostics->m_bLogOutboundRequests = FALSE;
	pDiagnostics->m_nFailureTableEntries = 1000;
	pDiagnostics->m_nFailureTableTimeSeconds = 1800;

	// the following are fixed up later as the obsolete
	// "nodestatus" settings are absorbed and converted by NodeConfig
	pDiagnostics->m_nodeinfoservlet = true;
	pDiagnostics->m_nodeinfoport = -1;
	pDiagnostics->m_nodeinfoclass = "";

	// Reset unknown parameters container
	pGeek->m_unknowns = "";


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
		while (fgets(buf, 4095, fp) != NULL)
		{
			// Split line into token and value
			if ((pValue = splitLine(buf)) == NULL)
				// line is empty, or a comment, or had no value
				continue;

			processItem(buf, pValue);
		}
		fclose(fp);
	}

	/////////////////////////////////
	//
	// Fix up any known compatibility changes
	//
	if ( (pDiagnostics->m_nodeinfoclass.Compare("")==0) ||
		 (pDiagnostics->m_nodeinfoclass.Compare("freenet.client.http.NodeStatusServlet")==0) ||
		 (pDiagnostics->m_nodeinfoport == -1) )
	{
		pDiagnostics->m_nodeinfoclass = "freenet.node.http.NodeInfoServlet";
		pDiagnostics->m_nodeinfoport = 8890;
	}

	/////////////////////////////////
	//
	// Load any additional settings from FLaunch.ini
	//

	ReadFLaunchIni();

}

void CConfigFile::Save()
{
	// TODO: fix localhost bug easier *HACK*
	/*
	if(pNormal->m_ipAddress == "localhost")
		pNormal->m_transient = TRANSIENT;
	*/

	// GOD THIS IS A FREAKING HACK. FIXME.
	if(pNormal->m_storeFile != "") {
		CString lower(pNormal->m_storeFile);
		lower.MakeLower();
		if(lower.Find("freenet") == -1) {
			if(pNormal->m_storeFile.Right(1) != "/" || pNormal->m_storeFile.Right(1) != "\\")
				pNormal->m_storeFile += "/";
			pNormal->m_storeFile += "freenet/";
		}
	}

	UpdateFLaunchIni();

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

	fprintf(fp, "[Node Config]\n");
	fprintf(fp, "# Freenet configuration file\n");
	fprintf(fp, "# This file was automatically generated by WinConfig on %s\n", _strdate(datestr));
	fprintf(fp, "\n");
	fprintf(fp, "[Freenet Node]\n");
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

	// storeSize ... our variable m_storeSize is in Megabytes so append an M
	fprintf(fp, "storeSize=%luM\n", pNormal->m_storeSize );
	fprintf(fp, "\n");
	fprintf(fp, "# The path to a single file (including file name, or a comma-separated list of files,\n");
	fprintf(fp, "# containing the data store.  The size of each file is given by <storeSize>.\n");
	fprintf(fp, "# Defaults to cache_<port> in the main freenet directory.\n");
	fprintf(fp, "%sstoreFile=%s\n",pNormal->m_storeFile.GetLength()?"":"#", pNormal->m_storeFile);

	fprintf(fp, "\n");
	fprintf(fp, "\n");
	fprintf(fp, "# The port to listen for incoming FNP (Freenet Node Protocol) connections on.\n");
	fprintf(fp, "listenPort=%d\n", pNormal->m_listenPort);
	fprintf(fp, "\n");
	fprintf(fp, "# The I.P. address of this node as seen by the public internet.\n");
	fprintf(fp, "# This is needed in order for the node to determine its own\n");
	fprintf(fp, "# NodeReference.\n");
	if (!IsValidNodeAddress(pNormal->m_ipAddress))
	{
		if (pNormal->m_ipAddress.CompareNoCase("AUTOMATIC")==0)
		{
			fprintf(fp, "ipAddress=\n"); // auto IP detection is turned on in config file by specifying a blank ip address
		}
		else
		{
			fprintf(fp, "ipAddress=localhost\n"); // invalid IP address so guess it's localhost
		}
	}
	else
	{
		if (pGeek->m_bAutoIP)
		{
			fprintf(fp, "ipAddress=\n"); // auto IP detection is turned on in config file by specifying a blank ip address
		}
		else
		{
			// a real valid IP or domain address:
			fprintf(fp, "ipAddress=%s\n", pNormal->m_ipAddress.GetBuffer(1));
		}
	}
	fprintf(fp, "# Transient nodes do not give out references to themselves, and should\n");
	fprintf(fp, "# therefore not receive any requests.  Set this to yes only if you are\n");
	fprintf(fp, "# on a slow, non-permanent connection.\n");
	fprintf(fp, "transient=%s\n", (pNormal->m_transient == TRANSIENT) ? "true" : "false");
	fprintf(fp, "\n");
	fprintf(fp, "# The directory to store any temporary files created by the node. It gets deleted\n");
	fprintf(fp, "# automatically on node start and stop.\n");
	fprintf(fp, "tempDir=%s\n", pNormal->m_tempFile);
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
	if (pAdvanced->m_adminPeer.GetLength() == 0)
		fprintf(fp, "#adminPeer=\n");
	else
		fprintf(fp, "adminPeer=%s\n", pAdvanced->m_adminPeer.GetBuffer(1));
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
	fprintf(fp, "# The number of references allowed in the routing table per node.  This\n");
	fprintf(fp, "# should not be set too high.  It is suggested to leave it at 100 for now.\n");
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
	fprintf(fp, "# The type of store we have (this text will get clearer soon).\n");
	fprintf(fp, "storeType=%s\n", pGeek->m_storeType);
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
	fprintf(fp, "logInboundContacts=%s\n",pDiagnostics->m_bLogInboundContacts?"true":"false");
	fprintf(fp, "logOutboundContacts=%s\n",pDiagnostics->m_bLogOutboundContacts?"true":"false");
	fprintf(fp, "logInboundRequests=%s\n",pDiagnostics->m_bLogInboundRequests?"true":"false");
	fprintf(fp, "logOutboundRequests=%s\n",pDiagnostics->m_bLogOutboundRequests?"true":"false");
	fprintf(fp, "\n\n");

	fprintf(fp, "########################\n");
	fprintf(fp, "# Services & Servlets\n");
	fprintf(fp, "########################\n");

	fprintf(fp, "\n");

	fprintf(fp, "# this line is deliberately commented out to let fred choose the defaults\n");
	fprintf(fp, "%services=mainport,distribution\n");
	fprintf(fp, "\n");

	// Mainport settings
	fprintf(fp, "########################\n");
	fprintf(fp, "# Mainport settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "mainport.class=freenet.interfaces.servlet.MultipleHttpServletContainer\n");
	fprintf(fp, "mainport.port=%d\n",pFProxy->m_fproxyport);
	fprintf(fp, "\n");

	// Fproxy settings
	fprintf(fp, "########################\n");
	fprintf(fp, "# FProxy settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "mainport.params.servlet.1.params.insertHtl=%d\n",pFProxy->m_fproxyinserthtl);
	fprintf(fp, "mainport.params.servlet.1.params.requestHtl=%d\n",pFProxy->m_fproxyrequesthtl);
	fprintf(fp, "mainport.params.servlet.1.params.filter=%s\n",pFProxy->m_bfproxyfilter?"true":"false");
	fprintf(fp, "mainport.params.servlet.1.params.passThroughMimeTypes=%s\n",pFProxy->m_strfproxyallowedmime);
	fprintf(fp, "mainport.params.servlet.1.params.pollForDroppedConnection=%s\n",pFProxy->m_fproxy_pollDroppedConnection?"true":"false");
	fprintf(fp, "mainport.params.servlet.1.params.splitFileRetryHtlIncrement=%d\n",pFProxy->m_fproxy_splitinchtl);
	fprintf(fp, "mainport.params.servlet.1.params.splitFileRetries=%d\n",pFProxy->m_fproxy_splitretries);
	fprintf(fp, "mainport.params.servlet.1.params.splitFileThreads=%d\n",pFProxy->m_fproxy_splitthreads);
	fprintf(fp, "mainport.params.servlet.1.params.showNewBuildWarning=%s\n", pFProxy->m_bShowNewBuildWarning?"true":"false");
	fprintf(fp, "\n");

	// Node info servlet settings
	fprintf(fp, "########################\n");
	fprintf(fp, "# Node information servlet settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "mainport.params.servlet.2.params.class=%s\n",pDiagnostics->m_nodeinfoclass);
	fprintf(fp, "mainport.params.servlet.2.params.port=%d\n",pDiagnostics->m_nodeinfoport);
	fprintf(fp, "failureTableSize=%d\n",pDiagnostics->m_nFailureTableEntries);
	fprintf(fp, "failureTableTime=%lu000\n",pDiagnostics->m_nFailureTableTimeSeconds);
	fprintf(fp, "\n");

/*	// FIXME: Node status settings (hardcoded for now) - automatically stripped on read due to it having been switched to nodeinfo previously.
	fprintf(fp, "########################\n");
	fprintf(fp, "# Node status servlet settings\n");
	fprintf(fp, "########################\n");
	fprintf(fp, "nodestatus.class=freenet.client.http.NodeStatusServlet\n");
	fprintf(fp, "nodestatus.port=8889\n");
	fprintf(fp, "\n");
*/

	// Write out unknown parameters
	if (pGeek->m_unknowns.GetLength() > 0)
	{
		fprintf(fp, "# Unknown parameters - these are not yet known or handled by the NodeConfig\n");
		fprintf(fp, "# utility, but are assumed to be valid and understandable to the node\n");
		fprintf(fp, "# if you see this in the file, then please email the parameters following\n");
		fprintf(fp, "# this comment header to devl@freenetproject.org, to prompt the developers\n");
		fprintf(fp, "# into updating this configuration utility - thanks\n");
		fprintf(fp, "\n");
		fprintf(fp, "%s\n", pGeek->m_unknowns.GetBuffer(0));
	}

	fclose(fp);
}


void CConfigFile::ReadFLaunchIni(void)
{
	DWORD dwPriority = GetPrivateProfileInt("Freenet Launcher", "Priority", THREAD_PRIORITY_IDLE, FLaunchIniFileName);
	DWORD dwPriorityClass = GetPrivateProfileInt("Freenet Launcher", "PriorityClass", IDLE_PRIORITY_CLASS, FLaunchIniFileName);
	pAdvanced->SetCPUPrioritySlider(dwPriority,dwPriorityClass);
}

void CConfigFile::UpdateFLaunchIni(void)
{
	DWORD dwPriority;
	DWORD dwPriorityClass;
	char szNumber[16];

	pAdvanced->GetCPUPrioritySlider(dwPriority,dwPriorityClass);
	sprintf(szNumber,"%lu",dwPriority);
	WritePrivateProfileString("Freenet Launcher", "Priority", szNumber, FLaunchIniFileName);
	sprintf(szNumber,"%lu",dwPriorityClass);
	WritePrivateProfileString("Freenet Launcher", "PriorityClass", szNumber, FLaunchIniFileName);
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
		// FIXME - testing explicitly for hardcoded "256M" is sick and twisted
		if(strcmp(val, "256M")==0)
		{
			// default value - we'll use our disk-space guesstimate instead
		}
		else
		{
			DWORDLONG v = _atoi64(val);
			char suffix = val[strspn(val,"0123456789")];
			switch (suffix)
			{
			case 'K':	v /= 1024;						break;
			case 'M':									break;
			case 'G':	v *= 1024;						break;
			case 'T':	v *= 1048576;					break;
			case 'P':	v *= 1073741824;				break;
			case 'E':	v *= 1073741824;	v *= 1024;	break;
			case 'k':	v /= 1000;						break;
			//
			// Bob H note : If the below cases fail to compile with 
			// "error C2520: conversion from unsigned __int64 to double not implemented, use signed __int64"
			// in VS6 then you need VS service pack 5 (NOT 6!) and the 'Processor Pack'.
			// http://msdn.microsoft.com/vstudio/downloads/updates/sp/vs6/sp5/default.aspx
			// http://msdn.microsoft.com/vstudio/downloads/tools/ppack/

			case 'm':	v =  (DWORDLONG)(float(v)*0.9765625);	break;
			case 'g':	v =  (DWORDLONG)(float(v)*976.5625);	break;
			case 't':	v =  (DWORDLONG)(float(v)*976562.5);	break;
			case 'p':	v =  (DWORDLONG)(float(v)*976562500);	break;
			case 'e':	v *= 1000000000; v = (DWORDLONG)(float(v)*0.9765625); break;
			default:	v /= 1048576;	break; // assume bytes
			}
			if (v<=0xffffffff)
				pNormal->m_storeSize = (DWORD)v;
			else
				pNormal->m_storeSize = 0xffffffff;
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
	{
		if (!strcmp(val, ""))
		{
			// Config file uses an empty ip address to indicate 'use automatic IP detection'
			pNormal->m_ipAddress = "AUTOMATIC"; // largely cosmetic
			pGeek->m_bAutoIP = TRUE;
		}
		else
		{
			pNormal->m_ipAddress = val;
			pGeek->m_bAutoIP = FALSE;
		}
		pNormal->m_strHiddenNodeAddress = pNormal->m_ipAddress;
	}

	// FECTempDir and mainport.params.servlet.1.params.tempDir both get put in tempDir

	else if (!strcmp(tok, "FECTempDir"))
		pNormal->m_tempFile = val;
	else if (!strcmp(tok, "mainport.params.servlet.1.params.tempDir"))
		pNormal->m_tempFile = val;
	else if (!strcmp(tok, "tempDir"))
		pNormal->m_tempFile = val;
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
	else if (!strcmp(tok, "adminPeer"))
		pAdvanced->m_adminPeer = val;
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
	else if (!strcmp(tok, "storeType"))
		pGeek->m_storeType = val;
	else if (!strcmp(tok, "streamBufferSize"))
		pGeek->m_streamBufferSize = atoi(val);
	else if (!strcmp(tok, "storeCipherName"))
		pGeek->m_storeCipherName = val;
	else if (!strcmp(tok, "storeCipherWidth"))
		pGeek->m_storeCipherWidth = atoi(val);
	// Servlets & FProxy
	else if (!strcmp(tok, "services"))
	{
		pFProxy->m_bfproxyservice = (strstr(_strupr(val),"MAINPORT"))?true:false;
		pDiagnostics->m_nodeinfoservlet = (strstr(_strupr(val),"MAINPORT"))?TRUE:FALSE;
/*		// absorb obsoleted 'nodestatus' setting - don't set m_nodeinfoservlet to FALSE
		// because that could replace it if the previous line had already set it to TRUE!
		if (strstr(_strupr(val),"NODESTATUS") ) pDiagnostics->m_nodeinfoservlet = TRUE; */

		/* ignore "distribution" for now as it's not configurable yet */
	}
	else if (!strcmp(tok, "mainport.class"))
		pFProxy->m_fproxyclass = val;
	else if (!strcmp(tok, "mainport.port"))
		pFProxy->m_fproxyport = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.insertHtl"))
		pFProxy->m_fproxyinserthtl = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.requestHtl"))
		pFProxy->m_fproxyrequesthtl = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.filter"))
		pFProxy->m_bfproxyfilter = atobool(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.passThroughMimeTypes"))
		pFProxy->m_strfproxyallowedmime = val;
	else if (!strcmp(tok, "mainport.params.servlet.1.params.pollForDroppedConnection"))
		pFProxy->m_fproxy_pollDroppedConnection = atobool(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.splitFileRetryHtlIncrement"))
		pFProxy->m_fproxy_splitinchtl = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.splitFileRetries"))
		pFProxy->m_fproxy_splitretries = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.splitFileThreads"))
		pFProxy->m_fproxy_splitthreads = atoi(val);
	else if (!strcmp(tok, "mainport.params.servlet.1.params.showNewBuildWarning"))
		pFProxy->m_bShowNewBuildWarning = atobool(val);

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

	else if (!strcmp(tok, "logInboundContacts"))
		pDiagnostics->m_bLogInboundContacts = atobool(val);
	else if (!strcmp(tok, "logInboundRequests"))
		pDiagnostics->m_bLogInboundRequests = atobool(val);
	else if (!strcmp(tok, "logOutboundContacts"))
		pDiagnostics->m_bLogOutboundContacts = atobool(val);
	else if (!strcmp(tok, "logOutboundRequests"))
		pDiagnostics->m_bLogOutboundRequests = atobool(val);

		// absorb obsoleted 'nodestatus' settings
	else if (!strcmp(tok, "nodestatus.port"))
	{
		if (pDiagnostics->m_nodeinfoport == -1)
			pDiagnostics->m_nodeinfoport = atoi(val);
	}
	else if (!strcmp(tok, "nodestatus.class"))
	{
		if (!pDiagnostics->m_nodeinfoclass.Compare(""))
			pDiagnostics->m_nodeinfoclass = val;
	}


		// replacement nodeinfo settings
	else if (!strcmp(tok, "nodeinfo.port"))
		pDiagnostics->m_nodeinfoport = atoi(val);
	else if (!strcmp(tok, "nodeinfo.class"))
		pDiagnostics->m_nodeinfoclass = val;

	else if (!strcmp(tok, "failureTableSize"))
		pDiagnostics->m_nFailureTableEntries = atoi(val);
	else if (!strcmp(tok, "failureTableTime"))
		pDiagnostics->m_nFailureTableTimeSeconds = atoi(val)/1000;

	else if (!strcmp(tok, "fproxy.port"))
		pFProxy->m_fproxyport = atoi(val);
	else if (!strcmp(tok, "nodestatus.port"))
		/*eat it*/  ;

/*
	else if (!strstr(tok, "fproxy.")) {
		CString s(tok);
		s.Replace("fproxy.", "mainport.params.servlet.1.");
		this->processItem((char *)(s.GetBuffer()) , val);
	}
	else if (!strstr(tok, "nodeinfo.")) {
		CString s(tok);
		s.Replace("nodeinfo.", "mainport.params.servlet.2.");
		this->processItem((char *)(s.GetBuffer()), val);
	}
	else if (!strstr(tok, "nodestatus.")) {
		Cstring s(tok);
		s.Replace("nodestatus.", "mainport.params.servlet.5.");
		this->processItem((char*)(s.GetBuffer()), val)
	}
*/

	else
	{
		// Add to 'unknown parameters' list
		pGeek->m_unknowns += tok;
		pGeek->m_unknowns += "=";
		pGeek->m_unknowns += val;
		pGeek->m_unknowns += "\n";
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

	// delete trailing line terminators
	s = buf + strlen(buf) - 1;

	while (*s == '\r' || *s == '\n')
		*s-- = '\0';

	// delete leading whitespace
	for (s = buf; *s; s++)
	{
		if (*s == '#')
			return NULL;
		else if (*s == ' ' || *s == '\t')
			continue;
		else if (*s == '%')
			continue;
		else
			break;
	}
	if (strlen(s) > 0)
		strcpy(buf, s);
	else
		return NULL;

	// bail if no value given
    if ((eq = strchr(buf, '=')) == NULL)
        return NULL;

	// split line
    *eq = '\0';

    // delete whitespace after key
    for (s = eq - 1; strchr(" \t", *s) != NULL; s--)
        *s = '\0';

	// catch if ends with =
	if(strlen(eq + 1) == 0)
		return NULL;

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
