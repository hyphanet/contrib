/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include <stdio.h>

#include "ezFCPlib.h"

/* Global data defs

	 These values are overriden by HFCP values, to allow different htl's
	 per key insertion, etc.
*/

/* Common FCP related default protocol values */
char  _fcpID[4] = { 0, 0, 0, 2 };

char *_fcpHost = EZFCP_DEFAULT_HOST;
int   _fcpPort = EZFCP_DEFAULT_PORT;
int   _fcpHtl = EZFCP_DEFAULT_HTL;
int   _fcpRawmode = EZFCP_DEFAULT_RAWMODE;

int   _fcpVerbosity = EZFCP_DEFAULT_VERBOSITY;
int   _fcpRegress = EZFCP_DEFAULT_REGRESS;
int   _fcpInsertAttempts = EZFCP_DEFAULT_INSERTATTEMPTS;
char *_fcpTmpDir = 0;

/* Basic accounting - ensure sockets are getting closed */
int   _fcpNumOpenSockets = 0;

