#include <time.h>

#define MAXFILELEN  256
#define CHKKEYLEN   128

#define INSERT_FILE_WAITING     0
#define INSERT_FILE_INPROG      1
#define INSERT_FILE_FAILED      2
#define INSERT_FILE_SAVEDATA	3
#define INSERT_FILE_DONE        4

#define INSERT_THREAD_IDLE      0
#define INSERT_THREAD_RUNNING   1
#define INSERT_THREAD_DONE      2
#define INSERT_THREAD_FAILED    3

#define FCPPUTSITE_ATTEMPTS        3
#define FCPPUTSITE_INSERT_THREADS  5
#define FCPPUTSITE_DEFAULT_FILE    "index.html"

typedef struct
{
	char    filename[MAXFILELEN];   /* obvious */
	char    relpath[MAXFILELEN];    /* relative path for URL purposes */
	char    chk[CHKKEYLEN+1];       /* CHK this file is published as */
	time_t	lastinserted;
	time_t	ctime;                  /* change time of file */
	int		size;					            /* size of file */
	int     insertStatus;
	int		retries;
	struct _sitefile *next;         /* next file in chain */
} SiteFile;

typedef struct
{
	SiteFile    *fileSlot;
	char        *metadata;          /* malloc()'ed metadata to insert */
	char        key[256];           /* key URI, if inserting metadata */
	time_t		starttime;
	char		threadStatus;
} PutJob;

typedef struct {
	char  *siteName;
	char	*siteDir;
	char	*pubKey;
	char	*privKey;
	char	*defaultFile;
	char	*recordFile;
	int		daysAhead;
	int		maxThreads;
	int		maxRetries;
	char	dodbr;
} PutSiteOptions;

/* fcpputsite.c exports */

char *strsav(char *old, int *oldlen, char *text_to_append);
int fcpLogCallback(int level, char *buf);

/* insertFreesite.c exports */

extern int insertFreesite(PutSiteOptions *opts);

/* scandir.c exports */

SiteFile *scan_dir(char *dirname, int *pNumFiles);

