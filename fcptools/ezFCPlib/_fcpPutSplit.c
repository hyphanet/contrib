//
//  This code is part of ezFCPlib - an FCP-based client library for Freenet
//
//  Designed and implemented by David McNab, heretic108@users.sourceforge.net
//  CopyLeft (c) 2001 by David McNab
//
//  The FreeWeb website is at http://freeweb.sourceforge.net
//  The website for Freenet is at http://freenet.sourceforge.net
//
//  This code is distributed under the GNU Public Licence (GPL) version 2.
//  See http://www.gnu.org/ for further details of the GPL.
//
//  Module: _fcpPutSplit
//
//  Description:	* invoked at ezFCPlib startup
//					* creates a thread which manages all splitfile inserts
//					  across all threads within the calling process
//					* manages queueing of splitfile insert jobs
//

#ifndef WINDOWS
#include "unistd.h"
#endif

#include "stdlib.h"

#include "ezFCPlib.h"

#include "time.h"


typedef struct
{
	splitJobIns		*key;
	splitChunkIns	*chunk;
	int delay;
}
chunkThreadParams;


#ifdef WINDOWS
#define OPEN_FILE_MODE (_O_RDONLY | _O_BINARY)
#else
#define OPEN_FILE_MODE 0
#endif


//
// EXPORTED DECLARATIONS
//

void	fcpSetSplitThreads(int _maxThreads);
void	_fcpInitSplit(int maxSplitThreads);

int		fcpInsSplitFile(HFCP *hfcp, char *key, char *fileName, char *metaData);

int		fcpSplitChunkSize = SPLIT_BLOCK_SIZE;	// size of splitfiles chunks


//
// IMPORTED DECLARATIONS
//

char *GetMimeType(char *pathname);

extern char     _fcpID[];


//
// PRIVATE DECLARATIONS
//

static int			insertSplitManifest(HFCP *hfcp, char *key, char *metaData);

static void			splitAddJob(splitJobIns *job);

static void			LaunchThread(void (*func)(void *), void *parg);
static void			splitInsMgr(void *nothing);

static void			chunkThread(chunkThreadParams *params);

static int			dumpQueue();

static splitJobIns	*newJob;
static splitJobIns	*jobQueue;
static splitJobIns	*jobQueueEnd;
static splitJobIns	*activeJobs;

static int			runningThreads = 0;		// number of split threads currently running

static int			maxThreads = FCP_MAX_SPLIT_THREADS;

static char			splitMgrRunning = 0;


//////////////////////////////////////////////////////////////////
//
// FUNCTION DECLARATIONS
//


//
// _fcpInitSplit()
//
// Arguments:		none
//
// Returns:			nothing
//
// Description:		Initialises and starts up the splitfile insert
//					manager thread
//

void _fcpInitSplit(int maxSplitThreads)
{
	time_t thistime;

	time(&thistime);
	srand(thistime);

	if (splitMgrRunning)
	{
		_fcpLog(FCP_LOG_NORMAL, "_fcpInitSplit: error - this func was called earlier");
		return;
	}

	_fcpLog(FCP_LOG_VERBOSE, "_fcpInitSplit: launching splitfile insert manager");

	maxThreads = (maxSplitThreads == 0) ? FCP_MAX_SPLIT_THREADS : maxSplitThreads;

	// Launch manager thread
	LaunchThread(splitInsMgr, NULL);

	while (!splitMgrRunning)
		mysleep(500);

	_fcpLog(FCP_LOG_VERBOSE,
			"_fcpInitSplit: splitfile insert manager now running, max %d threads",
			maxThreads);

}				// '_fcpInitSplit()


//
// Function:    fcpSetSplitSize
//
// Arguments:   hfcp		pointer to FCP handle, created with fcpCreateHandle()
//				splitsize	size of splitfile chunks, default defined in ezFCPlib.h
//
// Description: Sets the number of days to regress in retrying failed date redirects
//				call with fcpSplitChunkSize=0 to disable splitting of inserted files
//

void fcpSetSplitSize(_chunkSize)
{
	fcpSplitChunkSize = _chunkSize;
}


//
// Function:    fcpSetSplitThreads
//
// Arguments:   maxThreads		maximum number of concurrent split threads
//
// Description: Must be called before fcpStartup()
//				sets limit on number of concurrent splitfile insert threads
//

