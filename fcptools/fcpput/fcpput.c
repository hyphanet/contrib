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


#include <sys/types.h>
#include <sys/stat.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#define _GNU_SOURCE
#include "getopt.h"

#include "ezFCPlib.h"


extern long file_size(char *);
extern int  put_redirect(hFCP *hfcp, char *uri_dest);


void  parse_args(int argc, char *argv[]);
void  usage(char *);

char  *keyuri = 0;
char  *keyfile = 0;
char  *metafile = 0;

int    b_stdin = 0;

int main(int argc, char* argv[])
{
	hFCP *hfcp;
	hFCP *hfcp_meta;

	FILE *file;

	char  buf[1024];
	int   rc;

	rc = 0;

	if (fcpStartup()) {
		printf("Call to fcpStartup() failed\n");
		return 1;
	}

	/* must occur after fcpStartup() since it changes _fcp* variables */
	parse_args(argc, argv);

	hfcp = _fcpCreateHFCP();

	if (b_stdin) { /* read the key data from stdin */
		int c;

		if (fcpOpenKey(hfcp, "CHK@", _FCP_O_WRITE)) {
			_fcpLog(FCP_LOG_DEBUG, "could not open key for writing");
			return -1;
		}
		_fcpLog(FCP_LOG_DEBUG, "opened key for writing..");

		/* read it from stdin */
		while ((c = getc(stdin)) != -1) {
			buf[0] = c;
			fcpWriteKey(hfcp, buf, 1);
		}

		if (metafile) {
			int bytes;

			if (!(file = fopen(metafile, "rb"))) {
				
				_fcpLog(FCP_LOG_DEBUG, "could not open metadata file \"%s\"", metafile);
				return -1;
			}

			for (bytes = 0; (c = getc(file)) != -1; bytes++) {
				buf[0] = c;
				fcpWriteMetadata(hfcp, buf, 1);
			}

			if (bytes != file_size(metafile)) {
				_fcpLog(FCP_LOG_DEBUG, "did not write all bytes in metafile; wrote %d", bytes);
				return -1;
			}
			
			_fcpLog(FCP_LOG_DEBUG, "wrote key metadata:\n%s\n", buf);
		}

		_fcpLog(FCP_LOG_DEBUG, "now beginning network insert of file data..");
		
		fcpCloseKey(hfcp);
		_fcpLog(FCP_LOG_DEBUG, "wrote key to Freenet");
	}
	else { /* use keyfile as the filename of key data */

		if (fcpPutKeyFromFile(hfcp, keyfile, metafile)) {
			_fcpLog(FCP_LOG_CRITICAL, "Could not insert file \"%s\" into Freenet", keyfile);
			return -1;
		}
	}

	/* stored within hfcp->key->uri is the inserted CHK-type uri */
	
	hfcp_meta = _fcpCreateHFCP();
	hfcp_meta->key = _fcpCreateHKey();

	if (_fcpParseURI(hfcp_meta->key->uri, keyuri)) {
		_fcpLog(FCP_LOG_VERBOSE, "Invalid Freenet URI \"%s\"", keyuri);
		return -1;
	}

	/*** CHK ***/
	if (hfcp_meta->key->uri->type == KEY_TYPE_CHK) {
		_fcpLog(FCP_LOG_NORMAL, "%s", hfcp->key->uri->uri_str);
	}

	/*** KSK ***/
	else if (hfcp_meta->key->uri->type == KEY_TYPE_KSK) {
		if (put_redirect(hfcp_meta, hfcp->key->uri->uri_str)) {
			
			_fcpLog(FCP_LOG_VERBOSE, "Could not insert redirect \"%s\"", hfcp_meta->key->uri->uri_str);
			return -1;
		}
		else {
			_fcpLog(FCP_LOG_NORMAL, "%s", hfcp_meta->key->uri->uri_str);
		}
	}

	/*** SSK ***/
	else if (hfcp_meta->key->uri->type == KEY_TYPE_SSK) {
		if (put_redirect(hfcp_meta, hfcp->key->uri->uri_str)) {
			
			_fcpLog(FCP_LOG_VERBOSE, "Could not insert redirect \"%s\"", hfcp_meta->key->uri->uri_str);
			return -1;
		}
		else {
			_fcpLog(FCP_LOG_NORMAL, "%s", hfcp_meta->key->uri->uri_str);
		}
	}
			
	_fcpDestroyHFCP(hfcp);
	fcpTerminate();
	
#ifdef WINDOWS_DISABLE
	system("pause");
#endif
	
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
    {"metadata", 1, 0, 'm'},
    {"stdin", 0, 0, 's'},

    {"regress", 1, 0, 'e'},
    {"raw", 0, 0, 'r'},
    {"verbosity", 1, 0, 'v'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

    {0, 0, 0, 0}
  };
  char short_options[] = "n:p:l:m:se:rv:Vh";

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

		case 'm':
			metafile = (char *)malloc(strlen(optarg) + 1);
      strcpy(metafile, optarg);
      break;
			
		case 's':
			/* read from stdin for key data */ 
			b_stdin = 1;
      break;
			
		case 'e':
			i = atoi( optarg );
			if (i > 0) _fcpRegress = i;
			
    case 'r':
      _fcpRawmode = 1;
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

	if ((!keyfile) && (!b_stdin)) {
		usage("You must specify a local file, or use the --stdin option");
		exit(1);
	}
}


void usage(char *s)
{
	if (s) printf("Error: %s\n", s);

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001-2003 by David McNab <david@rebirthing.co.nz>\n");
	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");

	printf("Usages: fcpput [OPTIONS] <uri> <file>\n");
	printf("        fcpput [OPTIONS] --stdin <uri>\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host     Freenet node address (default \"%s\")\n", EZFCP_DEFAULT_HOST);
	printf("  -p, --port num         Freenet node port (default %d)\n", EZFCP_DEFAULT_PORT);
	printf("  -l, --htl num          Hops to live (default %d)\n\n", EZFCP_DEFAULT_HTL);

	printf("  -m, --metadata file    Read key metadata from local \"file\"\n");
	printf("  -s, --stdin            Read key data from stdin\n");

	printf("  -e, --regress num      Number of days to regress (default %d)\n", EZFCP_DEFAULT_REGRESS);
	printf("  -r, --raw              Raw mode - don't follow redirects\n");
	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n\n");

	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  uri                    URI to give newly inserted key; variations:\n");
	printf("                           CHK@\n");
	printf("                           KSK@<routing key>\n");
	printf("                           SSK@<private key>[/<docname>]\n\n");

	printf("  file                   Read key data from local \"file\"\n");
	printf("                         (optional if --stdin used)\n\n");

	printf("Examples:\n\n");

	printf("To insert a Content Hash Key (CHK) with file \"gpl.txt\":\n");
	printf("  fcpput CHK@ /home/hapi/gpl.txt\n\n");
	
	printf("To insert a Keyword Signed Key (KSK) with file \"gpl.txt\" against a\n");
	printf("freenet node at address raven.cp.net with hops to live 10:\n");
	printf("  fcpput --htl 10 --address raven.cp.net KSK@gpl.txt /home/hapi/gpl.txt\n\n");

	printf("To insert a Subspace Signed Key (SSK) with file \"gpl.txt\":\n");
	printf("  fcpput SSK@LNlEaG7L24af-OH~CKmyPOvJ~EM/ gpl.txt\n\n");

	printf("To insert an SSK within named document \"licenses\":\n");
	printf("  fcpput SSK@LNlEaG7L24af-OH~CKmyPOvJ~EM/licenses gpl.txt\n\n");
}

