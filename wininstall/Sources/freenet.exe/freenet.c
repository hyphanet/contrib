/*****************************************
 *  freenet.exe launcher                 *
 *  systray icon application             *
 *****************************************
 *  C-language version                   *
 *  Dave Hooper                          *
 *                                       *
 *  Based on assembly-language original  *
 *  by Sebastian Spaeth                  *
 *****************************************/

/*		freenet.c
		Dave Hooper (@1 May 2001)

		This file contains the entry point and the message pump */

//!!!ToDo: take the WinExec call for FCPProxy out once FProxy works again
#undef UNICODE
#include "windows.h"
#include "shellapi.h"
#include "winnls.h"
#include "rsrc.h"
#include "types.h"
#include "shared_data.h"
#include "freenet_tray.h"
#include "launchthread.h"

/******************************************************
 *  G L O B A L S                                     *
 ******************************************************
 *	Global Const data:                                *
 *		null-terminated strings, etc ...              *
 ******************************************************/

const char szempty[]="";

const char szWindowClassName[]="TrayIconFreenetClass";
const char szAppName[]="Freenet";

const char szgatewayURIdef[]="http://127.0.0.1:";

/* popup menu item text */
char szStopString[]="&Stop Freenet";
char szRestartString[]="Stop and &Restart Freenet";
char szStartString[]="&Start Freenet";
char szGatewayString[]="Open &Gateway";
char szConfigureString[]="&Configure";
char szExitString[]="E&xit";

/* tooltip text - matches order of FREENET_MODE enums */
const char *szFreenetTooltipText[]=
{
	"Freenet is stopped",
	"Freenet is running",
	"Freenet - No Gateway",
	"Freenet is running - No Internet connection",
	"Freenet is restarting... Please Wait",
	"Freenet is stopping... Please Wait",
	"Freenet is having Problems - check freenet.log",
	"Freenet stopped - No Internet connection"
};

/* string constants for use with the FLaunch.ini file */
const char szFreenetJar[]="Freenet.jar";
const char szflfile[]="./FLaunch.ini"; /* ie name of file */
const char szflsec[]="Freenet Launcher"; /* ie [Freenet Launcher] subsection text */
const char szjavakey[]="Javaexec"; /* ie Javaexec=java.exe */
const char szjavawkey[]="Javaw"; /* ie Javaw=javaw.exe */
const char szfservecliexeckey[]="fservecli"; /* ie Fservecli=Freenet.node.Node */
const char szfconfigexeckey[]="fconfig"; /* ie Fconfig=Freenet.node.gui.Config */
const char szfserveclidefaultexec[]="Freenet.node.gui.GUINode"; /* default for above */
const char szfconfigdefaultexec[]="Freenet.node.gui.Config freenet.ini"; /* default for above */


/* string constants for use with the freenet.ini file */
const char szfinifile[]="./freenet.ini"; /* ie name of file */
const char szfinisec[]="Freenet node"; /* ie [Freenet node] subsection text */
const char szfprxkey[]="fproxy.port"; /* ie fproxy.port=8081 */

/* for launching configuration dll */
const char szConfigDLLName[]="config.dll"; /* ie name of file */
const char szConfigProcName[]="InvokeConfig"; /* name of Config function(*/
typedef UINT (CALLBACK* LPFNDLLCONFIG)(HWND,BOOL); /* Format of the Configfunc*/
										



/******************************************************

 *	Global Variables data:                            *

 ******************************************************/
/*		strings, etc... */
char szjavapath[JAVAWMAXLEN];		/* used to read Javaexec= definition out of FLaunch.ini */
char szjavawpath[JAVAWMAXLEN];		/* used to read Javaw= definition out of FLaunch.ini */
char szfservecliexec[BUFLEN];			/* used to read Fservecli= definition out of FLaunch.ini */
char szfconfigexec[BUFLEN];			/* used to read Fconfig= definition out of FLaunch.ini */
char szgatewayURI[GATEWLEN];		/* used to store "http://127.0.0.1:8081" after the 8081 bit has been read from freenet.ini */

/*		flags, etc... */
FREENET_MODE nFreenetMode=FREENET_STOPPED;
bool bOpenGatewayOnStartup=false;	/* was freenet.exe called with the -open option?  */
UINT g_uintTaskbarExplodedMsg=0;	/* see MSDN - "Taskbar Creation Notification" */

/*		handles, etc... */
HANDLE hSemaphore=NULL;				/* unique handle used to guarantee only one instance of freenet.exe app is ever running at one time */
HANDLE hConfiguratorSemaphore=NULL;		/* mutex object used to ensure we never run two (or more!) copies of the configurator */
DWORD dwMonitorThreadId;			/* thread identifier for the background 'flasher' thread - global so we can PostThreadMessage to it */
HICON hHopsEnabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hHopsDisabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hAlertIcon=NULL;				/* icon handle - resource loaded during initialisation code */
HICON hRestartingIcon=NULL;			/* icon handle - resource loaded during initialisation code */
HICON hHopsNoGatewayIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hHopsNoInternetIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hThunderboltIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HMENU hPopupMenu=NULL;				/* handle to Popup Menu (right click on systray icon) */
HWND hWnd=NULL;						/* main window handle  */
HINSTANCE hInstance=NULL;			/* handle to the main application instance */
LPSTR lpszAppCommandLine;			/* global pointer to the command line passed to this app */


