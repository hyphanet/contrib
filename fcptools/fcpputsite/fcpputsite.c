/*
	fcpputsite.c - simple command line client that uses FCP
	for insertion of freesites

	CopyLeft () 2001 by David McNab
*/

#include <unistd.h>

#include "ezFCPlib.h"
#include "fcpputsite.h"

#define _GNU_SOURCE
#include "getopt.h"

/*
	PRIVATE DECLARATIONS
*/
static void parse_args(int argc, char *argv[]);
static int  parse_num(char *s);
static void usage(char *);
static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len);

/* Configurable command-line parameters
	 Strings/Arrays that have default values if not set explicitly from the
	 command line should be initialized to "", a zero-length string.
*/
static char  keyUri[L_URI];
static char  keyFile[L_FILENAME] = "";
static char  metaFile[L_FILENAME] = "";

static char  nodeAddr[L_HOST] = EZFCP_DEFAULT_HOST;
static int   nodePort = EZFCP_DEFAULT_PORT;
static int   htlVal = EZFCP_DEFAULT_HTL;

static int   regress = EZFCP_DEFAULT_REGRESS;
static int   rawMode = EZFCP_DEFAULT_RAWMODE;

static int   verbosity = FCP_LOG_NORMAL;
static int   genKeypair = 0;           // flag requiring keypair generation only
static int   dodelete = 0;				// set HFCP->delete flag.
static int	 fcpSplitChunkSize;
static int	 maxSplitThreads;

static PutSiteOptions siteOptions;


int main(int argc, char* argv[])
{
	int error;

	memset(&siteOptions, 0, sizeof(siteOptions));
	siteOptions.defaultFile = "index.html";
	siteOptions.maxRetries = FCPPUTSITE_ATTEMPTS;
	siteOptions.maxThreads = FCPPUTSITE_INSERT_THREADS;
	siteOptions.daysAhead = 0;
	siteOptions.dodbr=1;

	// go thru command line args
	parse_args(argc, argv);

	// try and fire up FCP library
	if (fcpStartup(nodeAddr, nodePort, htlVal, 0,
			maxSplitThreads) != 0)
	{
		_fcpLog(FCP_LOG_CRITICAL, "Unable to connect with Freenet node's FCP interface\n");
		return 1;
	}
	fcpSetDelete(dodelete);

	// Does the user just want a keypair?
	if (genKeypair)
	{
		HFCP *hfcp = fcpCreateHandle();

		siteOptions.pubKey = (char *) malloc(L_KEY);
		siteOptions.privKey = (char *) malloc(L_KEY);

		if (fcpMakeSvkKeypair(hfcp, siteOptions.pubKey,
				siteOptions.privKey) != 0) {
			_fcpLog(FCP_LOG_CRITICAL, "ERROR - failed to generate keypair\n");
			exit(1);
		}

		printf("Here's your keypair:\n");
		printf("Public:  %s\n", siteOptions.pubKey);
		printf("Private: %s\n", siteOptions.privKey);
		exit(0);
	}
	
	// all ok - now go off and try to insert the site
	
	error = insertFreesite(&siteOptions);
	_fcpLog(FCP_LOG_DEBUG, "fcpputsite: returned from insertFreesite");

	return error;
}

int fcpLogCallback(int level, char *buf)
{
	if (level <= verbosity)
		printf("%s\n", buf);

	return 0;
}

static void parse_args(int argc, char *argv[])
{
	static struct option long_options[] = {
		{"address", 1, NULL, 'n'},
		{"port", 1, NULL, 'p'},
		{"htl", 1, NULL, 'l'},
		{"raw", 0, NULL, 'r'},
		{"attempts", 1, NULL, 'a'},
		{"size", 1, NULL, 's'},
		{"split-threads", 1, NULL, 't'},
		{"insert-threads", 1, NULL, 'i'},
		{"no-dbr", 0, NULL, 'd'},
		{"gen-keypair", 0, NULL, 'g'},
		{"days", 1, NULL, 'f'},
		{"default", 1, NULL, 'D'},
		{"verbosity", 1, NULL, 'v'},
		{"version", 0, NULL, 'V'},
		{"help", 0, NULL, 'h'},
		{"delete", 0, NULL, 'X'},
		{"record-file", 1, NULL, 'R'},
		{0, 0, 0, 0}
	};
	static char short_options[] = "l:n:p:s:t:a:p:i:dgf:D:rv:VhR:X";

	/* c is the option code; i is buffer storage for an int */
	int c, i;

	while ((c = getopt(argc, argv, short_options)) != -1)
	{
		switch (c)
		{
			case 'n':
				strncpy( nodeAddr, optarg, L_HOST );
				break;

			case 'p':
				i = atoi( optarg );
				if (i > 0) nodePort = i;
				break;
	
			case 'l':
				i = atoi( optarg );
				htlVal = i;
				break;
	
			case 'r':
				rawMode = 1;
				break;
	
			case 'a':
				i = atoi( optarg );
				if (i > 0) siteOptions.maxRetries = i;
				break;
	
			case 's':
				i = atoi( optarg );
				if (i > 0) fcpSplitChunkSize = i;
				break;
	
			case 't':
				i = atoi( optarg );
				maxSplitThreads = i;
				break;
	
			case 'i':
				i = atoi( optarg );
				if (i > 0) siteOptions.maxThreads = i;
				break;
	 
			case 'd':
				siteOptions.dodbr = 0;
				break;
	 
			case 'g':
				genKeypair = 1;
				return;
	 
			case 'f':
				i = atoi( optarg );
				siteOptions.daysAhead = i;
				break;
	 
			case 'D':
				siteOptions.defaultFile=strdup(optarg);
				break;
	 
			case 'v':
				i = atoi( optarg );
				if ((i >= 0) && (i <= 4)) verbosity = i;
				break;
	
			case 'V':
				printf( "FCPtools Version %s\n", VERSION );
				exit(0);
	
			case 'h':
				usage(NULL);
				break;

			case 'X':
				dodelete=1;
				break;
			case 'R':
				siteOptions.recordFile=strdup(optarg);
				break;
		}

	}

	/* Process NAME, DIR, PUB, PRV parameters here */

	if (optind < argc) siteOptions.siteName = strdup(argv[optind++]);
	else usage("You must specify a site name");

	if (optind < argc) siteOptions.siteDir = strdup(argv[optind++]);
	else usage("You must specify a directory");

	if (optind < argc) siteOptions.pubKey = strdup(argv[optind++]);
	else usage("You must specify a public key");

	if (optind < argc) siteOptions.privKey = strdup(argv[optind++]);
	else usage("You must specify a private key");
}

