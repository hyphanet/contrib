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
#include "getopt.h"

#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

#ifdef DMALLOC
#include <dmalloc.h>
extern int _fcpDMALLOC;
#endif

/*
	local declarations
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

#ifdef DMALLOC
void track(const char *file, const unsigned int line,
					 const int func_id,
					 const DMALLOC_SIZE byte_size,
					 const DMALLOC_SIZE alignment,
					 const DMALLOC_PNT old_addr,
					 const DMALLOC_PNT new_addr);
#endif
	
/*
	fcpget globals
*/
char           *host;
unsigned short  port = EZFCP_DEFAULT_PORT;

int   verbosity = FCP_LOG_NORMAL;
int   htl       = EZFCP_DEFAULT_HTL;
int   retry     = EZFCP_DEFAULT_RETRY;
int   regress   = EZFCP_DEFAULT_REGRESS;
int   optmask   = 0;

char *logfile = 0; /* filename for logfile (if not stdin) */
FILE *logstream = 0; /* FILE * to logfile (or stdin) */

char *keyuri    = 0; /* passed in URI */
char *metafile  = 0; /* name of metadata filename */

char *file; /* file to write key data */

int   b_stdout   = 0; /* was -s passed? */
int   b_genkeys = 0; /* was -g passed? */


int main(int argc, char* argv[])
{
  hFCP *hfcp;
  
  char  buf[8193];
  int   bytes;
  int   rc;

#ifdef DMALLOC
	/*dmalloc_track(track);*/
	_fcpDMALLOC = dmalloc_mark();
#endif
  
  rc = 0;

	/* set this to the default, and then parse the command line */
  host = strdup(EZFCP_DEFAULT_HOST);
  
	/* now parse switches */
  parse_args(argc, argv);

	/* if logfile != 0, then try and open it */
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
  
  /* Call before calling *any* other fcp*() routines */
  if (fcpStartup(logstream, verbosity)) {
    fprintf(stdout, "Failed to initialize ezFCP library\n");
    rc = -1;
		goto cleanup;
  }
  
	/* Make sure all input args are sent to ezFCPlib as advertised */
  hfcp = fcpCreateHFCP(host, port, htl, optmask);

	/* set retry manually; TODO: set() functions for hfcp->options */
	hfcp->options->retry = retry;

  if (b_stdout) {
    /* write data to stdout */
    int fd;

    /* this call will fetch the key to local datastore */
    if (fcpOpenKey(hfcp, keyuri, FCP_MODE_O_READ)) {
			rc = -1;
			goto cleanup;
		}
    
    fd = fileno(stdout);

#if 0 /* metadata handling isn't done */
    while ((bytes = fcpReadMetadata(hfcp, buf, 8192)) > 0) {
      write(fd, buf, bytes);
		}
#endif

    while ((bytes = fcpReadKey(hfcp, buf, 8192)) > 0) {
      write(fd, buf, bytes);
		}

    /* not sure why this is here.. */
    fflush(stdin);

    if (fcpCloseKey(hfcp)) {
			rc = -1;
			goto cleanup;
		}
  }
  
  else { /* otherwise get the key and write it to a file */
		
    if (fcpGetKeyToFile(hfcp, keyuri, file, metafile)) {
      fprintf(stdout, "Could not retrieve key from Freenet: %s\n", keyuri);
      rc = -1;
			goto cleanup;
    }
  }

	rc = 0;

 cleanup:

	if (logfile) {
		fclose(logstream);
		free(logfile);
	}

	free(host);
	free(keyuri);
	free(metafile);

	free(file);

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

	printf("|| %s:%d, size %d, old_addr: %x, new_addr: %x ||\n", f, line, byte_size, old_addr, new_addr);

	return;
}
#endif


static void parse_args(int argc, char *argv[])
{
  struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"htl", 1, 0, 'l'},
    {"metadata", 1, 0, 'm'},
    {"stdout", 0, 0, 's'},

    {"retry", 1, 0, 'a'},
    {"regress", 1, 0, 'e'},
    {"skip-local", 0, 0, 'S'},
    {"rawmode", 0, 0, 'r'},

    {"verbosity", 1, 0, 'v'},
    {"logfile", 1, 0, 'f'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

    {0, 0, 0, 0}
  };
  char short_options[] = "n:p:l:m:sa:e:Srv:f:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != EOF) {
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
			
    case 'l':
      i = atoi( optarg );
      if (i >= 0) htl = i;
      break;
			
    case 'r':
      optmask |= FCP_MODE_RAW;
      break;
      
    case 'm':
      metafile = (char *)malloc(strlen(optarg) + 1);
      strcpy(metafile, optarg);
      break;
      
    case 's':
      /* read from stdout for key data */ 
      b_stdout = 1;
      break;
      
    case 'a':
      i = atoi( optarg );
      if (i > 0) retry = i;
      
    case 'e':
      i = atoi( optarg );
      if (i > 0) regress = i;
      
    case 'S':
      optmask |= FCP_MODE_SKIP_LOCAL;
      break;
			
    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) verbosity = i;
      break;
      
    case 'f':
      logfile = strdup(optarg);
      break;
      
    case 'V':
      printf("FCPtools Version %s\n", VERSION );
      exit(0);
      
    case 'h':
      usage(0);
      exit(0);
    }
  }
  
  if (optind < argc)
    keyuri = strdup(argv[optind++]);
  
  if (optind < argc)
    file = strdup(argv[optind++]);
  
  if (!keyuri) {
    usage("You must specify a valid URI and local filename for key data");
    exit(1);
  }
  
  if ((file) && (b_stdout)) {
    usage("You cannot specifiy both a key filename and --stdout");
    exit(1);
  }
  
  if ((!file) && (!b_stdout)) {
    usage("You must specify a local file, or use the --stdout option");
    exit(1);
  }
}


static void usage(char *s)
{
  if (s) printf("Error: %s\n", s);
  
  printf("FCPtools; Freenet Client Protocol Tools\n");
  printf("CopyLeft 2001 by David McNab <david@rebirthing.co.nz>\n\n");
  
  printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");
  
  printf("Usage: fcpget [-n hostname] [-p port] [-l hops to live]\n");
  printf("              [-m metadata] [-s] [-e regress] [-S] [-v verbosity]\n");
  printf("              [-V] [-h] freenet_uri [FILE]\n\n");
  
  printf("Options:\n\n");
  
  printf("  -n, --address host     Freenet node address\n");
  printf("  -p, --port num         Freenet node port\n");
  printf("  -l, --htl num          Hops to live\n\n");
  
/*printf("  -m, --metadata file    Write key metadata to local file\n");*/
  printf("  -a, --retry num        Number of retries after a timeout\n");
  printf("  -s, --stdout           Write key data to stdout\n");
/*printf("  -e, --regress num      Number of days to regress\n");*/
  printf("  -S, --skip-local       Skip key in local datastore on retrieve\n");
/*printf("  -r, --rawmode          Do not follow redirects on retrieve\n\n");*/
  
  printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
  printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
  printf("  -f, --logfile file     Full pathname for the output log file (default stdout)\n\n");
  
  printf("  -V, --version          Output version information and exit\n");
  printf("  -h, --help             Display this help and exit\n\n");
  
  printf("  uri                    URI to retrieve; variations:\n");
  printf("                           CHK@\n");
  printf("                           KSK@<routing key>\n");
  printf("                           SSK@<public key>[/<docname>]\n\n");
  
  printf("  file                   Write key data to local file\n");
  printf("                         (cannot be used with --stdout)\n\n");
  
  exit(0);
}