/*		lock objects for critical sections */
// enum LOCKCONTEXTS - defined in shared_data.h
HANDLE hnFreenetMode = NULL;  /* mutex for nFreenetMode */
HANDLE hSystray = NULL;	 /* mutex for systray (NOTIFYICONDATA) structure */
HANDLE * LOCKOBJECTS[] = {&hnFreenetMode, &hSystray, NULL};


/* icon array - must match order in FREENET_MODE in types.h */
const HICON *hFreenetIcons[] = 
{
	&hHopsDisabledIcon,
	&hHopsEnabledIcon,
	&hHopsNoGatewayIcon,
	&hHopsNoInternetIcon,
	&hRestartingIcon,
	&hHopsDisabledIcon,
	&hAlertIcon,
	&hHopsNoInternetIcon
};


/*		structures, etc... */
/*      systray structure: GLOBAL BECAUSE IT IS UPDATED BY/FROM MULTIPLE THREADS */
NOTIFYICONDATA note= {	sizeof(NOTIFYICONDATA),
						NULL,
						IDI_TRAY,
						NIF_ICON | NIF_MESSAGE | NIF_TIP,
						WM_SHELLNOTIFY,
						NULL,
						"" };


/******************************************************
 * F U N C T I O N S                                  *
 ******************************************************/

/******************************************************
 * Main:                                              *
 ******************************************************/

int PASCAL WinMain(HINSTANCE hInst, HINSTANCE hPrevInstance, LPSTR lpszCommandLine, int cmdShow) 
{
	MSG msg;
	DWORD dwGetMessageResult;
	WNDCLASSEX wc={	sizeof(wc), 
					CS_HREDRAW | CS_VREDRAW | CS_DBLCLKS,
					WndProc,
					0,
					0,
					(HANDLE) hInst, /* value of hInst */
					(HICON) NULL,
					(HCURSOR) NULL,
					(HBRUSH) NULL,
					NULL,
					szWindowClassName,
					(HICON) NULL};
	HANDLE hMonitorThread;

	
	/* make sure we have only one instance running */
    if ( !OnlyOneInstance() )
	{
		/* and if it is already running exit at once */
		return 0;
		/* Note, if app was already running, the call to OnlyOneInsyance will have
		   caused the existing running copy to load up the Gateway page */
	}
	

	/* make global copies of the hInstance and command line of this app: 
	   they could come in handy */
	hInstance = hInst;
	lpszAppCommandLine = lpszCommandLine;

	/* load in icons from resource table */

	hHopsEnabledIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_HOPSENABLED), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsDisabledIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_HOPSDISABLED), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hAlertIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_ALERT), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hRestartingIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_RESTARTING), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsNoGatewayIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_NOGWAY), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsNoInternetIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_NOINET), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hThunderboltIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_THUNDERBOLT), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);

	/* hConfiguratorSemaphore is used so we never load more than one instance of the configurator at a time */
	hConfiguratorSemaphore = CreateSemaphore(NULL,1,1,NULL);
	/* Lock objects: for critical sections on shared data, essentially */
	hnFreenetMode = CreateMutex(NULL, FALSE, NULL);
	hSystray = CreateMutex(NULL,FALSE,NULL);

	/* Create a separate thread to handle flashing the systray icon */
	hMonitorThread = CreateThread(NULL,1, MonitorThread, NULL, 0, &dwMonitorThreadId);
	/* wait for thread message to be created by MonitorThread:
	   we NEED to do this before we do post any messages to the thread message queue - 
	   so that BeginMonitoring works immediately on startup
	   and the app closes down correctly if it can't load an icon resource */

	while (PostThreadMessage(dwMonitorThreadId, WM_TESTMESSAGE, 0,0) == FALSE);


	/* did everything work so far? */
	if (hMonitorThread!=NULL &&
		hSystray!=NULL &&
		hConfiguratorSemaphore!=NULL && 
		hnFreenetMode!=NULL &&
		hRestartingIcon!=NULL &&
		hAlertIcon!=NULL &&
		hHopsDisabledIcon!=NULL &&
		hHopsEnabledIcon!=NULL && 
		hHopsNoGatewayIcon!=NULL && 
		hHopsNoInternetIcon!=NULL &&
		hThunderboltIcon!=NULL)
	{
		/*** main code: ***/
		Initialise();
		RegisterClassEx(&wc);
		hWnd = CreateWindowEx(	WS_EX_CLIENTEDGE,
								szWindowClassName,
								szAppName,
								WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX,
								CW_USEDEFAULT,
								CW_USEDEFAULT,
								0,
								0,
								NULL,
								NULL,
								hInstance,
								NULL );

		note.hWnd = (HWND)hWnd;
		Shell_NotifyIcon(NIM_ADD,&note);
		ModifyIcon();

		/* Starting FServe: we send a message to ourselves and it is handled later */
		SendMessage(hWnd, WM_COMMAND, IDM_STARTSTOP, 0);
		/* should we load the gateway page? */
		if (bOpenGatewayOnStartup)
		{
			Sleep(3000);
			if (nFreenetMode==FREENET_RUNNING)
			{
				/*  only do this if node is running FOR SURE  */
				SendMessage(hWnd, WM_COMMAND, IDM_GATEWAY, 0);
			}
		}
	
	    /* =================================== */
		/* Message pump:                       */
	    /* Loop until PostQuitMessage is sent  */
	    /* =================================== */
		while (1)
		{
			dwGetMessageResult = GetMessage(&msg,hWnd,0,0);
			if ( (dwGetMessageResult == 0xffffffff) || (dwGetMessageResult == 0) )
			{
				/* we end up here if there was a problem with GetMessage
				   we also end up here if PostQuitMessage is sent
				   either way we must break out of the while loop!  */
				break;
			}
			TranslateMessage(&msg);
			DispatchMessage(&msg);
		}
	}
	/*	when we get here it's because the message pump exited cleanly OR
		because earlier code failed (e.g. couldn't create monitor thread)  */
	/* CreateThread could have failed - there are some cleanup operations
		we can only perform when CreateThread WORKED:  */
	if (hMonitorThread)
	{
		PostThreadMessage(dwMonitorThreadId, WM_QUITMONITORINGTHREAD, 0,0);
		WaitForSingleObject(hMonitorThread, INFINITE);
		CloseHandle(hMonitorThread);
	}

	if (hWnd)
	{
		/* remove the icon from the system tray */
		Shell_NotifyIcon(NIM_DELETE, &note);
	}

	/* destroy objects by handle - not really necessary but it's nice to be tidy */
	if (hWnd) DestroyWindow(hWnd);
	if (hAlertIcon) DestroyIcon(hAlertIcon);
	if (hHopsEnabledIcon) DestroyIcon(hHopsEnabledIcon);
	if (hHopsDisabledIcon) DestroyIcon(hHopsDisabledIcon);
	if (hHopsNoGatewayIcon) DestroyIcon(hHopsNoGatewayIcon);
	if (hHopsNoInternetIcon) DestroyIcon(hHopsNoInternetIcon);
	if (hThunderboltIcon) DestroyIcon(hThunderboltIcon);

	if (hSemaphore) CloseHandle(hSemaphore);
	if (hSystray) CloseHandle(hSystray);

	if (hConfiguratorSemaphore) CloseHandle(hConfiguratorSemaphore);
	if (hnFreenetMode) CloseHandle(hnFreenetMode);

	return msg.wParam;
}




