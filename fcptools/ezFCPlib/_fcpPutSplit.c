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
#ifdef HAVE_PTHREAD_H
#include "pthread.h"
#endif
#endif

#include "stdlib.h"
#include "time.h"
#include "string.h"

#include "ezFCPlib.h"


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

static int			insertSplitManifest(HFCP *hfcp, char *key, char *metaData, char *file);

static void			splitAddJob(splitJobIns *job);

static int			LaunchThread(void (*func)(void *), void *parg);
static void			QuitThread();

static void			splitInsMgr(void *nothing);

static void			chunkThread(chunkThreadParams *params);

static int			dumpQueue();

static splitJobIns	*newJob;
static splitJobIns	*jobQueue;
static splitJobIns	*jobQueueEnd;
static splitJobIns	*activeJobs;

static int			runningThreads = 0;		// number of split threads currently running
static int			clientThreads = 0;		// number of caller threads currently running

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
	int			result;

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
	job->chunk = safeMalloc(job->numChunks * sizeof(splitChunkIns));
	job->status = SPLIT_INSSTAT_WAITING;
	job->fd = fd;
	job->next = NULL;
	job->key[0] = '\0';
	job->fileName = fileName;

	mimeType = GetMimeType(key);
	job->mimeType = (mimeType != NULL) ? mimeType : GetMimeType(key);

	// enqueue this insert job
	splitAddJob(job);

	clientThreads++;

	// wait for it to finish
	while (job->status != SPLIT_INSSTAT_MANIFEST && job->status != SPLIT_INSSTAT_FAILED)
		mysleep(1000);

	close(fd);

	// any good?
	if (job->status == SPLIT_INSSTAT_FAILED)
	{
		_fcpLog(FCP_LOG_NORMAL, "fcpInsSplitFile: insert of '%s' failed", fileName);
		free(job->chunk);
		clientThreads--;
		return -1;
	}

	// create a manifest for all the inserted chunks
	_fcpLog(FCP_LOG_VERBOSE, "fcpInsSplitFile: insert of '%s' successful", fileName);

	// insert this manifest
	result = insertSplitManifest(hfcp, key, metaData, strrchr(fileName, DIR_DELIM_CHAR));

	// update status for manager thread
	job->status = (result == 0) ? SPLIT_INSSTAT_SUCCESS : SPLIT_INSSTAT_FAILED;

	//printf("||||||||||||| cleared job status for %s at %x\n", fileName, job);

	// return to caller
	free(job->chunk);
	clientThreads--;

	return result;
}					// fcpInsSplitFile()




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

static int insertSplitManifest(HFCP *hfcp, char *key, char *metaData, char *file)
{
	splitJobIns *job = &hfcp->split;
	splitChunkIns *chunk;
	int i;
	char *splitManifest;
	char s[1024];

	_fcpLog(FCP_LOG_VERBOSE, "Creating splitfile manifest");

	runningThreads++;

	// Create mem block for metadata
    splitManifest = safeMalloc(256 + 1024 * hfcp->split.numChunks);

	// Add header info
    strcpy(splitManifest, "Version\nRevision=1\nEndPart\nDocument\n");

    sprintf(s, "SplitFile.Size=%x\n", hfcp->split.totalSize);
    strcat(splitManifest, s);

    sprintf(s, "SplitFile.BlockCount=%x\n", hfcp->split.numChunks);
    strcat(splitManifest, s);

	// Add the chunk records
    for(i = 0; i < hfcp->split.numChunks; i++)
    {
		//printf("### chunk[%d] = %x\n", i, &hfcp->split.chunk[i]);
		//printf("** hfcp->split.chunk[i].key = '%s'\n", hfcp->split.chunk[i].key);
        sprintf(s, "SplitFile.Block.%x=%s\n", i + 1, hfcp->split.chunk[i].key);
        strcat(splitManifest, s);
    }

	if (hfcp->split.mimeType != NULL)
	    sprintf(s, "Info.Format=%s\n", hfcp->split.mimeType);

    strcat( splitManifest, s);
    strcat( splitManifest, "End\n");

	_fcpLog(FCP_LOG_DEBUG, "%s: Inserting manifest:\n%s", file, splitManifest);

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
			runningThreads--;
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
			runningThreads--;
			return -1;
		}
	}
	else
	{
		// insert split manifest directly
		if (fcpPutKeyFromMem(hfcp, key, NULL, splitManifest, 0) != 0)
		if (0)
		{
			_fcpLog(FCP_LOG_NORMAL, "insertSplitManifest(): failed to insert direct split manifest");
			runningThreads--;
			return -1;
		}
	}

	_fcpLog(FCP_LOG_VERBOSE, "%s: successfully inserted splitfile manifest", splitManifest);
	runningThreads--;
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


