char *win32_rcs = "$Id: win32.c,v 1.1 2001/09/29 01:30:36 heretic108 Exp $";
/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

/* This is fcpproxy, an http interface client for Freenet.
 * Adapted by David McNab for Freenet from the JunkBusters proxy engine
 * The latest version of this program is at the FreeWeb website,
 * http://freeweb.sourceforge.net, and can be downloaded as a tarball, or from
 * cvs, or as precompiled binaries
 */

#ifdef _WIN32

#include <stdio.h>
#ifdef REGEX
#include "gnu_regex.h"
#endif
#include "fcpproxy.h"
#include <windows.h>

extern int hideConsole;
void InitWin32()
{
	WORD wVersionRequested;
	WSADATA wsaData;

	SetProcessShutdownParameters(0x100, SHUTDOWN_NORETRY);
	if (hideConsole) {
		FreeConsole();
	}

	wVersionRequested = MAKEWORD(2, 0);
	if (WSAStartup(wVersionRequested, &wsaData) != 0) {
		exit(1);
	}
}

char *win32_blurb =
"Internet Junkbuster Proxy(TM) Version " VERSION " for Windows is Copyright (C) 1997-8\n"
"by Junkbusters Corp.  This is free software; it may be used and copied under\n"
"the GNU General Public License: http://www.junkbusters.com/ht/en/gpl.html.\n"
"This program comes with ABSOLUTELY NO WARRANTY OF ANY KIND.\n"
"\n"
"For information about how to to configure the proxy and your browser, see\n"
"        http://www.junkbusters.com/ht/en/ijbwin.html#v" VERSION_MAJOR "\n"
"\n"
"The Internet Junkbuster Proxy(TM) is running and ready to serve!\n"
;
#endif
