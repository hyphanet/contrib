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

#include <sys/types.h>
#include <sys/stat.h>

#include <fcntl.h>
#include <stdio.h>

extern char   *_fcpHost;
extern int     _fcpPort;
extern int     _fcpHtl;
extern int     _fcpRawMode;
extern char    _fcpID[];

static int      fcpCloseKeyRead(hFCP *hfcp);
static int      fcpCloseKeyWrite(hFCP *hfcp);

/*
  Function:    fcpCloseKey()

  Arguments:   hfcp

  Returns:     0 if successful
  -1 if error occurred

  Description: finalises all operations on a key, depending on whether the
  key was opened for reading or writing
*/
int fcpCloseKey(hFCP *hfcp)
{
  if (hfcp->key->openmode & _FCP_O_READ)
	 return fcpCloseKeyRead(hfcp);
  else if (hfcp->key->openmode & _FCP_O_WRITE)
	 return fcpCloseKeyWrite(hfcp);
  else
	 return -1;
}


/*
  Function:    fcpCloseKeyRead()

  Arguments:   hfcp

  Description: closes a key after reading is complete
*/
static int fcpCloseKeyRead(hFCP *hfcp)
{
  hfcp->key->openmode = 0;
  _fcpSockDisconnect(hfcp);

  return 0;
}


/*
  Function:    fcpCloseKeyWrite()

  Arguments:   hfcp

  Description: closes a key's data and metadata temporary files
  performs the full insertion protocol sequence
  deletes the temporary files used
*/

/*
static int fcpCloseKeyWrite(hFCP *hfcp)
{
  char buf[1024];
  int fd, count, n;

  close(hfcp->wr_info.fd_data);
  if (hfcp->raw) close(hfcp->wr_info.fd_meta);

  if (_fcpSockConnect(hfcp) != 0) return -1;

  _fcpSockSend(hfcp, _fcpID, 4);

  if (hfcp->wr_info.num_meta_wr > 0) {
	 sprintf(buf,
				"ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nMetadataLength=%x\nData\n",
				hfcp->wr_info.uri->uri_str,
				hfcp->htl,
				hfcp->wr_info.num_data_wr + hfcp->wr_info.num_meta_wr,
				hfcp->wr_info.num_meta_wr);
  }
  else {
	 sprintf(buf,
				"ClientPut\nURI=%s\nHopsToLive=%x\nDataLength=%x\nData\n",
				hfcp->wr_info.uri->uri_str,
				hfcp->htl,
				hfcp->wr_info.num_data_wr
				);
  }
  
  count = strlen(buf);
  n = _fcpSockSend(hfcp, buf, count);
  if (n < count) {
	 _fcpSockDisconnect(hfcp);
	 return -1;
  }
  
  if (hfcp->wr_info.num_meta_wr > 0) {
	 fd = open(hfcp->wr_info.meta_temp_file, OPEN_MODE_READ);
	 
	 while ((count = read(fd, buf, 1024)) > 0)
		_fcpSockSend(hfcp, buf, count);
	 
	 close(fd);
  }
  
  if (hfcp->wr_info.num_data_wr > 0) {
	 fd = open(hfcp->wr_info.data_temp_file, OPEN_MODE_READ);
	 
	 while ((count = read(fd, buf, 1024)) > 0)
		_fcpSockSend(hfcp, buf, count);

	 close(fd);
  }
  
  unlink(hfcp->wr_info.meta_temp_file);
  unlink(hfcp->wr_info.meta_temp_file);

  if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_SUCCESS) {
	 _fcpSockDisconnect(hfcp);
	
	 return -1;
  }

  _fcpSockDisconnect(hfcp);

  return 0;
}
*/

