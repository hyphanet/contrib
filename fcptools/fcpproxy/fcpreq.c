
//
//  This code is part of the FCPtools client software for Freenet
//
//  Designed and implemented by David McNab, david@rebirthing.co.nz
//  CopyLeft (c) 2001 by David McNab
//
//  The FreeWeb website is at http://freeweb.sourceforge.net
//  The website for Freenet is at http://freenet.sourceforge.net
//
//  This code is distributed under the GNU Public Licence (GPL) version 2.
//  See http://www.gnu.org/ for further details of the GPL.
//
//  Note that most of the modules comprising fcpproxy have been taken from
//  the Internet Junkbusters proxy, and adapted to route freenet key requests
//  out to Freenet.
//
//  The original Internet Junkbusters proxy - source and binary - is available
//  from the Junkbusters website at http://www.junkbusters.com
//
// fcpreq.c
//
// FreeWeb http proxy server
//
// Relays normal web requests out to the mainstream web (if not blocking)
// and redirects requests on .free domain to Freenet
//
// Translation: http://www.mysite.free/file.html -> MSK@KSK@freeweb/mysite//file.html
//

#define _GNU_SOURCE
#include <stdlib.h>

#ifndef WINDOWS
#include <pthread.h>
#include <unistd.h>
#endif


#include "ezFCPlib.h"

#ifdef WINDOWS
#define mysleep(msecs)  Sleep(msecs)
#else
#define mysleep(msecs)  usleep((msecs) * 1000)
#endif


//
// EXPORTED DECLARATIONS
//

int fcpreq(char *host, int port, char *docpath, char *range, int sock);

void fcpreq_webblock(int onoff);
void fcpreq_sethtl(int htl);
void fcpreq_setRegress(int regress);
void fcpreq_setgateway(char *dir);

int fcpLogCallback(int level, char *buf);


//
// IMPORTED DECLARATIONS
//


extern int  verbosity;
extern char *webBlockPass;         // password for 'http://free/[no]block?password'

//
// PRIVATE DECLARATIONS
//

static int fcpreq_internal_req(char *docpath, int sock);
static int fcpreq_freeweb_req(char *host, char *docpath, int sock);
static int fcpreq_reply_webblock(int sock);
static int fcpreq_fproxy_req(char *host, char *docpath, char *range, int sock);

static void notify_block(int enable, int sock, char *passwd);
static void illegal_block(int sock);

static int hexdig_to_int(char dig);

static void fcpreq_404(int sock, char *keyname);

static int fcpreq_webblock_mode = 1;
static int fcpreq_htl = 100;
static int fcpreq_regress = 3;

static int splitWorkThreads = 8;
static int splitMaxThreads = 32;

static int fcpreq_curr_port = 8888;

static char fcpreq_gateway[256] = "";

static void decode_uri(char *path);
static int hexdig(char dig);

static int numhits = 0;

//static int nowSplitWorkingThreads=0; // number of splitfiles chunks being requested now
//static int nowSplitWaitingThreads=0; // total number of splitfiles chunks being requested and waiting now
extern int nowSplitWorkingThreads; // number of splitfiles chunks being requested now
extern int nowSplitWaitingThreads; // total number of splitfiles chunks being requested and waiting now


// added support for splitfiles
static int  fcpreq_getsplitfile( HFCP *hfcp, int workingThreads, int maxThreads, int sock, int startRange, int endRange);
static void LaunchThread(void (*func)(void *), void *parg);
static void splitblockThread(void *arg);
void fcpreq_setthreadnumber( int workThreads, int maxThreads);
static void fcpreq_getrange( int *start, int *end, char *range); // parses file resuming info from headers

typedef struct
{
    char 	    **buffer;
    char        *threadSlot;
	int			*blocksize;
    char        *metadata;          // safeMalloc()'ed metadata to insert
    char        key[4096];           // key URI, if inserting metadata
}
FcpGetJob;



////////////////////////////////////////////////////////////////////////////////////////
//
// FUNCTION DEFINITIONS - EXPORTS
//
////////////////////////////////////////////////////////////////////////////////////////

//
// fcpreq()
//

