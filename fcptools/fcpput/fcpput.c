// fcpget.c - simple command line client that uses FCP
// CopyLeft () 2001 by David McNab

#include "stdio.h"
#include "ezFCPlib.h"


static void parse_args(int argc, char *argv[]);
static void *usage(char *msg);
static char *strsav(char *old, char *text_to_append);
static char *bufsav(char *old, int old_len, char *buf_to_append, int add_len);

char        *keyUri = NULL;
char        *keyFile = NULL;
int         htlVal = 3;
char        *nodeAddr = "127.0.0.1";
int         nodePort = 8481;
char        *metaFile = NULL;
char        *metaData = NULL;
int         rawMode = 0;
int         silentMode = 0;
int         verbosity = FCP_LOG_NORMAL;


int main(int argc, char* argv[])
{
    HFCP *hfcp;
    char *keyData = NULL;
    char keyBuf[4096];
    int curlen, newlen;
    int insertError;
    int fd;

    // go thru command line args
    parse_args(argc, argv);

    // try and fire up FCP library
    if (fcpStartup(nodeAddr, nodePort, htlVal, rawMode) != 0)
        return 1;

    // create an FCP handle
    hfcp = fcpCreateHandle();
    fcpSetHtl(hfcp, htlVal);

    // get hold of some metadata if needed
    if (metaFile != NULL)
    {
        char meta_stdin = !strcmp(metaFile, "stdin");
        FILE *fp_meta = meta_stdin
            ? stdin
            : fopen(metaFile, "r");
        char metaLine[256];

        if (fp_meta == NULL)
        {
            printf("fcpput: failed to open metadata file '%s'\n", metaFile);
            exit(1);
        }

        if (meta_stdin)
        {
            // read metadata from stdin)
            metaData = strsav(NULL, "");

            if (!silentMode)
                printf("Enter metadata line by line, enter a line '.' to finish:\n");
            while (fgets(metaLine, sizeof(metaLine), fp_meta) != NULL)
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
            while (fgets(metaLine, sizeof(metaLine), fp_meta) != NULL)
            {
                metaData = strsav(metaData, metaLine);
                //metaData = strsav(metaData, "\n");
            }
        }
    }

    // read key data from stdin if required
    if (keyFile == NULL)
    {
        keyData = NULL;
        fd = 0;

        // read key from stdin
        if (!silentMode)
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
        insertError = fcpPutKeyFromFile(hfcp, keyUri, keyFile, metaData);
    }

    // clean up
    if (metaData != NULL)
        free(metaData);
    if (fd != 0)
        close(fd);

    // if insert worked, extract the actual URI
    if (insertError == 0)
        puts(hfcp->created_uri);

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


static void parse_args(int argc, char *argv[])
{
    int i;

    if (argc == 1)
        usage("missing key URI");

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
        else if (!strcmp(argv[i], "-s"))
            silentMode = 1;
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
    printf("fcpput: %s\n", s);
    printf("usage: fcpput [-h] [-htl htlval] [-n nodeAddr] [-p nodePort] [-r] [-m file] key [file]\n");
    printf("-h: display this help\n");
    printf("-s: don't display prompts for metadata or key data\n");
    printf("-htl htlVal: use HopsToLive value of htlVal, default 3\n");
    printf("-n nodeAddr: address of your freenet 0.4 node, default 'localhost'\n");
    printf("-p nodePort: FCP port for your freenet 0.4 node, default 8481\n");
    printf("-m file:     get key's metadata from file, 'stdin' means stdin\n");
    printf("-r:          raw mode - don't create redirects\n");
    printf("-v level:    verbosity of logging messages:\n");
    printf("             0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("             default is 2\n");
    printf("key          a Freenet key URI [freenet:]XXX@blah[/blah][//[path]]\n");
    printf("file         a file to take key data from - uses stdin if no filename\n");
    printf("NOTE - only the inserted key URI will be written to stdout\n"
            "Therefore, you can use this utility in shell `` (backtick) commands\n");
    exit(-1);
}


static char *strsav(char *old, char *text_to_append)
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
//          fprintf(logfp, "%s: realloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("realloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    } else {
        if((p = (char *)malloc(new_len)) == NULL) {
//          fprintf(logfp, "%s: malloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("malloc(%d) bytes for proxy_args failed!\n", new_len);
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
//          fprintf(logfp, "%s: realloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("realloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    } else {
        if((p = (char *)malloc(new_len)) == NULL) {
//          fprintf(logfp, "%s: malloc(%d) bytes for proxy_args failed!\n", prog, new_len);
            printf("malloc(%d) bytes for proxy_args failed!\n", new_len);
            exit(1);
        }
    }

    memcpy(p + old_len, buf_to_append, add_len);
    return(p);
}

