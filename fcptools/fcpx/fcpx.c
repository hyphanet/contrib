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
#include <stdlib.h>

#ifdef DMALLOC
#include <dmalloc.h>
extern int _fcpDMALLOC;
#endif

/*
  PRIVATE DECLARATIONS
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

static int  clienthello(hFCP *hfcp);
static int  clientinfo(hFCP *hfcp);

#ifdef DMALLOC
void track(const char *file, const unsigned int line,
					 const int func_id,
					 const DMALLOC_SIZE byte_size,
					 const DMALLOC_SIZE alignment,
					 const DMALLOC_PNT old_addr,
					 const DMALLOC_PNT new_addr);
#endif

/* Configurable command-line parameters
	 Strings/Arrays that have default values if not set explicitly from the
	 command line should be initialized to "", a zero-length string.
*/
char           *host;
unsigned short  port = EZFCP_DEFAULT_PORT;

int    verbosity = FCP_LOG_NORMAL;
char  *logfile = 0;
FILE  *logstream = 0;
char  *command = 0;


int main(int argc, char* argv[])
{
	hFCP *hfcp;
	int   rc;
	
#ifdef DMALLOC
	/*dmalloc_track(track);*/
	_fcpDMALLOC = dmalloc_mark();
#endif
	
	/* set the host first, before parsing command line */
	host = strdup(EZFCP_DEFAULT_HOST);
	
	/* go thru command line args */
	parse_args(argc, argv);
	
	if (!command) usage("Did not specifiy an FCP command");
	
	if (logfile) {
		
		/* if there's an error opening the file, default to stdout */
		if (!(logstream = fopen(logfile, "w"))) {
			fprintf(stdout, "Could not open logfile.. using stdout\n");
			logstream = stdout;
		}
	}
	else { /* nothing specified? default to stdout */
		logstream = stdout;
	}
	
	/* Call before calling *any* other ?fcp* routines */
	if (fcpStartup(logstream, verbosity)) {
    fprintf(stdout, "Failed to initialize FCPLib\n");
		rc = -1;
		goto cleanup;
	}
	
	/* Make sure all input args are sent to ezFCPlib as advertised */
	hfcp = fcpCreateHFCP(host, port, 0, 0);

	rc = 0;
	
	if (!strncasecmp(command, "hello", 11)) {
		rc = clienthello(hfcp);
	}
	else if (!strncasecmp(command, "info", 10)) {
		rc = clientinfo(hfcp);
	}
	else {
		usage("Did not specify a supported FCP command");
	}
	
 cleanup:
	
	if (logfile) {
		fclose(logstream);
		free(logfile);
	}
	
	fcpDestroyHFCP(hfcp);
	free(hfcp);
	
  fcpTerminate();
	
#ifdef DMALLOC
	dmalloc_verify(0);
	dmalloc_log_changed(_fcpDMALLOC, 1, 1, 1);
	
	dmalloc_shutdown();
#endif
	
	return rc;
}


#ifdef DMALLOC
void track(const char *file, const unsigned int line,
											const int func_id,
											const DMALLOC_SIZE byte_size,
											const DMALLOC_SIZE alignment,
											const DMALLOC_PNT old_addr,
											const DMALLOC_PNT new_addr)
{
	char f[33];

	if (!file) strcpy(f, "NULL");
	else strncpy(f, file, 32);

	if ((!strcmp(file, "fcpCreation.c")) && (line == 187)) {
		printf("|| %s:%d, size %d, old_addr: %x, new_addr: %x ||\n", f, line, byte_size, old_addr, new_addr);
	}

	return;
}
#endif


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
			logfile = strdup(optarg);
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

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("CopyLeft 2001 by David McNab <david@rebirthing.co.nz>\n");
	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");
	
	printf("Usage: fcpx [-n hostname] [-p port] [-v verbosity] [-f filename]\n");
	printf("            COMMAND\n\n");

	printf("COMMAND is one of the following FCP commands:\n\n");

	printf("  hello\n");
	printf("  info\n\n");

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
		fprintf(stdout, "Could not send Hello message to node\n");
		return -1;
	}
	
	/* Now dump the info */
	fprintf(stdout, "Description: %s\n", hfcp->description);
	fprintf(stdout, "Protocol: %s\n", hfcp->protocol);
	fprintf(stdout, "HighestSeenBuild: %lu\n", hfcp->highest_build);
	fprintf(stdout, "MaxFileSize: %lu\n", hfcp->max_filesize);

	return 0;
}


static int clientinfo(hFCP *hfcp)
{
	if (fcpClientInfo(hfcp) != 0) {
		fprintf(stdout, "Could not send Info message to node\n");
		return -1;
	}

	/* Now dump the info */
	fprintf(stdout, "Architecture: %s\n", hfcp->response.nodeinfo.architecture);
	fprintf(stdout, "OperatingSystem: %s\n", hfcp->response.nodeinfo.operatingsystem);
	fprintf(stdout, "OperatingSystemVersion: %s\n", hfcp->response.nodeinfo.operatingsystemversion);
	fprintf(stdout, "NodeAddress: %s\n", hfcp->response.nodeinfo.nodeaddress);
	fprintf(stdout, "NodePort: %lu\n", hfcp->response.nodeinfo.nodeport);
	fprintf(stdout, "JavaVendor: %s\n", hfcp->response.nodeinfo.javavendor);
	fprintf(stdout, "JavaName: %s\n", hfcp->response.nodeinfo.javaname);
	fprintf(stdout, "JavaVersion: %s\n", hfcp->response.nodeinfo.javaversion);
	fprintf(stdout, "Processors: %lu\n", hfcp->response.nodeinfo.processors);
	fprintf(stdout, "MaximumMemory: %lu\n", hfcp->response.nodeinfo.maximummemory);
	fprintf(stdout, "AllocatedMemory: %lu\n", hfcp->response.nodeinfo.allocatedmemory);
	fprintf(stdout, "FreeMemory: %lu\n", hfcp->response.nodeinfo.freememory);
	fprintf(stdout, "EstimatedLoad: %lu\n", hfcp->response.nodeinfo.estimatedload);
	fprintf(stdout, "EstimateRateLimitingLoad: %lu\n", hfcp->response.nodeinfo.estimateratelimitingload);
	fprintf(stdout, "DatastoreMax: %lu\n", hfcp->response.nodeinfo.datastoremax);
	fprintf(stdout, "DatastoreFree: %lu\n", hfcp->response.nodeinfo.datastorefree);
	fprintf(stdout, "DatastoreUsed: %lu\n", hfcp->response.nodeinfo.datastoreused);
	fprintf(stdout, "MaxFileSize: %lu\n", hfcp->response.nodeinfo.maxfilesize);
	fprintf(stdout, "MostRecentTimestamp: %lu\n", hfcp->response.nodeinfo.mostrecenttimestamp);
	fprintf(stdout, "LeastRecentTimestamp: %lu\n", hfcp->response.nodeinfo.leastrecenttimestamp);
	fprintf(stdout, "RoutingTime: %lu\n", hfcp->response.nodeinfo.routingtime);
	fprintf(stdout, "AvailableThreads: %lu\n", hfcp->response.nodeinfo.availablethreads);
	fprintf(stdout, "ActiveJobs: %lu\n", hfcp->response.nodeinfo.activejobs);

	return 0;
}

