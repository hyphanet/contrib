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
#include <string.h>

#include "ezFCPlib.h"

/*** Should accept a few parameters ****/
int fcpStartup(void)
{

#ifndef WINDOWS
	_fcpTmpDir = "/tmp";
#else
	/* Call WSAStartup thing */
	/* Then set _fcpTmpDir to something that makes sense on windoze */
#endif

	_fcpHost = (char *)malloc(strlen(EZFCP_DEFAULT_HOST) + 1);
	strcpy(_fcpHost, EZFCP_DEFAULT_HOST);

	return 0;
}


void fcpTerminate(void)
{
	return;
}

