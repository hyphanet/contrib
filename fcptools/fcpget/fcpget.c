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

#include <string.h>
#include <stdlib.h>
#include <errno.h>

/*
	local declarations
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

/*
	fcpget globals
*/
char           *host;
unsigned short  port = EZFCP_DEFAULT_PORT;

int  verbosity = FCP_LOG_NORMAL;
int  htl       = EZFCP_DEFAULT_HTL;
int  retry     = EZFCP_DEFAULT_RETRY;
int  regress   = EZFCP_DEFAULT_REGRESS;
int  optmask   = 0;

char *logfile = 0;

char  *keyuri    = 0;
char  *keyfile   = 0;
char  *metafile  = 0;

int    b_stdout   = 0;


int main(int argc, char* argv[])
{
	hFCP *hfcp;
	FILE *file;

	char  buf[1024];
	int   rc;

	rc = 0;
	host = strdup(EZFCP_DEFAULT_HOST);

	parse_args(argc, argv);

	/* Call before calling *any* other ?fcp* routines */
	if (fcpStartup(logfile, retry, verbosity)) {
		fprintf(stdout, "Failed to initialize ezFCP library\n");
		return -1;
	}

	/* Make sure all input args are sent to ezFCPlib as advertised */
	hfcp = fcpCreateHFCP(host, port, htl, regress, optmask);

	if (b_stdout) {
		/* write the key data to stdout */
		int c;

		if (fcpOpenKey(hfcp, keyuri, FCP_O_READ))	return -1;

#if 0 /* all this has to be adapted for get */
		while ((c = putc(stdout)) != -1) {
			buf[0] = c;
			fcpWriteKey(hfcp, buf, 1);
		}

		/* @@@ TODO: verify on Unix */
		fflush(stdout);

		if (metafile) {
			int bytes;
			int metafile_size;

			if (!(file = fopen(metafile, "rb"))) {
				fprintf(stdout, "Could not open metadata file \"%s\"\n", metafile);				
				return -1;
			}

			for (bytes = 0; (c = getc(file)) != -1; bytes++) {
				buf[0] = c;
				fcpWriteMetadata(hfcp, buf, 1);
			}

			metafile_size = file_size(metafile);
			if (bytes != metafile_size) {
				fprintf(stdout, "Wrote %d/%d bytes of metadata; discarded rest\n", bytes, metafile_size);
				return -1;
			}
		}
#endif

		if (fcpCloseKey(hfcp)) return -1;
	}

	else {
		/* use keyfile as the filename of key data */
		if (fcpGetKeyToFile(hfcp, keyuri, keyfile, metafile)) {
			fprintf(stdout, "Could not retrieve \"%s\" from freenet to file \"%s\"\n", keyuri, keyfile);
			return -1;
		}
	}

	fprintf(stdout, "Operation Successfull\n");
	fcpTerminate();

	/*fprintf(stdout, "%s\n", hfcp->key->target_uri->uri_str);*/
	fcpDestroyHFCP(hfcp);
	
#ifdef WINDOWS_DISABLE
	system("pause");
#endif
	
	return 0;
}


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

    {"verbosity", 1, 0, 'v'},
    {"logfile", 1, 0, 'f'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

    {0, 0, 0, 0}
  };
  char short_options[] = "n:p:l:m:sa:e:Sv:f:Vh";

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
      optmask |= HOPT_SKIP_LOCAL;
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
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);
			
    case 'h':
      usage(0);
      exit(0);
		}
	}
	
  if (optind < argc) {
		keyuri = (char *)malloc(strlen(argv[optind]) + 1);
		strcpy(keyuri, argv[optind++]);
	}
	
  if (optind < argc) {
		keyfile = (char *)malloc(strlen(argv[optind]) + 1);
		strcpy(keyfile, argv[optind++]);
	}

	if (!keyuri) {
		usage("You must specify a valid URI and local filename for key data");
		exit(1);
	}

	if ((keyfile) && (b_stdout)) {
		usage("You cannot specifiy both a key filename and --stdout");
		exit(1);
	}

	if ((!keyfile) && (!b_stdout)) {
		usage("You must specify a local file, or use the --stdout option");
		exit(1);
	}
}


static void usage(char *s)
{
	if (s) printf("Error: %s\n", s);
	
	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001-2003 by David McNab <david@rebirthing.co.nz>\n\n");

	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");

	printf("Usage: fcpget [-n hostname] [-p port] [-l hops to live]\n");
	printf("              [-m metadata] [-s] [-e regress] [-S] [-v verbosity]\n");
	printf("              [-V] [-h] freenet_uri filename\n\n");

	printf("Options:\n\n");

	printf("  -n, --address host     Freenet node address\n");
	printf("  -p, --port num         Freenet node port\n");
	printf("  -l, --htl num          Hops to live\n\n");

	printf("  -m, --metadata file    Read key metadata from local file\n");
	printf("  -a, --retry num        Number of retries after a timeout\n");
	printf("  -s, --stdout           Write key data to stdout\n");
/*printf("  -e, --regress num      Number of days to regress\n");*/
	printf("  -S, --skip-local       Skip key in local datastore on retrieve\n\n");

	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
	printf("  -f, --logfile file     Full pathname for the output log file (default stdout)\n\n");

	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  uri                    URI to give newly inserted key; variations:\n");
	printf("                           CHK@\n");
	printf("                           KSK@<routing key>\n");
	printf("                           SSK@<private key>[/<docname>]\n\n");

	printf("  file                   Write key data to local file\n");
	printf("                         (cannot be used with --stdout)\n\n");
	
	exit(0);
}