int fcpreq(char *host, int port, char *docpath, char *range, int sock)
{
    int host_len;
    char newhost[128];

#ifdef WINDOWS
if (host && strlen(host) > 127)
{
    MessageBox(0, "panic: host too long", "fcpproxy", MB_SYSTEMMODAL);
    Sleep(5000);
}
#endif

	// deal with the %nn URI encoding
	decode_uri(docpath);

	strcpy(newhost, host ? host : "free");

    host_len = strlen(newhost);

    // strip off '.new.net' from end if present - an IE 5.5 quirk
    if (host_len > 8 && !strcmp(newhost + host_len - 8, ".new.net"))
    {
        newhost[host_len - 8] = '\0';
        host_len -= 8;
    }

    //printf("fcpreq_callback: begin\n");


    // test if we're requesting on .free domain
    if (host_len == 4 && !strcmp(newhost, "free"))
    {
        // request on 'http://free'
        //return fcpreq_internal_req(headers, sock);
        fcpreq_fproxy_req(newhost, docpath, range, sock);
        return 1;
    }
    else if (host_len > 4 && !strcmp(newhost + host_len - 5, ".free"))
    {
        // host ends in '.free' - here's a freeweb request
        fcpreq_freeweb_req(newhost, docpath, sock);
        return 1;
    }
    else if (!strcmp(newhost, "127.0.0.1")
            || !strcmp(newhost, "localhost")
            || !strcmp(newhost, "freenet")
            || !strcmp(newhost, "freeweb")
            || port == fcpreq_curr_port)
    {
        // fproxy emulation
        fcpreq_fproxy_req(newhost, docpath, range, sock);
        return 1;
    }
    else if (fcpreq_webblock_mode)
        // mainstream web request, but web blocking is on
        return fcpreq_reply_webblock(sock);
    else
        // mainstream web request - permit this
    {
        numhits++;
        return 0;
    }
}				// 'fcpreq()'



void fcpreq_webblock(int onoff)
{
    fcpreq_webblock_mode = onoff;
}

void fcpreq_setgateway(char *dir)
{
    strcpy(fcpreq_gateway, dir);
}


////////////////////////////////////////////////////////////////////////////////////////
//
// FUNCTION DEFINITIONS - PRIVATE
//
////////////////////////////////////////////////////////////////////////////////////////


//
// request sent to freeweb proxy itself
//

static int fcpreq_internal_req(char *docpath, int sock)
{
    char *http_reply =
        "HTTP/1.0 200 OK\n"
        "Connection: close\n"
        "Content-Type: text/html\n\n"
        "<html>"
        "<head>"
        "<title>Welcome to FreeWeb</title>\n"
        "</head>"
        "<body bgcolor=\"#000000\" text=#ffff80 link=\"#66FF66\">"
        "<center><h1>Welcome to FreeWeb</h1><br>\n"
        "Your FreeWeb Proxy is working ok\n"
        "</body></html>\n"
        ;
    int reply_len = strlen(http_reply);
    send(sock, http_reply, reply_len, 0);
    return 1;
}

int strcmp666(char *s1, char *s2)
{
    int result = strcmp(s1, s2);
    return result;
}


//
// fcpreq_freeweb_req
//
// Translate FreeWeb URL to a Freenet key, and retrieve the key from Freenet,
// and send it (or a 404) back to browser
//

