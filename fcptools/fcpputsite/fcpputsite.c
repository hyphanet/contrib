// fcpputsite.c - simple command line client that uses FCP
// for insertion of freesites
// CopyLeft () 2001 by David McNab

#include "stdio.h"
#include "ezFCPlib.h"


//
// IMPORTED DECLARATIONS
//

extern int insertFreesite(char *siteName, char *siteDir, char *pubKey, char *privKey,
                          char *defaultFile, int daysFuture, int maxThreads, int maxRetries);

extern int fcpSplitChunkSize;


//
// EXPORTED DECLARATIONS
//

char *strsav(char *old, char *text_to_append);
int fcpLogCallback(int level, char *buf);


//
// PRIVATE DECLARATIONS
//

static void parse_args(int argc, char *argv[]);
static int  usage(char *msg);
static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len);

static int  htlVal = 3;
static char *nodeAddr = "127.0.0.1";
static int  nodePort = 8481;
static int  verbosity = FCP_LOG_NORMAL;

static int  daysFuture = 0;             // number of days ahead to insert mapfile
static char privKey[80];                    // SSK private key
static char pubKey[80];                     // SSK public key
static char *siteName = NULL;               // name of site - SSK subspace identifier
static char *siteDir = NULL;                // directory of site's files
static char *defaultFile = "index.html";    // redirect target for unnamed cdoc
static int  generateKeys = 0;           // flag requiring keypair generation only
static int  maxThreads = 5;             // maximum number of concurrent insert threads
static int  maxAttempts = 3;                // maximum number of insert attempts

static int	maxSplitThreads = 0;


int main(int argc, char* argv[])
{
    int error;

    // go thru command line args
    parse_args(argc, argv);

    // try and fire up FCP library
    if (fcpStartup(nodeAddr, nodePort, htlVal, 0, maxSplitThreads) != 0)
    {
        _fcpLog(FCP_LOG_CRITICAL, "Unable to connect with Freenet node's FCP interface\n");
        return 1;
    }

    // Does the user just want a keypair?
    if (generateKeys)
    {
        HFCP *hfcp = fcpCreateHandle();

        if (fcpMakeSvkKeypair(hfcp, pubKey, privKey) != 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "ERROR - failed to generate keypair\n");
            exit(1);
        }

        printf("Here's your keypair:\n");
        printf("Public:  %s\n", pubKey);
        printf("Private: %s\n", privKey);
        exit(0);
    }

    // all ok - now go off and try to insert the site
    error = insertFreesite(siteName, siteDir, pubKey, privKey,
                            defaultFile, daysFuture, maxThreads, maxAttempts);
    return error;
}

int fcpLogCallback(int level, char *buf)
{
    if (level <= verbosity)
        puts(buf);
    return 0;
}


static void parse_args(int argc, char *argv[])
{
    int i;

    // is user generating keys instead of inserting?
    if (argc == 1)
        usage("missing parameters");

    for (i = 1; i < argc; i++)
    {
        if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "--help") || !strcmp(argv[i], "-help"))
            usage(NULL);
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
        else if (!strcmp(argv[i], "-ss"))
            fcpSplitChunkSize = (++i < argc)
                        ? parse_num(argv[i])
                        : (int)usage("missing splitfile chunk size");
        else if (!strcmp(argv[i], "-st"))
            maxSplitThreads = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing max splitfile threads");
        else if (!strcmp(argv[i], "-f"))
            daysFuture = (++i < argc)
                        ? atoi(argv[i])
                        : usage("missing future days argument");
        else if (!strcmp(argv[i], "-a"))
            maxAttempts = (++i < argc)
                        ? atoi(argv[i])
                        : usage("missing maxAttempts value");
        else if (!strcmp(argv[i], "-t"))
            maxThreads = (++i < argc)
                        ? atoi(argv[i])
                        : usage("missing thread count");
        else if (!strcmp(argv[i], "-def"))
            defaultFile = (++i < argc)
                        ? argv[i]
                        : (char *)usage("missing default file");
        else if (!strcmp(argv[i], "-g"))
            generateKeys = 1;
        else
        {
            // have we run out of args?
            if (i + 4 != argc)
                usage("Incorrect number of arguments");

            // cool - get main args
            siteName = argv[i++];
            siteDir = argv[i++];
            strcpy(pubKey, argv[i++]);
            strcpy(privKey, argv[i]);
        }
    }
}


static int usage(char *s)
{
    if (s != NULL)
        printf("%s\n\n", s);
    printf("fcpputsite - Insert a directory of files into Freenet as a freesite\n");
    printf("Written by David McNab - http://freeweb.sourceforge.net\n\n");
    printf("usage: fcpputsite [options] name dir pubKey privKey\n");
    printf("Options are:\n");
    printf("  -h: display this help\n");
    printf("  -htl val:    use HopsToLive value of val, default 3\n");
    printf("  -n addr:     address of your freenet 0.4 node, default 'localhost'\n");
    printf("  -p port:     FCP port for your freenet 0.4 node, default 8481\n");
    printf("  -v level:    verbosity of logging messages:\n");
    printf("               0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("               default is 2\n");
	printf("  -ss:         size of splitfile chunks, default %d\n", SPLIT_BLOCK_SIZE);
	printf("  -st:         max number of splitfile threads, default %d\n", FCP_MAX_SPLIT_THREADS);
    printf("  -g:          DON'T insert a site - just create an SVK keypair instead\n");
    printf("  -f numDays:  insert a map file numDays in the future, default 0 (today)\n");
    printf("  -def file:   name of site's 'default' file, default is index.html\n");
    printf("               the default file MUST exist in selected directory\n");
    printf("  -t threads:  the maximum number of insert threads (default 5)\n");
    printf("  -a attempts: maximum number of attempts at inserting each file (default 3)\n");
    printf("Required arguments are:\n");
    printf("  name:        name of site - more formally, the SSK subspace identifier\n");
    printf("  dir:         the directory containing the freesite\n");
    printf("  pubKey:      the SSK public key\n");
    printf("  privKey:     the SSK private key\n");
    exit(-1);
}


char *strsav(char *old, char *text_to_append)
{
    int old_len, new_len;
    char *p;

    if(( text_to_append == NULL) || (*text_to_append == '\0')) {
        return(old);
    }

    if(old) {
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
        if((p = (char *)malloc(new_len)) == NULL) {
            _fcpLog(FCP_LOG_CRITICAL, "malloc(%d) bytes for proxy_args failed!\n", new_len);
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

    if(buf_to_append == NULL)
        return(old);

    if(old == NULL)
        old_len = 0;

    new_len = old_len + add_len;

    if(old) {
        if((p = (char *)realloc(old, new_len)) == NULL) {
            _fcpLog(FCP_LOG_CRITICAL, "realloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    } else {
        if((p = (char *)malloc(new_len)) == NULL) {
            _fcpLog(FCP_LOG_CRITICAL, "malloc(%d) bytes for proxy_args failed!\n", new_len);
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

