// insertFreesite.c - part of fcpputsite
// This module is the engine that does the inserting
// CopyLeft (c) 2001 by David McNab

#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>

#ifdef WINDOWS
#include <process.h>
#else
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#endif

#include "ezFCPlib.h"
#include "fcpputsite.h"


//
// IMPORTED DECLARATIONS
//

extern int      fcpLogCallback(int level, char *buf);
extern SiteFile *scan_dir(char *dirname, int *pNumFiles);
extern char     *strsav(char *old, char *text_to_append);
extern char     *GetMimeType(char *pathname);


//
// EXPORTED DECLARATIONS
//

int insertFreesite(char *siteName, char *siteDir, char *pubKey, char *privKey,
                    char *defaultFile, int daysFuture, int maxThreads,
                    int maxretries);


//
// PRIVATE DECLARATIONS
//

static void LaunchThread(void (*func)(void *), void *parg);

static void putsiteThread(void *arg);

static int  numFiles;
static int  maxTries;

static int  defaultIndex;


////////////////////////////////////////////////////////////////
// END OF DECLARATIONS
////////////////////////////////////////////////////////////////


int insertFreesite(char *siteName, char *siteDir, char *pubKey, char *privKey,
                   char *defaultFile, int daysFuture, int maxThreads,
                   int maxAttempts)
{
    SiteFile *files;
    int     i;
    char    *s;
    char    *threadStatus;
    PutJob  *pPutJob;
    int     running = 1;
    int     someFilesFailed = 0;
	int		clicks = 0;

    maxTries = maxAttempts;

    // truncate trailing '/' from dir if any
    s = siteDir + strlen(siteDir) - 1;
    if (*s == '/' || *s == '\\')
        *s = '\0';

    // scan the directory recursively and get a list of files
    if ((files = scan_dir(siteDir, &numFiles)) == NULL)
    {
        _fcpLog(FCP_LOG_CRITICAL, "insertFreesite: can't read directory '%s'", siteDir);
        return -1;
    }

    // ensure default file actually exists
    for (i = 0; i < numFiles; i++)
    {
        if (!strcmp(defaultFile, files[i].relpath))
        {
            defaultIndex = i;
            break;
        }
    }
    if (i == numFiles)
    {
        _fcpLog(FCP_LOG_CRITICAL, "FATAL: default file '%s' not found", defaultFile);
        free(files);
        return -1;
    }

    // ok - we can go ahead with the job
    _fcpLog(FCP_LOG_NORMAL, "--------------------------------------------------");
    _fcpLog(FCP_LOG_NORMAL, "Inserting site:   %s", siteName);
    _fcpLog(FCP_LOG_NORMAL, "--------------------------------------------------");
    _fcpLog(FCP_LOG_NORMAL, "Directory:        %s", siteDir);
    _fcpLog(FCP_LOG_NORMAL, "Public Key:       %s", pubKey);
    _fcpLog(FCP_LOG_NORMAL, "Private Key:      %s", privKey);
    _fcpLog(FCP_LOG_NORMAL, "Default file:     %s", defaultFile);
    _fcpLog(FCP_LOG_NORMAL, "Days ahead:       %d", daysFuture);
    _fcpLog(FCP_LOG_NORMAL, "Maximum threads:  %d", maxThreads);
    _fcpLog(FCP_LOG_NORMAL, "Maximum attempts: %d", maxAttempts);

    // print out list of files
    _fcpLog(FCP_LOG_VERBOSE, "Files in directory '%s' are:", siteDir);
    for (i = 0; i < numFiles; i++)
        _fcpLog(FCP_LOG_VERBOSE, " %9d %s", files[i].size, files[i].relpath);

    //
    // Now insert all these files
    //

    // create and initialise status tables
    threadStatus = safeMalloc(maxThreads);
    for (i = 0; i < maxThreads; i++)
        threadStatus[i] = INSERT_THREAD_IDLE;

    // the big loop - search for free thread slots and dispatch insert threads till all done
    while (running)
    {
        int firstWaitingFile;
        int firstThreadSlot;

        // search for waiting files
        for (firstWaitingFile = 0; firstWaitingFile < numFiles; firstWaitingFile++)
            if (files[firstWaitingFile].insertStatus == INSERT_FILE_WAITING)
                break;
        if (firstWaitingFile == numFiles)
        {
            // All files are either in progress or donw - wait for all to complete
            while (1)
            {
                for (i = 0; i < maxThreads; i++)
                    if (threadStatus[i] == INSERT_THREAD_RUNNING)
                        break;
                if (i == maxThreads)
                {
                    // all done
                    running = 0;
                    break;
                }
                else
                    sleep(1);  // one or more threads currently running
            }
        }

        if (!running)
            break;

        // search for a thread slot
        for (firstThreadSlot = 0; firstThreadSlot < maxThreads; firstThreadSlot++)
            if (threadStatus[firstThreadSlot] == INSERT_THREAD_IDLE)
			{
                break;
			}

        // any threads available yet?
        if (firstThreadSlot == maxThreads)
        {
            // no - wait a while and restart
            sleep(1);
			if (++clicks == 180)
				_fcpLog(FCP_LOG_DEBUG, "fcpputsite: all thread slots full");
            continue;
        }

		_fcpLog(FCP_LOG_DEBUG, "fcpputsite: found thread slot for inserting %s",
			strrchr(files[firstWaitingFile].filename, DIR_DELIM_CHAR)+1);

        // Fill out a job form and launch thread
        //numthreads++;
        files[firstWaitingFile].insertStatus = INSERT_FILE_INPROG;
        threadStatus[firstThreadSlot] = INSERT_THREAD_RUNNING;
        pPutJob = safeMalloc(sizeof(PutJob));
        pPutJob->fileSlot = files + firstWaitingFile;
        pPutJob->threadSlot = threadStatus + firstThreadSlot;
        pPutJob->metadata = NULL;
        pPutJob->key[0] = '\0';
        LaunchThread(putsiteThread, (void *)pPutJob);
        //sucksite_thread(pPutJob);

    }			// 'while (inserting files)'


	_fcpLog(FCP_LOG_DEBUG, "fcpputsite: broke main insert loop");

    // did file inserts all succeed?
    for (i = 0; i < numFiles; i++)
    {
        if (files[i].insertStatus != INSERT_FILE_DONE)
        {
            someFilesFailed = 1;
            _fcpLog(FCP_LOG_CRITICAL, "Failed to insert '%s'", files[i].relpath);
        }
    }

    if (someFilesFailed)
    {
        _fcpLog(FCP_LOG_CRITICAL, "One or more inserts failed - aborting");
        return -1;
    }
    else
    {
        char *metaRoot;                                 // metadata for MSK root key
        char *metaMap;                                  // metadata for site map key
        char *metaChk;

        char dbrRootUri[128];
        char dbrTargetUri[128];
        char mapChkUri[128];

        int mapLen;
        HFCP *hfcp;

        time_t timeNow;

        hfcp = fcpCreateHandle();

		_fcpLog(FCP_LOG_VERBOSE,
			"insertFreesite: file inserts succeeded, inserting metadata");

        // create a DBR root
        sprintf(dbrRootUri, "SSK@%s/%s", privKey, siteName);

        metaRoot = strsav(NULL,
                        "Version\nRevision=1\nEndPart\n"
                        "Document\nDateRedirect.Target=freenet:SSK@");
        metaRoot = strsav(metaRoot, pubKey);
        metaRoot = strsav(metaRoot, "/");
        metaRoot = strsav(metaRoot, siteName);
        metaRoot = strsav(metaRoot, "\nEnd\n");

        // create dbr target uri
        time(&timeNow);
        sprintf(dbrTargetUri, "SSK@%s/%lx-%s",
                        privKey,
                        (timeNow - (timeNow % 86400)) + (daysFuture * 86400),
                        siteName);

        // create mapfile
        metaMap = strsav(NULL, "Version\nRevision=1\nEndPart\n");
        for (i = 0; i < numFiles; i++)
        {
            metaMap = strsav(metaMap, "Document\nName=");
            metaMap = strsav(metaMap, files[i].relpath);
            metaMap = strsav(metaMap, "\nInfo.Format=");
            metaMap = strsav(metaMap, GetMimeType(files[i].relpath));
            metaMap = strsav(metaMap, "\nRedirect.Target=");
            metaMap = strsav(metaMap, files[i].chk);
            metaMap = strsav(metaMap, "\n");

            if (i == defaultIndex)
            {
                // Create an unnamed cdoc for default file
                metaMap = strsav(metaMap, "EndPart\n");
                metaMap = strsav(metaMap, "Document\n");
                metaMap = strsav(metaMap, "Info.Format=");
                metaMap = strsav(metaMap, GetMimeType(files[i].relpath));
                metaMap = strsav(metaMap, "\nRedirect.Target=");
                metaMap = strsav(metaMap, files[i].chk);
                metaMap = strsav(metaMap, "\n");
            }

            if (i + 1 < numFiles)
                // not the last cdoc - need an 'EndPart'
                metaMap = strsav(metaMap, "EndPart\n");

        }       // 'for (each file written to mapfile)'
        metaMap = strsav(metaMap, "End\n");
        mapLen = strlen(metaMap);

        _fcpLog(FCP_LOG_NORMAL, "METADATA IS %d BYTES LONG", mapLen);

		// insert DBR root
        if (fcpPutKeyFromMem(hfcp, dbrRootUri, NULL, metaRoot, 0) != 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "Failed to insert DBR root - aborting");
            return 1;
        }

        if (mapLen <= 32767)
//        if (mapLen <= 1)
        {
            // make and insert normal mapfile at dbr target
            if (fcpPutKeyFromMem(hfcp, dbrTargetUri, NULL, metaMap, 0) != 0)
            {
                _fcpLog(FCP_LOG_CRITICAL, "Failed to insert mapfile at '%s'", dbrTargetUri);
                return -1;
            }
        }
        else
        {
            // too big for an ssk, insert map as a chk and redirect to that
            _fcpLog(FCP_LOG_CRITICAL, "Metadata map at %d bytes is too large - adding CHK step", mapLen);
            fcpDestroyHandle(hfcp);
            hfcp = fcpCreateHandle();
            if (fcpPutKeyFromMem(hfcp, "CHK@", NULL, metaMap, 0) != 0)
            {
                _fcpLog(FCP_LOG_CRITICAL, "Failed to insert large mapfile as CHK");
                return -1;
            }

            // inserted ok as CHK - now build a redirect to that CHK
            metaChk = strsav(NULL,
                            "Version\nRevision=1\nEndPart\n"
                            "Document\nRedirect.Target=");
            metaChk = strsav(metaChk, hfcp->created_uri);
            metaChk = strsav(metaChk, "\nEnd\n");

            fcpDestroyHandle(hfcp);

            hfcp = fcpCreateHandle();
    
            // now insert at DBR target a redirect to mapfile CHK
            if (fcpPutKeyFromMem(hfcp, dbrTargetUri, NULL, metaChk, 0) != 0)
            {
                _fcpLog(FCP_LOG_CRITICAL, "Failed to insert large mapfile as CHK");
                return -1;
            }
        }

        // should be ok now

        _fcpLog(FCP_LOG_NORMAL, "============================================");
        _fcpLog(FCP_LOG_NORMAL, "Successfully inserted SSK@%s/%s// - %d days ahead",
                                pubKey, siteName, daysFuture);
        return 0;
    }
}               // 'insertFreesite()'