static int fcpreq_freeweb_req(char *host, char *docpath, int sock)
{
    char *host1 = strdup(host);
    char *host2 = host1;
    char freenet_key[256];
    char path[4096];
    //char *metadata;

    //char buf[1024];
    //char *cont;
    //int len;

    //char correct_uri[256];
    char http_reply[1024];
    int reply_len;

    // strip '.free' from end of host
    host1[strlen(host1) - 5] = '\0';

    // strip 'www' if present
    if (!strncmp(host1, "www.", 4))
        host1 += 4;

    // Convert hostname to Freenet key
    sprintf(freenet_key, "freenet:KSK@freeweb/%s", host1);

    // Send back initial 200 response
    printf("200: %s\n", freenet_key);

    sprintf(http_reply,
                "HTTP/1.0 200 OK\nConnection: close\n"
                "Content-Type: text/html\n\n"
                "<html>"
                "<head>"
                "<title>FreeWeb User Update</title>\n"
                "</head>\n"
                "<body bgcolor=\"#ffe0ff\" text=#000000 link=\"#000040\""
                "vlink=\"#000040\" alink=\"#000040\">\n"
                "<font face=\"Arial, Helvetica, sans-serif\">"
                "<h1>FreeWeb User Update!</h1>\n"
                "<font size=3>\n"
                "You are trying to surf a FreeWeb site\n"
                "via the pseudo-DNS address <b>\"www.%s.free\"</b><br>\n"
                "Please note that due to security issues, this 'pseudo-dns'\n"
                "feature no longer exists within FreeWeb\n"
                "<br><br>\n"
                "The correct Freenet address for the site you're after is:\n"
                "<br><br>\n"
                "<b><font size=\"-1\">\n"
                "<a href=\"http://127.0.0.1:%d/freeweb/%s//\">http://127.0.0.1:%d/freeweb/%s//</a></b>\n"
                "</font><br><br>\n"
                "Please bookmark this address after you click on the above link\n"
                "<br><br>\n"
                "<p align=\"left\"><font size=\"-1\">Notes:</font></p>\n"
                "<ul>\n"
                "<li><div align=\"left\"><font size=\"-1\">\n"
                "This is <i>not</i> a bug</font></div></li>"
                "<li><div align=\"left\"><font size=\"-1\">\n"
                "You will always see this notice if you're trying to surf a site as\n"
                " <b>www.somesite.free</b></font></div></li>\n"
                "</ul>\n"
                "</body></html>\n",
                host1, fcpreq_curr_port, host1, fcpreq_curr_port, host1);

    reply_len = strlen(http_reply);

#ifdef WINDOWS
if (strlen(http_reply) > 1023)
{
    MessageBox(0, "panic: http_reply too long", "fcpproxy", MB_SYSTEMMODAL);
    Sleep(5000);
}
#endif


    send(sock, http_reply, reply_len, 0);

    // Clean up and get out
    free(host2);
    return 0;
}



//
// fcpreq_getsplitfile
//
// Get all blocks from a splitfile, and send them as they arrive as soon as possible.
//
// params: workingThreads -> number of active requests
//         maxThreads -> number of active requests + blocks waiting to be sent to the browser

