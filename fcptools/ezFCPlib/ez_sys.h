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

/* ******************************************************* *
 * This file is intended for use only from within ezfcplib *
 ********************************************************* */

#ifndef _EZ_SYS_H
#define _EZ_SYS_H 

#ifdef WIN32

/* function aliases */
#define snprintf _snprintf
#define vsnprintf _vsnprintf
#define open _open
#define close _close
#define commit _commit

/* constants */
#define _FCP_READFILE_FLAGS (_O_RDONLY | _O_BINARY)
#define _FCP_WRITEFILE_FLAGS (_O_CREAT | _O_WRONLY | _O_TRUNC | _O_BINARY)

#define _FCP_CREATEFILE_MODE (_S_IWRITE | _S_IREAD)
#define _FCP_READFILE_MODE (0)

#define _FCP_DIR_SEP '\\'

typedef SOCKET FCPSOCKET;

#else

/* constants */
#define _FCP_READFILE_FLAGS (O_RDONLY)
#define _FCP_WRITEFILE_FLAGS (O_CREAT | O_WRONLY | O_TRUNC)

#define _FCP_CREATEFILE_MODE (S_IWUSR | S_IRUSR)
#define _FCP_READFILE_MODE (0)

#define _FCP_DIR_SEP '/'

typedef int FCPSOCKET;
#endif

#define _FCP_READ   0x0001
#define _FCP_WRITE  0x0002

#ifdef DMALLOC
#include "dmalloc.h"
extern int _fcpDMALLOC;
#endif

/*
	Function prototypes
*/

extern hBlock *_fcpCreateHBlock(void);
extern void    _fcpDestroyHBlock(hBlock *);

extern hKey   *_fcpCreateHKey(void);
extern void    _fcpDestroyHKey(hKey *);

extern hOptions *_fcpCreateHOptions(void);
extern void      _fcpDestroyHOptions(hOptions *);

/* Metadata handling functions */
extern int     _fcpMetaParse(hMetadata *, char *buf);
extern void    _fcpMetaFree(hMetadata *);

extern hMetadata  *_fcpCreateHMetadata(void);
extern void        _fcpDestroyHMetadata(hMetadata *);
extern void        _fcpDestroyHMetadata_cdocs(hMetadata *);

/* Some FEC definitions */
extern hSegment   *_fcpCreateHSegment(void);
extern void        _fcpDestroyHSegment(hSegment *);

/* fcpLog */
extern void  _fcpLog(int level, char *format, ...);

extern void  _fcpOpenLog(FILE *logstream, int verbosity);
extern void  _fcpCloseLog(void);

/* Socket functions */
extern int   _fcpSockConnect(hFCP *hfcp);
extern void  _fcpSockDisconnect(hFCP *hfcp);

extern int   _fcpRecvResponse(hFCP *hfcp);
extern int   _fcpSockRecv(hFCP *hfcp, char *buf, int len);
extern int   _fcpSockRecvln(hFCP *hfcp, char *resp, int len);

/* send/recv substitutes */
extern int   _fcpRecv(FCPSOCKET socket, char *buf, int len);
extern int   _fcpSend(FCPSOCKET socket, char *buf, int len);

/* read/write substitutes */
extern int   _fcpRead(int fd, char *buf, int len);
extern int   _fcpWrite(int fd, char *buf, int len);

/* Others */
extern char *_fcpGetMimetype(char *pathname);

extern int   _fcpLink(hBlock *h, int access);
extern void  _fcpUnlink(hBlock *h);

extern int   _fcpTmpfile(char *filename);
extern long  _fcpFilesize(char *filename);

extern int   _fcpCopyFile(char *dest, char *src);
extern int   _fcpDeleteFile(hBlock *h);

extern long  xtol(char *);
extern int   memtoi(char *);

extern int   put_file(hFCP *hfcp, char *uri);
extern int   put_fec_splitfile(hFCP *hfcp);

extern int   put_date_redirect(hFCP *hfcp, char *uri);
extern int   put_redirect(hFCP *hfcp, char *uri_src, char *uri_dest);

extern int   get_file(hFCP *hfcp, char *uri);
extern int   get_fec_splitfile(hFCP *hfcp, char *key_filename, char *meta_filename);

extern int   get_follow_redirects(hFCP *hfcp, char *uri);
extern int   get_size(hFCP *hfcp, char *uri);

extern hDocument *cdocFindDoc(hMetadata *meta, char *cdocName);
extern char      *cdocLookupKey(hDocument *doc, char *keyName);

#endif /* EZ_SYS_H */