bool OnlyOneInstance()
{
	HWND hMainWnd;

	hSemaphore = CreateSemaphore(NULL,0,1,szWindowClassName);
	if (GetLastError() != ERROR_ALREADY_EXISTS)
	{
		/* this is the first (and only) instance of the app - return true */
		return true;
	}

	/* app is already running -- */
	/* -- so tell the app to launch the Gateway page */
	/* -- and we then exit */
	CloseHandle(hSemaphore);
	hMainWnd = FindWindow(szWindowClassName, NULL);
	if (hMainWnd)
	{
		hMainWnd = GetLastActivePopup(hMainWnd);
		SendMessage(hMainWnd,WM_COMMAND,IDM_GATEWAY,0);
	}
	return false;

}


/* One-time initialisation - ONLY DO THIS ONCE.  'refreshing settings' is performed by calling ReloadSettings */
void Initialise(void)
{
	char szbuffer[MAX_PATH*2+2048];
	char szShortPathbuffer[MAX_PATH+1];
	DWORD getenv;
	DWORD buffersize,dwStrlen;
	char dummy[1];
	char *szCLASSPATH;
	LPSTR szCommandLinePtr, szEndPointer;
	
	GetAppDirectory(szbuffer);
	SetCurrentDirectory(szbuffer);

//!!! TEMPORY STARTING FCPPROXY UNTIL WE GET A NATIVE FPROXY BACK
WinExec("fcpproxy.exe",SW_MINIMIZE);

	/* set up the environment variable for CLASSPATH: */
	lstrcat(szbuffer,"\\");
	lstrcat(szbuffer,szFreenetJar);
	GetShortPathName(szbuffer, szShortPathbuffer, sizeof(szShortPathbuffer)-1);
	dwStrlen = lstrlen(szShortPathbuffer);
	/* buffer now holds, e.g., "G:\Progra~1\Freenet\Freenet.jar" 
		where G:\Program Files\Freenet in this example is the current directory */
	/* dwStrlen equals the length of this string */
	/* bump up the size of dwStrlen to account for the fact that we need to add
		a semicolon to the string later, and a NUL character too ...
		... also increment a bit more for good measure */
	dwStrlen+=5;

	/* try and APPEND this string to the current value of CLASSPATH - do this by
		first reading the current value of CLASSPATH - and we'll need to dynamically
		allocate memory until we're sure we have a big enough buffer to do this */
	buffersize=0;
	szCLASSPATH=NULL;
	// first call to determine what size buffer we need in to read in current value of CLASSPATH
	// Cannot use NULL for buffer pointer.  so using a 'dummy' pointer (in this case it 
	// happens to be a pointer to a single byte)
	getenv=GetEnvironmentVariable("CLASSPATH",dummy,0);

	while ( (getenv!=0) && (getenv+dwStrlen>buffersize) )
	{
		// initially, szCLASSPATH = NULL.  however, if we've been
		// in this loop before, deallocate memory first before reallocating it
		if (szCLASSPATH!=NULL)
		{
			GlobalFree(szCLASSPATH);
			szCLASSPATH=NULL;
		}

		// allocate as much memory as we think we need - 
		// whatever GetEnvironmentVariable says we need plus the
		// size of the string we want to add
		buffersize = getenv+dwStrlen;
		szCLASSPATH=(char *)GlobalAlloc(GPTR,buffersize * sizeof(char) );

		// did the allocation succeed?
		if (szCLASSPATH==NULL)
		{
		// allocation failed - nothing we can do (Windows must be low on resources)
			break;
		}
		// else, loop round and try the GetEnvironmentVariable call with the new
		// buffer size and see if it can fit this time
			
		getenv=GetEnvironmentVariable("CLASSPATH",szCLASSPATH,buffersize);
	}

	if (getenv==0)
	{
		// environment variable doesn't already exist - so create it
		// and set its value to G:\Program Files\Freenet\Freenet.jar
		// success.
		SetEnvironmentVariable("CLASSPATH",szShortPathbuffer);
	}
	else if ( (szCLASSPATH!=NULL) && (getenv+dwStrlen<=buffersize) )
	{
		// allocation succeeded - our buffer is more than big enough to store
		// the result of GetEnvironmentVariable AND the thing we want to append
		// - therefore GetEnvironmentVariable has succeeded and we now have a
		// copy of the current value of CLASSPATH in szCLASSPATH
		// we can now add the string we need onto the end of the current value
		// of CLASSPATH, separated using a semicolon
		lstrcat(szCLASSPATH,";");
		lstrcat(szCLASSPATH,szShortPathbuffer);
		SetEnvironmentVariable("CLASSPATH",szCLASSPATH);
	}
	// else - allocation failed - Windows must be low on resources.
	
	if (szCLASSPATH)
	{
		// free the memory we used
		if (szCLASSPATH!=NULL)
		{
			GlobalFree(szCLASSPATH);
			szCLASSPATH=NULL;
		}
	}


	/*  Load in settings from flaunch.ini etc.  */
	ReloadSettings();

	
	/* Parse the application command line: */
	/* Currently the following command-line arguments to freenet,exe are supported:
				-open			open the gateway page on startup
	*/
	szCommandLinePtr = lpszAppCommandLine;
	if (szCommandLinePtr)
	{
		if (*szCommandLinePtr)
		{
			szCommandLinePtr = SkipSpace(szCommandLinePtr);
			/* Get the first token (it will be the executable name) */
			/* This also returns a pointer to the end of the command line */
			GetFirstToken(szCommandLinePtr, &szEndPointer);
			/* and ignore it ! now - get the next tokens ...   */
			/* while there's still tokens on the command line: */
			do
			{
				if (CompareString(	LOCALE_SYSTEM_DEFAULT,
									NORM_IGNORECASE | SORT_STRINGSORT | NORM_IGNOREWIDTH,
									szCommandLinePtr, 
									-1,
									"-open",
									-1) == 2 )
				{
					/* Confused?  CompareString returns '2' if the strings are identical */
					bOpenGatewayOnStartup=true;
				}

				szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
			}
			while (szCommandLinePtr != NULL);

		}
	}
}