int fcpreq_getsplitfile( HFCP *hfcp, int workingThreads, int maxThreads, int sock, int startRange, int endRange)
{
	int size;
	int i,j;
	int nb;
	char s2[4096], *s3;
	char *threadStatus;
	char **buffers;
	int *blocksizes;
	int startBlock=0, startOffset=0;
	int endBlock=-1, endSize;
	int typicalSize=-1;
	int requestingthreads=0;
	int totalthreads=0;
	int lastsentblock=0;
	int lastrequestedblock=0;
	int recvBytes=0;
    FcpGetJob  *pGetJob;

    _fcpLog(FCP_LOG_VERBOSE, "This is a splitfile");

	s3= cdocLookupKey( hfcp->fields, "SplitFile.Size");
	size= strtol(s3, NULL, 16);
    _fcpLog(FCP_LOG_VERBOSE, "Size: %d", size);

	s3= cdocLookupKey( hfcp->fields, "SplitFile.BlockCount");
	nb= strtol(s3, NULL, 16);
	endBlock= nb;
    _fcpLog(FCP_LOG_VERBOSE, "Blocks: %d", nb);



	// safeMallocing some stuff
	threadStatus= safeMalloc( sizeof(int)*(nb+2));
    for (i = 0; i <= nb+1; i++)
        threadStatus[i] = 0;
		// 0 idle
		// 1 working
   		// 2 request done but data not send to the browser
	    // 3 data sent, block done
	    // -1 error on block


	buffers=safeMalloc( sizeof(char*)*(nb+2));
    for (i = 0; i <= nb+1; i++)
        buffers[i] = NULL;

	blocksizes=safeMalloc( sizeof(int)*(nb+2));
    for (i = 0; i <= nb+1; i++)
        blocksizes[i] = 0;


    //request all blocks
	while( lastsentblock<endBlock)
	{
		// is there a free thread slot?
		if( nowSplitWorkingThreads<workingThreads &&
			nowSplitWaitingThreads<maxThreads && lastrequestedblock <endBlock)
		{
			nowSplitWorkingThreads++;
			nowSplitWaitingThreads++;

			// looking up key
			sprintf( s2, "SplitFile.Block.%x", lastrequestedblock+1);
			printf("forking to request block %x : %s\n", lastrequestedblock+1, s2);

			s3= cdocLookupKey( hfcp->fields, s2);
			if( strstr( s3, "freenet:") != NULL) {
				s3+= 8;
			}


			// forking

		    _fcpLog(FCP_LOG_VERBOSE, "Requesting block number %d : %s", lastrequestedblock+1, s3);

			threadStatus[lastrequestedblock+1]=1;
	        pGetJob = safeMalloc(sizeof(FcpGetJob));
	        pGetJob->buffer = &(buffers[lastrequestedblock+1]);
	        pGetJob->threadSlot = &(threadStatus[lastrequestedblock+1]);
	        pGetJob->metadata = NULL;
			pGetJob->blocksize= &(blocksizes[lastrequestedblock+1]);
	        strcpy( pGetJob->key, s3);
	        LaunchThread(splitblockThread, (void *)pGetJob);

			lastrequestedblock++;


   		    _fcpLog(FCP_LOG_VERBOSE, "working: %d | waiting: %d \n", nowSplitWorkingThreads, nowSplitWaitingThreads);
		}

		// checking all thread slots for an error and counting them
		for( i=/*lastsentblock*/1; i<=nb; i++)
		{
			// this block had an error... putting all status to -2 so the childs know they have to stop
			if( threadStatus[i]==-1)
			{
     		    _fcpLog(FCP_LOG_VERBOSE, "error on block %d. stopping request.", i);
				for(i=0; i<nb+1; i++)
				{
					threadStatus[i]=-2;
				}
				return recvBytes;
			}
			if( typicalSize==-1 && i<nb-1) { // finds the size of the chunks from the first downloaded one
				// if found, calculates the start and end block
				if( blocksizes[i] != 0) {
					typicalSize=blocksizes[i];

					startBlock= startRange/typicalSize+1;
					startOffset= startRange%typicalSize;
//					printf("startBlock= %d - startOffset= %d\n", startBlock, startOffset);

					if( endRange != -1) {
						endBlock= endRange/typicalSize+1;
						endSize= endRange- ((endBlock-1)*typicalSize)+1;
					} else {
						endBlock= nb;
						endSize= size- ((endBlock-1)*typicalSize);
					}
//					printf("endBlock= %d - endSize= %d\n", endBlock, endSize);

					if( lastrequestedblock<startBlock-1) {
						lastrequestedblock=startBlock-1;
					}
					for( j=1; j<startBlock; j++){
						if( threadStatus[j]!= 3) {
							threadStatus[j]=2;
						}
					}
				}
			}
		}



		mysleep(1000);


		i=lastsentblock+1;
		// check if some blocks are ready to be sent in the right order of course
		while( threadStatus[i]==2 && typicalSize != -1)
		{
			lastsentblock++;

			if( i>= startBlock && i<=endBlock) {
			    _fcpLog(FCP_LOG_VERBOSE, "Sending data from block %d : %d bytes", i, blocksizes[i]);
				recvBytes+=blocksizes[i];
				if( i==endBlock) {

		        	if( send(sock,
					     buffers[i]+((i==startBlock)?startOffset:0),
						 endSize-((i==startBlock)?startOffset:0),
						 0) == -1) {
							for(j=0; j<nb+1; j++) // kills all threads
							{
								threadStatus[j]=-2;
							}
							 return recvBytes;
					}
				} else {
		        	if( send(sock,
					     buffers[i]+((i==startBlock)?startOffset:0),
						 blocksizes[i]-((i==startBlock)?startOffset:0),
						 0) == -1) {
							for(j=0; j<nb+1; j++)  // kills all threads
							{
								threadStatus[j]=-2;
							}
							 return recvBytes;
					}
				}
			}

			if( buffers[i] != NULL) { free( buffers[i]); }
			threadStatus[i]=3;  // done
			i++;
		}
	}

	return recvBytes;
}


//
// splitblockThread
//
// Requests a block.
//

