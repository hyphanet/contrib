/*
  fcpget.c - simple command line client that uses FCP

  CopyLeft () 2001 by David McNab
*/


#include "ezFCPlib.h"

#define _GNU_SOURCE
#include "getopt.h"

/*
  IMPORTED DECLARATIONS
*/


/*
  PRIVATE DECLARATIONS
*/
static void parse_args(int argc, char *argv[]);
static void usage(char *msg);

/* Configurable command-line parameters
	 Strings/Arrays that have default values if not set explicitly from the
	 command line should be initialized to "", a zero-length string.
*/
char  keyUri[L_URI];
char  keyFile[L_FILENAME] = "";
char  metaFile[L_FILENAME] = "";

char  nodeAddr[L_HOST] = EZFCP_DEFAULT_HOST;
int   nodePort = EZFCP_DEFAULT_PORT;
int   htlVal = EZFCP_DEFAULT_HTL;

int   rawMode = EZFCP_DEFAULT_RAWMODE;
int   verbosity = FCP_LOG_NORMAL;


int main(int argc, char* argv[])
{
    HFCP hfcpBlk;
    HFCP *hfcp = &hfcpBlk;
    char buf[1024];
    int count;
    int fd;

    void *blk;
    int numtimes = 1;
    int i;

/**
    blk = safeMalloc(100);
    blk = realloc(blk, 120);
    free(blk);
**/

    // go thru command line args
    parse_args(argc, argv);

    // try and fire up FCP library
    _fcpLog(FCP_LOG_VERBOSE, "Attempting secret handshake with %s:%d", nodeAddr, nodePort);

    if (fcpStartup(nodeAddr, nodePort, htlVal, rawMode, 0) != 0)
    {
        _fcpLog(FCP_LOG_CRITICAL, "Failed to connect to node - aborting");
        return 1;
    }

    _fcpLog(FCP_LOG_VERBOSE, "Successfully connected to node");

// repeat many times - hunting mem leaks
    for (i = 0; i < numtimes; i++)
    {
        // create an FCP handle
        fcpInitHandle(hfcp);
        fcpSetHtl(hfcp, htlVal);
    
        // try to get the key open
        _fcpLog(FCP_LOG_VERBOSE, "Trying to open '%s'", keyUri);
        if (fcpOpenKey(hfcp, keyUri, (_FCP_O_READ | (hfcp->raw ? _FCP_O_RAW : 0))) != 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "Failed to open '%s'", keyUri);
            return -1;
        }
    
        // snarf key's metadata
        if (metaFile[0])
        {
            printf("---METADATA DUMP NOT IMPLEMENTED YET---\n");
            if (!strcmp(metaFile, "stdout"))
            {
                // dump metadata to stdout
                //printf("---START-OF_METADATA---\n%s", hfcp->meta->raw);
                //puts("---END-OF-METADATA-----");
            }
            else
            {
                // nuke metadata file if it exists
                //unlink(metaFile);
    
#ifdef WINDOWS
                // open a file to write the key to
                //if ((fd = _open(metaFile, _O_CREAT | _O_RDWR | _O_BINARY, _S_IREAD | _S_IWRITE)) < 0)
#else
                //if ((fd = open(metaFile, O_CREAT | O_WRONLY, S_IREAD | S_IWRITE)) < 0)
#endif
                //{
                //    printf("Cannot create file '%s'\n", metaFile);
                //    return -1;
                //}
    
                //write(fd, hfcp->meta->raw, hfcp->meta->len);
                //close(fd);
            }
    
        }
    
        // output key data, if any
        if (hfcp->keysize > 0)
        {
            // nuke file if it exists
            if (keyFile[0])
            {
                unlink(keyFile);
#ifdef WINDOWS
                // open a file to write the key to
                if ((fd = open(keyFile, _O_CREAT | _O_RDWR | _O_BINARY, _S_IREAD | _S_IWRITE)) < 0)
                {
                    printf("Cannot create file '%s'\n", keyFile);
                    return -1;
                }
#else
                // open a file to write the key to
                if ((fd = open(keyFile, O_CREAT| O_WRONLY, S_IREAD | S_IWRITE)) < 0)
                {
                    printf("Cannot create file '%s'\n", keyFile);
                    return -1;
                }
#endif
            }
            else
                fd = 1;
    
            // suck all of key's data into this file
            while ((count = fcpReadKey(hfcp, buf, 1024)) > 0)
                write(fd, buf, count);
    
            if (keyFile != NULL)
                close(fd);
        }
    
        // all done
        fcpCloseKey(hfcp);
        fcpDestroyHandle(hfcp);

    }       // 'for (numtimes)'

    return 0;

}