void putsiteThread(void *arg)
{
    PutJob *job = (PutJob *)arg;
    HFCP *hfcp = fcpCreateHandle();
    int status;
    int i;
    char meta[256];

    sprintf(meta, "Version\nRevision=1\nEndPart\nDocument\nInfo.Format=%s\nEnd\n",
                GetMimeType(job->fileSlot->filename));

    if (job->metadata == NULL)
    {
        // insert a file
        _fcpLog(FCP_LOG_VERBOSE, "inserting '%s'", job->fileSlot->relpath);
        for (i = 0; i < maxTries; i++)
        {
            if (i > 0)
                _fcpLog(FCP_LOG_NORMAL, "retry %d for file %s", i, job->fileSlot->filename);

            if ((status = fcpPutKeyFromFile(hfcp, "CHK@", job->fileSlot->filename, meta)) == 0)
            {
                // successful insert
                strcpy(job->fileSlot->chk, hfcp->created_uri);
                job->fileSlot->insertStatus = INSERT_FILE_DONE;
                _fcpLog(FCP_LOG_NORMAL, "Successfully inserted %s with %d retries",
                                        job->fileSlot->filename, i);
                break;
            }
        }
        if (status != 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "Insert failed after %d retries: %s", maxTries, job->fileSlot->filename);
            job->fileSlot->insertStatus = INSERT_FILE_FAILED;
        }
        *(job->threadSlot) = INSERT_THREAD_IDLE;
    }
    else
    {
        // inserting metadata against a given key
        _fcpLog(FCP_LOG_VERBOSE, "Inserting metadata at %s", job->key);
        _fcpLog(FCP_LOG_DEBUG, job->metadata);

        for (i = 0; i < maxTries; i++)
        {
            if (i > 0)
                _fcpLog(FCP_LOG_NORMAL, "retry %d for key %s", i, job->metadata);

            if ((status = fcpPutKeyFromMem(hfcp, job->key, NULL, job->metadata, 0)) == 0)
            //if ((status = 0) == 0)
            {
                // successful insert
                _fcpLog(FCP_LOG_NORMAL, "Successfully inserted key %s with %d retries",
                                        job->key, i);
                break;
            }
        }
        if (status != 0)
        {
            _fcpLog(FCP_LOG_CRITICAL, "Insert failed after %d retries: %s", maxTries, job->key);
            *(job->threadSlot) = INSERT_THREAD_FAILED;
        }
        *(job->threadSlot) = INSERT_THREAD_DONE;
    }
    //free(job);
}               // 'putsiteThread()'


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
