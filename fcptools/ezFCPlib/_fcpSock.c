
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

#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>


/*
  IMPORTED DECLARATIONS
*/
extern char _fcpHost[];
extern int  _fcpPort;
extern int	_fcpNumOpenSockets;


struct sockaddr_in server;
struct hostent* hp;


/* Portable version
*/

int _fcpSockInit()
{
  server.sin_family=AF_INET;
  server.sin_port=htons((unsigned short)_fcpPort);

  hp=gethostbyname(_fcpHost);

  if(!hp) {
	 unsigned long int addr=inet_addr(_fcpHost);
	 
	 if(addr!=-1) hp=gethostbyaddr((char*)addr,sizeof(addr),AF_INET);
		
	 if(!hp) {
		if(errno!=ETIMEDOUT)
		  errno=-1; /* use h_errno */
		_fcpLog(FCP_LOG_CRITICAL, "Unknown host '%s'", _fcpHost);
		return(-1);
	 }
  }
  
  memcpy((char*)&server.sin_addr,(char*)hp->h_addr,sizeof(server.sin_addr));
  
  return 0;
}


int _fcpSockConnect(HFCP *hfcp)
{
  int Status;


  hfcp->conn.socket = socket(PF_INET,SOCK_STREAM,0);
  if(hfcp->conn.socket == -1) {
	 _fcpLog(FCP_LOG_CRITICAL, "Cannot create client socket.");
	 return -1;
  }
  
  Status = connect(hfcp->conn.socket, (struct sockaddr *)&server, sizeof(server));

  if(Status < 0) {
	 close(hfcp->conn.socket);
	 hfcp->conn.socket = -1;
	 
	 _fcpLog(FCP_LOG_CRITICAL, "Connect fail.");
  }

  if (Status < 0) return -1;

  hfcp->conn.response.body.datachunk.data = NULL;
  hfcp->conn.response.body.datachunk.dataptr = NULL;
  hfcp->conn.response.body.datachunk.length = 0;
  hfcp->conn.response.body.keypair.privkey = NULL;
  hfcp->conn.response.body.keypair.pubkey = NULL;
  hfcp->conn.response.body.keypair.uristr = NULL;

  // OK - we're in :)
  _fcpNumOpenSockets++;
  _fcpLog(FCP_LOG_DEBUG, "%d open sockets", _fcpNumOpenSockets);
  
  return 0;
}       // 'fcpSockConnect()'


void _fcpSockDisconnect(HFCP *hfcp)
{
  close(hfcp->conn.socket);

  hfcp->conn.socket = -1;

  _fcpNumOpenSockets--;
  _fcpLog(FCP_LOG_DEBUG, "%d open sockets", _fcpNumOpenSockets);
}


int _fcpSockReceive(HFCP *hfcp, char *buf, int len)
{
  return read(hfcp->conn.socket, buf, len);
}


int _fcpSockSend(HFCP *hfcp, char *buf, int len)
{
  return write(hfcp->conn.socket, buf, len);
}
