/*
  fcpget.c - simple command line client that uses FCP

  CopyLeft () 2001 by David McNab
*/


#include <sys/stat.h>

#include <fcntl.h>
#include <stdio.h>

#define _GNU_SOURCE
#include "getopt.h"

#include "ezFCPlib.h"


/*
  PRIVATE DECLARATIONS
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

/* Configurable command-line parameters
	 Strings/Arrays that have default values if not set explicitly from the
	 command line should be initialized to "", a zero-length string.
*/
char *keyUri;
char *keyFile;
char *metaFile;


int main(int argc, char* argv[])
{
	HFCP *hfcp;
	char buf[1024];
	int count;
	int fd;
	
	int numtimes = 3;
	int i;
	
	/* go thru command line args */
	parse_args(argc, argv);
	
	/* try and fire up FCP library */
	if (fcpStartup()) {
		_fcpLog(FCP_LOG_CRITICAL, "Failed to connect to node - aborting");
		return 1;
	}
	
	/* create an FCP handle */
	hfcp = fcpCreateHandle();
	
	/* repeat many times - hunting mem leaks */
	for (i = 0; i < numtimes; i++) {
		int res;
		
		/* try to get the key open */
		_fcpLog(FCP_LOG_VERBOSE, "Trying to open '%s'", keyUri);
		res = fcpOpenKey(hfcp, keyUri, (_FCP_O_READ | (hfcp->raw ? _FCP_O_RAW : 0)));
		
		if (res != 0)
			_fcpLog(FCP_LOG_CRITICAL, "Failed to open '%s', retry %d", keyUri, i);
		else
			break;
	}
	
	if (i<numtimes) {
		
		/* output key data, if any */
		if (hfcp->keysize > 0) {
			
			/* nuke file if it exists */
			if (keyFile[0]) {
				unlink(keyFile);
				
#ifdef WINDOWS
				/* open a file to write the key to */
				if ((fd = open(keyFile, _O_CREAT | _O_RDWR | _O_BINARY, _S_IREAD | _S_IWRITE)) < 0) {
					printf("Cannot create file '%s'\n", keyFile);
					return -1;
				}
#else
				/* open a file to write the key to */
				if ((fd = open(keyFile, O_CREAT| O_WRONLY, S_IRUSR | S_IWUSR)) < 0) {
					printf("Cannot create file '%s'\n", keyFile);
					return -1;
				}
#endif
			} /* if (keyFile[0]) */
			else
				fd = 1; /* I believe fd==1 is STDOUT */
			
			/* suck all of key's data into this file */
			while ((count = fcpReadKey(hfcp, buf, 1024)) > 0)
				write(fd, buf, count);
			
			if (fd != 1) close(fd);
		} /* if (hfcp->keysize > 0) */
		
		if (hfcp->rawMetadata) {
			
			if (metaFile[0]) {
				if ((metaFile[0]== '-') && (metaFile[1]=='\0'))
					fd = 1;
				
				else {
					/* nuke file if it exists */
					unlink(metaFile);
#ifdef WINDOWS
					/* open a file to write the key to */
					if ((fd = open(metaFile, _O_CREAT | _O_RDWR | _O_BINARY, _S_IREAD | _S_IWRITE)) < 0) {
						printf("Cannot create file '%s'\n", metaFile);
						return -1;
					}
#else
					/* open a file to write the key to */
					if ((fd = open(metaFile, O_CREAT| O_WRONLY, S_IREAD | S_IWRITE)) < 0) {
						printf("Cannot create file '%s'\n", metaFile);
						return -1;
					}
#endif
				}
				/* suck all of key's data into this file */
				write(fd, hfcp->rawMetadata, strlen(hfcp->rawMetadata));
				
				if(fd != 1) close(fd);
			} /* if (metaFile[0]) */
		} /* if (hfcp->rawMetadata) */
		
			/* all done */
		fcpCloseKey(hfcp);
		fcpDestroyHandle(hfcp);
		
	} /* if (i<numtimes) */
	
	return 0;
}

/* IMPORTANT
   This function should bail if the parameters are bad in any way.  main() can
   then continue cleanly. */

static void parse_args(int argc, char *argv[])
{
  static struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"htl", 1, 0, 'l'},
    {"regress", 1, 0, 'e'},
    {"raw", 0, 0, 'r'},
    {"metadata", 1, 0, 'm'},
    {"verbosity", 1, 0, 'v'},
    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},
    {0, 0, 0, 0}
  };
  static char short_options[] = "n:p:l:e:rm:v:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != -1) {
    switch (c) {

    case 'n':
      fcpSetHost(optarg);
      break;

    case 'p':
      i = atoi( optarg );
      if (i > 0) _fcpPort = i;
      break;

    case 'l':
      i = atoi( optarg );
      if (i > 0) _fcpHtl = i;
      break;

    case 'e':
      i = atoi( optarg );
      if (i > 0) _fcpRegress = i;
      break;

    case 'r':
      _fcpRawmode = 1;
      break;

    case 'm':
			metaFile = (char *) malloc(strlen(optarg) + 1);
			
      strcpy(metaFile, optarg);;
      break;

    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) _fcpVerbosity = i;
      break;
			
    case 'V':
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);
			
    case 'h':
      usage(0);
      break;
    }
  }
	
  if (optind < argc) {
		metaFile = (char *) malloc(strlen(argv[optind]) + 1);
		strcpy(keyUri, argv[optind++]);
	}
  else usage("You must specify a key");
	
  /* If there's another parameter, it's the FILE to store the results (optional) */
  if (optind < argc) {
		keyFile = (char *) malloc(strlen(argv[optind]) + 1);
    strcpy(keyFile, argv[optind++]);
	}
}


static void usage(char *s)
{
	if (s) printf("Error: %s\n", s);
	
	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001 by David McNab\n\n");
	
	printf("Usage: fcpget [OPTIONS] key [file]\n\n");
	
	printf("Options:\n\n");
	printf("  -n, --address host   Freenet node address (default \"%s\")\n", EZFCP_DEFAULT_HOST);
	printf("  -p, --port num       Freenet node port (default %d)\n", EZFCP_DEFAULT_PORT);
	printf("  -l, --htl num	       Hops to live (default %d)\n", EZFCP_DEFAULT_HTL);
	printf("  -e, --regress num    Number of days to regress (default %d)\n", EZFCP_DEFAULT_REGRESS);
	printf("  -r, --raw            Raw mode - don't follow redirects\n\n");
	
	printf("  -m, --metadata file  Write key's metadata to file (default \"stdout\")\n");
	printf("  -v, --verbosity num  Verbosity of log messages (default 2)\n");
	printf("                       0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n\n");
	
	printf("  -V, --version	       Output version information and exit\n");
	printf("  -h, --help           Display this help and exit\n\n");
	
	printf("  key                  Freenet key (freenet:KSK@gpl.txt)\n");
	printf("  file                 Write key's data to file (default \"stdout\")\n\n");
	
	exit(0);
}