void fcpSetSplitThreads(int _maxThreads)
{
	if (splitMgrRunning)
	{
		_fcpLog(FCP_LOG_CRITICAL,
				"fcpSetSplitThreads: cannot call this after calling fcpStartup()");
		return;
	}

	maxThreads = _maxThreads;
}


//
// fcpInsSplitFile()
//
// Enqueue a file for inserting with split
//
// Arguments:	hfcp		standard ezFCPlib handle created with fcpCreateHandle()
//				fileName	pathname of file to insert
//
// Returns:		0		if file was successfully split up and inserted
//				-1		some kind of failure happened (see log)


int fcpInsSplitFile(HFCP *hfcp, char *key, char *fileName, char *metaData)
{
	struct stat fileStat;
	int			fd;
	char		*mimeType;

	splitJobIns *job = &hfcp->split;

	if (stat(fileName, &fileStat) < 0)
	{
		_fcpLog(FCP_LOG_CRITICAL, "fcpInsSplitFile: cannot stat '%s'", fileName);
		return -1;
	}


	if ((fd = open(fileName, OPEN_FILE_MODE)) < 0)
	{
		_fcpLog(FCP_LOG_CRITICAL, "fcpInsSplitFile: cannot open '%s'", fileName);
		return -1;
	}

	strcpy(job->key, key);
	job->totalSize = fileStat.st_size;
	job->doneChunks = 0;
	job->numChunks = (job->totalSize / fcpSplitChunkSize) + ((job->totalSize % fcpSplitChunkSize) ? 1 : 0);
	job->chunk = malloc(job->numChunks * sizeof(splitChunkIns));
	job->status = SPLIT_INSSTAT_WAITING;
	job->fd = fd;
	job->next = NULL;
	job->key[0] = '\0';
	job->fileName = fileName;

	mimeType = GetMimeType(key);
	job->mimeType = (mimeType != NULL) ? mimeType : GetMimeType(key);

	// enqueue this insert job
	splitAddJob(job);

	// wait for it to finish
	while (job->status != SPLIT_INSSTAT_SUCCESS && job->status != SPLIT_INSSTAT_FAILED)
		mysleep(1000);

	close(fd);

	// any good?
	free(job->chunk);
	if (job->status == SPLIT_INSSTAT_FAILED)
	{
		_fcpLog(FCP_LOG_NORMAL, "fcpInsSplitFile: insert of '%s' failed", fileName);
		return -1;
	}

	// create a manifest for all the inserted chunks
	_fcpLog(FCP_LOG_VERBOSE, "fcpInsSplitFile: insert of '%s' failed", fileName);
	return insertSplitManifest(hfcp, key, metaData);

}




//
// insertSplitManifest()
//
// Called after all split chunks are inserted
// Creates a manifest file and inserts that
//
// Arguments:	hfcp	standard ezFCPlib handle
//
// Returns:		0		if successful
//				-1		if failed
//

static int insertSplitManifest(HFCP *hfcp, char *key, char *metaData)
{
	splitJobIns *job = &hfcp->split;
	splitChunkIns *chunk;
	int i;
	char *splitManifest;
	char s[1024];

	printf("Creating splitfile manifest\n");

	// Create mem block for metadata
    splitManifest = malloc(100 + 512 * hfcp->split.numChunks);

	// Add header info
    strcpy(splitManifest, "Version\nRevision=1\nEndPart\nDocument\n");

    sprintf(s, "SplitFile.Size=%x\n", hfcp->split.totalSize);
    strcat(splitManifest, s);

    sprintf(s, "SplitFile.BlockCount=%x\n", hfcp->split.numChunks);
    strcat(splitManifest, s);

	// Add the chunk records
    for(i = 0; i < hfcp->split.numChunks; i++)
    {
        sprintf(s, "SplitFile.Block.%x=%s\n", i + 1, hfcp->split.chunk[i].key);
        strcat(splitManifest, s);
    }
	if (hfcp->split.mimeType != NULL)
	    sprintf(s, "Info.Format=%s\n", hfcp->split.mimeType);
    strcat( splitManifest, s);
    strcat( splitManifest, "End\n");

	//
	// is the splitfile manifest too big to insert directly under key?
	//

	// skip leading 'freenet:' if any
	if (!strncmp(key, "freenet:", 8))
		key += 8;

	if ((strncmp(key, "CHK@", 4) != 0) && (strlen(splitManifest) > MAX_KSK_LEN))
	{
		// insert split manifest as chk, then redirect to it
		HFCP *hfcp1 = fcpCreateHandle();
		char redirMeta[256];

		// insert splitfile manifest
		if (fcpPutKeyFromMem(hfcp1, "CHK@", splitManifest, splitManifest, 0) != 0)
		{
			_fcpLog(FCP_LOG_NORMAL, "insertSplitManifest(): failed to insert manifest");
			return -1;
		}

		// create redirect to this manifest
		sprintf(redirMeta,
				"Version\nRevision=1\nEndPart\nDocument\nRedirect.Target=%s\nEnd\n",
				hfcp1->created_uri);

		// Now insert this redirect key
		if (fcpPutKeyFromMem(hfcp, key, NULL, redirMeta, 0) != 0)
		{
			_fcpLog(FCP_LOG_NORMAL, "insertSplitManifest(): failed to insert redirect to split manifest");
			return -1;
		}
	}
	else
	{
		// insert split manifest directly
		if (fcpPutKeyFromMem(hfcp, key, NULL, splitManifest, 0) != 0)
		{
			_fcpLog(FCP_LOG_NORMAL, "insertSplitManifest(): failed to insert direct split manifest");
			return -1;
		}
	}

	_fcpLog(FCP_LOG_VERBOSE, "successfully inserted splitfile manifest:\n");
	puts(splitManifest);
	return 0;
}



