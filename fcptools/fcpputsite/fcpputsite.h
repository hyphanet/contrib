
#ifdef WINDOWS

#define DIR_DELIM_CHAR      '\\'
#define DIR_DELIM_STRING    "\\"

#else

#define DIR_DELIM_CHAR      '/'
#define DIR_DELIM_STRING    "/"

#include "dirent.h"
#include "sys/stat.h"
#include "unistd.h"

#endif


#define MAXFILELEN  256
#define CHKKEYLEN   128

#define INSERT_FILE_WAITING     0
#define INSERT_FILE_INPROG      1
#define INSERT_FILE_FAILED      2
#define INSERT_FILE_DONE        3

#define INSERT_THREAD_IDLE      0
#define INSERT_THREAD_RUNNING   1
#define INSERT_THREAD_DONE      2
#define INSERT_THREAD_FAILED    3

typedef struct _sitefile
{
    char    filename[MAXFILELEN];   // obvious
    char    relpath[MAXFILELEN];    // relative path for URL purposes
    char    chk[CHKKEYLEN+1];       // CHK this file is published as
    int     size;                   // size of file
    int     insertStatus;
    struct _sitefile *next;         // next file in chain
}
SiteFile;


typedef struct
{
    SiteFile    *fileSlot;
    char        *threadSlot;
    char        *metadata;          // malloc()'ed metadata to insert
    char        key[256];           // key URI, if inserting metadata
}
PutJob;
