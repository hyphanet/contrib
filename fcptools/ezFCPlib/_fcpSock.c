/*
	This code is part of FreeWeb - an FCP-based client for Freenet

	sockadDesigned and implemented by David McNab, david@rebirthing.co.nz
	CopyLeft (c) 2001 by David McNab

	The FreeWeb website is at http://freeweb.sourceforge.net
	The website for Freenet is at http://freenet.sourceforge.net

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

#ifndef WINDOWS
#include <sys/types.h>
#include <sys/time.h>
#include <sys/socket.h>

#include <unistd.h>
#endif

#include "ezFCPlib.h"

#ifndef WINDOWS
#include <netinet/in.h>
#include <arpa/inet.h>

#include <netdb.h>
#endif

#include <errno.h>
#include <fcntl.h>

/*
	IMPORTED DECLARATIONS
*/
extern char *_fcpHost;
extern int   _fcpPort;
extern int	 _fcpNumOpenSockets;


#ifdef WINDOWS
static int GetAddr(const char* HostName, int Port, struct sockaddr* Result);
static struct sockaddr  _fcpSockAddr;
#else
struct sockaddr_in server;
struct hostent* hp;
#endif


#ifdef WINDOWS

/* windows version */

int _fcpSockInit()
{
  WORD wVersionRequested;
  WSADATA wsaData;
  
  /* start up sockets iterface just in case it hasn't been done already */
  SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
  wVersionRequested = MAKEWORD(2, 0);
  if (WSAStartup(wVersionRequested, &wsaData) != 0)
    return -1;
  
  /* locate Freenet FCP port */
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

/* unix version */

int _fcpSockInit()
{
  server.sin_family=AF_INET;
  server.sin_port=htons((unsigned short)_fcpPort);
	
  hp=gethostbyname(_fcpHost);
	
	if(!hp)
    {
			long int addr=inet_addr(_fcpHost);
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