/* IMPORTANT
   This function should bail if the parameters are bad in any way.  main() can
   then continue cleanly. */

static void parse_args(int argc, char *argv[])
{
  static struct option long_options[] = {
    {"htl", 1, NULL, 'l'},
    {"address", 1, NULL, 'n'},
    {"port", 1, NULL, 'p'},
    {"metadata", 1, NULL, 'm'},
    {"raw", 0, NULL, 'r'},
    {"verbosity", 1, NULL, 'v'},
    {"version", 0, NULL, 'V'},
    {"help", 0, NULL, 'h'},
    {0, 0, 0, 0}
  };
  static char short_options[] = "l:n:p:m:rv:Vh";

  /* c is the option code; i is buffer storage for an int */
  int c, i;

  while ((c = getopt_long(argc, argv, short_options, long_options, 0)) != EOF) {

    //printf("%s\n", optarg);
    switch (c) {

    case 'l':
      i = atoi( optarg );
      htlVal = (i <= 0 ? 0 : i);
      break;
      
    case 'n':
      strncpy( nodeAddr, optarg, L_URI );
      break;
      
    case 'p':
      i = atoi( optarg );
      nodePort = ( i <= 0 ? EZFCP_DEFAULT_PORT : i );
      break;
      
    case 'm':
      strncpy( metaFile, optarg, L_FILENAME );
      break;
      
    case 'r':
      rawMode = 1;
      break;
      
    case 'v':
      i = atoi( optarg );
      verbosity = ( i <= 0 ? 0 : i );
      break;
      
    case 'V':
      printf( "FCPtools Version %s\n", VERSION );
      exit(0);
      
    case 'h':
      usage(NULL);
      break;
    }
  }

  //printf("\noptind: %d :: argc: %d\n\n", optind, argc);
  if (optind < argc) strncpy(keyUri, argv[optind++], L_URI);
  else usage("You must specify a key");

  /* If there's another parameter, it's the FILE to store the results in.
     Default value is "stdout" if not passed */

  if (optind < argc)
    strncpy(keyFile, argv[optind++], L_FILENAME);
}


static void usage(char *s)
{
    if (s) printf("Error: %s\n", s);
    printf("FCPtools; Freenet Client Protocol Tools\n");
    printf("Copyright (c) 2001 by David McNab\n\n");

    printf("Usage: fcpget [OPTIONS] key [file]\n\n");

    printf("Options:\n\n");
    printf("  -l. --htl htlVal       Hops to Live value, default %d\n", htlVal);
    printf("  -n, --address addr     Address of your Freenet node,\n");
    printf("                         default \"%s\"\n", nodeAddr);
    printf("  -p, --port port        Port of your freenet 0.4 node, default %d\n", nodePort);
    printf("  -m, --metadata file    Write key's metadata to file (default \"stdout\")\n");
    printf("  -r, --raw              Raw mode - don't follow redirects\n");
    printf("  -v, --verbosity level  Verbosity of logging messages:\n");
    printf("                         0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("                         (default=2)\n");
    printf("  -V, --version          Output version information and exit\n");
    printf("  -h, --help             Display this help and exit\n\n");

    printf("  key                    Freenet key (freenet:KSK@gpl.txt)\n");
    printf("  file                   Write key's data to file (default \"stdout\")\n\n");

    exit(0);
}

int fcpLogCallback(int level, char *buf)
{
    if (level <= verbosity)
      puts(buf);
    return 0;
}

