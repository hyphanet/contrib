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

int   verbosity = FCP_LOG_NORMAL;
int   htl       = EZFCP_DEFAULT_HTL;
int   retry     = EZFCP_DEFAULT_RETRY;
int   regress   = EZFCP_DEFAULT_REGRESS;
int   optmask   = 0;

char *logfile = 0; /* filename for logfile (if not stdout) */
FILE *logstream = 0; /* FILE * to logfile (or stdout) */

char *keyuri    = 0; /* passed in URI */
char *metafile  = 0; /* name of metadata filename */

char *files[128]; /* array of files to insert (last FILES... parameter) */
int   file_count = 0; /* number of files in the array */

int   b_stdin   = 0; /* was -s passed? */
int   b_genkeys = 0; /* was -g passed? */


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
    fprintf(stdout, "Failed to initialize ezFCP library\n");
    rc = -1;
		goto cleanup;
  }
	
  hfcp = fcpCreateHFCP(host, port, htl, optmask);

	/* set retry manually; TODO: set() functions for hfcp->options */
	hfcp->options->retry = retry;
  
  if (b_genkeys) {
    
    /* generate a keypair and just exit */
    if (fcpMakeSvkKeypair(hfcp, buf, buf+40, buf+80)) {
      fprintf(stdout, "Could not generate keypair\n");
      rc = -1;
			goto cleanup;
    }
    
    fprintf(stdout, "Public: %s\nPrivate: %s\n", buf, buf+40);
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
		
#if 0 /* metadata handling isn't done */
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
#endif
    
    if (fcpCloseKey(hfcp)) {
			rc = -1;
			goto cleanup;
		}
  }
  else { /* call fcpPutKeyFromFile() */
		
		for (i=0; i<file_count; i++) {
			
			if (fcpPutKeyFromFile(hfcp, keyuri, files[i], metafile)) {
				fprintf(stdout, "Could not insert \"%s\" into freenet from file \"%s\"\n", keyuri, files[i]);
				rc = -1;
				goto cleanup;
			}
			
			fprintf(stdout, "%s <= %s\n", hfcp->key->target_uri->uri_str, files[i]);
		}
	}

	if (file_count > 1)
		fprintf(stdout, "Put %d/%d files into Freenet\n", i, file_count);

	/* make sure we enter 'cleanup' with a success value; all others with errors (!0) */
	rc = 0;
	
 cleanup:
	
	if (logfile) {
		fclose(logstream);
		free(logfile);
	}

	free(host);
	free(keyuri);
	free(metafile);

	for (i=0; i < file_count; i++)
		free(files[i]);

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
  struct option long_options[] = {
    {"address", 1, 0, 'n'},
    {"port", 1, 0, 'p'},
    {"htl", 1, 0, 'l'},
    {"metadata", 1, 0, 'm'},
    {"stdin", 0, 0, 's'},

    {"retry", 1, 0, 'a'},
    {"regress", 1, 0, 'e'},
    {"delete-local", 0, 0, 'D'},

    {"verbosity", 1, 0, 'v'},
    {"logfile", 1, 0, 'f'},
    {"genkeysy", 0, 0, 'g'},

    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},

    {0, 0, 0, 0}
  };
  char short_options[] = "n:p:l:m:sa:e:Dv:f:gVh";

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
			/* read from stdin for key data */ 
			b_stdin = 1;
      break;
			
		case 'a':
			i = atoi( optarg );
			if (i > 0) retry = i;
			
		case 'e':
			i = atoi( optarg );
			if (i > 0) regress = i;
			
    case 'D':
      optmask |= FCP_MODE_DELETE_LOCAL;
      break;
			
    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) verbosity = i;
      break;

    case 'f':
			logfile = strdup(optarg);
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
	printf("CopyLeft 2001 by David McNab <david@rebirthing.co.nz>\n");
	printf("Currently maintained by Jay Oliveri <ilnero@gmx.net>\n\n");

	printf("Usage: fcpput [-n hostname] [-p port] [-l hops to live]\n");
	printf("              [-m metadata] [-s] [-e regress] [-D] [-v verbosity]\n");
	printf("              [-g] [-V] [-h] freenet_uri [FILE]...\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host     Freenet node address\n");
	printf("  -p, --port num         Freenet node port\n");
	printf("  -l, --htl num          Hops to live\n\n");

/*printf("  -m, --metadata file    Read key metadata from local file\n");*/
	printf("  -a, --retry num        Number of retries after a timeout\n");
	printf("  -s, --stdin            Read key data from stdin\n");
/*printf("  -e, --regress num      Number of days to regress\n");*/
	printf("  -D, --delete-local     Delete key from local datastore on insert\n\n");

	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
	printf("  -f, --logfile file     Full pathname for the output log file (default stdout)\n\n");

	printf("  -g, --genkeys          Generate a keypair then exit\n\n");

	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  uri                    URI to give newly inserted key; variations:\n");
	printf("                           CHK@\n");
	printf("                           KSK@<routing key>\n");
	printf("                           SSK@<private key>[/<docname>]\n\n");

	printf("  file                   Read key data from local file\n");
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