void ReloadSettings(void)
{
	char szbuffer[MAX_PATH*2+2048];

	/* set the current directory to the path name so we can use GetProfile commands in the current directory */
	GetAppDirectory(szbuffer);	
	SetCurrentDirectory(szbuffer);

	/* Get the javaw line and if that isn't there fall back on the javaexec line ... */
	/* (Note, javaw.exe is used in preference to java.exe) */
	if (!GetPrivateProfileString(szflsec, szjavawkey, szempty, szbuffer, JAVAWMAXLEN, szflfile))
	{
		GetPrivateProfileString(szflsec, szjavakey, szempty, szbuffer, JAVAWMAXLEN, szflfile);
	}
	/* .. and convert to short filename format, because we want one SIMPLE string for javaw.exe path */
	GetShortPathName(szbuffer, szjavawpath, sizeof(szjavawpath) );


	/* Get the javaexec line ...*/
	if (!GetPrivateProfileString(szflsec, szjavakey, szempty, szbuffer, JAVAWMAXLEN, szflfile))
	{
		GetPrivateProfileString(szflsec, szjavawkey, szempty, szbuffer, JAVAWMAXLEN, szflfile);
	}
	/* ... and convert to short filename format, because we want one SIMPLE string for java.exe path */
	GetShortPathName(szbuffer, szjavapath, sizeof(szjavapath) );



	/* get the fservecli launch string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfservecliexeckey, szfserveclidefaultexec, szfservecliexec, BUFLEN, szflfile);

	/* get the fconfig launch string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfconfigexeckey, szfconfigdefaultexec, szfconfigexec, BUFLEN, szflfile);

	/* form the gateway string - the "http://127.0.0.1:" is constant */
	lstrcpy(szgatewayURI, szgatewayURIdef);
	/* then append the port number of fproxy, looked up from freenet.ini */
	GetPrivateProfileString(szfinisec, szfprxkey, szempty, szgatewayURI+lstrlen(szgatewayURI), 6, szfinifile);

}


