/*
	fcpget.c - simple command line client that uses FCP
	CopyLeft () 2001 by David McNab
*/

#include <sys/types.h>
#include <sys/stat.h>

#include "ezFCPlib.h"

#define _GNU_SOURCE
#include "getopt.h"

#include <unistd.h>
#include <stdio.h>

#define FCPPUT_ATTEMPTS 3

extern int fcpSplitChunkSize;

static void parse_args(int argc, char *argv[]);
static void usage(char *);
static char *strsav(char *old, char *text_to_append);
static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len);
static int parse_num(char *s);

/* Configurable command-line parameters
   Strings/Arrays that have default values if not set explicitly from the
   command line should be initialized to "", a zero-length string.
*/
char  keyUri[L_URI];
char  keyFile[L_FILENAME] = "";
char  metaFile[L_FILENAME] = "";
char *metaData = 0;
 
char  nodeAddr[L_HOST] = EZFCP_DEFAULT_HOST;
int   nodePort = EZFCP_DEFAULT_PORT;
int   htlVal = EZFCP_DEFAULT_HTL;
int   regress = EZFCP_DEFAULT_REGRESS;
int   rawMode = EZFCP_DEFAULT_RAWMODE;
int   quiet = 0;
int   attempts = FCPPUT_ATTEMPTS;
int   splitThreads = FCP_MAX_SPLIT_THREADS;

int   verbosity = FCP_LOG_NORMAL;


int main(int argc, char* argv[])
{
    HFCP *hfcp;
    char *keyData = 0;
    char keyBuf[4096];
    int curlen, newlen;
    int insertError;
    int fd;

		struct stat fileStat;

    parse_args(argc, argv);

    // try and fire up FCP library
    if (fcpStartup(nodeAddr, nodePort, htlVal, rawMode, splitThreads) != 0)
        return 1;

    // create an FCP handle
    hfcp = fcpCreateHandle();
    fcpSetHtl(hfcp, htlVal);

    // get hold of some metadata if needed
    if (metaFile[0])
    {
        char meta_stdin = !strcmp(metaFile, "stdin");
        FILE *fp_meta = meta_stdin
            ? stdin
            : fopen(metaFile, "r");
        char metaLine[256];

        if (fp_meta == 0)
        {
            printf("fcpput: failed to open metadata file '%s'\n", metaFile);
            exit(1);
        }

        if (meta_stdin)
        {
            // read metadata from stdin)
            metaData = strsav(0, "");

            if (!quiet)
                printf("Enter metadata line by line, enter a line '.' to finish:\n");
            while (fgets(metaLine, sizeof(metaLine), fp_meta) != 0)
                if (metaLine[0] == '.')
                    break;
                else
                {
                    metaData = strsav(metaData, metaLine);
                    metaData = strsav(metaData, "\n");
                }
        }
        else
        {
            while (fgets(metaLine, sizeof(metaLine), fp_meta) != 0)
            {
                metaData = strsav(metaData, metaLine);
                //metaData = strsav(metaData, "\n");
            }
        }
    }

    // read key data from stdin if required
    if (keyFile == 0)
    {
        keyData = 0;
        fd = 0;

        // read key from stdin
        if (!quiet)
            printf("Enter key data line by line, prees Control-D to finish:\n");

        curlen = 0;

        while ((newlen = read(fd, keyBuf, 4096)) > 0)
        {
            keyData = bufsav(keyData, curlen, keyBuf, newlen);
            curlen += newlen;
        }

        // ok - got metadata and file in mem, insert it
        insertError = fcpPutKeyFromMem(hfcp, keyUri, keyData, metaData, curlen);
        free(keyData);
    }
    else
    {
		int fileSize;
		HFCP *hfcpRedir;
		char chk[L_URI];
		char metaRedir[1024];

		if (stat(keyFile, &fileStat) < 0)
		{
			_fcpLog(FCP_LOG_CRITICAL, "fcpput: cannot stat '%s'", keyFile);
			return -1;
		}

		fileSize = fileStat.st_size;

		if (fileSize < L_KSK || strstr(keyUri, "CHK@"))
		{
			int i = 1;

			do
				_fcpLog(FCP_LOG_VERBOSE,
					"Key is small or chk, inserting directly, attempt %d/%d",
					i, attempts);
			while ((insertError = fcpPutKeyFromFile(hfcp, keyUri, keyFile, metaData)) != 0 && i++ <= attempts);
		}
		else
		{
			int i = 0;

			_fcpLog(FCP_LOG_VERBOSE, "Key too big, inserting as CHK");

			// gotta insert as CHK, then insert a redirect to it
			while ((insertError = fcpPutKeyFromFile(hfcp, "CHK@", keyFile, metaData)) != 0 && i++ < attempts)
			{
				printf("Insert attempt %d/%d failed\n", i, attempts);
				return -1;
			}

			// uplift the CHK URI, generate metadata for a redirect
			strcpy(chk, hfcp->created_uri);
			_fcpLog(FCP_LOG_VERBOSE, "Inserted as %s", chk);

			sprintf(metaRedir,
					"Version\nRevision=1\nEndPart\nDocument\nRedirect.Target=%s\nEnd\n",
					chk);

			// insert the redirect
			i = 0;
			while ((insertError = fcpPutKeyFromMem(hfcp, keyUri, 0, metaRedir, 0)) != 0 && i++ < attempts)
			{
				printf("Redirect insert attempt %d/%d failed\n", i, attempts);
				return -1;
			}
			_fcpLog(FCP_LOG_VERBOSE, "Redirect inserted successfully");
		}

    }

    // clean up
    if (metaData != 0)
        free(metaData);
    if (fd != 0)
        close(fd);

    // if insert worked, extract the actual URI
    if (insertError == 0)
        puts(hfcp->created_uri);
	else
		printf("Failed to insert, sorry\n");

    // all done
    fcpDestroyHandle(hfcp);
    return insertError;
}