char *xxx = 0x1467658;


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
	int breakloop = 0;

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
		breakloop = 0;

		if (++clicks == 600)
		{
			clicks = 0;
			_fcpLog(FCP_LOG_DEBUG, "%d threads running, %d clients, queue dump follows",
					runningThreads, clientThreads);
			//dumpQueue();
		}

		// de-queue any freshly completed or failed jobs
		for (tmpJob = jobQueue; tmpJob != NULL; tmpJob = tmpJob->next)
		{
			if (tmpJob->status == SPLIT_INSSTAT_BADNEWS)
				// mark as failed so client thread can pick it up
				tmpJob->status = SPLIT_INSSTAT_FAILED;

			// skip if we haven't reached a completion state
			if (tmpJob->status != SPLIT_INSSTAT_SUCCESS
				&& tmpJob->status != SPLIT_INSSTAT_FAILED
				&& tmpJob->doneChunks < tmpJob->numChunks
				&& tmpJob->status == SPLIT_INSSTAT_INPROG)
			{
				prevJob = tmpJob;
				continue;
			}

			// job at tmpJob is complete, dequeue it
			_fcpLog(FCP_LOG_DEBUG, "Queue dump: before ditching job for '%s'",
					tmpJob->fileName);
			dumpQueue();

			// is this job done?
			if (tmpJob->doneChunks >= tmpJob->numChunks
				&& tmpJob->status == SPLIT_INSSTAT_INPROG)
				// yes - promote to 'manifest' stage so client thread can pick it up
				tmpJob->status = SPLIT_INSSTAT_MANIFEST;
				prevJob = tmpJob;

			// now de-queue it
			if (tmpJob == jobQueue)
			{
				// trivial case - we're at head of queue
				tmpJob = jobQueue = jobQueue->next;
				if (jobQueue == NULL)
				{
					jobQueueEnd = NULL;
					break;
				}
			}
			else
			{
				// use 'prev' ptr to unlink this job
				prevJob->next = tmpJob->next;
				if (tmpJob == jobQueueEnd)
					// we're ditching last in queue, so we need to
					// point jobQueueEnd to previous item
					jobQueueEnd = prevJob;
			}

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: after ditching");
			dumpQueue();
		}

		// Check for any new jobs
		if (newJob != NULL)
		{
			splitJobIns *temp = &newJob->next;

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
			newJob->status = SPLIT_INSSTAT_INPROG;

			newJob->next = NULL;
			newJob = NULL;

			_fcpLog(FCP_LOG_DEBUG, "Queue dump: after adding new job");
			dumpQueue();

			continue;
		}

		// No more to do if thread quota is maxed out
		if (runningThreads >= maxThreads)
			continue;

		//if (jobQueue == NULL)
		//	_fcpLog(FCP_LOG_DEBUG, "Job queue is empty");
		//_fcpLog(FCP_LOG_DEBUG, "splitInsMgr: looking for next chunk to insert");

		// We have spare thread slots - search for next chunk insert job to launch
		for (tmpJob = jobQueue; tmpJob != NULL && !breakloop; tmpJob = tmpJob->next)
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
					chunkThreadParams *params = safeMalloc(sizeof(chunkThreadParams));

					chunk->status = SPLIT_INSSTAT_INPROG;		// correct code

					if (i + 1 < tmpJob->numChunks)
						chunk->size = fcpSplitChunkSize;
					else
						chunk->size = tmpJob->totalSize - (i * fcpSplitChunkSize);

					params->chunk = chunk;
					params->chunk->index = i;
					params->key = tmpJob;

					// load in a chunk of the key
					// create a buf, and read in the part of the file
					buf = safeMalloc(fcpSplitChunkSize);
					params->chunk->chunk = buf;

					lseek(tmpJob->fd, i * fcpSplitChunkSize, 0);
					read(tmpJob->fd, buf, fcpSplitChunkSize);

					_fcpLog(FCP_LOG_DEBUG, "splitmgr: launching thread for chunk %d, file %s",
							i, params->key->fileName);

					// fire up a thread to insert this chunk
					if (LaunchThread(chunkThread, params) != 0)
					{
						// failed thread launch - force restart of main loop
						_fcpLog(FCP_LOG_CRITICAL, "thread launch failed: chunk %d, file %s",
								i, params->key->fileName);
						breakloop = 1;
						chunk->status = SPLIT_INSSTAT_WAITING;
						free(buf);
						free(params);
						break;
					}
					else
						chunk->status = SPLIT_INSSTAT_INPROG;

					// Successful launch - Add to tally of running threads
					runningThreads++;
				}
			}				// 'for each splitfile chunk'
		}					// 'for (each spare thread slot)'
	}						// 'while (1)'
}							// 'splitInsMgr()'


