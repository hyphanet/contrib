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
 *	Global Variables data:                            *
 ******************************************************/
/*		strings, etc... */
extern const char szempty[];
extern const char szWindowClassName[];
extern const char szAppName[];

extern char szHomeDirectory[MAX_PATH];
extern char szjavawpath[JAVAWMAXLEN];	/* used to read Javaexec= definition out of FLaunch.ini */
extern char szfservecliexec[BUFLEN];		/* used to read Fservecli= definition out of FLaunch.ini */
extern char szFserveSeedExec[BUFLEN];		/* used to store "fserve.exe" */
extern char szFserveSeedCmdPre[BUFLEN];		/* used to store "--seed" */
extern char szFserveSeedCmdPost[BUFLEN];		/* used to store "" */
extern char szFserveExportCmdPre[BUFLEN];		/* used to store "--export" */
extern char szFserveExportCmdPost[BUFLEN];		/* used to store "" */

/*		flags, etc... */
extern FREENET_MODE nFreenetMode;
extern bool bUsingFProxy;
extern int nPriority;
extern int nPriorityClass;

/*		handles, etc... */
extern HINSTANCE hInstance;

/* Icons: */
extern HICON hHopsEnabledIcon;


/*		structures, etc... */
/*      systray structure: GLOBAL BECAUSE IT IS UPDATED BY/FROM MULTIPLE THREADS */
extern NOTIFYICONDATA note;

/*		function pointers  (for runtime linking of platform-specific APIs), etc... */
extern FARPROC InetIsOffline;


/*		lock objects for critical sections */
enum LOCKCONTEXTS
{
	NFREENETMODE=0,
	SYSTRAY=1,
	DIALOGBOXES=2,
	LOGFILEDIALOGBOX=3
};
extern HANDLE * LOCKOBJECTS[];


/******************************************************
 * Functions shared between threads:                  *
 ******************************************************/

/* helper function to select appropriate icon and tooltip text given current state */
extern void ModifyIcon(void);
/* helper function for critical (thread safe) sections */
extern void LOCK(enum LOCKCONTEXTS lockcontext);
extern void UNLOCK(enum LOCKCONTEXTS lockcontext);
/* allow icon thread to wait until fserve execution has stopped */
extern void WaitForFServe(void);

extern const int KAnimationSpeed;

#endif /*FREENET_TRAY_SHARED_DATA_H_INCLUDED*/