void GetAppDirectory(char * szbuffer)
{
	/* Get path this executable is running from, and how long the path is in characters */
	DWORD nCharacters = GetModuleFileName(NULL, szbuffer, BUFLEN);
	/* chop off the end part as this will be "freenet.exe"
	   - i.e. rewind from the end to find the first \ character
	   and set it to null - this chops off the executable name leaving just the path name */
	char * bufferpointer = szbuffer+nCharacters;
	while (nCharacters--)
	{
		if (*bufferpointer=='\\')
		{
			// special case - if just a bare drive then need terminating back slash
			// eg D:\ rather than D:
			if ( (bufferpointer>szbuffer) &&
				 (bufferpointer<szbuffer+sizeof(szbuffer)) &&
				 (*(bufferpointer-1)==':') )
			{
				bufferpointer++;
			}
			*bufferpointer='\0';
			break;
		}
		--bufferpointer;
	}
}


/* launches the java interpreter and starts the freenet node running */
void StartFserve(void)
{
	/* tells the monitor thread to load up and run the freenet node, then sit and
		WaitFor...() until prcInfo.hProcess dies...  after this the monitor thread
		will set up a timeout-driven loop to blink the freenet icon between 'nohops'
		and 'alarm' icons - this state would then remains until ExitFserve is called.
		Note - calling StartFserve again is ok - the monitor thread will attempt to
		reload and restart the freenet node - all being well (assuming the end user
		has fixed whatever it was that caused the node to die last time) it will
		load and run correctly */
	PostThreadMessage(dwMonitorThreadId, WM_BEGINMONITORING, 0,0);
}


/* stops the freenet node running */
void ExitFserve(void)
{
	/*	Tells the monitor thread to kill the freenet node... the thread will then
		stop WaitFor..() on process handle and set state to FREENET_STOPPED thereby
		killing the timeout-driven flasing-icon loop had it been running. */
	PostThreadMessage(dwMonitorThreadId, WM_ENDMONITORING, 0,0);
}



/*	recycles freenet node - stop followed by restart.  Note nFreenetMode is set to
	FREENET_RESTARTING for the duration of the stop.  This brings about a different
	systray icon and tooltip used to uniquely identify this condition.
	RestartFserve is currently only called (automatically) after the user has launched
	the configuration tool */
void RestartFserve(void)
{
	LOCK(NFREENETMODE);
	if (nFreenetMode==FREENET_RUNNING)
	{
		nFreenetMode=FREENET_RESTARTING;
		UNLOCK(NFREENETMODE);
		ModifyIcon();
		ExitFserve();
		ReloadSettings();
		StartFserve();
	}
	else
	{
		UNLOCK(NFREENETMODE);
	}
}



/* helper function to set appropriate Hops icon in systray at appropriate times

   also sets the tooltip text appropriately */

void ModifyIcon(void)

{

	LOCK(SYSTRAY);
	switch (nFreenetMode)
	{
	case FREENET_CANNOT_START:
		/*	flashing icon behaviour - each call generates a
			different icon depending on the current one		*/
		if (note.hIcon == hAlertIcon)
		{
			note.hIcon = hHopsDisabledIcon;
		}
		else
		{
			note.hIcon = hAlertIcon;
		}
		break;

	default:
		note.hIcon = *(hFreenetIcons[nFreenetMode]);
		break;
	}
	
	lstrcpy(note.szTip, szFreenetTooltipText[nFreenetMode]);
	Shell_NotifyIcon(NIM_MODIFY,&note);
	UNLOCK(SYSTRAY);
}



/*	Launches the configuration DLL, or, if that isn't available, the standard
	freenet node java configurator */
