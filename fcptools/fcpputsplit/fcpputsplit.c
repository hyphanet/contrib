// fcpputsplit.c - inserts a splitfile
// CopyLeft () 2001 by David McNab

#include "stdio.h"
#include "ezFCPlib.h"

#ifdef WINDOWS
#include <process.h>
#else
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#endif

#define MAXRETRIES 10
#define KEYSIZE  1024

#ifdef WINDOWS
#define mysleep(msecs)  Sleep(msecs)
#else
#define mysleep(msecs)  usleep((msecs) * 1000)
#endif

char *GetMimeType(char *pathname);

static void parse_args(int argc, char *argv[]);
static void *usage(char *msg);
//static char *strsav(char *old, char *text_to_append); what's this?


static int putsplitfile( HFCP *hfcp, int workingThreads,  int file);
static void LaunchThread(void (*func)(void *), void *parg);
static void splitblockThread(void *arg);


char        *keyUri = NULL;
int         *blockSizes;
char        **blockCHK;
char        **buffers;
int         blockCount;
int         fileSize;
char        *keyFile = NULL;
int         htlVal = 3;
char        *nodeAddr = "127.0.0.1";
int         nodePort = 8481;
char        *metaFile = NULL;
char        *metaData = NULL;
int         rawMode = 0;
int         silentMode = 0;
int         verbosity = FCP_LOG_NORMAL;
int         partsize=SPLIT_BLOCK_SIZE;
int         maxthreads=8;

