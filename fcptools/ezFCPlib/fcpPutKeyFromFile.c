/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/

#include <sys/types.h>
#include <sys/stat.h>

#ifndef WINDOWS
#include <unistd.h>
#endif

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

#include "ezFCPlib.h"


extern int   crSockConnect(hFCP *hfcp);
extern void  crSockDisconnect(hFCP *hfcp);


int fcpPutKeyFromFile(hFCP *hfcp, char *key, char *filename, char **meta)
{






}

