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

#include <time.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <fcntl.h>

#include "ez_sys.h"

int fcpReadKey(hFCP *hfcp, char *buf, int len)
{
	int bytes;
	int rc;

	if (hfcp->key->tmpblock->fd == -1) return -1;

	/* while there's still data in the tmp block */
	
	bytes = 0;
	while (len) {

		rc = _fcpRead(hfcp->key->tmpblock->fd, buf, len);

		_fcpLog(FCP_LOG_DEBUG, "rc from ReadKey: %d", rc);

		if (rc < 0) {
			_fcpLog(FCP_LOG_DEBUG, "error during call to fcpReadKey()");
			return -1;
		}

		_fcpLog(FCP_LOG_DEBUG, "rc from ReadKey: %d", rc);

		/* Info was read.. update indexes */
		len -= rc;
		bytes += rc;

		/* note: this usually gets hit twice, a redundant case when
			 attempting to read past EOF */

		if (rc == 0) {
			break;
		}
	}
	
	return bytes;
}


int fcpReadMetadata(hFCP *hfcp, char *buf, int len)
{
	int bytes;
	int count;
	int rc;
	
	if (hfcp->key->metadata->tmpblock->fd == -1) return -1;

	/* while there's still data in the tmp block */

	bytes = 0;
	while (len) {

		count = (len > 8192 ? 8192 : len);
		rc = _fcpRead(hfcp->key->metadata->tmpblock->fd, buf+bytes, count);
		
		if (rc < 0) {
			_fcpLog(FCP_LOG_DEBUG, "error during call to fcpReadMetadata()");
			return -1;
		}

		/* Info was read.. update indexes */
		len -= rc;
		buf += rc;

		/* note: this usually gets hit twice, a redundant case when
			 attempting to read past EOF */
		
		if (rc < count)
			break;
	}
	
	return bytes;
}

