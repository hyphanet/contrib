/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
  CopyLeft (c) 2001 by David McNab
	
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


#include <sys/types.h>
#include <sys/stat.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#define _GNU_SOURCE
#include "getopt.h"

#include "ezFCPlib.h"


void  parse_args(int argc, char *argv[]);
void  usage(char *);

char  *keyuri = 0;
char  *keyfile = 0;
char  *metafile = 0;


int main(int argc, char* argv[])
{
	hFCP *hfcp;
	int rc;

	if (fcpStartup()) {
		printf("Call to fcpStartup() failed\n");
		return 1;
	}

	parse_args(argc, argv);
	
	hfcp = _fcpCreateHFCP();
	rc = fcpPutKeyFromFile(hfcp, keyfile, metafile);

	if (rc) printf("bad\n");

	_fcpDestroyHFCP(hfcp);
	fcpTerminate();

	return 0;
}


/* IMPORTANT
   This function should bail if the parameters are bad in any way.  main() can
   then continue cleanly. */

void parse_args(int argc, char *argv[])
{
  struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"htl", 1, 0, 'l'},
    {"regress", 1, 0, 'e'},
    {"raw", 0, 0, 'r'},
    {"key", 1, 0, 'k'},
    {"metadata", 1, 0, 'm'},

    {"verbosity", 1, 0, 'v'},
		{"attempts", 1, 0, 'a'},
		/* {"size", 1, 0, 's'}, */
		{"threads", 1, 0, 't'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

    {0, 0, 0, 0}
  };
  /* char short_options[] = "n:p:l:e:rk:m:v:a:s:t:Vh"; */
  char short_options[] = "n:p:l:e:rk:m:v:a:t:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != EOF) {
    switch (c) {
			
    case 'n':
			if (_fcpHost) free(_fcpHost);
			_fcpHost = (char *)malloc(strlen(optarg) + 1);
			
      strcpy( _fcpHost, optarg);
      _fcpLog(FCP_LOG_DEBUG, "parse_args() using host %s", _fcpHost);
      break;
			
    case 'p':
      i = atoi( optarg );
      if (i > 0) _fcpPort = i;
      _fcpLog(FCP_LOG_DEBUG, "parse_args() using port %d", _fcpPort);
      break;
			
    case 'l':
      i = atoi( optarg );
      if (i >= 0) _fcpHtl = i;
      break;
			
		case 'e':
			i = atoi( optarg );
			if (i > 0) _fcpRegress = i;
			
    case 'r':
      _fcpRawmode = 1;
      break;
			
    case 'k':
			keyuri = (char *)malloc(strlen(optarg) + 1);
      strcpy(keyuri, optarg);
      break;
			
		case 'm':
			metafile = (char *)malloc(strlen(optarg) + 1);
      strcpy(metafile, optarg);
      break;
			
    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) _fcpVerbosity = i;
      break;
			
		case 'a':
			i = atoi( optarg );
			break;
			
    case 'V':
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);
			
    case 'h':
      usage(0);
      exit(0);
		}
	}
	
  if (optind < argc) {
		keyfile = (char *)malloc(strlen(argv[optind]) + 1);
		strcpy(keyfile, argv[optind++]);
	}
	else {
		if (!keyfile) {
			/* This means no file to insert was specified via "file" param, nor -k */
			usage("You must specify a file parameter, or use the option \"-k\"");
			exit(1);
		}
	}
	
	if (!keyuri) {
		keyuri = (char *)malloc(5);
		strcpy(keyuri, "CHK@");
	}
}


void usage(char *s)
{
	if (s) printf("Error: %s\n", s);

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001 by David McNab\n\n");

	printf("Usage: fcpput [OPTIONS] file\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host     Freenet node address (default \"%s\")\n", EZFCP_DEFAULT_HOST);
	printf("  -p, --port num         Freenet node port (default %d)\n", EZFCP_DEFAULT_PORT);
	printf("  -l, --htl num          Hops to live (default %d)\n", EZFCP_DEFAULT_HTL);
	printf("  -e, --regress num      Number of days to regress (default %d)\n", EZFCP_DEFAULT_REGRESS);
	printf("  -r, --raw              Raw mode - don't follow redirects\n");
	printf("  -k, --key key          Name of Freenet key (if unspecified, insert as CHK\n");
	printf("  -m, --metadata file    Read key's metadata from file (default \"stdin\")\n\n");

	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n\n");

  printf("  -a, --attempts num     Attempts to insert each file (default %d)\n", 1);
  /* printf("  -s, --size num         Size of splitfile chunks (default %d)\n", CHUNK_BLOCK_SIZE); */
  printf("  -t, --threads num      Number of splitfile threads (default %d)\n\n", FCP_MAX_SPLIT_THREADS);

	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  key                    Freenet key (...)\n");
	printf("  file                   Read key's data from file (default \"stdin\")\n\n");
	
	printf("NOTE - only the inserted key URI will be written to stdout\n"
				 "Therefore, you can use this utility in shell backtick commands\n\n");
 
	exit(0);
}


