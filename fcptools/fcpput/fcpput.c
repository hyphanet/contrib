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

static void parse_args(int argc, char *argv[]);
static void usage(char *);

#ifdef DMALLOC
void track(const char *file, const unsigned int line,
					 const int func_id,
					 const DMALLOC_SIZE byte_size,
					 const DMALLOC_SIZE alignment,
					 const DMALLOC_PNT old_addr,
					 const DMALLOC_PNT new_addr);
#endif

/* Global vars to fcpput */
char           *host;
unsigned short  port = EZFCP_DEFAULT_PORT;

int   verbosity   = FCP_LOG_NORMAL;
int   htl         = EZFCP_DEFAULT_HTL;
int   retry       = EZFCP_DEFAULT_RETRY;
int   optmask     = 0;
int   future      = 0;
int   min_timeout = 0; /* mininum timeout value */

char *logfile = 0;   /* filename for logfile (if not stdout) */
FILE *logstream = 0; /* FILE * to logfile (or stdout) */

char *keyuri    = 0; /* passed in URI */
char *metafile  = 0; /* name of metadata filename */

char *files[128]; /* array of files to insert (last FILES... parameter) */
int   file_count = 0; /* number of files in the array */

int   b_stdin   = 0; /* was -s passed? */
int   b_genkeys = 0; /* was -g passed? */

int   test_1    = 0; /* fcpOpenKey(), fcpCloseKey() test */


int main(int argc, char* argv[])
{
  hFCP *hfcp;
  int   rc;
  
  char  buf[8193];
  int   bytes;
	int   i;
  
  rc = 0;
	i = 0;
  
#ifdef DMALLOC
	/*dmalloc_track(track);*/
	_fcpDMALLOC = dmalloc_mark();
#endif

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
    fprintf(stdout, "Failed to initialize FCPLib\n");
    rc = -1;
		goto cleanup;
  }
	
  hfcp = fcpCreateHFCP(host, port, htl, optmask);

	/* set retry and DBR info manually */
	hfcp->options->retry = retry;
	hfcp->options->future = future;
	hfcp->options->min_timeout = min_timeout;

#ifdef DMALLOC
	/*dmalloc_verify(0);*/
	dmalloc_log_changed(_fcpDMALLOC, 1, 0, 1);
#endif
	  
  if (b_genkeys) {
		char pub[L_KEY+1];
		char priv[L_KEY+1];
		char crypt[L_KEY+1];
    
    /* generate a keypair and just exit */

    if (fcpMakeSvkKeypair(hfcp, pub, priv, crypt)) {
      fprintf(stdout, "Could not generate keypair\n");
      rc = -1;
			goto cleanup;
    }
    
    fprintf(stdout, "Public: %s\nPrivate: %s\nEntropy: %s\n", pub, priv, crypt);
    rc = 0;
		goto cleanup;
  }

	if ((test_1) && (files[0])) { /* test_1 uses fcpOpenKey() instead of fcpPutKeyFromFile() */
    int kfd;
    int mfd;
    
    if (fcpOpenKey(hfcp, keyuri, FCP_MODE_O_WRITE)) {
			rc = -1;
			goto cleanup;
		}
		
		if ((kfd = open(files[0], _FCP_READFILE_FLAGS, _FCP_READFILE_MODE)) == -1) {
			fprintf(stdout, "test_1: Could not open key file \"%s\"\n", files[0]);
			return -1;
		}
    
		while ((bytes = read(kfd, buf, 8192)) > 0) {
			buf[bytes] = 0;
			fcpWriteKey(hfcp, buf, bytes);
		}
		close(kfd);

		if (metafile) {
			if ((mfd = open(metafile, _FCP_READFILE_FLAGS, _FCP_READFILE_MODE)) == -1) {
				fprintf(stdout, "test_1: Could not open metadata file \"%s\"\n", metafile);
				return -1;
			}
			
			while ((bytes = read(mfd, buf, 8192)) > 0) {
				buf[bytes] = 0;
				fcpWriteMetadata(hfcp, buf, bytes);
			}
			close(mfd);
		}
		
    if (fcpCloseKey(hfcp)) {
			rc = -1;
			goto cleanup;
		}

		fprintf(stdout, "%s\n", hfcp->key->target_uri->uri_str);

		rc = 0;
		goto cleanup;
	}
  
  if (b_stdin) {		
    /* read the key data from stdin */
    int fd;
    
    if (fcpOpenKey(hfcp, keyuri, FCP_MODE_O_WRITE)) {
			rc = -1;
			goto cleanup;
		}
    
    fd = fileno(stdin);
		
    while ((bytes = read(fd, buf, 8192)) > 0)
      fcpWriteKey(hfcp, buf, bytes);

    /* not sure why this is here.. */
    fflush(stdin);

    if (metafile) {
      int mfd;
			
      if ((mfd = open(metafile, _FCP_READFILE_FLAGS, _FCP_READFILE_MODE)) == -1) {
				fprintf(stdout, "Could not open metadata file \"%s\"\n", metafile);				
				return -1;
      }
      
      while ((bytes = read(mfd, buf, 8192)) > 0) {
				buf[bytes] = 0;
				fcpWriteMetadata(hfcp, buf, bytes);
      }
      close(mfd);
    }
    
    if (fcpCloseKey(hfcp)) {
			rc = -1;
			goto cleanup;
		}

		fprintf(stdout, "%s\n", hfcp->key->target_uri->uri_str);
  }
  else { /* call fcpPutKeyFromFile() */
		
		for (i=0; i<file_count; i++) {
			
			if (fcpPutKeyFromFile(hfcp, keyuri, files[i], metafile)) {
				fprintf(stdout, "Could not insert file into Freenet: %s\n", files[i]);
				rc = -1;
				goto cleanup;
			}
			
			fprintf(stdout, "%s/%s\n", hfcp->key->target_uri->uri_str, files[i]);
		}
	}

	/* make sure we enter 'cleanup' with a success value; all others with errors (!0) */
	rc = 0;

 cleanup:
	
	if (logfile) {
		fclose(logstream);
		free(logfile);
	}

	free(host);
	free(keyuri);
	if (metafile) free(metafile);

	for (i=0; i < file_count; i++)
		free(files[i]);

	fcpDestroyHFCP(hfcp);
	free(hfcp);

  fcpTerminate();