void splitAddJob(splitJobIns *job)
{
	int i;

	_fcpLog(FCP_LOG_DEBUG, "splitAddJob: adding job to insert '%s'", job->fileName);

	job->status = SPLIT_INSSTAT_WAITING;
	for (i = 0; i < job->numChunks; i++)
	{
		job->chunk[i].key[0] = '\0';
		job->chunk[i].status = SPLIT_INSSTAT_WAITING;
	}

	while (newJob != NULL)
	{
		_fcpLog(FCP_LOG_DEBUG, "splitAddJob: waiting for split insert queue to come free");
		mysleep(1000);
	}
	newJob = job;

	_fcpLog(FCP_LOG_DEBUG, "splitAddJob: queued job to insert '%s'", job->fileName);

}





//
// splitInsMgr()
//
// Arguments:		none
//
// Returns:			nothing - never returns
//
// Description:		This is the splitfile insert manager thread
//					nothing (except small keys) gets inserted
//					into Freenet, except through this thread

static void splitInsMgr(void *nothing)
{
	splitJobIns	*tmpJob, *prevJob;

	splitJobIns *tmp1;
	int clicks = 0;

	// Indicate we're running
	splitMgrRunning = 1;

	// initialise queue
	newJob = NULL;
	jobQueue = jobQueueEnd = NULL;

	// endless loop, taking care of inserts
	while (1)
	{
		// let things breathe a bit
		mysleep(1000);

		// de-queue any freshly completed or failed jobs
		for (tmpJob = jobQueue; tmpJob != NULL; tmpJob = tmpJob->next)
		{
			if (tmpJob->status == SPLIT_INSSTAT_BADNEWS)
				// mark as failed so client thread can pick it up
				tmpJob->status = SPLIT_INSSTAT_FAILED;
			else if (tmpJob->doneChunks == tmpJob->numChunks)
				// mark as complete so client thread can pick it up
				tmpJob->status = SPLIT_INSSTAT_SUCCESS;
			else
			{
				// no change - no need to de-queue
				prevJob = tmpJob;
				continue;
			}

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: before ditching job for '%s'",
					tmpJob->fileName);
			dumpQueue();

			// if we get here, then job at tmpJob is complete, dequeue it
			if (tmpJob == jobQueue)
			{
				// trivial case - we're at head of queue
				jobQueue = jobQueue->next;
				if (jobQueue == NULL)
					jobQueueEnd = NULL;
			}
			else
				// use 'prev' ptr to unlink this job
				prevJob->next = tmpJob->next;

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: after ditching");
			dumpQueue();
		}

		// Check for any new jobs
		if (newJob != NULL)
		{
			// Add this job to main queue
			_fcpLog(FCP_LOG_DEBUG, "splitInsMgr: got req to insert file '%s'",
					newJob->fileName);

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: before adding job for '%s'",
					newJob->fileName);
			dumpQueue();

			if (jobQueueEnd != NULL)
				jobQueueEnd->next = newJob;
			else
				jobQueue = newJob;

			jobQueueEnd = newJob;
			newJob->next = NULL;
			newJob = NULL;

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: after adding new job");
			dumpQueue();

			continue;
		}

		// No more to do if thread quota is maxed out
		if (runningThreads >= maxThreads)
			continue;

		if (jobQueue == NULL)
			_fcpLog(FCP_LOG_DEBUG, "Job queue is empty");

		_fcpLog(FCP_LOG_DEBUG, "splitInsMgr: looking for next chunk to insert");

		// We have spare thread slots - search for next chunk insert job to launch
		for (tmpJob = jobQueue; tmpJob != NULL; tmpJob = tmpJob->next)
		{
			int i;

			// everything on queue is incomplete
			for (i = 0; i < tmpJob->numChunks; i++)
			{
				splitChunkIns *chunk = &tmpJob->chunk[i];
				char *buf;

				// bail if we've maxed out thread limit
				if (runningThreads >= maxThreads)
					continue;

				if (chunk->status == SPLIT_INSSTAT_WAITING)
				{
					chunkThreadParams *params = malloc(sizeof(chunkThreadParams));

					chunk->status = SPLIT_INSSTAT_INPROG;

					if (i + 1 < tmpJob->numChunks)
						chunk->size = fcpSplitChunkSize;
					else
						chunk->size = tmpJob->totalSize - (i * fcpSplitChunkSize);

					params->chunk = chunk;
					params->key = tmpJob;
					params->delay = rand() / 4;

					// load in a chunk of the key
					// create a buf, and read in the part of the file
					buf = malloc(fcpSplitChunkSize);
					params->chunk->chunk = buf;

					lseek(tmpJob->fd, i * fcpSplitChunkSize, 0);
					read(tmpJob->fd, buf, fcpSplitChunkSize);

					// Add to tally of running threads
					runningThreads++;

					// fire up a thread to insert this chunk
					LaunchThread(chunkThread, params);
				}
			}
		}
	}
}				// 'splitInsMgr()'


