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

/* why? */
/*#include <time.h>*/

#include "ez_sys.h"


/* WARNING * Multithreaded programs should mutex this function */

int _fcpTmpfile(char *filename)
{
	char tempdir[L_FILENAME+1];
	char tempfile[L_FILENAME+1];

	int search = 1;
	int rc;

	struct stat st;
	time_t seedseconds;

	time(&seedseconds);
	srand((unsigned int)seedseconds);

#ifdef WIN32
	snprintf(tempdir, L_FILENAME, "%s", getenv("TEMP"));
#else
	strcpy(tempdir, "/tmp");
#endif

	while (search) {
		snprintf(tempfile, L_FILENAME, "%s%ceztmp_%x", tempdir, _FCP_DIR_SEP, (unsigned int)rand());

		if (stat(tempfile, &st))
			if (errno == ENOENT) search = 0;
	}

	/* set the filename parameter to the newly generated Tmp filename */

	strncpy(filename, tempfile, L_FILENAME);
	_fcpLog(FCP_LOG_DEBUG, "_fcpTmpfile() filename: %s", filename);
	
	/* I think creating the file right here is good in avoiding
		 race conditions.  Let the caller close the file (leaving a
		 zero-length file behind) if it needs to be re-opened
		 (ie when calling fcpGet()) */
	
	rc = open(filename, _FCP_WRITEFILE_FLAGS, _FCP_CREATEFILE_MODE);
	if (rc == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not re-open temp file");
		return -1;
	}

	/* close the file before exiting */
	if (close(rc) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not close temp file");
		return -1;
	}

	return 0;
}

/*******************************************************************/
/* substitutes for read() and write() */
/*******************************************************************/

int _fcpRead(int fd, char *buf, int len)
{
	int rc;
	int bs;

	bs = 0; rc = 1;
	while (len && (rc != 0)) {

		rc = read(fd, buf+bs, len);

		len -= rc;
		bs += rc;
	}

	if (rc < 0) return -1;
	else return bs;
}

int _fcpWrite(int fd, char *buf, int len)
{
	int rc;
	int bs;

	bs = 0; rc = 1;
	while (len && (rc != 0)) {

		rc = write(fd, buf+bs, len);

		len -= rc;
		bs += rc;
	}

	if (rc < 0) return -1;
	else return bs;
}

long _fcpFilesize(char *filename)
{
	int size;

	struct stat fstat;
	
	if (!filename) size = -1;
	else if (stat(filename, &fstat)) size = -1;
	else size = fstat.st_size;
	
	return size;
}

int _fcpCopyFile(char *dest, char *src)
{
	_fcpLog(FCP_LOG_DEBUG, "CopyFile(): %s => %s", src, dest);

#ifdef WIN32

	if (CopyFile(src, dest, FALSE) == 0) {
		_fcpLog(FCP_LOG_DEBUG, "couldn't CopyFile()");
		return 0;
	}

	return _fcpFilesize(dest);

#else

	char buf[8193];

	int dfd;
	int sfd;

	int count;
	int bytes;

	dfd = sfd = -1;

	if (!dest) {
		_fcpLog(FCP_LOG_DEBUG, "OOPS: dest: %s", dest);
		return 0;
	}

	if (!src) {
		_fcpLog(FCP_LOG_DEBUG, "OOPS: src: %s", src);
		return 0;
	}

	if ((dfd = open(dest, _FCP_WRITEFILE_FLAGS, _FCP_CREATEFILE_MODE)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "couldn't open destination file: %s", dest);
		return -1;
	}

	if ((sfd = open(src, _FCP_READFILE_FLAGS, _FCP_READFILE_MODE)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "couldn't open destination file: %s", src);
		goto cleanup;
	}
	
	for (bytes = 0; (count = read(sfd, buf, 8192)) > 0; bytes += count)
		_fcpWrite(dfd, buf, count);

	if (count == -1) {
		_fcpLog(FCP_LOG_DEBUG, "a read returned an error");
		goto cleanup;
	}

	_fcpLog(FCP_LOG_DEBUG, "_fcpCopyFile() copied %d bytes", bytes);

	if (sfd != -1) close(sfd);
	if (dfd != -1) close(dfd);

	return bytes;

 cleanup:

	if (sfd != -1) close(sfd);
	if (dfd != -1) close(dfd);

	return -1;

#endif
}

int _fcpLink(hBlock *h, int access)
{
	int flag;

	if (h->fd != -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpLink(): %s - fd != -1", h->filename);
		return -1;
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
			_fcpLog(FCP_LOG_DEBUG, "_fcpLink(): %s - access mode invalid", h->filename);
			return -2;
	}

	if (h->fd == -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpLink(): %s - open() returned -1", h->filename);
		return -1;
	}

	/* if we reach here, life is peachy */
	_fcpLog(FCP_LOG_DEBUG, "_fcpLink(): %s - LINKED", h->filename);
	return 0;
}

void _fcpUnlink(hBlock *h)
{
	if (h->fd == 0) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpUnlink(): fd == 0");
		h->fd = -1;
		return;
	}

	if (h->fd == -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpUnlink(): %s - fd already closed / not opened", h->filename);
		h->fd = -1;
		return;
	}

	if (close(h->fd) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "_fcpUnlink(): %s - close() returned -1", h->filename);
		h->fd = -1;
		return;
	}

	_fcpLog(FCP_LOG_DEBUG, "_fcpUnlink(): %s - UN*LINKED", h->filename);
	h->fd = -1;
}

int _fcpDeleteFile(hBlock *h)
{
	int rc;

	rc = 0;

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
		_fcpLog(FCP_LOG_DEBUG, "error %d in _fcpDeleteFile(): %s", rc, h->filename);
		return -1;
	}

	h->filename[0] = 0;
	return 0;
}