#ifdef DMALLOC
	/*dmalloc_verify(0);*/
	dmalloc_log_changed(_fcpDMALLOC, 1, 0, 1);

	dmalloc_vmessage("*** Exiting fcpput.c\n", 0);
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


/* IMPORTANT
   This function should bail if the parameters are bad in any way.  main() can
   then continue cleanly. */

static void parse_args(int argc, char *argv[])
{
  struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"htl", 1, 0, 'l'},
    {"metadata", 1, 0, 'm'},
    {"logfile", 1, 0, 'o'},

    {"stdin", 0, 0, 's'},
    {"retry", 1, 0, 'a'},
    {"delete-local", 0, 0, 'D'},
    {"dbr", 0, 0, 'd'},
    {"future-days", 1, 0, 'f'},
    {"min-timeout", 1, 0, 't'},

		{"meta-redirect", 0, 0, 'M'},

    {"verbosity", 1, 0, 'v'},
    {"genkeys", 0, 0, 'g'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

		{"1", 0, 0, '1'}, /* undocumented: uses fcpOpenKey(), fcpCloseKey() instead
												 of fcpPutKeyFromFile(); inverse true also for fcpget */

    {0, 0, 0, 0}
  };
  char short_options[] = "n:p:l:m:o:sa:Ddf:t:Mv:gVh1";

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

    case 'o':
			logfile = strdup(optarg);
      break;
			
		case 's':
			/* read from stdin for key data */ 
			b_stdin = 1;
      break;
			
		case 'a':
			i = atoi( optarg );
			if (i > 0) retry = i;
			break;

    case 'D':
      optmask |= FCP_MODE_REMOVE_LOCAL;
      break;
			
    case 'd':
      optmask |= FCP_MODE_DBR;
      break;
			
		case 'f':
			i = atoi( optarg );
			if (i > 0) future = i;
			break;

		case 't':
			i = atoi( optarg );
			if (i > 0) min_timeout = i;
			break;

		case 'M':
      optmask |= FCP_MODE_REDIRECT_METADATA;
      break;

    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) verbosity = i;
      break;

    case 'g':
			b_genkeys = 1;
			break;

    case 'V':
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);
			
    case 'h':
      usage(0);
      exit(0);

    case '1':
			test_1 = 1;
			printf( "Using undocumented test_1\n");
		}
	}

	if (b_genkeys) return;

  if (optind < argc)
		keyuri = strdup(argv[optind++]);

	while (optind < argc)
		files[file_count++] = strdup(argv[optind++]);

	if (!keyuri) {
		usage("You must specify a valid URI and local filename for key data");
		exit(1);
	}

	if ((file_count) && (b_stdin)) {
		usage("You cannot specifiy both a key filename and --stdin");
		exit(1);
	}

	if ((file_count==0) && (!b_stdin)) {
		usage("You must specify a local file, or use the --stdin option");
		exit(1);
	}
}


static void usage(char *s)
{
	if (s) printf("Error: %s\n", s);

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("CopyLeft 2001-2004 by David McNab <david@rebirthing.co.nz>\n");
	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");

	printf("Usage: fcpput [-n hostname] [-p port] [-l hops to live]\n");
	printf("              [-m metadata] [-M] [-d] [-f days]\n");
	printf("              [-a retry] [-D] [-o logfile] [-v verbosity]\n");
	printf("              [-s] [-V] [-h] freenet_uri [FILE]...\n\n");

	printf("       fcpput [-n hostname] [-p port] [-g]\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host     Freenet node address\n");
	printf("  -p, --port num         Freenet node port\n");
	printf("  -l, --htl num          Hops to live\n\n");

	printf("  -m, --metadata file    Read key metadata from local file\n");
	printf("  -M, --meta-redirect    Insert metadata via redirect\n\n");

	printf("  -d, --dbr              Insert key as a date-based redirect\n");
	printf("  -f, --future-days num  Number of days into the future to insert DBR key\n\n");

	printf("  -a, --retry num        Number of retries after a timeout\n");
	printf("  -D, --remove-local     Remove key from local datastore on insert\n\n");

	printf("  -o, --logfile file     Full pathname for the output log file (default stdout)\n");
	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n\n");

	printf("  -s, --stdin            Read key data from stdin\n");
	printf("  -g, --genkeys          Generate a keypair then exit\n");
	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  uri                    URI to give newly inserted key; variations:\n");
	printf("                           CHK@\n");
	printf("                           KSK@<routing key>\n");
	printf("                           SSK@<private key>[/<docname>]\n\n");

	printf("  FILE                   Read key data from one or more files\n");
	printf("                         (cannot be used with --stdin)\n\n");

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