void splitblockThread( void *job)
{
	int current=0;
	char *pMetadata=NULL;
    HFCP *hfcpLocal;
	FcpGetJob *params = (FcpGetJob *)job;


	hfcpLocal = fcpCreateHandle();
	fcpSetHtl(hfcpLocal, fcpreq_htl);

	printf("key: %s\n", params->key);
	pMetadata=NULL;

	// requests key
	_fcpLog(FCP_LOG_VERBOSE, "thread: seeking key '%s'", params->key);
	current=fcpGetKeyToMem(hfcpLocal, params->key, params->buffer, &pMetadata);
	_fcpLog(FCP_LOG_VERBOSE, "thread: retcode %d fetching '%s'", params->key);


	if( current<0) { // error in request
		*(params->threadSlot)=-1;
	} else { // success. change status to done
		*(params->blocksize)=current;
                if (*(params->threadSlot) != 3) { *(params->threadSlot)=2; }	
	}

	if( pMetadata != NULL) {free( pMetadata);}

    fcpDestroyHandle(hfcpLocal);
	nowSplitWorkingThreads--;
	while (*(params->threadSlot) != 3)
	{
		mysleep(50);
		if( *(params->threadSlot) == -2) { // one block failed. exit
			if( current &&  *(params->buffer) != NULL) {
				 free( *(params->buffer));
			}
			nowSplitWaitingThreads--;
   		    _fcpLog(FCP_LOG_VERBOSE, "working: %d | waiting: %d \n", nowSplitWorkingThreads, nowSplitWaitingThreads);
			return;
		}
	} // waits the father to send back the data to the browser
	nowSplitWaitingThreads--;
    _fcpLog(FCP_LOG_VERBOSE, "working: %d | waiting: %d \n", nowSplitWorkingThreads, nowSplitWaitingThreads);
}



//
// fcpreq_fproxy_req()
//
// http request handler that emulates Freenet FProxy :)
//

