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

	_fcpLog(FCP_LOG_DEBUG, "Entered _fcpTmpfile()");

	time(&seedseconds);
	srand((unsigned int)seedseconds);

#ifdef WIN32
	snprintf(tempdir, L_FILENAME, "%s\\local settings\\temp", getenv("USERPROFILE"));
#else
	strcpy(tempdir, "/tmp");
#endif

	while (search) {
		snprintf(tempfile, L_FILENAME, "%s%ceztmp_%x", tempdir, FCP_DIR_SEP, (unsigned int)rand());

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
	
	rc = open(filename, FCP_WRITEFILE_FLAGS, FCP_CREATEFILE_MODE);
	/*_fcpWrite(rc, "*", 1);*/

	return rc;
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

/*
  function xtol()

  Convert a hexadecimal string into an int. This is the hex version of
	atoi.
*/
long xtol(char *s)
{
  long val = 0;
  
  if (!s) return 0L;
  
  for (; *s != '\0'; s++)
    if (*s >= '0' && *s <= '9')
      val = val * 16 + *s - '0';
    else if (*s >= 'a' && *s <= 'f')
      val = val * 16 + (*s - 'a' + 10);
    else if (*s >= 'A' && *s <= 'F')
      val = val * 16 + (*s - 'A' + 10);
    else
      break;
  
  return val;
}

/*
	function memtoi()
	extension of atoi()

	this func recognises suffices on numbers
	eg '64k' will get parsed as 65536
	recognises the suffices 'k', 'K', 'm', 'M', 'g', 'G'
	(Thanks to mjr)
*/

int memtoi(char *s)
{
	int n = atoi(s);
	switch (s[strlen(s)-1])
	{
		case 'G':
		case 'g':
			return n << 30;
		case 'M':
		case 'm':
			return n << 20;
		case 'K':
		case 'k':
			return n << 10;
		default:
			return n;
	 }
}

int copy_file(char *dest, char *src)
{
	char buf[8193];

	int dfd;
	int sfd;

	int count;
	int bytes;

	if (!dest) {
		_fcpLog(FCP_LOG_DEBUG, "OOPS: dest: %s", dest);
		return 0;
	}

	if (!src) {
		_fcpLog(FCP_LOG_DEBUG, "OOPS: src: %s", src);
		return 0;
	}

	if ((dfd = open(dest, FCP_WRITEFILE_FLAGS, FCP_CREATEFILE_MODE)) == -1) {

		_fcpLog(FCP_LOG_DEBUG, "couldn't open destination file: %s", dest);
		return -1;
	}

	if ((sfd = open(src, FCP_READFILE_FLAGS)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "couldn't open destination file: %s", src);
		return -1;
	}
	
	for (bytes = 0; (count = read(sfd, buf, 8192)) > 0; bytes += count)
		_fcpWrite(dfd, buf, count);

	if (count == -1) {
		_fcpLog(FCP_LOG_DEBUG, "a read returned an error");
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "copy_file copied %d bytes", bytes);

	close(sfd);
	close(dfd);

	return bytes;
}

int tmpfile_link(hKey *h, int flags)
{
	if ((h->tmpblock->fd = open(h->tmpblock->filename, flags)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not link to key tmpfile: %s", h->tmpblock->filename);
		return -1;
	}
	
	if ((h->metadata->tmpblock->fd = open(h->metadata->tmpblock->filename, flags)) == -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not link to meta tmpfile: ", h->metadata->tmpblock->filename);
		return -1;
	}

	_fcpLog(FCP_LOG_DEBUG, "LINKED - key: %s, meta: %s", h->tmpblock->filename, h->metadata->tmpblock->filename);
	return 0;
}


void tmpfile_unlink(hKey *h)
{
	/* close the temporary key file */
	if (h->tmpblock->fd != -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not un*link from key tmpfile: %s", h->tmpblock->filename);
		close(h->tmpblock->fd);
	}

	/* close the temporary metadata file */
	if (h->metadata->tmpblock->fd != -1) {
		_fcpLog(FCP_LOG_DEBUG, "could not un*link from meta tmpfile: ", h->metadata->tmpblock->filename);
		close(h->metadata->tmpblock->fd);
	}

	h->tmpblock->fd = -1;
	h->metadata->tmpblock->fd = -1;
	
	_fcpLog(FCP_LOG_DEBUG, "UN*LINKED - key: %s, meta: %s", h->tmpblock->filename, h->metadata->tmpblock->filename);
}

