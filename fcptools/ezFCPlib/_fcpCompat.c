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
#include <stdlib.h>
#include <errno.h>
#include <string.h>

#include <fcntl.h>
				

extern char *_fcpTmpDir;

/*********************************************************************/

void _fcpSockDisconnect(hFCP *hfcp)
{
  if (hfcp->socket == FCP_SOCKET_DISCONNECTED)
		return;

#ifdef WIN32
	closesocket(hfcp->socket);
#else
	close(hfcp->socket);
#endif

	hfcp->socket = FCP_SOCKET_DISCONNECTED;
}

int _fcpTmpfile(char **filename)
{
	char s[513];
	int search = 1;

	struct stat st;
	time_t seedseconds;

	time(&seedseconds);
	srand((unsigned int)seedseconds);

	while (search) {
		snprintf(s, 512, "%s/eztmp_%x", _fcpTmpDir, (unsigned int)rand());

		if (stat(s, &st) == -1)
			if (errno == ENOENT) search = 0;
	}

	/* set the filename parameter to the newly generated Tmp filename */
	*filename = (char *)malloc(strlen(s) + 1);
	strcpy(*filename, s);

#ifdef WIN32
	/* this call should inherit permissions from the parent dir */
	return creat(*filename, O_CREAT);

#else
	/* on *nix, this creates a file with perms "rw-------" (600) */
	return creat(*filename, O_CREAT | S_IRUSR | S_IWUSR);

#endif
}

