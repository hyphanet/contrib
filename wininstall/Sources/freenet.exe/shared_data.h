#ifndef FREENET_TRAY_SHARED_DATA_H_INCLUDED
#define FREENET_TRAY_SHARED_DATA_H_INCLUDED

#include "windows.h"
#include "shellapi.h"

/******************************************************
 * #defines:                                          *
 ******************************************************/

/* sizes */
#define JAVAWMAXLEN MAX_PATH*2+2048 /* taking NO chances over path length! */
#define BUFLEN 400

/* windows messages */
#define WM_BEGINMONITORING WM_USER+6
#define WM_ENDMONITORING WM_USER+7
#define WM_QUITMONITORINGTHREAD WM_USER+8
#define WM_TESTMESSAGE WM_USER+9 /* this is just a hint used to wait for the thread message queue to be created */

/******************************************************
 * Functions shared between threads:                  *
 ******************************************************/

/* helper function to select appropriate icon and tooltip text given current state */
void ModifyIcon(void);
/* helper function for critical (thread safe) sections */
void LOCK(enum LOCKCONTEXTS lockcontext);
void UNLOCK(enum LOCKCONTEXTS lockcontext);

/******************************************************
 *	Global Variables data:                            *
 ******************************************************/
/*		strings, etc... */
extern const char szempty[];
extern const char szWindowClassName[];
extern const char szAppName[];

extern char szjavapath[JAVAWMAXLEN];	/* used to read Javaexec= definition out of FLaunch.ini */
extern char szfservecliexec[BUFLEN];		/* used to read Fservecli= definition out of FLaunch.ini */

/*		flags, etc... */
extern FREENET_MODE nFreenetMode;

/*		handles, etc... */
extern PROCESS_INFORMATION prcInfo;		/* handles to java interpreter running freenet node - process handle, thread handle, and identifiers of both */

/*		structures, etc... */
/*      systray structure: GLOBAL BECAUSE IT IS UPDATED BY/FROM MULTIPLE THREADS */
extern NOTIFYICONDATA note;



/*		lock objects for critical sections */
enum LOCKCONTEXTS
{
	NFREENETMODE=0,
	SYSTRAY=1
};
extern HANDLE * LOCKOBJECTS[];

#endif /*FREENET_TRAY_SHARED_DATA_H_INCLUDED*/