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

/* Global vars
 */
extern int   _fcpVerbosity;   /* verbosity of log messages; ranges from 0 through 4 */
extern FILE *_fcpLogStream;   /* stream used to send log messages; may be file or stdin */

extern char *_fcpTmpDir;      /* temporary file directory (depends on WINDOWS/!WINDOWS) */
extern char *_fcpHomeDir;     /* Home directory; not currently used */

extern int   _fcpSplitblock;  /* Mininum size necessary to begin splitfile insertion
																 (may not be the same as size returned by Fred FEC routines */

extern int   _fcpRetry;       /* Nuber of times to retry on Timeout message */

/*
	Function prototypes
*/

extern hBlock *_fcpCreateHBlock(void);
extern void    _fcpDestroyHBlock(hBlock *);

extern hKey   *_fcpCreateHKey(void);
extern void    _fcpDestroyHKey(hKey *);

/* Metadata handling functions */
extern int     _fcpMetaParse(hMetadata *, char *buf);
extern void    _fcpMetaFree(hMetadata *);

extern hMetadata  *_fcpCreateHMetadata(void);
extern void        _fcpDestroyHMetadata(hMetadata *);

/* Some FEC definitions */
extern hSegment   *_fcpCreateHSegment(void);
extern void        _fcpDestroyHSegment(hSegment *);

/* fcpLog */
extern void  _fcpLog(int level, char *format, ...);

/* Socket functions */
extern int   _fcpSockConnect(hFCP *hfcp);
extern void  _fcpSockDisconnect(hFCP *hfcp);

extern int   _fcpRecvResponse(hFCP *hfcp);
extern int   _fcpSockRecv(hFCP *hfcp, char *buf, int len);
extern int   _fcpSockRecvln(hFCP *hfcp, char *resp, int len);

extern char *_fcpGetMimetype(char *pathname);

extern int   _fcpTmpfile(char **filename);

extern long  file_size(char *filename);
extern void  unlink_key(hKey *hKey);
extern long  xtoi(char *);

#endif /* EZ_SYS_H */

