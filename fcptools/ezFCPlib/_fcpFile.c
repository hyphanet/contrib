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

/*
	Return Values: non-zero integer on success, ~0 (one's complement) on error
*/

unsigned long _fcpFilesize(char *filename)
{
	unsigned long size;

	struct stat fstat;
	
	if (!filename) size = ~0L;
	else if (stat(filename, &fstat)) size = ~0L;
	else size = fstat.st_size;
	
	return size;
}

int _fcpCopyFile(char *dest, char *src)
{
	_fcpLog(FCP_LOG_DEBUG, "CopyFile(): %s => %s", src, dest);

#ifdef WIN32

	if (CopyFile(src, dest, FALSE) == 0) {
		_fcpLog(FCP_LOG_DEBUG, "couldn't CopyFile()");
		return -1;
	}

	return _fcpFilesize(dest);

#else

	{
		char buf[8193];
		
		int dfd;
		int sfd;
		
		int count;
		int bytes;
		
		dfd = sfd = -1;
		
		if (!dest) {
			_fcpLog(FCP_LOG_DEBUG, "OOPS: dest: %s", dest);
			return -1;
		}
		
		if (!src) {
			_fcpLog(FCP_LOG_DEBUG, "OOPS: src: %s", src);
			return -1;
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
		
		_fcpLog(FCP_LOG_DEBUG, "_fcpCopyFile() copied %u bytes", bytes);
		
		if (sfd != -1) close(sfd);
		if (dfd != -1) close(dfd);
		
		return bytes;
		
	cleanup:
		
		if (sfd != -1) close(sfd);
		if (dfd != -1) close(dfd);
		
		return -1;
	}
#endif
}