static int fcpreq_fproxy_req(char *host, char *docpath, char *range, int sock)
{
    char freenet_key[512];
    HFCP hfcpBlk;
    HFCP *hfcp = &hfcpBlk;
    int fd;
    //char gateway_pathname[256];
    char *s;
    int bytesRcvd = 0;
    int blocking;
	int splitStart, splitEnd;

    // deliberate memory leak for testing
    //safeMalloc(8192);

    // delete '?key='
    if (*docpath && !strncmp(docpath, "?key=", 5))
        strcpy(docpath, docpath+5);

    // delete '&submit
    if ((s = strchr(docpath, '&')) != NULL)
        *s = '\0';

    //
    // send 404 on requests for 'robots.txt' so as to allow spiders
    //
    if (!strcmp(docpath, "robots.txt"))
    {
        fcpreq_404(sock, "robots.txt");
        return -1;
    }

    // if request is empty, send back the gateway page
    if (*docpath == '\0' || !strcmp(docpath, "freeweb/") || !strcmp(docpath, "/"))
    {
        char buf[4096];
        int len;
        char *http_200 =
                "HTTP/1.0 200 OK\n"
                "Connection: close\n"
                "Content-Type: text/html\n\n";

        if ((fd = open(fcpreq_gateway, 0)) < 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "Can't find default page at '%s'", fcpreq_gateway);
            fcpreq_404(sock, fcpreq_gateway);
            return -1;
        }

        // Send gateway page back to browser
        send(sock, http_200, strlen(http_200), 0);
        while ((len = read(fd, buf, 1024)) > 0)
            send(sock, buf, len, 0);
        close(fd);
        _fcpLog(FCP_LOG_NORMAL, "No key - sending default gateway page");
        return 0;
    }

    // look out for special URLs 'block' and 'noblock'
    blocking = !strncmp(docpath, "block?", 6);
    if (blocking || !strncmp(docpath, "noblock?", 8))
    {
        notify_block(blocking, sock, docpath + (blocking ? 6 : 8));
        return 0;
    }

    // Create a handle to Freenet FCP and set htl
    fcpInitHandle(hfcp);
    fcpSetHtl(hfcp, fcpreq_htl);

    // Convert hostname to Freenet key
    sprintf(freenet_key, "%s", docpath);


    //
    // WARNING - FILTHY HACK !!!
    //
    // A certain web spider program converts the double slash in MSK keys to a single slash
    // so we have to detect this and convert it back :(
    //

    if ((s = strstr(freenet_key, "MSK@SSK@")) != NULL)
    {
        if ((s = strchr(s, '/')) != NULL)
        {
            if ((s = strchr(s+2, '/')) != NULL && s[1] != '/')
            {
                // found second slash after 'MSK@SSK@' - convert to '//'
                char temp[256];

                if (s[1])
                {
                    // URL doesn't end with double-slash
                    s++;
                    strcpy(temp, s);
                    *s++ = '/';
                    strcpy(s, temp);
                }
                else
                {
                    // URL ends with double-slash
                    *++s = '/';
                    *++s = '\0';
                }
            }
        }
    }
    // END OF FILTHY HACK

    // Open Freenet key
    if (fcpOpenKey(hfcp, freenet_key, _FCP_MODE_O_READ) < 0)
    {
        fcpreq_404(sock, freenet_key);
        return -1;
    }
    else
    {
        // Found the key
        char buf[4096];
        int len;
        char *http_200 =
                "HTTP/1.0 200 OK\n"
                "Connection: close\n"
                ;


        printf("200: %s\n", freenet_key);

        // Send back initial 200 response
        send(sock, http_200, strlen(http_200), 0);

		// if its a splitfile, send the length of it to the browser
		if(hfcp->fields != NULL && hfcp->fields->type == META_TYPE_04_SPLIT)
		{
			sprintf(buf, "Content-Length: %ld\n", strtol(cdocLookupKey( hfcp->fields, "SplitFile.Size"), NULL, 16));
            send(sock, buf, strlen(buf), 0);
			// we accept ranges
			sprintf(buf, "Accept-Ranges: bytes\n");
            send(sock, buf, strlen(buf), 0);
			// parse range if present
			fcpreq_getrange( &splitStart, &splitEnd, range);
			// send range headers
			sprintf(buf, "Content-Range: %d-%d/%d\n",
				splitStart,
				(splitEnd!=-1)?splitEnd:(strtol(cdocLookupKey( hfcp->fields, "SplitFile.Size"), NULL, 16)-1),
				strtol(cdocLookupKey( hfcp->fields, "SplitFile.Size"), NULL, 16));
            send(sock, buf, strlen(buf), 0);

			if( cdocLookupKey( hfcp->fields, "Info.Filename") != NULL)
			{
				sprintf(buf, "Content-Disposition: attachment; filename=\"%s\"\n", cdocLookupKey( hfcp->fields, "Info.Filename"));
            	send(sock, buf, strlen(buf), 0);
			}
		}

        // Notify the content type - it IS there, ISN'T IT!!!!!!
        if (hfcp->mimeType[0] != '\0')
        {
            sprintf(buf, "Content-Type: %s\n\n", hfcp->mimeType);
            send(sock, buf, strlen(buf), 0);
        }
        else
        {
            // no friggin metadata :(
            //sprintf(buf, "Content-Type: text/html\n\n");
            sprintf(buf, "\n");
            send(sock, buf, strlen(buf), 0);
        }

		if(hfcp->fields != NULL && hfcp->fields->type == META_TYPE_04_SPLIT)
		{
			// its a splitfile !
			bytesRcvd= fcpreq_getsplitfile( hfcp, splitWorkThreads, splitMaxThreads, sock, splitStart, splitEnd);
		}
		else
		{
	        // Pass data from Freenet to browser
	        bytesRcvd = 0;
	        while ((len = fcpReadKey(hfcp, buf, 4096)) > 0)
	        {
	            bytesRcvd += len;
	            send(sock, buf, len, 0);
	        }
		}

        _fcpLog(FCP_LOG_VERBOSE, "Total %d bytes from %s", bytesRcvd, freenet_key);
        fcpCloseKey(hfcp);
    }

    // Clean up and get out
    fcpDestroyHandle(hfcp);
    return 0;

}       // 'fcpreq_fproxy_req()


//
// fcpreq_reply_webblock()
//
// This function gets called if browser attempts to retrieve anything from the mainstream web
// while web blocking is enabled
//
// Simply writes an error page back to browser
//

