/*
  fcpx.c

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
#include "getopt.h"

#include <stdio.h>
#include <string.h>

/*
  PRIVATE DECLARATIONS
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

static int  clienthello(hFCP *hfcp);
static int  clientinfo(hFCP *hfcp);

/* Configurable command-line parameters
	 Strings/Arrays that have default values if not set explicitly from the
	 command line should be initialized to "", a zero-length string.
*/
char           *host;
unsigned short  port = EZFCP_DEFAULT_PORT;

int   verbosity = FCP_LOG_NORMAL;
char *logfile = 0;
char *command;


int main(int argc, char* argv[])
{
	hFCP *hfcp;
	int   rc;

	host = strdup(EZFCP_DEFAULT_HOST);

	/* go thru command line args */
	parse_args(argc, argv);

	if (!command) usage("Did not specifiy an FCP command");

	/* Call before calling *any* other ?fcp* routines */
	if (fcpStartup(logfile, 0, verbosity)) {
		fprintf(stdout, "Failed to initialize ezFCP library\n");
		return -1;
	}

	/* Make sure all input args are sent to ezFCPlib as advertised */
	hfcp = fcpCreateHFCP(host, port, 0, 0, 0);

	if (!strncasecmp(command, "clienthello", 11)) {
		rc = clienthello(hfcp);
	}

	else if (!strncasecmp(command, "clientinfo", 10)) {
		rc = clientinfo(hfcp);
	}

	else {
		usage("Did not specify a supported FCP command");
	}

	if (rc) fprintf(stdout, "Command \"%s\" returned error code: %d", command, rc);
	return rc;
}


/* IMPORTANT
   This function should bail if the parameters are bad in any way.  main() can
   then continue cleanly. */

static void parse_args(int argc, char *argv[])
{
  static struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"verbosity", 1, 0, 'v'},
    {"logfile", 1, 0, 'f'},
    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},
    {0, 0, 0, 0}
  };
  static char short_options[] = "n:p:v:f:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != -1) {
    switch (c) {

    case 'n':
			if (host) free(host);
			host = (char *)malloc(strlen(optarg) + 1);
			
      strcpy(host, optarg);
      break;

    case 'p':
      i = atoi( optarg );
      if (i > 0) port = i;
      break;

    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) verbosity = i;
      break;

    case 'f':
			if (logfile) free(logfile);
			logfile = (char *)malloc(strlen(optarg) + 1);
			
      strcpy(logfile, optarg);
      break;
			
    case 'V':
      fprintf(stdout, "FCPtools Version %s\n", VERSION);
      exit(0);
			
    case 'h':
      usage(0);
      break;
    }
  }

  if (optind < argc) {
		command = (char *)malloc(strlen(argv[optind]) + 1);
		strcpy(command, argv[optind++]);
	}

  if (optind < argc) {
		usage("Too many parameters");
	}
}


static void usage(char *s)
{
	if (s) printf("Error: %s\n", s);

/*
"01234567890123456789012345678901234567890123456789012345678901234567890123456789"
*/

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001-2003 by David McNab <david@rebirthing.co.nz>\n");
	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");
	
	printf("Usage: fcpx [-n hostname] [-p port] [-v verbosity] [-f filename]\n");
	printf("            COMMAND\n\n");

	printf("COMMAND is one of the following FCP commands:\n\n");

	printf("  clienthello\n");
	printf("  clientinfo\n\n");

	printf("Options:\n\n");

	printf("  -n, --address host     Freenet node address\n");
	printf("  -p, --port num         Freenet node port\n\n");

	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
	printf("  -f, --logfile file     Full pathname for the output log file (default stdout)\n\n");

	exit(0);
}


static int clienthello(hFCP *hfcp)
{
	if (fcpClientHello(hfcp) != 0) {
		fprintf(stdout, "Could not send ClientHello message to node\n");
		return -1;
	}
	
	/* Now dump the info */
	fprintf(stdout, "Node Description: %s\n", hfcp->description);
	fprintf(stdout, "Node Protocol: %s\n", hfcp->protocol);
	fprintf(stdout, "Highest Seen Build: %d\n", hfcp->highest_build);
	fprintf(stdout, "Max Filesize: %d\n", hfcp->max_filesize);

	fcpDestroyHFCP(hfcp);
	return 0;
}


static int clientinfo(hFCP *hfcp)
{
	/*
	if (fcpClientInfo(hfcp) != 0) {
		fprintf(stdout, "Could not send ClientInfo message to node\n");
		return -1;
	}
	*/
}