int fcpLogCallback(int level, char *buf)
{
    if (level <= verbosity)
        puts(buf);
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
    {"raw", 0, 0, 'r'},
    {"metadata", 1, 0, 'm'},
		{"size", 1, 0, 's'},
		{"quiet", 0, 0, 'q'},
		{"attempts", 1, 0, 'a'},
		{"threads", 1, 0, 't'},
    {"verbosity", 1, 0, 'v'},
    {"version", 0, 0, 'V'},
    {"help", 0, 0, 'h'},
    {0, 0, 0, 0}
  };
  static char short_options[] = "l:n:p:e:m:s:qa:t:rv:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != EOF) {
    switch (c) {

    case 'n':
      strncpy( nodeAddr, optarg, L_HOST );
      break;

    case 'p':
      i = atoi( optarg );
      if (i > 0) nodePort = i;
      break;

    case 'l':
      i = atoi( optarg );
      if (i > 0) htlVal = i;
      break;

    case 'r':
      rawMode = 1;
      break;

    case 'm':
      strncpy( metaFile, optarg, L_FILENAME );
      break;

		case 's':
			i = atoi( optarg );
			if (i > 0) fcpSplitChunkSize = i;
			break;

		case 'q':
			quiet = 1;
			break;

		case 'a':
			i = atoi( optarg );
			if (i > 0) attempts = i;
			break;

		case 't':
			i = atoi( optarg );
			if (i > 0) splitThreads = i;
			break;
 
    case 'v':
      i = atoi( optarg );
      if ((i >= 0) && (i <= 4)) verbosity = i;
      break;

    case 'V':
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);

    case 'h':
      usage(0);
      break;
		}
	}

  if (optind < argc) strncpy(keyUri, argv[optind++], L_URI);
  else usage("You must specify a key");

  /* If there's another parameter, it's the FILE to store the results in.
     Default value is "" if not passed */
  if (optind < argc)
    strncpy(keyFile, argv[optind++], L_FILENAME);
}

static void usage(char *s)
{
	if (s) printf("Error: %s\n", s);

	printf("FCPtools; Freenet Client Protocol Tools\n");
	printf("Copyright (c) 2001 by David McNab\n\n");

	printf("Usage: fcpput [OPTIONS] key [file]\n\n");

	printf("Options:\n\n");
	printf("  -n, --address host     Freenet node address (default \"%s\")\n", EZFCP_DEFAULT_HOST);
	printf("  -p, --port num         Freenet node port (default %d)\n", EZFCP_DEFAULT_PORT);
	printf("  -l, --htl num          Hops to live (default %d)\n", EZFCP_DEFAULT_HTL);
	printf("  -e, --regress num      Number of days to regress (default %d)\n", EZFCP_DEFAULT_REGRESS);
	printf("  -r, --raw              Raw mode - don't follow redirects\n\n");
	
	printf("  -m, --metadata file    Read key's metadata from file (default \"stdin\")\n");
	printf("  -v, --verbosity num    Verbosity of log messages (default 2)\n");
	printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n\n");

	printf("  -q, --quiet            Quiet mode - no prompts for metadata or key data\n");
  printf("  -a, --attempts num     Attempts to insert each file (default %d)\n", FCPPUT_ATTEMPTS);
  printf("  -s, --size num         Size of splitfile chunks (default %d)\n", SPLIT_BLOCK_SIZE);
  printf("  -t, --threads num      Number of splitfile threads (default %d)\n\n", FCP_MAX_SPLIT_THREADS);

	printf("  -V, --version          Output version information and exit\n");
	printf("  -h, --help             Display this help and exit\n\n");

	printf("  key                    Freenet key (...)\n");
	printf("  file                   Read key's data from file (default \"stdin\")\n\n");
	
	printf("NOTE - only the inserted key URI will be written to stdout\n"
				 "Therefore, you can use this utility in shell backtick commands\n\n");
 
	exit(0);
}

static char *strsav(char *old, char *text_to_append)
{
    int old_len, new_len;
    char *p;

    if(( text_to_append == 0) || (*text_to_append == '\0')) {
        return(old);
    }

    if(old) {
        old_len = strlen(old);
    } else {
        old_len = 0;
    }

    new_len = old_len + strlen(text_to_append) + 1;

    if(old) {
        if((p = (char *)realloc(old, new_len)) == 0) {
//          fprintf(logfp, "%s: realloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("realloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    } else {
        if((p = (char *)safeMalloc(new_len)) == 0) {
//          fprintf(logfp, "%s: safeMalloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("safeMalloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    }

    strcpy(p + old_len, text_to_append);
    return(p);
}


static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len)
{
    int new_len;
    char *p;

    if(buf_to_append == 0)
        return(old);

    if(old == 0)
        old_len = 0;

    new_len = old_len + add_len;

    if(old) {
        if((p = (char *)realloc(old, new_len)) == 0) {
//          fprintf(logfp, "%s: realloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("realloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    } else {
        if((p = (char *)safeMalloc(new_len)) == 0) {
//          fprintf(logfp, "%s: safeMalloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("safeMalloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    }

    memcpy(p + old_len, buf_to_append, add_len);
    return(p);
}


/*
	Extension of atoi()
	
	This func recognises suffices on numbers
	eg '64k' will get parsed as 65536
	recognises the suffices 'k', 'K', 'm', 'M', 'g', 'G'

	Thanks to mjr for this lovely snippet
*/

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
