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

#endif

#ifdef DMALLOC
#include "dmalloc.h"
extern int _fcpDMALLOC;
#endif

/* Function prototypes */

/* Struct Creation & Destruction functions */

extern hBlock     *_fcpCreateHBlock(void);
extern void        _fcpDestroyHBlock(hBlock *);

extern hKey       *_fcpCreateHKey(void);
extern void        _fcpDestroyHKey(hKey *);

extern hOptions   *_fcpCreateHOptions(void);
extern void        _fcpDestroyHOptions(hOptions *);

extern hSegment   *_fcpCreateHSegment(void);
extern void        _fcpDestroyHSegment(hSegment *);
extern void        _fcpDestroyHSegments(hKey *);

/* Metadata handling functions */

extern int         _fcpMetaParse(hMetadata *, char *buf);
extern char       *_fcpMetaString(hMetadata *);
extern void        _fcpMetaFree(hMetadata *);

extern hMetadata  *_fcpCreateHMetadata(void);
extern void        _fcpDestroyHMetadata(hMetadata *);
extern void        _fcpDestroyHMetadata_cdocs(hMetadata *);

extern void        _fcpDestroyHDocument(hDocument *h);

/* metadata routines */

extern hDocument *cdocFindDoc(hMetadata *meta, char *cdocName);
extern char      *cdocLookupKey(hDocument *doc, char *keyName);

extern hDocument *cdocAddDoc(hMetadata *meta, char *cdocName);
extern int        cdocAddKey(hDocument *doc, char *key, char *val);

/* Mimetypes */

extern char     *_fcpGetMimetype(char *pathname);

/* Log functions */

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

/* Put and related functions */

extern int   _fcpPutBlock(hFCP *hfcp, hBlock *keyblock, hBlock *metablock, char *uri);
extern int   _fcpPutSplitfile(hFCP *hfcp);

extern int   _fcpInsertRoot(hFCP *hfcp);
extern char *_fcpDBRString(hURI *uri, int future);

/* Get and related functions */

extern int   _fcpGetBLock(hFCP *hfcp, hBlock *keyblock, hBlock *metablock, char *uri);
extern int   _fcpGetSplitfile(hFCP *hfcp);

extern int   _fcpGetKeyToFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename);

/* hBlock functions */

extern int   _fcpBlockLink(hBlock *h, int access);
extern void  _fcpBlockUnlink(hBlock *h);

extern int   _fcpBlockSetFilename(hBlock *h, char *filename);
extern int   _fcpBlockUnsetFilename(hBlock *h);

/* Generic file functions */

extern int            _fcpTmpfile(char *filename);
extern unsigned long  _fcpFilesize(char *filename);

extern int            _fcpCopyFile(char *dest, char *src);
extern int            _fcpDeleteBlockFile(hBlock *h);

extern int            fcpGetFilesize(hFCP *hfcp, char *uri);

/* miscellaneous helper functions */

extern long  xtol(char *);
extern int   memtoi(char *);

#if 0 /* DEPRECATE ALL THIS ? */
extern int   put_date_redirect(hFCP *hfcp, char *uri);
extern int   put_redirect(hFCP *hfcp, char *uri_src, char *uri_dest);

long       cdocIntVal(hMetadata *meta, char *cdocName, char *keyName, long  defVal);
long       cdocHexVal(hMetadata *meta, char *cdocName, char *keyName, long  defVal);
char      *cdocStrVal(hMetadata *meta, char *cdocName, char *keyName, char *defVal);
#endif

#endif /* EZ_SYS_H */

