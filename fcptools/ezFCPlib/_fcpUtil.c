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

/*
#ifndef P_tmpdir
 #define P_tmpdir "/tmp"
#endif
*/

/*
  xtoi()

  Convert a hexadecimal number string into an int
  this is the hex version of atoi
*/
long xtoi(char *s)
{
    long val = 0;

    if (s == NULL)
        return 0L;

    for (; *s != '\0'; s++)
        if (*s >= '0' && *s <= '9')
            val = val * 16 + *s - '0';
        else if (*s >= 'a' && *s <= 'f')
            val = val * 16 + (*s - 'a' + 10);
        else if (*s >= 'A' && *s <= 'F')
            val = val * 16 + (*s - 'A' + 10);
        else
            break;

    return val;
}

long timeLastMidnight()
{
    time_t timenow;

    time(&timenow);
    timenow -= timenow % 86400;
    return timenow;
}

int opentemp(char filename[])
{
	int fd;
	static time_t seedseconds;
	struct stat dirstats;
	
	if (!seedseconds) {
		time(&seedseconds);
		srand((unsigned int)seedseconds);
	}
	do {
		/* Normally this will only be done once so it shouldn't
		 * be a problem to put this inside the loop */
		if (!stat(P_tmpdir, &dirstats) && dirstats.st_mode & (S_IFDIR|S_IWUSR|S_IXUSR))
			sprintf(filename, "%s/eztmp%x", P_tmpdir, (unsigned int)rand());
		else
			/* If P_tmpdir is not accessible, use current working dir */
			sprintf(filename, "eztmp%x", (unsigned int)rand());
		fd = open(filename, O_WRONLY|O_CREAT|O_EXCL, 0600);
	} while (fd < 0 && errno == EEXIST);
	return fd;
}