static int dumpQueue()
{
	splitJobIns *job;

	for (job = jobQueue; job != NULL; job = job->next)
	{
		int i;
		char buf[1024];
		char buf1[10];

		_fcpLog(FCP_LOG_DEBUG, "  %s", job->fileName);

		sprintf(buf, "    %d chunks, inserting: ", job->numChunks);
		for (i = 0; i < job->numChunks; i++)
		{
			switch (job->chunk[i].status)
			{
			case SPLIT_INSSTAT_IDLE:
			case SPLIT_INSSTAT_WAITING:
			case SPLIT_INSSTAT_INPROG:
				sprintf(buf1, "%d,", i);
				strcat(buf, buf1);
			}
		}
		_fcpLog(FCP_LOG_DEBUG, buf);

	}
}


//
// chunkThread()
//
// Thread to insert a single chunk
//

static void chunkThread(chunkThreadParams *params)
{
	static int keynum = 0;
	char mem[1024];
	HFCP *hfcp = fcpCreateHandle();

	//printf("thread %d delaying for %d ms\n", keynum, params->delay);
	//Sleep(params->delay);
	//keynum++;

	_fcpLog(FCP_LOG_DEBUG, "chunkThread: inserting chunk %d/%d of %s",
			params->key->doneChunks, params->key->numChunks,
			params->key->fileName);

	if (fcpPutKeyFromMem(hfcp, "CHK@", params->chunk->chunk, NULL, params->chunk->size) != 0)
	{
		_fcpLog(FCP_LOG_NORMAL, "chunkThread: failed to insert chunk");
		params->chunk->status = SPLIT_INSSTAT_FAILED;
		params->key->status = SPLIT_INSSTAT_BADNEWS;
		runningThreads--;
		return;
	}

	params->chunk->status = SPLIT_INSSTAT_SUCCESS;
	params->key->doneChunks++;
	//sprintf(params->chunk->key, "CHK@%03d", keynum);
	strcpy(params->chunk->key, hfcp->created_uri);

	//strncpy(mem, params->chunk->chunk, params->chunk->size);
	//mem[params->chunk->size] = '\0';

	//printf("thrd: inserted %s with %d bytes of data '%s'\n", params->chunk->key, params->chunk->size, mem);

	free(params->chunk->chunk);

	_fcpLog(FCP_LOG_VERBOSE,
			"chunkThread: success inserting %d/%d: %s",
			params->key->doneChunks, params->key->numChunks, hfcp->created_uri);

	free(params);
	fcpDestroyHandle(hfcp);
	runningThreads--;
	return;
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