void StartConfig(void)
{
	UINT result;
	HINSTANCE hConfigDLL = LoadLibrary(szConfigDLLName);
	LPFNDLLCONFIG /*LPCONFIGPROC*/ pProcAddress = NULL;

	if (hConfigDLL)
	{
		pProcAddress = (LPFNDLLCONFIG)GetProcAddress(hConfigDLL,szConfigProcName);
		if (pProcAddress)
		{
			result = pProcAddress(NULL, FALSE);	/*BOOL InvokeConfig(HWND parentHwnd,BOOL Wizardmode);*/
			// when we get here configuration is complete
			// need to restart the server
			if (!result) PostMessage(hWnd, WM_COMMAND, IDM_RESTART, 0);
			// configuration complete - release the 'configurator' mutex
			ReleaseSemaphore(hConfiguratorSemaphore,1,NULL);
		}
	}

	/* Unload the DLL */
	FreeLibrary(hConfigDLL);

	if ((!hConfigDLL) || (!pProcAddress) )
	{
		// couldn't load DLL, or, couldn't find Config function within DLL.
		// Fall back to the original Java configurator
		StartConfigOrig();
	}
}


//	dwJavaConfigProcId is global so that we can use it to give the focus to the configurator

//	window if the user clicks on 'Configure' a second time

DWORD dwJavaConfigProcId=0;

void StartConfigOrig(void)

{

	STARTUPINFO StartConfigInfo={sizeof(STARTUPINFO),

							NULL,NULL,NULL,

							0,0,0,0,0,0,0,

							STARTF_USESHOWWINDOW,

							SW_NORMAL,

							0,NULL,

							NULL,NULL,NULL};

	PROCESS_INFORMATION prcConfigInfo;


	// commenting stuff out, as we are calling an external app and not a java class
	// for Java classes use szjavawpath and szexecbuf again when creating the process
	// char szexecbuf[sizeof(szjavawpath)+2+sizeof(szfconfigexec)];
	// lstrcpy(szexecbuf, szjavawpath);
	// lstrcat(szexecbuf, " ");
	// lstrcat(szexecbuf, szfconfigexec); 

	if (!CreateProcess((char*)szfconfigexec, NULL, NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartConfigInfo, &prcConfigInfo) )

	{

		MessageBox(NULL, "Unable to launch configurator for Freenet", "Cannot Config", MB_OK | MB_ICONERROR | MB_TASKMODAL);

	}

	else

	{

		DWORD dwThreadId;

		HANDLE hJavaConfigThread = CreateThread(NULL,1,WaitForJavaConfigurator,(LPVOID)(prcConfigInfo.hProcess),0,&dwThreadId);

		dwJavaConfigProcId=prcConfigInfo.dwProcessId;

		CloseHandle(hJavaConfigThread);

		CloseHandle(prcConfigInfo.hThread);

	}

}





DWORD WINAPI WaitForJavaConfigurator(LPVOID lpvprochandle)

{

	HANDLE hJavaConfig = (HANDLE)lpvprochandle;



	WaitForSingleObject(hJavaConfig, INFINITE);

	CloseHandle(hJavaConfig);

	// configuration complete - release the 'configurator' mutex

	ReleaseSemaphore(hConfiguratorSemaphore,1,NULL);

	// need to restart the server

	PostMessage(hWnd, WM_COMMAND, IDM_RESTART, 0);



	return 0;

}









/*	WindowProc - implements the popup menu, the mouse handling, and

	the obvious windows events (WM_CREATE / WM_DESTROY)	*/

MENUITEMINFO gatewayitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_DEFAULT | MFS_GRAYED, IDM_GATEWAY, NULL,NULL,NULL,0,szGatewayString, 0 };

MENUITEMINFO startstopitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_STARTSTOP, NULL,NULL,NULL,0,szStartString, 0 };

MENUITEMINFO restartitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_GRAYED, IDM_RESTART, NULL,NULL,NULL,0,szRestartString, 0 };

MENUITEMINFO configitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_CONFIGURE, NULL,NULL,NULL,0,szConfigureString, 0 };

MENUITEMINFO exititem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_EXIT, NULL,NULL,NULL,0,szExitString, 0 };

MENUITEMINFO separatoritem = {sizeof(MENUITEMINFO), MIIM_TYPE, MFT_SEPARATOR, 0, IDM_GATEWAY, NULL,NULL,NULL,0,NULL, 0 };



LRESULT CALLBACK WndProc( HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)