static void usage(char *s)
{
	if (s)
	   	printf("Error: %s\n", s);

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001 by David McNab\n\n");

	printf("Usage: fcpputsite [OPTIONS] name dir pubkey prvkey\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host        Freenet node address (default \"%s\")\n", EZFCP_DEFAULT_HOST);
	printf("  -p, --port num            Freenet node port (default %d)\n", EZFCP_DEFAULT_PORT);
	printf("  -l, --htl num             Hops to live (default %d)\n", EZFCP_DEFAULT_HTL);
	printf("  -r, --raw                 Raw mode - don't follow redirects\n\n");

	printf("  -a, --attempts num        Attempts to insert each file (default %d)\n", FCPPUTSITE_ATTEMPTS);
	printf("  -s, --size num            Size of splitfile chunks (default %d)\n", SPLIT_BLOCK_SIZE);
	printf("  -t, --split-threads num   Number of splitfile threads (default %d)\n", FCP_MAX_SPLIT_THREADS);
	printf("  -i, --insert-threads num  Max number of insert threads (default %d)\n\n", FCPPUTSITE_INSERT_THREADS);
 
	printf("  -d, --no-dbr              Do not insert a dbr redirection\n");
	printf("  -g, --gen-keypair         Do not insert - just create an SVK keypair instead\n");
	printf("  -f, --days num            Insert a map file num days in the future (default is today)\n");
	printf("  -D, --default file        Name of site's default file (default \"%s\")\n", FCPPUTSITE_DEFAULT_FILE);
	printf("                            (the default file must exist in the specified directory)\n");  
	printf("  -v, --verbosity num       Verbosity of log messages (default 2)\n");
	printf("                            0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
	printf("  -R, --record-file name    Records progress of a large site in 'name' for resuming later\n\n");
 	
	printf("  -V, --version             Output version information and exit\n");
	printf("  -h, --help                Display this help and exit\n\n");
 
	printf("Required arguments:\n\n");

	printf("  name      Name of site (SSK subspace identifier)\n");
	printf("  dir       The directory containing the site\n");
	printf("  pubkey    The SSK public key\n");
	printf("  prvkey    The SSK private key\n\n");
	
	exit(-1);
}


char *strsav(char *old, int *oldlen, char *text_to_append)
{
	int old_len, new_len;
	char *p;

	if(( text_to_append == NULL) || (*text_to_append == '\0')) {
		return(old);
	}

	if(old) {
	if (oldlen)
		old_len=*oldlen;
	else
		old_len = strlen(old);
	} else {
		old_len = 0;
	}

	new_len = old_len + strlen(text_to_append) + 1;

	if(old) {
		if((p = (char *)realloc(old, new_len)) == NULL) {
			_fcpLog(FCP_LOG_CRITICAL, "realloc(%d) bytes for proxy_args failed!\n", new_len);
			exit(1);
		}
	} else {
		if((p = (char *)safeMalloc(new_len)) == NULL) {
			_fcpLog(FCP_LOG_CRITICAL,
				"safeMalloc(%d) bytes for proxy_args failed!\n", new_len);
			exit(1);
		}
	}

	strcpy(p + old_len, text_to_append);
	if (oldlen)
		*oldlen += strlen(text_to_append);
	return(p);
}


static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len)
{
	int new_len;
	char *p;

	if(buf_to_append == NULL)
		return(old);

	if(old == NULL)
		old_len = 0;

	new_len = old_len + add_len;

	if(old) {
		if((p = (char *)realloc(old, new_len)) == NULL) {
			_fcpLog(FCP_LOG_CRITICAL,
					"realloc(%d) bytes for proxy_args failed!\n", new_len);
			exit(1);
		}
	} else {
		if((p = (char *)safeMalloc(new_len)) == NULL) {
			_fcpLog(FCP_LOG_CRITICAL,
					"safeMalloc(%d) bytes for proxy_args failed!\n", new_len);
			exit(1);
		}
	}

	memcpy(p + old_len, buf_to_append, add_len);
	return(p);
}


//
// extension of atoi()
//
// this func recognises suffices on numbers
//
// eg '64k' will get parsed as 65536
//
// recognises the suffices 'k', 'K', 'm', 'M', 'g', 'G'
//
// Thanks to mjr for this lovely snippet

static int parse_num(char *s)
{
	int n = atoi(s);
	switch (s[strlen(s)-1])
	{
		case 'G':
		case 'g':
			return n << 30;
		case 'M':
		case 'm':
			return n << 20;
		case 'K':
		case 'k':
			return n << 10;
		default:
			return n;
	 }
}