static int dumpQueue()
{
	splitJobIns *job;

	for (job = jobQueue; job != NULL; job = job->next)
	{
		int i;
		char buf[1024];
		char buf1[1024];

		sprintf(buf, "%s(%d): ", strrchr(job->fileName, DIR_DELIM_CHAR), job->numChunks);

		switch (job->status)
		{
		case SPLIT_INSSTAT_WAITING:
			sprintf(buf1, "waiting", job);
			strcat(buf, buf1);
			break;
		case SPLIT_INSSTAT_INPROG:
			for (i = 0; i < job->numChunks; i++)
			{
				switch (job->chunk[i].status)
				{
				case SPLIT_INSSTAT_IDLE:
				case SPLIT_INSSTAT_WAITING:
					sprintf(buf1, "%d,", i);
					strcat(buf, buf1);
					break;
				case SPLIT_INSSTAT_INPROG:
					sprintf(buf1, "(%d),", i);
					strcat(buf, buf1);
					break;
				}
			}
			break;
		case SPLIT_INSSTAT_MANIFEST:
			sprintf(buf1, "inserting manifest", job);
			strcat(buf, buf1);
			break;
		case SPLIT_INSSTAT_SUCCESS:
			sprintf(buf1, "success", job);
			strcat(buf, buf1);
			break;
		case SPLIT_INSSTAT_BADNEWS:
			sprintf(buf1, "badnews", job);
			strcat(buf, buf1);
			break;
		case SPLIT_INSSTAT_FAILED:
			sprintf(buf1, "failed", job);
			strcat(buf, buf1);
			break;

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
	int mypid = getpid();

//params->chunk->status = SPLIT_INSSTAT_SUCCESS;
//params->key->doneChunks++;

	_fcpLog(FCP_LOG_VERBOSE, "%d:chunkThread: inserting chunk index %d of %s",
			mypid,
			params->chunk->index,
			params->key->fileName);

	if (fcpPutKeyFromMem(hfcp, "CHK@", params->chunk->chunk, NULL, params->chunk->size)
		!= 0)
	{
		_fcpLog(FCP_LOG_VERBOSE, "%d:chunkThread: failed to insert chunk index %d of %s",
			mypid,
			params->chunk->index,
			params->key->fileName);
		params->chunk->status = SPLIT_INSSTAT_FAILED;
		params->key->status = SPLIT_INSSTAT_BADNEWS;
		runningThreads--;
		QuitThread();
		return;
	}

//	printf("### params->chunk = %x\n", params->chunk);

	params->chunk->status = SPLIT_INSSTAT_MANIFEST;
	params->key->doneChunks++;

//	sprintf(params->chunk->key, "CHK@%03d", keynum);
	strcpy(params->chunk->key, hfcp->created_uri);		// correct code

	//strncpy(mem, params->chunk->chunk, params->chunk->size);
	//mem[params->chunk->size] = '\0';

	//printf("thrd: inserted %s with %d bytes of data '%s'\n", params->chunk->key, params->chunk->size, mem);

	free(params->chunk->chunk);

	_fcpLog(FCP_LOG_VERBOSE, "%d:chunkThread: inserted chunk index %d of %s\nkey=%s",
			mypid,
			params->chunk->index,
			params->key->fileName,
			params->chunk->key);

	free(params);
	fcpDestroyHandle(hfcp);

	// only decrement thread count if we're not ready to write the manifest
	runningThreads--;
	_fcpLog(FCP_LOG_DEBUG, "%d:chunkThread: %d threads now running",
			mypid, runningThreads);
	QuitThread();
	return;
}


static int LaunchThread(void (*func)(void *), void *parg)
{
#ifdef SINGLE_THREAD_DEBUG
    (*func)(parg);
    return;
#else
#ifdef WINDOWS
	chunkThreadParams *params = parg;

    return _beginthread(func, 0, parg) == -1 ? -1 : 0;
#else
    pthread_t pth;
	pthread_attr_t attr;

	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	return pthread_create(&pth, &attr, (void *(*)(void *))func, parg);
#endif
#endif
}               // 'LaunchThread()'


static void QuitThread()
{
#ifdef WINDOWS
	_endthread();
#else
	pthread_exit("");
#endif
}