{

	POINT mousepos;



	GetCursorPos(&mousepos);



	switch (message)

	{

		case WM_CREATE:



			hPopupMenu = CreatePopupMenu();

			InsertMenuItem(hPopupMenu, 0, TRUE, &gatewayitem);

			InsertMenuItem(hPopupMenu, 1, TRUE, &startstopitem);

			InsertMenuItem(hPopupMenu, 2, TRUE, &restartitem);

			InsertMenuItem(hPopupMenu, 3, TRUE, &configitem);

			InsertMenuItem(hPopupMenu, 4, TRUE, &separatoritem);

			InsertMenuItem(hPopupMenu, 5, TRUE, &exititem);

		       

			/* see MSDN - we need to do this to safeguard against the systray icon

			   blowing itself away if / when  explorer.exe  eats itself */

			g_uintTaskbarExplodedMsg = RegisterWindowMessage(TEXT("TaskbarCreated"));



			break;



		case WM_DESTROY:



			DestroyMenu(hPopupMenu);

			break;





		case WM_COMMAND:

			{

				if(lParam==0) // msg comes from menu

				{

					switch (wParam)

					{

						case IDM_GATEWAY: // menu choice Open Gateway (or doubleclick on icon)

							/* is the Node running? - if no, then don't open the Gateway page */

							if (nFreenetMode==FREENET_RUNNING) 

							{

								ShellExecute(hWnd, "open", szgatewayURI, NULL, NULL, SW_SHOWNORMAL);

							}

							break;



						case IDM_STARTSTOP: // menu choice Start/Stop FProxy

						

							/* is the server up? */

							switch (nFreenetMode)

							{

								case FREENET_RUNNING:

								case FREENET_RESTARTING:

									// yes - stop server

									ExitFserve();

									break;



								case FREENET_STOPPED:

								case FREENET_STOPPING:

								case FREENET_CANNOT_START:

								default:

									// no - start the server

									StartFserve();

									break;

							}

							break;



						case IDM_CONFIGURE: // menu choice configure - run the Config tool

							

							// is the configurator already running?

							if (WaitForSingleObject(hConfiguratorSemaphore, 1) == WAIT_TIMEOUT)

							{

								// configurator still running - give it the focus

								// following lines work for the Config.dll configurator

								HWND ConfiguratorWindow = FindWindow(NULL, "Freenet Configurator");

								SetForegroundWindow(ConfiguratorWindow);

								// following lines work for the Java configurator

								EnumWindows(SetFocusByProcId, (LPARAM)dwJavaConfigProcId);

								break;

							}

							else

							{

								// we now 'own' the configurator semaphore object

								StartConfig();

								/* StartConfig will automatically cause RestartFserve() to run when completed */

								/* it will also release the semaphore */

							}



							break;



						case IDM_RESTART: // menu choice restart - essentially Stop followed by Start



							// only applicable if node is running

							if (nFreenetMode==FREENET_RUNNING)

							{

								RestartFserve();

							}

							break;



						case IDM_EXIT: // otherwise menu choice exit, exiting

						default:

							// Closing down FServe:  Don't "DestroyWindow" here or

							// our message pump will die horribly... instead we destroy the hWnd

							// in the main thread, after the PostQuitMessage has caused the

							// message pump to stop running

							ExitFserve();

							PostQuitMessage(0);   // this is the end...

							break;



					} // switch (wParam)



				} // if lParam==0

			}

			break;





		case WM_SHELLNOTIFY:



			if (wParam==IDI_TRAY)

			{

				switch (lParam)

				{

					case WM_RBUTTONDOWN :

						SetForegroundWindow(hWnd);

						switch (nFreenetMode)

						{

							case FREENET_RUNNING:

								/* Node is running - can stop it */

								/* Can view gateway */

								/* Can 'restart' it */

								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND|MF_ENABLED,IDM_STARTSTOP,szStopString);

								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_ENABLED,IDM_GATEWAY,szGatewayString);

								ModifyMenu(hPopupMenu,IDM_RESTART,MF_BYCOMMAND|MF_ENABLED,IDM_RESTART,szRestartString);

								break;



							case FREENET_STOPPED:

							case FREENET_CANNOT_START:

							case FREENET_STOPPING:

								/* Node is stopped */

								/* or Node is stopping - but I'm allowing you to queue up a restart command */

								/* Cannot view gateway */

								/* Cannot 'restart' it */

								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND|MF_ENABLED,IDM_STARTSTOP,szStartString);

								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_GRAYED,IDM_GATEWAY,szGatewayString);

								ModifyMenu(hPopupMenu,IDM_RESTART,MF_BYCOMMAND|MF_GRAYED,IDM_RESTART,szRestartString);

								break;



							case FREENET_RESTARTING:

							default:

								/* Node is restarting - essentially a different kind of 'stopping' with

								   additional state.  I'm allowing you to queue up a stop command */

								/* Cannot view gateway */

								/* Cannot 'restart' it more */

								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND|MF_ENABLED,IDM_STARTSTOP,szStartString);

								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_GRAYED,IDM_GATEWAY,szGatewayString);

								ModifyMenu(hPopupMenu,IDM_RESTART,MF_BYCOMMAND|MF_GRAYED,IDM_RESTART,szRestartString);

								break;

						}



						TrackPopupMenu(hPopupMenu,TPM_RIGHTALIGN,mousepos.x,mousepos.y,0,hWnd,NULL);

						PostMessage(hWnd,WM_NULL,0,0);

						break;



					case WM_LBUTTONDBLCLK:

						SendMessage(hWnd,WM_COMMAND,IDM_GATEWAY,0); // this opens the Gateway page

						break;



					case WM_LBUTTONDOWN :

						break;



				}

			}

            break;



    	default:

			

			/* see MSDN - we need to do this to safeguard against the systray icon

			   blowing itself away if / when  explorer.exe  eats itself */

			if (message == g_uintTaskbarExplodedMsg)

			{

				LOCK(SYSTRAY);

				Shell_NotifyIcon(NIM_ADD,&note);

				ModifyIcon();

				UNLOCK(SYSTRAY);

			}

						

			// Let windows handle all messages we choose to ignore.

			return(DefWindowProc(hWnd, message, wParam, lParam));



	} // switch (message)



	return 0;

}









