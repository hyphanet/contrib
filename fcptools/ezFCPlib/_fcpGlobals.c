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

/* NOTE: in a multithreaded implementation, this is *shared* */

/* Common FCP related default protocol values */

int   _fcpVerbosity;   /* verbosity of log messages; ranges from 0 through 4 */
FILE *_fcpLogStream;   /* stream used to send log messages; may be file or stdin */

char  _fcpTmpDir[L_FILENAME+1];    /* temporary file directory (depends on WIN32/!WIN32) */
char  _fcpHomeDir[L_FILENAME+1];   /* Home directory; not currently used */

int   _fcpSplitblock;  /* Mininum size necessary to begin splitfile insertion
                          (may not be the same as size returned by Fred FEC routines */

int   _fcpRetry;       /* Nuber of times to retry on Timeout message */
int   _fcpDMALLOC;

