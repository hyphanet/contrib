/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
  CopyLeft (c) 2001 by David McNab
	
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

#include <sys/types.h>
#include <sys/stat.h>

#ifndef WINDOWS
#include <sys/socket.h>
#endif

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ezFCPlib.h"

/* Why isn't this defined where the manpage claims it is? */
extern int    snprintf(char *str, size_t size, const char *format, ...);

extern int    crSockConnect(hFCP *hfcp);
extern void   crSockDisconnect(hFCP *hfcp);
extern char  *crTmpFilename(void);


int fcpWriteKey(hFCP *hfcp, char *buf, int len)
{
	int count;
	int rc;

	count = (len > 8192 ? 8192 : len);

	/* While there's still data to write from caller.. */
	while (len) {

		rc = write(hfcp->key->tmpblock->fd, buf, count);
		
		if (rc != count) {
			_fcpLog(FCP_LOG_CRITICAL, "fcpWriteKey ERROR: Error writing %d byte block to %s",
							count, hfcp->key->tmpblock->filename);
			return -1;
		}

		/* Info was written.. update indexes */
		hfcp->key->size += count;

		len -= count;
		buf += count;
	}
	
	return 0;
}

