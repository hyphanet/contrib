/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"

#include <signal.h>

/*
  Imported Declarations
*/

extern char  _fcpHost[];
extern int   _fcpPort;
extern int   _fcpHtl;
extern int   _fcpRawMode;
extern char  _fcpID[];
extern int   _fcpRegress;


/*
  Function:    fcpStartup()

  Arguments: host       string containing hostname. NULL arg uses default of "127.0.0.1"
             port       port number to communicate with FCP on. <= 0 defaults to Freenet standard 8082
             defaultHtl default hops to live. if 0, gets set to EZFCP_HTL_DEFAULT
             raw        set to disable automatic metadata handling
             maxSplitThreads	maximum number of splitfile insert threads,
                              if 0 defaults to FCP_MAX_SPLIT_THREADS

  Returns:  0 if successful
            -1 if failed
*/

int fcpStartup(char *host, int port, int defaultHtl, int raw, int maxSplitThreads)
{
  //sigset_t sigset;

  HFCP hfcpBlk;
  HFCP *hfcp = &hfcpBlk;

  char *handshake = "ClientHello\nEndMessage\n";
  int  n;
  int  len;

  // set global parms
  strncpy(_fcpHost, host ? host: EZFCP_DEFAULT_HOST, L_HOST);
  _fcpPort = (port > 0) ? port : EZFCP_DEFAULT_PORT;
  _fcpHtl = (defaultHtl >= 0) ? defaultHtl : EZFCP_DEFAULT_HTL;
  _fcpRawMode = (raw > 0) ? 1 : 0;
  _fcpRegress = EZFCP_DEFAULT_REGRESS;

  _fcpLog(FCP_LOG_DEBUG, "fcpStartup: begin");

  if (_fcpSockInit() != 0) return -1;

  // Create temporary handle
  fcpInitHandle(hfcp);

  // try a handshake
  if (_fcpSockConnect(hfcp) != 0) return -1;

  len = strlen(handshake);
  _fcpLog(FCP_LOG_DEBUG, "sending fcp id bytes");
  n = _fcpSockSend(hfcp, _fcpID, 4);

  if (n < 4) {
	 _fcpLog(FCP_LOG_CRITICAL, "failed to send ID bytes");
	 return -1;
  }
  _fcpLog(FCP_LOG_DEBUG, "sending handshake...");
  
  n = _fcpSockSend(hfcp, handshake, len);
  if (n < len) {

#ifdef WINDOWS
	 int err = WSAGetLastError();
	 _fcpLog(FCP_LOG_CRITICAL, "Error %d sending handshake", err);
#endif
	 _fcpSockDisconnect(hfcp);
	 return -1;
  }

  _fcpLog(FCP_LOG_DEBUG, "fcpStartup: awaiting response");
  
  if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_HELLO) {
	 _fcpSockDisconnect(hfcp);
	 return -1;
  }
  
  _fcpLog(FCP_LOG_DEBUG, "fcpStartup: got response");
  
  _fcpSockDisconnect(hfcp);
  
  // All ok - now fire up splitfile insert manager
  _fcpInitSplit(maxSplitThreads);
  
  // success
  return 0;
  
} // 'fcpStartup()'
