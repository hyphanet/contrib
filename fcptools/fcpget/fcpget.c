// fcpget.c - simple command line client that uses FCP
// CopyLeft () 2001 by David McNab

#include "stdio.h"
#include "stdlib.h"

#ifdef WINDOWS
#include <crtdbg.h>
#else
#include "unistd.h"
#endif

#include "ezFCPlib.h"

//
// IMPORTED DECLARATIONS
//


// defined in ezFCPlib.h

//
// PRIVATE DECLARATIONS
//

static void parse_args(int argc, char *argv[]);
static void *usage(char *msg);

char        *keyUri = NULL;
char        *keyFile = NULL;
int         htlVal = 25;
char        *nodeAddr = "localhost";
int         nodePort = 8481;
char        *metaFile = NULL;
int         rawMode = 0;
int         verbosity = FCP_LOG_NORMAL;


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
    if (keyUri == NULL) {
        _fcpLog(FCP_LOG_CRITICAL, "You must specify a key to get!");
        return -1;
    }

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
        if (metaFile != NULL)
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
            if (keyFile != NULL)
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


static void parse_args(int argc, char *argv[])
{
    int i;

    if (argc == 1)
      usage("no key specified");

    for (i = 1; i < argc; i++)
    {
        if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "--help") || !strcmp(argv[i], "-help"))
            usage("help information");
        else if (!strcmp(argv[i], "-n"))
            nodeAddr = (++i < argc)
                        ? argv[i]
                        : (char *)usage("missing node address");
        else if (!strcmp(argv[i], "-htl"))
            htlVal = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing htl argument");
        else if (!strcmp(argv[i], "-p"))
            nodePort = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing port number");
        else if (!strcmp(argv[i], "-v"))
            verbosity = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing verbosity level");
        else if (!strcmp(argv[i], "-m"))
            metaFile = (++i < argc)
                        ? argv[i]
                        : (char *)usage("missing metadata filename\n");
        else if (!strcmp(argv[i], "-r"))
            rawMode = 1;
        else
        {
            // have we run out of args?
            if (i == argc)
                usage("missing key argument");

            // cool - get URI and possibly file as well
            keyUri = argv[i++];
            keyFile = (i < argc) ? argv[i] : NULL;
        }
    }
}


static void *usage(char *s)
{
    printf("fcpget: %s\n", s);
    printf("usage: fcpget [-h] [-htl htlval] [-n nodeAddr] [-p nodePort] [-r] [-m file] key [file]\n");
    printf("-h: display this help\n");
    printf("-htl htlVal: use HopsToLive value of htlVal, default 25\n");
    printf("-n nodeAddr: address of your freenet 0.4 node, default 'localhost'\n");
    printf("-p nodePort: FCP port for your freenet 0.4 node, default 8481\n");
    printf("-m file:     write key's metadata to file, 'stdout' means stdout\n");
    printf("-r:          raw mode - don't follow redirects\n");
    printf("-v level:    verbosity of logging messages:\n");
    printf("             0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("             default is 2\n");
    printf("key          a Freenet key URI [freenet:]XXX@blah[/blah][//[path]]\n");
    printf("file         a file to save key data to - stdout if no filename\n");
    exit(0);
}

int fcpLogCallback(int level, char *buf)
{
    if (level <= verbosity)
        puts(buf);
    return 0;
}

