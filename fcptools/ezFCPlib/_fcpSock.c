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

#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <unistd.h>

#endif

#include "ezFCPlib.h"


//
// IMPORTED DECLARATIONS
//

extern char _fcpHost[];
extern int  _fcpPort;

extern int	_fcpNumOpenSockets;


#ifdef WINDOWS
// windows
static int GetAddr(const char* HostName, int Port, struct sockaddr* Result);
static struct sockaddr  _fcpSockAddr;
#else
// unix
struct sockaddr_in server;
struct hostent* hp;
#endif


#ifdef WINDOWS

// windows version

int _fcpSockInit()
{
    WORD wVersionRequested;
    WSADATA wsaData;

    // start up sockets iterface just in case it hasn't been done already
    SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
    wVersionRequested = MAKEWORD(2, 0);
    if (WSAStartup(wVersionRequested, &wsaData) != 0)
        return -1;

    // locate Freenet FCP port
    if (GetAddr(_fcpHost, _fcpPort, &_fcpSockAddr) == 0)
        return -1;

    return 0;
}


static int GetAddr(const char* HostName, int Port, struct sockaddr* Result)
{
    struct hostent*     Host;
    SOCKADDR_IN         Address;

    memset(Result, 0, sizeof(*Result));
    memset(&Address, 0, sizeof(Address));

    Host = gethostbyname(HostName);
    if(Host != NULL)
    {
        Address.sin_family  = AF_INET;
        Address.sin_port    = htons((short)Port);
        memcpy(&Address.sin_addr, Host->h_addr_list[0], Host->h_length);
        memcpy(Result, &Address, sizeof(Address));
    }
    return Host != NULL;
}


#else

// unix version

int _fcpSockInit()
{
  server.sin_family=AF_INET;
  server.sin_port=htons((unsigned short)_fcpPort);

  hp=gethostbyname(_fcpHost);

    if(!hp)
    {
	 unsigned long int addr=inet_addr(_fcpHost);
        if(addr!=-1)
            hp=gethostbyaddr((char*)addr,sizeof(addr),AF_INET);
	 
        if(!hp)
        {
		if(errno!=ETIMEDOUT)
		  errno=-1; /* use h_errno */
		_fcpLog(FCP_LOG_CRITICAL, "Unknown host '%s'", _fcpHost);
		return(-1);
	 }
  }

  memcpy((char*)&server.sin_addr,(char*)hp->h_addr,sizeof(server.sin_addr));
  
  return 0;
}


#endif


int _fcpSockConnect(HFCP *hfcp)
{
  int Status;
    ////int s;

#ifdef WINDOWS
    hfcp->conn.socket = socket(AF_INET, SOCK_STREAM, 0);
    Status = connect(hfcp->conn.socket, &_fcpSockAddr, sizeof(_fcpSockAddr));
#else
  hfcp->conn.socket = socket(PF_INET,SOCK_STREAM,0);
    if(hfcp->conn.socket == -1)
    {
	 _fcpLog(FCP_LOG_CRITICAL, "Cannot create client socket.");
	 return -1;
  }


  Status = connect(hfcp->conn.socket, (struct sockaddr *)&server, sizeof(server));

    if(Status < 0)
    {
	 close(hfcp->conn.socket);
	 hfcp->conn.socket = -1;

        _fcpLog(FCP_LOG_CRITICAL, "Connect fail.");
  }

#endif

    if (Status < 0)
        return -1;

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

}       // 'fcpConnect()'


void _fcpSockDisconnect(HFCP *hfcp)
{
#ifdef WINDOWS
    closesocket(hfcp->conn.socket);
#else
  close(hfcp->conn.socket);
#endif
  hfcp->conn.socket = -1;

  _fcpNumOpenSockets--;
  _fcpLog(FCP_LOG_DEBUG, "%d open sockets", _fcpNumOpenSockets);

}       // 'fcpClose()'


int _fcpSockReceive(HFCP *hfcp, char *buf, int len)
{
#ifdef WINDOWS
    return recv(hfcp->conn.socket, buf, len, 0);
#else
  return read(hfcp->conn.socket, buf, len);
#endif
}


int _fcpSockSend(HFCP *hfcp, char *buf, int len)
{
#ifdef WINDOWS
  return send(hfcp->conn.socket, buf, len, 0);
#else
  return write(hfcp->conn.socket, buf, len);
#endif
}
