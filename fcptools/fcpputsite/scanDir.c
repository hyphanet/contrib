/*
  insertFreesite.c - part of fcpputsite
  This module is the engine that does the inserting

  CopyLeft (c) 2001 by David McNab
*/

#include <sys/stat.h>

#include "ezFCPlib.h"
#include "fcpputsite.h"

#ifndef WINDOWS
#include <dirent.h>
#endif

/*
	IMPORTED DECLARATIONS
/*

extern int fcpLogCallback(int level, char *buf);

/*
  EXPORTED DECLARATIONS
*/

SiteFile *scan_dir(char *dirname, int *pNumFiles);

/*
	PRIVATE DECLARATIONS
*/

static SiteFile *scan_dir_recurse(char *dirname, SiteFile *curlist);

static int      numFiles;
static int      dirPrefixLen;

/*
	END DECLARATIONS
*/

SiteFile *scan_dir(char *dirname, int *pNumFiles)
{
    SiteFile *filelist;
    SiteFile *fileArray;
    int i;
#ifdef WINDOWS
    char *s;
#endif

    dirPrefixLen = strlen(dirname);
    numFiles = *pNumFiles = 0;
    filelist = scan_dir_recurse(dirname, NULL);

    /* were there any files in that dir? */
    if (filelist != NULL) {

			/* yes - convert the linked list into an array, and derive relative pathnames */
			fileArray = safeMalloc(sizeof(SiteFile) * numFiles);
			for (i = 0; i < numFiles; i++) {
				SiteFile *temp = filelist;
				memcpy(&fileArray[i], temp, sizeof(SiteFile));
				strcpy(fileArray[i].relpath, fileArray[i].filename + dirPrefixLen + 1);
				
				/* convert evil DOS backslashes into nice unix forward slashes */
#ifdef WINDOWS
				for (s = fileArray[i].relpath; *s != '\0'; s++)
					if (*s == '\\')
						*s = '/';
#endif
				filelist = filelist->next;
				free(temp);
			}
			*pNumFiles = numFiles;
			return fileArray;
    }
    else
			return NULL;
}


static SiteFile *scan_dir_recurse(char *dirname, SiteFile *curlist)
{
    SiteFile *filelist_temp;
    SiteFile *filelist;
    char subpath[256];
    struct stat fileStat;

#ifdef WINDOWS
    WIN32_FIND_DATA finddata;
    HANDLE dirhandle;
#else
    DIR *pDir;
    struct dirent *dirEntry;
#endif

    filelist = curlist;

    strcpy(subpath, dirname);
#ifdef WINDOWS
    strcat(subpath, "/");
    strcat(subpath, "*.*");
#endif

    /* Open the directory */
#ifdef WINDOWS
    if ((dirhandle = FindFirstFile(subpath, &finddata)) == NULL)
#else
    if ((pDir = opendir(dirname)) == NULL)
#endif
        return NULL;

    /* loop to read all the directory entries */
#ifdef WINDOWS
    while(FindNextFile(dirhandle, &finddata) != 0)
#else
    while ((dirEntry = readdir(pDir)) != NULL)
#endif
    {

			/* Skip parent */
#ifdef WINDOWS
        if (!strcmp(finddata.cFileName, ".."))
            continue;
#else
        if (!strcmp(dirEntry->d_name, "..") || !strcmp(dirEntry->d_name, "."))
            continue;
#endif

        /* Get attributes */
        strcpy(subpath, dirname);
        strcat(subpath, "/");
#ifdef WINDOWS
        strcat(subpath, finddata.cFileName);
#else
        strcat(subpath, dirEntry->d_name);
#endif
        stat(subpath, &fileStat);
        if (fileStat.st_mode & S_IFDIR)
        {
					/* directory - recurse into it */
            filelist_temp = scan_dir_recurse(subpath, filelist);
            filelist = filelist_temp;
        }
        else
        {
					/* normal file - append to list */
            filelist_temp = (SiteFile *)safeMalloc(sizeof(SiteFile));
            strcpy(filelist_temp->filename, subpath);
            filelist_temp->next = filelist;
            filelist_temp->chk[0] = '\0';
            filelist_temp->size = fileStat.st_size;
			filelist_temp->ctime = fileStat.st_ctime;
            filelist_temp->insertStatus = INSERT_FILE_WAITING;
            filelist = filelist_temp;

            /* update files count */
            numFiles++;
        }
    }

    /* Finished - close directory */
#ifdef WINDOWS
    FindClose(dirhandle);
#else
    closedir(pDir);
#endif

    return filelist;
} /* 'scan_dir_recurse()' */