static int fcpreq_reply_webblock(sock)
{
    char *http_reply =
                "HTTP/1.0 403 Access Denied\n"
                "Connection: close\n"
                "Content-Type: text/html\n\n"
                "<html>"
                "<head>"
                "<title>FreeWeb Security Alert</title>\n"
                "</head>"
                "<body bgcolor=\"#000000\" text=#ffa0ff link=\"#66FF66\""
                "vlink=\"#66FF66\" alink=\"#66FF66\">\n"
                "<font face=\"Arial, Helvetica, sans-serif\">"
                "<h1>FreeWeb Security Alert!</h1>\n"
                "<font size=3>You're seeing this page because the\n"
                "<b>FreeWeb Anonymity Filter</b> is <b>on</b>\n"
                "and your browser attempted to retrieve\n"
                "a resource outside of Freenet.<br><br>\n"
                "While this is generally safe, there are certain individuals who are placing\n"
                "<i>web bugs</i> and other tracing mechanisms into Freenet pages, which can\n"
                "cause your Freenet browsing activities to be logged to a computer on\n"
                "the mainstream web - a total violation of your anonymity.<br><br>\n"
                "Unless you know a Freenet web page and all its links to be safe, the only way to\n"
                "guarantee your privacy is to leave this filter <b>on</b> while you are surfing\n"
                "within Freenet, and only turn if <b>off</b> when you are surfing the mainstream web.<br><br>\n"
                "To disable or re-enable this filter, double-click\n"
                "on the FreeWeb icon in your task tray, then click on 'Configure'<br><br>\n"
                "<b>A Final Warning</b> - for the anonymity filter to be effective, you'll\n"
                "need to regularly clear or at least prune your browser's cache</font>\n"
                "</body></html>\n";
    int reply_len = strlen(http_reply);

    send(sock, http_reply, reply_len, 0);
    return 1;
}


static void notify_block(int enable, int sock, char *passwd)
{
    char *http_reply =
                "HTTP/1.0 200 OK\n"
                "Connection: close\n"
                "Content-Type: text/html\n\n"
                "<html>"
                "<head>"
                "<title>FreeWeb Security Settings Change</title>\n"
                "</head>"
                ;
    char *http_block =
                "<body bgcolor=\"#000000\" text=#00FF00 link=\"#00FF00\""
                "vlink=\"#00FF00\" alink=\"#00FF00\">\n"
                "<font face=\"Arial, Helvetica, sans-serif\">"
                "<h1>FreeWeb Security Notice</h1>\n"
                "<font size=3>Web blocking is <b>ON</b><br><br>\n"
                "Note that you can disable blocking by visiting the address\n"
                "<b>http://free/noblock?password</b>, where <b>password</b> is\n"
                "the password you chose with the '<b>-w</b> command line option.<br><br>\n"
                ;
    char *http_noblock =
                "<body bgcolor=\"#000000\" text=#ff0000 link=\"#FF0000\""
                "vlink=\"#FF0000\" alink=\"#FF0000\">\n"
                "<font face=\"Arial, Helvetica, sans-serif\">"
                "<h1>FreeWeb Security Notice</h1>\n"
                "<font size=3>Web blocking is <b>OFF</b><br><br>\n"
                "Please note that this makes you vulnerable to 'web bugs'\n"
                "and can destroy your anonymity<br><br>\n"
                "Note that you can re-enable blocking by visiting the address\n"
                "<b>http://free/block?password</b>, where <b>password</b> is\n"
                "the password you chose with the '<b>-w</b> command line option.<br><br>\n"
                ;

    char *http_end =
                "Please click your browser's REFRESH button now!\n"
                "</font>\n"
                "</body></html>\n";

    int reply_len = strlen(http_reply);

    // validate password
    if (webBlockPass == NULL || strcmp(passwd, webBlockPass) != 0)
    {
        illegal_block(sock);
        return;
    }

    send(sock, http_reply, reply_len, 0);
    fcpreq_webblock(enable);
    if (enable)
        send(sock, http_block, strlen(http_block), 0);
    else
        send(sock, http_noblock, strlen(http_noblock), 0);
    send(sock, http_end, strlen(http_end), 0);


}


