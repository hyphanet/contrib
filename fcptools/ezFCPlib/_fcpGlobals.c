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

#include <stdio.h>


/* Global data defs

	 These values are overriden by HFCP values, to allow different htl's
	 per key insertion, etc.
*/

/* Common FCP related default protocol values */
char  _fcpID[4] = { 0, 0, 0, 2 };

char *_fcpHost = 0; /* In case I forget again.. this is correct */

unsigned short _fcpPort = EZFCP_DEFAULT_PORT;
int   _fcpHtl = EZFCP_DEFAULT_HTL;
int   _fcpRawmode = EZFCP_DEFAULT_RAWMODE;

int   _fcpVerbosity = EZFCP_DEFAULT_VERBOSITY;
int   _fcpRegress = EZFCP_DEFAULT_REGRESS;
char *_fcpTmpDir = 0;

/* Basic accounting - ensure sockets are getting closed */
int   _fcpNumOpenSockets = 0;

