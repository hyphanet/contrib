/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.

  Note that most of the modules comprising fcpproxy have been taken from
  the Internet Junkbusters proxy, and adapted to route freenet key requests
  out to Freenet.

  The original Internet Junkbusters proxy - source and binary - is available
  from the Junkbusters website at http://www.junkbusters.com
*/


#include "ezFCPlib.h"

/*
  IMPORTED DECLARATIONS
*/
extern void fcpreq_setgateway(char *dir);
extern void fcpreq_webblock(int enabled);
extern void fcpreq_setdir(char *dir);
extern void fcpreq_setthreadnumber( int workThreads, int maxThreads);
extern void proxy(int port, int extproxyenabled, char *extproxyaddr, int extproxyport);


static void parse_args(int argc, char *argv[]);

int nowSplitWorkingThreads=0; // number of splitfiles chunks being requested now
int nowSplitWaitingThreads=0; // total number of splitfiles chunks being requested and waiting now


char *nodeAddr = "localhost";
int nodePort = 8481;
int browsePort = -1;
int verbosity = FCP_LOG_NORMAL;

char *webBlockPass = NULL;         // password for 'http://free/[no]block?password'

#ifdef WINDOWS
char gatewayPath[256];
char *gatewayFile = gatewayPath;
char progdir[256];
#else
char *gatewayFile = "gateway.html";
#endif

/*
  PRIVATE DECLARATIONS
*/

static int usage();

int htlVal = 10;
int singleThread = 0;

int main(int argc, char *argv[])
{
    int numAttempts = 0;
    int maxAttempts = 100;
    int waitAttempts = 5;

#ifdef WINDOWS
    char *exename;
    strcpy(gatewayPath, _pgmptr);
    exename = strrchr(gatewayPath, '\\'); // point to slash between path and filename
    *exename++ = '\0'; // split the string and point to filename part
    strcpy(progdir, gatewayPath);
    strcat(gatewayPath, "\\gateway.html");

#endif
    parse_args(argc, argv);

    // must start up the FCP interface library
    //printf("Danger - binding to freenet\n");

    printf("fcpproxy: attempting secret handshake with freenet node at %s:%d ...\n", nodeAddr, nodePort);

    while (fcpStartup(nodeAddr, nodePort, htlVal, 0, 0) != 0)
    {
	if (++numAttempts >= maxAttempts)
        {
            printf("fcpproxy: can't connect to freenet node after %d retries\n",
                   numAttempts);
            exit(1);
        }
        else
        {
            printf("fcpproxy: failed to handshake node, attempt %d or %d\n",
                   numAttempts, maxAttempts);
#ifdef WINDOWS
            Sleep(1000 * waitAttempts);
#else
            usleep(1000000 * waitAttempts);
#endif
        }
    }
    printf("fcpproxy: secret handshake accepted\n");

    fcpreq_webblock(1);
    fcpreq_setgateway(gatewayFile);
    printf("Launching fcpproxy (%s)...\n", VERSION);
    if (singleThread)
        printf("Single thread mode...\n");

    proxy(browsePort, 0, NULL, 0);
    return 0;
}

static void parse_args(int argc, char *argv[])
{
    int i;
    int maxThreads=32, workThreads=8;


    for (i = 1; i < argc; i++)
    {
        if (!strcmp(argv[i], "-s"))
            singleThread = 1;
        else if (!strcmp(argv[i], "-w"))
        {
            if (++i < argc)
                webBlockPass = argv[i];
            else
                usage();
        }
        else if (!strcmp(argv[i], "-n"))
        {
            if (++i < argc)
                nodeAddr = argv[i];
            else
                usage();
        }
        else if (!strcmp(argv[i], "-p"))
        {
            if (++i < argc)
                nodePort = atoi(argv[i]);
            else
                usage();
        }
        else if (!strcmp(argv[i], "-v"))
            verbosity = (++i < argc)
                        ? atoi(argv[i])
                        : usage("missing verbosity level");
        else if (!strcmp(argv[i], "-o"))
            workThreads = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage();
        else if (!strcmp(argv[i], "-t"))
            maxThreads = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage();
        else if (!strcmp(argv[i], "-b"))
        {
            if (++i < argc)
                browsePort = atoi(argv[i]);
            else
                usage();
        }
        else if (!strcmp(argv[i], "-htl"))
            htlVal = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing htl argument");
        else if (!strcmp(argv[i], "-g"))
        {
            if (++i < argc)
                gatewayFile = argv[i];
            else
                usage();
        }
        else
            usage();
    }
    fcpreq_setthreadnumber( workThreads, maxThreads);

}


static int usage()
{
    printf("usage: fcpproxy [-h] [-n nodeAddr] [-p nodePort] [-b browsePort] [-g gatewayfile]\n");
    printf("-h: display this help\n");
    printf("-n nodeAddr: address of your freenet 0.4 node, default 'localhost'\n");
    printf("-p nodePort: FCP port for your freenet 0.4 node, default 8481\n");
    printf("-b browsePort: the port fcpproxy listens on for browser http requests, default 8888\n");
    printf("-htl htlVal:    the HopsToLive value to use for Freenet requests, default 10\n");
    printf("-g gatewaypath: the pathname of a file to use as the html gateway page, default './gateway.html'\n");
    printf("-w password:    password for 'http://[no]block?password' URLs which\n");
    printf("                turn web blocking on or off\n");
    printf("-v level:       verbosity of logging messages:\n");
    printf("                0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("                default is 2\n");
    printf("-s:             Run fcpproxy in single-thread mode\n");
    printf("-o:             Number of working threads for splitfiles (default: 24)\n");
    printf("-t:             Number of total threads for splitfiles (default: 32)\n");
    exit(0);
}