static void illegal_block(int sock)
{
    char *http_reply =
                "HTTP/1.0 403 Access Denied\n"
                "Connection: close\n"
                "Content-Type: text/html\n\n"
                "<html>"
                "<head>"
                "<title>FreeWeb Security Alert</title>\n"
                "</head>"
                "<body bgcolor=\"#000000\" text=#FF9966 link=\"#FF9966\""
                "vlink=\"#FF9966\" alink=\"#FF9966\">\n"
                "<font face=\"Arial, Helvetica, sans-serif\">"
                "<h1>FreeWeb Security Alert!</h1>\n"
                "<font size=3>You're seeing this page because you (or something \n"
                "in the Freenet page you are viewing) has tried to change the web \n"
                "blocking settings using an incorrect password<br><br>\n"
                "To enable or disable web blocking, you must use the URL<br>\n"
                "<b>http://free/block?password</b> or<br><b>http://free/noblock?password</b>,<br>"
                "where <b>password</b> is the password you chose in the '<b>-w</b>' option when \n"
                "you ran fwpwoxy from the command line<br>\n"
                "</font></body></html>\n";

    int reply_len = strlen(http_reply);

    send(sock, http_reply, reply_len, 0);
}



void fcpreq_sethtl(int htl)
{
    fcpreq_htl = htl;
}


void fcpreq_setRegress(int regress)
{
    fcpreq_regress = regress;
}



static int hexdig_to_int(char dig)
{
    if (dig >= '0' && dig <= '9')
        return dig - '0';
    else if (dig >= 'A' && dig <= 'F')
        return dig - 'A' + 10;
    else if (dig >= 'a' && dig <= 'f')
        return dig - 'a' + 10;
    else
        return 0;
}


static void fcpreq_404(int sock, char *key)
{
    // Send back a 404
    char *http_404 =
            "HTTP/1.0 404 Page Not Found\n"
            "Connection: close\n"
            "Content-Type: text/html\n\n"
            "<html>"
            "<head>"
            "<title>FreeWeb - Page Not Found</title>\n"
            "</head><body>\n"
            "<h1>Page Not Found</h1>\n"
            "<h3>Sorry, but I was unable to find the Freenet page you requested</h3>\n"
            "</body></html>\n"
            ;
    send(sock, http_404, strlen(http_404), 0);
    printf("404: %s\n", key);
}

int fcpLogCallback(int level, char *buf)
{
    if (level <= verbosity)
        puts(buf);
    return 0;
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


void fcpreq_setthreadnumber( int workThreads, int maxThreads)
{
	splitWorkThreads= workThreads;
	if( maxThreads > workThreads) { splitMaxThreads= maxThreads; }
	else { splitMaxThreads= splitWorkThreads; }
}


static void decode_uri(char *path)
{
	char *src, *dest;

	for (src = dest = path; *src != '\0'; src++, dest++)
		if (*src == '%')
		{
			*dest = hexdig(src[1]) * 16 + hexdig(src[2]);
			src += 2;
		}
		else
			*dest = *src;
	*dest = '\0';
}

static int hexdig(char dig)
{
	if (dig >= '0' && dig <= '9')
		return dig - '0';
	else if (dig >= 'A' && dig <= 'F')
		return dig - 'A' + 10;
	else if (dig >= 'a' && dig <= 'f')
		return dig - 'a' + 10;
	else
		return 0;
}


static void fcpreq_getrange( int *start, int *end, char *range)
{
	char *rangetmp;
	char *p1, *p2;

	*start=0;
	*end=  -1;

//	printf("headers : %s\n", headers);


	if( range==NULL) {
//		printf("no range\n");
	} else {
//		printf("range: %s\n", range);
		rangetmp= safeMalloc( strlen( range)+1);
		p1=range;
		p2=rangetmp;

		while( *p1) {
			*p2= tolower( *p1);
			p1++; p2++;
		}

		if( (p1=strstr( rangetmp, "range: bytes=")) != NULL) { // search for "range:"
			p1+=13;
			if( (p2=strchr( p1, '-')) != NULL) { // search for start byte
				*p2='\0';
				*start= atoi( p1);

				p1= p2+1;   // search for end bytes if it is specified
				p2= strchr( p1, '\n');
				if( p2!=p1) {  // no end byte, request to end
					*p2= '\0';
					*end=atoi( p1);
					if( *end < *start) {
						*end=-1; // error: end before start!
					}
				}
			}
		}
		free( rangetmp);
	}

//	printf(" range found: %d - %d\n", *start, *end);
}

