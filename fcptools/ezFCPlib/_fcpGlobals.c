
/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"


/* Global data defs */
char  _fcpHost[L_HOST];
int   _fcpPort;
int   _fcpHtl;
int   _fcpRawMode;

// Remove the following comments when temp files are confirmed to be working
// correctly.
//char  _fcpProgPath[256];
//int   _fcpFileNum;       /* temporary file count */

char  _fcpID[4] = { 0, 0, 0, 2 };
int   _fcpRegress;

/* Basic accounting - ensure sockets are getting closed */
int   _fcpNumOpenSockets = 0;