int main(int argc, char* argv[])
{
    HFCP *hfcp;
    int insertError;
    int fd;
    int i;
    char s[4096];
    struct stat st;

    // go thru command line args
    parse_args(argc, argv);

    // can we open the file?
#ifdef WINDOWS
    if ((fd = _open(keyFile, _O_BINARY)) < 0)
#else
    if ((fd = open(keyFile, 0)) < 0)
#endif
	{
	    // failure - cannot open
	    printf("cannot open file!\n");
	    return -1;
	}

    // how big's this file?
    //filesize = _filelength(fd);
    stat(keyFile, &st);
    fileSize = st.st_size;

    // calculates the number of blocks
    blockCount= fileSize/partsize;
    if( fileSize % partsize) { blockCount++; }

    printf("Filesize= %d\n", fileSize);
    printf("Splitting into %d parts\n", blockCount);

    // try and fire up FCP library
    if (fcpStartup(nodeAddr, nodePort, htlVal, rawMode, maxthreads) != 0)
        return 1;

    // create an FCP handle
    hfcp = fcpCreateHandle();
    fcpSetHtl(hfcp, htlVal);

    // now the big loop !
    insertError = putsplitfile( hfcp, maxthreads, fd);

    // clean up
    if (fd != 0)
        close(fd);


    if( !insertError) { // no error? insert the metadata
        metaData= malloc( 100+KEYSIZE*blockCount);
        strcpy( metaData, "Version\nRevision=1\nEndPart\nDocument\n");
        sprintf( s, "SplitFile.Size=%x\n", fileSize);
        strcat( metaData, s);
        sprintf( s, "SplitFile.BlockCount=%x\n", blockCount);
        strcat( metaData, s);

        for( i=0; i<blockCount; i++)
        {
            sprintf( s, "SplitFile.Block.%x=%s\n", i+1, blockCHK[i]);
            strcat( metaData, s);
        }
        sprintf( s, "Info.Format=%s\n", GetMimeType(keyFile));
        strcat( metaData, s);
        strcat( metaData, "End\n");


        i=0;
        printf("inserting metadata...\n");
        printf("metadata length : %d\n", strlen( metaData));

        if( strlen( metaData) <= 32768) {

            while( (insertError = fcpPutKeyFromMem(hfcp, keyUri, "", metaData, 0)) != 0  && i<MAXRETRIES)
            {
                i++;
                printf("Failed. Retrying.\n");
            }
            if( i<MAXRETRIES+1) { printf("Successfully inserted, at :\n%s\n", hfcp->created_uri);
            } else {
                printf("Error : could not insert metadata\n");
            }
        } else {  // inserting CHK step
            // too big for an ssk, insert map as a chk and redirect to that

            fcpDestroyHandle(hfcp);
            hfcp = fcpCreateHandle();
            fcpSetHtl(hfcp, htlVal);
            i=0;
            while ((insertError=fcpPutKeyFromMem(hfcp, "CHK@", NULL, metaData, 0)) != 0 && i<MAXRETRIES)
            {   i++;
                printf("Failed. Retrying.\n");
            }

            if( insertError) {
                printf("Failed to insert large mapfile as CHK");
                return -1;
            }

            // inserted ok as CHK - now build a redirect to that CHK
            free(metaData);
            metaData= malloc( 8096);
            sprintf( metaData, "Version\nRevision=1\nEndPart\nDocument\nRedirect.Target=%s\nEnd\n",hfcp->created_uri);

            fcpDestroyHandle(hfcp);

            hfcp = fcpCreateHandle();
            fcpSetHtl(hfcp, htlVal);

            // now insert at DBR target a redirect to mapfile CHK
            i=0;
            while ((insertError=fcpPutKeyFromMem(hfcp, keyUri, NULL, metaData, 0)) != 0 && i<MAXRETRIES)
            {
                i++;
                printf("Failed. Retrying.\n");
            }

            if( insertError) {
                _fcpLog(FCP_LOG_CRITICAL, "Failed to insert large mapfile as CHK");
                return -1;
            }
            printf("Successfully inserted, at :\n%s\n", hfcp->created_uri);

        }
        free( metaData);

    } else {
        printf("Error during insert... not inserting metadata\n");
    }
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
        else if (!strcmp(argv[i], "-s"))
            partsize = (++i < argc)
                        ? atoi(argv[i])*1024
                        : (int)usage("missing part size");
        else if (!strcmp(argv[i], "-t"))
            maxthreads = (++i < argc)
                        ? atoi(argv[i])
                        : (int)usage("missing threads number");
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
    printf("fcpputsplit: %s\n", s);
    printf("usage: fcpputsplit [-h] [-htl htlval] [-n nodeAddr] [-s partsize] [-p nodePort] [-t nb] key [file]\n");
    printf("-h: display this help\n");
    printf("-htl htlVal: use HopsToLive value of htlVal, default 3\n");
    printf("-n nodeAddr: address of your freenet 0.4 node, default 'localhost'\n");
    printf("-p nodePort: FCP port for your freenet 0.4 node, default 8481\n");
    printf("-v level:    verbosity of logging messages:\n");
    printf("             0=silent, 1=critical, 2=normal, 3=verbose, 4=debug\n");
    printf("             default is 2\n");
    printf("-t nb:       number of blocks to insert at the same time, default is 8\n");
    printf("-s partsize: size of each chunk, in KB\n");
    printf("key          a Freenet key URI [freenet:]XXX@blah[/blah][//[path]]\n");
    printf("file         a file to take key data from - uses stdin if no filename\n");
    exit(-1);
}




