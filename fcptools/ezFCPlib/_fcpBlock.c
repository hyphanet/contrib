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

#include "ezFCPlib.h"

#ifdef WIN32
#include <winbase.h>
#endif

#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

#ifndef WIN32
#include <time.h>
#endif

#include "ez_sys.h"

/*
	functions that operate on hBlock structures; the main function to insert blocks
	is in _fcpPut.c
*/

int _fcpBlockLink(hBlock *h, int access)
{
	int flag;

	if (h->fd != -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpBlockLink(): %s - fd != -1", h->filename);
		return -1;
	}

	/* if the file was deleted, get another tmp filename */
	if (!h->filename[0]) {

		_fcpTmpfile(h->filename);
		h->m_delete = 1;
	}

#ifdef WIN32
	flag = h->binary_mode ? O_BINARY : 0;
#else
	flag = 0;
#endif

	switch (access) {
		case _FCP_READ:
			h->fd = open(h->filename, _FCP_READFILE_FLAGS | flag, _FCP_READFILE_MODE);
			break;

		case _FCP_WRITE:
			h->fd = open(h->filename, _FCP_WRITEFILE_FLAGS | flag, _FCP_CREATEFILE_MODE);
			break;

		default:
			_fcpLog(FCP_LOG_DEBUG, "_fcpBlockLink(): %s - access mode invalid", h->filename);
			return -2;
	}

	if (h->fd == -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpBlockLink(): %s - open() returned -1", h->filename);
		return -1;
	}

	/* if we reach here, life is peachy */
	/*_fcpLog(FCP_LOG_DEBUG, "_fcpBlockLink(): %s - LINKED", h->filename);*/
	return 0;
}

void _fcpBlockUnlink(hBlock *h)
{
	if (h->fd == 0) {
		/*_fcpLog(FCP_LOG_DEBUG, "_fcpBlockUnlink(): fd == 0");*/
		h->fd = -1;
		return;
	}

	if (h->fd == -1) {
		/*_fcpLog(FCP_LOG_DEBUG, "_fcpBlockUnlink(): %s - fd already closed / not opened", h->filename);*/
		h->fd = -1;
		return;
	}

	if (close(h->fd) == -1) {
		/*_fcpLog(FCP_LOG_DEBUG, "_fcpBlockUnlink(): %s - close() returned -1", h->filename);*/
		h->fd = -1;
		return;
	}

	/*_fcpLog(FCP_LOG_DEBUG, "_fcpBlockUnlink(): %s - UN*LINKED", h->filename);*/
	h->fd = -1;
}

int _fcpBlockSetFilename(hBlock *h, char *filename)
{
	if (h->filename[0]) _fcpDeleteBlockFile(h);

	strncpy(h->filename, filename, L_FILENAME);
	h->size = _fcpFilesize(filename);

	return 0;
}

int _fcpBlockUnsetFilename(hBlock *h)
{
	h->filename[0]='\0';
	h->m_delete = 0;

	return 0;
}

int _fcpDeleteBlockFile(hBlock *h)
{
	int rc;

	rc = 0;

	/* only do this if file is marked 'delete', otherwise just skip to the
		 and and only set the null char */
	if (h->m_delete) {

		if (h->fd == 0) {
			_fcpLog(FCP_LOG_DEBUG, "fd==0; this condition should never be reached");
			return -1;
		}
		if (h->fd > 0) {
			_fcpLog(FCP_LOG_DEBUG, "fd>0; this file needs to be closed first");
			
			close(h->fd);
			h->fd = -1;
		}
		
		if (h->filename[0] != 0) {
			
			/* one way or another, set rc=0 on success, -1 on failure*/
#ifdef WIN32
			rc = (DeleteFile(h->filename) != 0 ? 0 : 1);
			if (rc != 0) rc = GetLastError();
#else
			rc = unlink(h->filename);
#endif
			
		}
		else _fcpLog(FCP_LOG_DEBUG, "tmpfile doesn't exist apparantly");
		
		if (rc != 0) {
			_fcpLog(FCP_LOG_DEBUG, "error %d in _fcpDeleteBlockFile(): %s", rc, h->filename);
			return -1;
		}
		
		_fcpLog(FCP_LOG_DEBUG, "deleted file: %s", h->filename);
	}

	else
		_fcpLog(FCP_LOG_DEBUG, "file marked for keep.. not deleting %s", h->filename);
		
		
	h->filename[0] = 0;
	h->m_delete = 0;

	return 0;
}