/* If this looks confusing see article Q178893 in MSDN.

   All I'm doing is enumerating ALL the top-level windows in the system and matching

   against the Java Config process Id.  For each window enumerated I call the

   SetForegroundWindow API function */

BOOL CALLBACK SetFocusByProcId(HWND hWnd, LPARAM lParam)

{

	/* called for each window in system */

	/* we're using it to hunt for windows created by the Java Configurator process */

	/* First find out if this window was created by the Java Configurator process: */

	DWORD dwThreadId;

	GetWindowThreadProcessId(hWnd, &dwThreadId);

	if (dwThreadId != (DWORD)lParam)

	{

		/* This window was NOT created by the process... keep enumerating */

		return TRUE;

	}



	/* This window WAS created by the process */

	SetForegroundWindow(hWnd);



	/* return true to keep enumerating - there may be more windows */

	return TRUE;

}

 









/*****************************************/

/* command-line parsing helper routines: */

/*****************************************/



void GetFirstToken(LPSTR szCurrentPointer, LPSTR *pszEndPointer)

{

	/* get a pointer to the END of the command line */

	LPSTR szEndPointer;

	szEndPointer = szCurrentPointer;

	while (*szEndPointer++)

	{

		/* just loop, ending when we hit the NUL at the end of the string */

	}

	*pszEndPointer = szEndPointer;



	/* find first space character - after obeying rules for command line parsing -

	   and set this character to a NUL char i.e. '\0'  */

	GetToken(szCurrentPointer);

}



void GetToken(LPSTR szCurrentPointer)

{

	enum eCurrentState{LOOKING,GOTBACKSLASH,GOTOPENQUOTE,GOTBOTH};

	enum eCurrentState nState = LOOKING;



	while(*szCurrentPointer != '\0')

	{

		switch(nState)

		{

		case LOOKING:

		default:

			/* normal mode of operation */

			switch (*szCurrentPointer)

			{

			case ' ':

			case '\t':

				*szCurrentPointer='\0';

				return;



			case '\\':

				nState = GOTBACKSLASH;

				break;



			case '\"':

				nState=GOTOPENQUOTE;

				break;

			

			default:

				break;

			}

			break;



		case GOTBACKSLASH:

			/* got a \ character - if next character is a " then we DON'T go into " parsing mode!

			   in fact just consume this character  */

			nState = LOOKING;

			break;



		case GOTOPENQUOTE:

			switch (*szCurrentPointer)

			{

			case '\"':

				/* its the closing quote - go back to looking */

				nState = LOOKING;

				break;

			case '\\':

				/* it's a backslash within a quotes ... */

				nState = GOTBOTH;

				break;

			default:

				break;

			}

			/* else stay in 'got open quote' mode and just consume characters */

			break;



		case GOTBOTH:

			/* an escaped character within an enquoted string

			   consume the character and go back to GOTOPENQUOTE */

			nState = GOTOPENQUOTE;

			break;





		}



		szCurrentPointer++;

	

	}



}





LPSTR GetNextToken(LPSTR szCurrentPointer, const LPSTR szEndPointer)

{

	// fast-forward to NUL character at end of string

	while (*szCurrentPointer++)

	{

		// just loop

	}



	// szCurrentPointer is now pointing to the character AFTER the NUL character

	if (szCurrentPointer>=szEndPointer)

	{

		// we've hit the end of the provided command line - abort

		return NULL;

	}



	// else...

	// we are NOT YET at the end of the command line - so return something useful



	// skip any 'space' charaters...

	szCurrentPointer = SkipSpace(szCurrentPointer);



	// ... and find the next SPACE character and turn it into a NUL,

	// and return the START pointer... OH LOOK WE CAN USE GETFIRSTTOKEN again!

	GetToken(szCurrentPointer);

	return szCurrentPointer;

}





LPSTR SkipSpace(LPSTR szString)

{

	/* given a pointer into a string,

	   returns a pointer that points to the first non-space character of that string

	   i.e. skips ' ' and '\t' characters  */

	do

	{

		if (*szString!=' ' && *szString!='\t')

		{

			return szString;

		}

		szString++;

	} while (1);

}









void LOCK(enum LOCKCONTEXTS lockcontext)

{

	WaitForSingleObject(*(LOCKOBJECTS[lockcontext]), INFINITE);

}



void UNLOCK(enum LOCKCONTEXTS lockcontext)

{

	ReleaseMutex(*(LOCKOBJECTS[lockcontext]));

}