int putsplitfile( HFCP *hfcp, int workingThreads, int file)
{
    int size;
    int i,q;
    char *threadStatus;
    int requestingthreads=0;
    int lastsentblock=-1;
    int lastrequestedblock=0;
    int recvBytes=0;
    fcpPutJob  *pfcpPutJob;


    threadStatus= malloc( sizeof(int)*(blockCount+2));
    for (i = 0; i <= blockCount+1; i++)
        threadStatus[i] = 0; // 0 idle   1 working     2 done -1 error

    buffers=malloc( sizeof(char*)*(blockCount+2));
    for (i = 0; i <= blockCount+1; i++)
        buffers[i] = NULL;

    blockSizes=malloc( sizeof(int)*(blockCount+2));
    for (i = 0; i <= blockCount+1; i++)
        blockSizes[i] = 0;

    blockCHK=malloc( sizeof(int)*(blockCount+2));
    for (i = 0; i <= blockCount+1; i++)
        blockCHK[i] = NULL;



    while( lastsentblock<blockCount-1)
    {
        if( requestingthreads<workingThreads &&
            lastrequestedblock < blockCount)
        {
            printf("Forking to put block %x\n", lastrequestedblock+1);

            if( lastrequestedblock < blockCount-1) {
                blockSizes[lastrequestedblock]= partsize;
            } else {
                blockSizes[lastrequestedblock]= fileSize-(partsize*(blockCount-1));
            }
            printf("Size of block %d : %d\n", lastrequestedblock+1, blockSizes[lastrequestedblock]);

            // going to the start of this chunk
            lseek( file, partsize*(lastrequestedblock), 0);

            // reading it to memory
            buffers[lastrequestedblock]= malloc( blockSizes[lastrequestedblock]);
            size= read( file, buffers[lastrequestedblock], blockSizes[lastrequestedblock]);

            // forking
            threadStatus[lastrequestedblock]=1;
            pfcpPutJob = malloc(sizeof(fcpPutJob));
            pfcpPutJob->buffer = buffers[lastrequestedblock];
            pfcpPutJob->threadSlot = &(threadStatus[lastrequestedblock]);
            pfcpPutJob->blocksize= blockSizes[lastrequestedblock];
            pfcpPutJob->key= &(blockCHK[lastrequestedblock]);
            LaunchThread(splitblockThread, (void *)pfcpPutJob);

            lastrequestedblock++;

        }


        // counting number of threads and looking if one of them failed
        requestingthreads=0;
        for( i=0; i<blockCount; i++)
        {
            if( threadStatus[i]==-1)
            {
                printf("block %d failed... exiting...\n", i);
                for(i=0; i<blockCount+1; i++)  // killing other threads
                {
                    threadStatus[i]=-2;
                }
                return -1;
            }
            if( threadStatus[i]==1)
            {
                requestingthreads++;
            }
        }



        mysleep(50);


        /// freeing finished blocks
        i=lastsentblock+1;
        while( threadStatus[i]==2)
        {
            lastsentblock++;
            printf( "Block %d done : %d bytes !!!!!!\n", i+1, blockSizes[i]);
            recvBytes+=blockSizes[i];

            free( buffers[i]);
            i++;

            if( threadStatus[i] != 2)
            {
                printf("***** STATUS *****    . waiting    - requesting     * done\n");
                for( q=0; q<blockCount; q++)
                {
                    if( threadStatus[q]==2) {
                        printf("*");
                    } else if (threadStatus[q]==1) {
                        printf("-");
                    } else {
                        printf(".");
                    }
                }
                printf("\n\n");
            }


        }
    }

    return 0;
}



void splitblockThread( void *job)
{
    int error=1;
    char *pMetadata=NULL;
    HFCP *hfcpLocal;
    fcpPutJob *params= (fcpPutJob *)job;
    int retries=0;


    while( retries< MAXRETRIES && error != 0)
    {
        hfcpLocal = fcpCreateHandle();
        fcpSetHtl(hfcpLocal, htlVal);

        pMetadata=NULL;
        error=fcpPutKeyFromMem(hfcpLocal, "CHK@", params->buffer, NULL, params->blocksize);
        printf("key: %s\n", hfcpLocal->created_uri);

        if( *(params->threadSlot) == -2) { // one block failed
            if( !error) {free( params->buffer);}
            fcpDestroyHandle(hfcpLocal);
            return;
        }


        if( error == 0) {
            *(params->threadSlot)=2;
            *(params->key)= malloc( strlen( hfcpLocal->created_uri)+1);
            strcpy( *(params->key), hfcpLocal->created_uri);
            fcpDestroyHandle(hfcpLocal);
            return;
        }

        retries ++;
    }
    *(params->threadSlot)=-1;

}



static void LaunchThread(void (*func)(void *), void *parg)
{
#ifdef SINGLE_THREAD_DEBUG
    (*func)(parg);
    return;
#else
#ifdef WINDOWS
    _beginthread(func, 0, parg);
#else
    pthread_t pth;
    pthread_create(&pth, NULL, (void *(*)(void *))func, parg);
#endif
#endif
}               // 'LaunchThread()'


