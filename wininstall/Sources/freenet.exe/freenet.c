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


#undef UNICODE
#include "stdafx.h"
#include "freenet_tray.h"
#include "launchthread.h"
#include "refs.h"
#include "logfile.h"
#include "about.h"
#include "stdlib.h" // for atol

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
char szConfigureString[]="&Configure...";
char szAboutString[]="&About";
char szExitString[]="E&xit";
char szImportString[]="&Import Refs";
char szExportString[]="&Export Refs";
char szViewLogFileString[]="&View Logfile...";

/* tooltip text - matches order of FREENET_MODE enums */
const char *szFreenetTooltipText[]=
{
	"Freenet is Stopped",
	"Freenet is Running",
	"Freenet is Restarting... Please Wait",
	"Freenet is Stopping... Please Wait",
	"Freenet is Starting... Please Wait",
	"Freenet is Having Problems - check freenet.log"
};

/* string constants for use with the FLaunch.ini file */
const char szFreenetJar[]="freenet.jar";         /* name of the freenet.jar to be appended to the classpath */
const char szFreenetExtJar[]="freenet-ext.jar";  /* name of an external dependency .jar to be appended to the classpath */
const char szflfile[]="./FLaunch.ini";           /* name of the ini file */
const char szflsec[]="Freenet Launcher";         /* ie [Freenet Launcher] subsection text */
const char szjavakey[]="Javaexec"; /* ie Javaexec=java.exe */
const char szjavawkey[]="Javaw"; /* ie Javaw=javaw.exe */
const char szprioritykey[]="Priority"; /* ie Priority=0 */
const int  nDefaultPriority = THREAD_PRIORITY_IDLE;
const char szpriorityclasskey[]="PriorityClass"; /* ie PriorityClass=32 */
const int  nDefaultPriorityClass = IDLE_PRIORITY_CLASS;
const char szfservecliexeckey[]="fservecli"; /* ie Fservecli=Freenet.node.Main */
const char szfserveclidefaultexec[]="freenet.node.Main"; /* default for above */
const char szfconfigexeckey[]="fconfig"; /* ie Fconfig=Freenet.node.gui.Config */
const char szfconfigdefaultexec[]="NodeConfig.exe"; /* default for above */
const char szfconfigisjavakey[]="fconfigUseJava"; /* ie fconfigUseJava=0 to run fconfigexec as an app, or fconfigUseJava=1 to run "java %fconfigexec%" */
const char szfservecliconfigkey[]="cfgnode"; /* ie cfgnode=freenet.config.Setup */
const char szfserveclidefaultconfig[]="freenet.config.Setup";
const char szfseedcmdprekey[]="seedcmdpre"; /* ie seedcmdpre="--seed" */
const char szfseeddefaultprecmd[]="--seed";
const char szfseedcmdpostkey[]="seedcmdpost"; /* ie seedcmdpost="" */
const char szfseeddefaultpostcmd[]="";
const char szfseedexportcmdprekey[]="exportcmdpre";  /* ie exportcmdpre="--export" */
const char szfseeddefaultexportprecmd[]="--export";
const char szfseedexportcmdpostkey[]="exportcmdpost"; /* ie exportcmdpost="" */
const char szfseeddefaultexportpostcmd[]="";
const char* szTempDirs[]={"FECTempDir","mainport.params.servlet.1.params.tempDir","tempDir",NULL}; /* ie FECTempDir=C:\windows\temp\freenet , etc*/ 
const char szClasspathExtraKey[]="Classpath Extra"; /* ie Classpath Extra=F:\Something\Whatsit.jar;G:\Something Else\Doodad.jar */

/* string constants for use with the freenet.ini file */
const char szfinifile[]="./freenet.ini"; /* ie name of file */
const char szfinisec[]="Freenet node"; /* ie [Freenet node] subsection text */
const char szfprxkey[]="mainport.port"; /* ie mainport.port=8888 */
const char szfprxdefaultvalue[]="8888"; 

/* for launching configuration dll */
const char szConfigDLLName[]="config.dll"; /* ie name of file */
const char szConfigProcName[]="InvokeConfig"; /* name of Config function */
typedef UINT (CALLBACK * LPFNDLLCONFIG)(HWND,BOOL); /* Format of the Configfunc*/


/* Command line options */
enum eCommands
{
	COMMAND_OPEN=0,
	COMMAND_IMPORT,
	COMMAND_EXPORT,
	COMMAND_HELP,
	COMMAND_CREATECONFIG,
	COMMAND_MERGECONFIG,
	NUMBER_OF_COMMANDS
};
const char *szCommandText[] = {"-open", "-import", "-export", "-seed", "-help", "-createconfig", "-mergeconfig",NULL };
const int CommandActions[] = {COMMAND_OPEN, COMMAND_IMPORT, COMMAND_EXPORT, COMMAND_IMPORT, COMMAND_HELP, COMMAND_CREATECONFIG, COMMAND_MERGECONFIG};


/******************************************************
 *	Global Variables data:                            *
 ******************************************************/
/*		strings, etc... */
char szHomeDirectory[MAX_PATH];		/* used to store the directory in which freenet.exe, etcm are located */

char szjavawpath[JAVAWMAXLEN];		/* used to read Javaw= definition out of FLaunch.ini */
char szfservecliexec[BUFLEN];		/* used to read Fservecli= definition out of FLaunch.ini */
char szfconfigexec[BUFLEN];			/* used to read Fconfig= definition out of FLaunch.ini */
char szFserveConfig[BUFLEN];	    /* used to read cfgnode= definition out of FLaunch.ini */
char szFserveSeedCmdPre[BUFLEN];	/* used to store "--seed" */
char szFserveSeedCmdPost[BUFLEN];	/* used to store "" */
char szFserveExportCmdPre[BUFLEN];	/* used to store "--export" */
char szFserveExportCmdPost[BUFLEN];	/* used to store "" */
char szfprxPort[6];                 /* used to store just the fproxy port (Default 8888) from freenet.ini bit of above */
WORD fprxPort;                      /* Decimal version of Port number as read from freenet.ini */
char szgatewayURI[GATEWLEN];		/* used to store "http://127.0.0.1:8888" after the fproxy port has been read from freenet.ini */
char szClasspathExtra[65535];       /* used to store additional jars to put into the CLASSPATH env variable */

/*		flags, etc... */
FREENET_MODE nFreenetMode=FREENET_STOPPED;
bool bOpenGatewayOnStartup=false;	/* was freenet.exe called with the -open option?  */
UINT g_uintTaskbarExplodedMsg=0;	/* see MSDN - "Taskbar Creation Notification" */
bool bFConfigExecUseJava=false;
int nPriority=THREAD_PRIORITY_IDLE;  /* the priority of the jre for fred */
int nPriorityClass=IDLE_PRIORITY_CLASS;  /* the priority class of the jre for fred */
const int GANIMATIONFRAMES=4;
int g_n_AnimationCounter=0;
const int KAnimationSpeed = 300; /* ms */

/*		handles, etc... */
HANDLE hSemaphore=NULL;				/* unique handle used to guarantee only one instance of freenet.exe app is ever running at one time */
HANDLE hConfiguratorSemaphore=NULL;		/* mutex object used to ensure we never run two (or more!) copies of the configurator */
DWORD dwMonitorThreadId;			/* thread identifier for the background 'flasher' thread - global so we can PostThreadMessage to it */
HICON hHopsEnabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hHopsDisabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hAlertIcon=NULL;				/* icon handle - resource loaded during initialisation code */
HICON hHopsNoGatewayIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hHopsNoInternetIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hThunderboltIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hStartingIcon[4]={NULL,NULL,NULL,NULL}; // GANIMATIONFRAMES
HICON hStoppingIcon[4]={NULL,NULL,NULL,NULL};
HMENU hPopupMenu=NULL;				/* handle to Popup Menu (right click on systray icon) */
HWND hWnd=NULL;						/* main window handle  */
HINSTANCE hInstance=NULL;			/* handle to the main application instance */
LPSTR lpszAppCommandLine;			/* global pointer to the command line passed to this app */

HANDLE hURLDLL=NULL;				/* DLL handle for loading the function "InetIsOffline" at runtime */
FARPROC InetIsOffline=NULL;			/* function pointer for "InetIsOffline" function */


/*		lock objects for critical sections */
// enum LOCKCONTEXTS - defined in shared_data.h
HANDLE hnFreenetMode = NULL;  /* mutex for nFreenetMode */
HANDLE hSystray = NULL;	 /* mutex for systray (NOTIFYICONDATA) structure */
HANDLE hDialogBoxes = NULL;  /* mutex for controlling whether to create an Import (or Export) Refs dialog, or set focus to an existing one */
HANDLE hLogfileViewerDialogBox = NULL;  /* mutex for controlling whether to create a Logfile Viewer dialog, or set focus to an existing one */
HANDLE hAboutDialogBox = NULL;  /* mutex for controlling whether to create an About box, or set focus to an existing one */
HANDLE * LOCKOBJECTS[] = {&hnFreenetMode, &hSystray, &hDialogBoxes, &hLogfileViewerDialogBox, &hAboutDialogBox, NULL};


/* icon array - must match order in FREENET_MODE in types.h */
const HICON *hFreenetIcons[] = 
{
	&hHopsDisabledIcon,
	&hHopsEnabledIcon,
	hStoppingIcon,
	hStoppingIcon,
	hStartingIcon,
	&hAlertIcon
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
	int wsaError=0;
	WSADATA wsadata;
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
	HANDLE hMonitorThread=NULL;

	/* make global copies of the hInstance and command line of this app: 
	   they could come in handy */
	hInstance = hInst;
	lpszAppCommandLine = lpszCommandLine;


	/* Any command line switches to act on? */
	if (!Initialise())
	{
		/* Initialise returns FALSE if the operation indicated
		   by the command line settings has completed, e.g. Ref import */
		/* Otherwise initialise returns TRUE, telling us to continue running
		   the freenet tray app */
		goto clearup;
	}


	/* make sure we have only one instance running */
	if ( !OnlyOneInstance() )
	{
		/* and if it is already running exit at once */
		return 0;
		/* Note, if app was already running, the call to OnlyOneInsyance will have
		   caused the existing running copy to load up the Gateway page */
	}

	/* load in icons from resource table */
	hHopsEnabledIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_HOPSENABLED), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsDisabledIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_HOPSDISABLED), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hAlertIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_ALERT), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsNoGatewayIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_NOGWAY), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hHopsNoInternetIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_NOINET), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hThunderboltIcon = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_THUNDERBOLT), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStartingIcon[0] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STARTING1), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStartingIcon[1] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STARTING2), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStartingIcon[2] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STARTING3), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStartingIcon[3] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STARTING4), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStoppingIcon[0] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STOPPING1), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStoppingIcon[1] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STOPPING2), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStoppingIcon[2] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STOPPING3), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);
	hStoppingIcon[3] = (HICON)LoadImage(hInstance, MAKEINTRESOURCE(IDI_STOPPING4), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);


	/* hConfiguratorSemaphore is used so we never load more than one instance of the configurator at a time */
	hConfiguratorSemaphore = CreateSemaphore(NULL,1,1,NULL);

	/* Lock objects: for critical sections on shared data, essentially */
	hnFreenetMode = CreateMutex(NULL, FALSE, NULL);
	hSystray = CreateMutex(NULL,FALSE,NULL);
	hDialogBoxes = CreateMutex(NULL,FALSE,NULL);
	hLogfileViewerDialogBox = CreateMutex(NULL,FALSE,NULL);
	hAboutDialogBox = CreateMutex(NULL,FALSE,NULL);
	
	/* Initialise WinSock */
	wsaError = WSAStartup(MAKEWORD(1,1),&wsadata);

	/* did everything work so far? */
	if (hnFreenetMode!=NULL &&
		hSystray!=NULL &&
		hDialogBoxes!=NULL &&
		hLogfileViewerDialogBox!=NULL &&
		hAboutDialogBox!=NULL &&
		hSystray!=NULL &&
		hConfiguratorSemaphore!=NULL && 
		hnFreenetMode!=NULL &&
		hAlertIcon!=NULL &&
		hHopsDisabledIcon!=NULL &&
		hHopsEnabledIcon!=NULL && 
		hHopsNoGatewayIcon!=NULL && 
		hHopsNoInternetIcon!=NULL &&
		hThunderboltIcon!=NULL &&
		hStartingIcon[0]!=NULL &&
		hStartingIcon[1]!=NULL &&
		hStartingIcon[2]!=NULL &&
		hStartingIcon[3]!=NULL &&
		hStoppingIcon[0]!=NULL &&
		hStoppingIcon[1]!=NULL &&
		hStoppingIcon[2]!=NULL &&
		hStoppingIcon[3]!=NULL &&
		wsaError==0)
	{

		/* Create a separate thread to handle flashing the systray icon */
		hMonitorThread = CreateThread(NULL,1, MonitorThread, NULL, 0, &dwMonitorThreadId);
	}
	
	/* did everything work so far? */
	if (hMonitorThread!=NULL)
	{
		/* wait for thread message to be created by MonitorThread:
		   we NEED to do this before we do post any messages to the thread message queue - 
		   so that BeginMonitoring works immediately on startup
		   and the app closes down correctly if it can't load an icon resource */
		while (PostThreadMessage(dwMonitorThreadId, WM_TESTMESSAGE, 0,0) == FALSE);

		/*** main code: ***/
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
	
		/* =================================== */
		/* Message pump:                       */
		/* Loop until PostQuitMessage is sent  */
		/* =================================== */
		while (1)
		{
			dwGetMessageResult = GetMessage(&msg,NULL,0,0);
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

clearup:
	/* destroy objects by handle - not really necessary but it's nice to be tidy */
	if (wsaError==0) WSACleanup();
	if (hWnd) DestroyWindow(hWnd);
	if (hAlertIcon) DestroyIcon(hAlertIcon);
	if (hHopsEnabledIcon) DestroyIcon(hHopsEnabledIcon);
	if (hHopsDisabledIcon) DestroyIcon(hHopsDisabledIcon);
	if (hHopsNoGatewayIcon) DestroyIcon(hHopsNoGatewayIcon);
	if (hHopsNoInternetIcon) DestroyIcon(hHopsNoInternetIcon);
	if (hThunderboltIcon) DestroyIcon(hThunderboltIcon);

	if (hSemaphore) CloseHandle(hSemaphore);
	if (hSystray) CloseHandle(hSystray);
	if (hDialogBoxes) CloseHandle(hDialogBoxes);
	if (hLogfileViewerDialogBox) CloseHandle(hLogfileViewerDialogBox);

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
		// fproxy is running
		hMainWnd = GetLastActivePopup(hMainWnd);
		SendMessage(hMainWnd,WM_COMMAND,IDM_GATEWAY,0);
	}
	return false;

}



BOOL ParseCommandLine (char * szCommandLinePtr)
{
	unsigned int i;
	BOOL bUnusual=FALSE;

	LPSTR szEndPointer;
	

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
				for (i=0; (!bUnusual) && (szCommandText[i] != NULL); ++i)
				{
					if (CompareString(	LOCALE_SYSTEM_DEFAULT,
										NORM_IGNORECASE | SORT_STRINGSORT | NORM_IGNOREWIDTH,
										szCommandLinePtr, 
										-1,
										szCommandText[i],
										-1) == 2 )
					{
						/* Confused?  CompareString returns '2' if the strings are identical */
						switch (CommandActions[i])
						{
						case COMMAND_OPEN:
							bOpenGatewayOnStartup=true;
							break;
						case COMMAND_IMPORT:
							szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
							if (szCommandLinePtr == NULL)
							{
								bUnusual=TRUE;
							}
							else
							{
								ImportFileWithProgressPump(szCommandLinePtr);
								return FALSE;
							}	
							break;
						case COMMAND_EXPORT:
							szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
							if (szCommandLinePtr == NULL)
							{
								bUnusual=TRUE;
							}
							else
							{
								ExportFile(szCommandLinePtr); // no pump needed, command is quick to execute
								return FALSE;
							}	
							break;
						case COMMAND_HELP:
							bUnusual=TRUE;
							break;
						case COMMAND_CREATECONFIG:
							szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
							if (szCommandLinePtr == NULL)
							{
								bUnusual=TRUE;
							}
							else
							{
								CreateConfig(szCommandLinePtr);
								return FALSE;
							}
							break;
						case COMMAND_MERGECONFIG:
							szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
							if (szCommandLinePtr == NULL)
							{
								bUnusual=TRUE;
							}
							else
							{
								const char * szSecondArg = GetNextToken(szCommandLinePtr, szEndPointer);
								if (szSecondArg == NULL)
								{
									bUnusual=TRUE;
								}
								else
								{
									MergeConfig(szCommandLinePtr, szSecondArg);
									return FALSE;
								}
							}
							break;
						default:
							bUnusual=TRUE;
							break;
						}
					}
				}

				if (i==NUMBER_OF_COMMANDS)
				{
					bUnusual=TRUE;
					break;
				}

				if (bUnusual)
				{
					break;
				}

				if (szCommandLinePtr != NULL)
				{
					szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer);
				}
			}
			while (szCommandLinePtr != NULL);

			if (bUnusual)
			{
				MessageBox(NULL,"Usage:\n"
								"-open                loads the Freenet Gateway page on startup\n"
								"-import FILENAME     imports the reference file specified by FILENAME\n"
								"-export FILENAME     exports a reference for your node into FILENAME\n"
								"-createconfig FILENAME     Creates a default configuration file\n"
								"-mergeconfig FILE1 FILE2   Updates FILE1 to contain the latest configuration\n"
								"              information from FILE2 (most useful if FILE2 was created with\n"
								"              -createconfig FILE2 in the first place)\n"
								"-help                Shows this dialog",
								"Freenet Control Panel help",MB_OK);
				return FALSE;
			}
		}
	}

	return TRUE;
}

void SetClasspathEnvForThisThread(void)
{
	char szbuffer[MAX_PATH*2+2048+65535];
	char szShortPathbuffer[MAX_PATH+1];
	DWORD getenv;
	DWORD buffersize,dwStrlen;
	char dummy[1];

	char *szCLASSPATH;

	/* set the current directory to the path name so we can use GetProfile commands in the current directory */
	SetCurrentDirectory(szHomeDirectory);
	/* Get any additional CLASSPATH jars specified by the user in the .ini file:  */
	GetPrivateProfileString(szflsec, szClasspathExtraKey, szempty, szClasspathExtra, 65535, szflfile);

	lstrcpy(szbuffer,szHomeDirectory);
    GetShortPathName(szbuffer, szShortPathbuffer, sizeof(szShortPathbuffer)-1);

	/* set up the environment variable for CLASSPATH: */
    strcpy(szbuffer,szShortPathbuffer);
	lstrcat(szbuffer,"\\");
	lstrcat(szbuffer,szFreenetJar);
    lstrcat(szbuffer,";");
    lstrcat(szbuffer,szShortPathbuffer);
   	lstrcat(szbuffer,"\\");
    lstrcat(szbuffer,szFreenetExtJar);

	if (lstrlen(szClasspathExtra))
	{
		lstrcat(szbuffer,";");
		lstrcat(szbuffer,szClasspathExtra);
	}

	dwStrlen = lstrlen(szbuffer);
	/* buffer now holds, e.g., "G:\Progra~1\Freenet\Freenet.jar;G:\Progra~1\Freenet\Freenet-ext.jar" 
		where G:\Program Files\Freenet in this example is the current directory */
	/* dwStrlen equals the length of this string */
	/* bump up the size of dwStrlen to account for the fact that we need to add
		a semicolon to the string later, and a NULL character too ...
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
		SetEnvironmentVariable("CLASSPATH",szbuffer);
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
		lstrcat(szCLASSPATH,szbuffer);
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
}

/* One-time initialisation - ONLY DO THIS ONCE.  'refreshing settings' is performed by calling ReloadSettings */
BOOL Initialise(void)
{
	GetAppDirectory(szHomeDirectory);
	SetCurrentDirectory(szHomeDirectory);

	SetClasspathEnvForThisThread();

	/*  Load in settings from flaunch.ini etc.  */
	ReloadSettings();

	/* Map the InetIsOffline function from shell32.dll (winme/2k/xp/etc) or url.dll(win9x) */
	hURLDLL = LoadLibrary("shell32.dll");
	if (hURLDLL)
	{
		InetIsOffline = GetProcAddress(hURLDLL, "InetIsOffline");
	}
	if (!InetIsOffline)
	{
		if (hURLDLL)
		{
			FreeLibrary(hURLDLL);
			hURLDLL=NULL;
		}
		hURLDLL = LoadLibrary("url.dll");
		if (hURLDLL)
		{
			InetIsOffline = GetProcAddress(hURLDLL, "InetIsOffline");
		}
	}
	if (!InetIsOffline)
	{
		if (hURLDLL)
		{
			FreeLibrary(hURLDLL);
			hURLDLL=NULL;
		}
	}
	/* At this point we have just ONE module handle open, (either url.dll or shell32.dll as appropriate),
	   and one function pointer to the InetIsOffline function (or NULL if we couldn't find it in either DLL) */

	
	/* Parse the application command line: */
	/* Currently the following command-line arguments to freenet,exe are supported:
				-open			open the gateway page on startup
				-import			import the ref. file given by following argument
	*/
	if (!ParseCommandLine(lpszAppCommandLine))
	{
		// either we hit an error during parsing and popped up a dialog box;
		// or the parser "did the job"  (e.g.  freenet -import blah)
		return FALSE;
	}

	return TRUE;

}



void ReloadSettings(void)
{
	char szbuffer[MAX_PATH*2+2048];

	szbuffer[0]='\0';
	szjavawpath[0]='\0';

	/* set the current directory to the path name so we can use GetProfile commands in the current directory */
	SetCurrentDirectory(szHomeDirectory);

	/* Get the javaexec line and if that isn't there fall back on the javaw line ... */
	/* (Note, java.exe is used in preference to javaw.exe because:
	    1.  Freenet node doesn't create a window so don't need any Windows overhead of javaw
	    2.  Javaw.exe process cannot be 'stopped' cleanly because node doesn't create a window
	        (there is no window to send a CLOSE message to, so must resort to TerminateThread which is unpleasant)
	    3.  Java.exe can be stopped 'cleanly' by sending a CLOSE message (Freenet.exe tries three different close messages :-)
	    4.  java.exe can also be stopped 'cleanly' by sending a ^C to its STDIN handle (Freenet.exe doesn't do this yet
	        but will soon, especially when we really roll out with 'free' JVMs which not be as versatile as Sun's) */
	/* UPDATE 6th March 2003:
	   I'm pissed off trying to work around system configurations.  Instead I now use javaw.exe in preference
	   and simply TerminateProcess it instead of trying to shut it down cleanly.  It works.  Fuck it */
	if (!GetPrivateProfileString(szflsec, szjavawkey, szempty, szbuffer, JAVAWMAXLEN, szflfile))
	{
		if (!GetPrivateProfileString(szflsec, szjavakey, szempty, szbuffer, JAVAWMAXLEN, szflfile))
		{
			// try and find a generic "java.exe" in the path - if it works, use that, if not default to "javaw.exe"
			HANDLE hJavaExecutable = LoadLibrary("javaw.exe");
			if (hJavaExecutable==NULL || hJavaExecutable==INVALID_HANDLE_VALUE)
			{
				hJavaExecutable = LoadLibrary("java.exe");
			}
			if (hJavaExecutable!=NULL && hJavaExecutable!=INVALID_HANDLE_VALUE)
			{
				GetModuleFileName(hJavaExecutable, szbuffer, JAVAWMAXLEN);
				FreeLibrary(hJavaExecutable);
			}
			else
			{
				// try and find whatever .jar is associated with in the registry
				// if it isn't named "java.exe" or "javaw.exe" then ignore it
				// TODO
			}
		}
	}
	/* .. and convert to short filename format, because we want one SIMPLE string for javaw.exe path */
	GetShortPathName(szbuffer, szjavawpath, sizeof(szjavawpath) );

	/* get the fservecli launch string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfservecliexeckey, szfserveclidefaultexec, szfservecliexec, BUFLEN, szflfile);

	/* get the fconfig launch string and settings from flaunch.ini */
	bFConfigExecUseJava = (GetPrivateProfileInt(szflsec, szfconfigisjavakey, 0, szflfile) != 0);
	GetPrivateProfileString(szflsec, szfconfigexeckey, szfconfigdefaultexec, szfconfigexec, BUFLEN, szflfile);
	if (!bFConfigExecUseJava)
	{
		/* szfconfigexec is an executable path rather than a java entry point */
		/* convert to short filename format, because we want one SIMPLE string for exe path */
		lstrcpy(szbuffer, szHomeDirectory);
		lstrcat(szbuffer, "\\");
		lstrcat(szbuffer, szfconfigexec);
		if (!GetShortPathName(szbuffer, szfconfigexec, sizeof(szfconfigexec) ))
		{
			#ifdef _DEBUG
			DWORD why=GetLastError();
			// "winerror.h"
			#endif
		}
	}

	/* get the fserve seed command string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfseedcmdprekey, szfseeddefaultprecmd, szFserveSeedCmdPre, BUFLEN, szflfile);
	GetPrivateProfileString(szflsec, szfseedcmdpostkey, szfseeddefaultpostcmd, szFserveSeedCmdPost, BUFLEN, szflfile);
	/* get the fserve export command string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfseedexportcmdprekey, szfseeddefaultexportprecmd, szFserveExportCmdPre, BUFLEN, szflfile);
	GetPrivateProfileString(szflsec, szfseedexportcmdpostkey, szfseeddefaultexportpostcmd, szFserveExportCmdPost, BUFLEN, szflfile);

	/* get the fserve config command string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfservecliconfigkey, szfserveclidefaultconfig, szFserveConfig, BUFLEN, szflfile);

	/* Get the priority and priorityclass information from flaunch.ini */
	nPriority=GetPrivateProfileInt(szflsec, szprioritykey, nDefaultPriority, szflfile);
	nPriorityClass=GetPrivateProfileInt(szflsec, szpriorityclasskey, nDefaultPriorityClass, szflfile);

	/* form the gateway string - the "http://127.0.0.1:" is constant */
	lstrcpy(szgatewayURI, szgatewayURIdef);
	/* then append the port number of fproxy, looked up from freenet.ini */
	GetPrivateProfileString(szfinisec, szfprxkey, szfprxdefaultvalue, szfprxPort, 6, szfinifile);
	strcpy(szgatewayURI+lstrlen(szgatewayURI), szfprxPort);
	fprxPort = (WORD)atol(szfprxPort);
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
				++bufferpointer;
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
		and 'alarm' icons - this state then remains until ExitFserve is called.
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
	RestartFserve was originally written to be called (automatically) after the user
	has run the configuration tool */
void RestartFserve(void)
{
	LOCK(NFREENETMODE);
	nFreenetMode=FREENET_RESTARTING;
	UNLOCK(NFREENETMODE);

	ModifyIcon();
	ExitFserve();
	ReloadSettings();
	StartFserve();
}







/* helper function to set appropriate Hops icon in systray at appropriate times
   also sets the tooltip text appropriately */
void ModifyIcon(void)
{
	LOCK(SYSTRAY);

	switch (nFreenetMode)
	{
	/* add here all cases that flash between an icon and the 'hops disabled' icon */
	case FREENET_CANNOT_START:
		/*	flashing icon behaviour - each call generates a
			different icon depending on the current one		*/
		if (note.hIcon == *(hFreenetIcons[nFreenetMode]) )
		{
			note.hIcon = hHopsDisabledIcon;
		}
		else
		{
			note.hIcon = *(hFreenetIcons[nFreenetMode]);
		}
		break;

	/* add here all 'animated' icons */
	case FREENET_STARTING:
	case FREENET_STOPPING:
	case FREENET_RESTARTING:
		if (g_n_AnimationCounter>=GANIMATIONFRAMES-1)
		{
			g_n_AnimationCounter = 0;
		}
		else
		{
			++g_n_AnimationCounter;
		}
		note.hIcon = (hFreenetIcons[nFreenetMode])[g_n_AnimationCounter];
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

	SetCurrentDirectory(szHomeDirectory);

	if (hConfigDLL)
	{
		pProcAddress = (LPFNDLLCONFIG)GetProcAddress(hConfigDLL,szConfigProcName);
		if (pProcAddress)
		{
			SetCurrentDirectory(szHomeDirectory);
			CreateConfig("./default.ini");
			MergeConfig(szfinifile,"./default.ini");
			result = (pProcAddress)(NULL,FALSE);	/* BOOL InvokeConfig (HWND parentHwnd, BOOL Wizardmode); */
			// when we get here configuration is complete
			// need to restart the server
			if (!result)
			{
				// result==zero means config was successful
				// so restart the node
				SetCurrentDirectory(szHomeDirectory);
				CreateConfig("./default.ini");
				MergeConfig(szfinifile,"./default.ini");
				PostMessage(hWnd, WM_COMMAND, IDM_RESTART, 0);
			}
			// configuration complete - release the 'configurator' mutex
			ReleaseSemaphore(hConfiguratorSemaphore,1,NULL);
		}
		
		/* Unload the DLL */
		FreeLibrary(hConfigDLL);
	}

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
STARTUPINFO StartConfigInfo={sizeof(STARTUPINFO),
						NULL,NULL,NULL,
						0,0,0,0,0,0,0,
						STARTF_USESHOWWINDOW,
						SW_HIDE,
						0,NULL,
						NULL,NULL,NULL};
void StartConfigOrig(void)
{
	PROCESS_INFORMATION prcConfigInfo;

	char szexecbuf[sizeof(szjavawpath)+sizeof(szfconfigexec)+2];

	char * szexec;

	if (bFConfigExecUseJava)
	{
		szexec = szjavawpath;
		lstrcpy(szexecbuf, szjavawpath);
		lstrcat(szexecbuf, " ");
		lstrcat(szexecbuf, szfconfigexec); 
	}
	else
	{
		szexec = szfconfigexec;
		lstrcpy(szexecbuf, szfconfigexec);
	}

	SetCurrentDirectory(szHomeDirectory);
	CreateConfig("./default.ini");
	MergeConfig(szfinifile,"./default.ini");
	if (!CreateProcess(szexec, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartConfigInfo, &prcConfigInfo) )
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
	DWORD ExitCode;
	HANDLE hJavaConfig = (HANDLE)lpvprochandle;

	WaitForSingleObject(hJavaConfig, INFINITE);
	// configuration complete - release the 'configurator' mutex
	ReleaseSemaphore(hConfiguratorSemaphore,1,NULL);
	// need to restart the server

	// If we exited the configuration with exit code 0, restart the node
	GetExitCodeProcess(hJavaConfig, &ExitCode);
	CloseHandle(hJavaConfig);
	SetCurrentDirectory(szHomeDirectory);
	CreateConfig("./default.ini");
	MergeConfig(szfinifile,"./default.ini");

	if(ExitCode==0)
	{
		if(MessageBox(NULL,"The new settings will only take effect after restarting the node.\r\nShould the node be restarted now?\r\nWARNING: This aborts all current data transfers!",szAppName,MB_YESNO|MB_ICONQUESTION|MB_SYSTEMMODAL) == IDYES)
		{
			PostMessage(hWnd, WM_COMMAND, IDM_RESTART, 0);
		}
	}

	return 0;
}




/*	WindowProc - implements the popup menu, the mouse handling, and
	the obvious windows events (WM_CREATE / WM_DESTROY)	*/
MENUITEMINFO separatoritem = {sizeof(MENUITEMINFO), MIIM_TYPE, MFT_SEPARATOR, 0, IDM_GATEWAY, NULL,NULL,NULL,0,NULL, 0 };
MENUITEMINFO gatewayitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_DEFAULT | MFS_GRAYED, IDM_GATEWAY, NULL,NULL,NULL,0,szGatewayString, 0 };
MENUITEMINFO startstopitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_STARTSTOP, NULL,NULL,NULL,0,szStartString, 0 };
MENUITEMINFO restartitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_GRAYED, IDM_RESTART, NULL,NULL,NULL,0,szRestartString, 0 };
MENUITEMINFO configitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_CONFIGURE, NULL,NULL,NULL,0,szConfigureString, 0 };
MENUITEMINFO aboutitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_ABOUT, NULL,NULL,NULL,0,szAboutString, 0 };
MENUITEMINFO exititem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_EXIT, NULL,NULL,NULL,0,szExitString, 0 };
MENUITEMINFO importrefitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_IMPORT, NULL,NULL,NULL,0,szImportString, 0 };
MENUITEMINFO exportrefitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_EXPORT, NULL,NULL,NULL,0,szExportString, 0 };
MENUITEMINFO viewlogfileitem = {sizeof(MENUITEMINFO), MIIM_ID | MIIM_DATA | MIIM_TYPE | MIIM_STATE, MFT_STRING, MFS_ENABLED, IDM_VIEWLOGFILE, NULL,NULL,NULL,0,szViewLogFileString, 0 };

LRESULT CALLBACK WndProc( HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	int c=0;

	POINT mousepos;

	GetCursorPos(&mousepos);

	switch (message)
	{
		case WM_CREATE:

			hPopupMenu = CreatePopupMenu();
			InsertMenuItem(hPopupMenu, c, TRUE, &gatewayitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &separatoritem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &importrefitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &exportrefitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &configitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &separatoritem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &startstopitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &restartitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &viewlogfileitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &separatoritem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &aboutitem); ++c;
			InsertMenuItem(hPopupMenu, c, TRUE, &exititem); ++c;
		       
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
								case FREENET_STARTING:
								case FREENET_RESTARTING:
									// yes - stop server
									ExitFserve();
									break;

								case FREENET_STOPPED:
								case FREENET_STOPPING:
								case FREENET_CANNOT_START:
								default:
									// no - start the server
									ReloadSettings();
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
							if (nFreenetMode==FREENET_RUNNING || nFreenetMode==FREENET_STARTING || nFreenetMode==FREENET_STOPPING)
							{
								RestartFserve();
							}
							break;

						case IDM_IMPORT: // menu choice Import Refs
							ImportRefs();
							break;

						case IDM_EXPORT: // menu choice Export Refs
							ExportRefs();
							break;

						case IDM_VIEWLOGFILE: // menu choice View LogFile...
							CreateLogfileViewer(hWnd);
							break;

						case IDM_ABOUT:	// show the help file
							CreateAboutBox(hWnd);
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
								/* Node is stopped - can start it */
								/* or Node is stopping - but I'm allowing you to queue up a start command */
								/* Cannot view gateway */
								/* Cannot 'restart' it */
								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND|MF_ENABLED,IDM_STARTSTOP,szStartString);
								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_GRAYED,IDM_GATEWAY,szGatewayString);
								ModifyMenu(hPopupMenu,IDM_RESTART,MF_BYCOMMAND|MF_GRAYED,IDM_RESTART,szRestartString);
								break;

							case FREENET_STARTING:
								/* Node is running - can stop it */
								/* Cannot view gateway */
								/* Cannot 'restart' it */
								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND|MF_ENABLED,IDM_STARTSTOP,szStopString);
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
						// show last active popup window -- you know, in case it has been shuffled
						// to the bottom of the pile on the desktop
						SetForegroundWindow(hWnd);
						SetForegroundWindow(GetLastActivePopup(hWnd));
						break;

				}
			}
            break;

    	default:
			
			/* we need to do this to safeguard against the systray icon
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
				/* it's the closing quote - go back to looking */
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

		++szCurrentPointer;
	
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
		++szString;
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




void DeleteFilesInDirectory(const char* szDirectory)
{
	HANDLE hFind;
	WIN32_FIND_DATA findData;
	BOOL bCreated;
	char szFindMatch[MAX_PATH+5];

	if(szDirectory==NULL || strlen(szDirectory)==0)
		return; // empty directory name

	//TODO: automatically use win2k security if in win2k
	//TODO: recursively make directories
	
	bCreated = CreateDirectory(szDirectory, NULL);

	// search all files in directory

	lstrcpyn(szFindMatch, szDirectory, MAX_PATH);
	if(szFindMatch[strlen(szFindMatch)-1] != '\\' || szFindMatch[strlen(szFindMatch) - 1] != '/') // append a '\'
		lstrcat(szFindMatch, "\\");
	lstrcat(szFindMatch, "*.*");
	hFind = FindFirstFile(szFindMatch, &findData);

	if(hFind!=NULL && hFind!=INVALID_HANDLE_VALUE)
	{
		char szFilename[MAX_PATH*2+6];
		do
		{
			lstrcpy(szFilename, szDirectory);
			lstrcat(szFilename, findData.cFileName);
			DeleteFile(szFilename);
		} while(FindNextFile(hFind, &findData));
		FindClose(hFind);
		hFind=NULL;
	}
}
    
void ClearTempDirectories(void)
{
	// this should be done every time the node is started or stopped (while the node is not running preferably) 
	char tempdir[MAX_PATH+1]; 
	const char ** szTempDirectoryKey;
    
	SetCurrentDirectory(szHomeDirectory); 
    
	for (szTempDirectoryKey=szTempDirs; *szTempDirectoryKey!=NULL; ++szTempDirectoryKey)
	{
		GetPrivateProfileString(szfinisec, *szTempDirectoryKey, szempty, tempdir, MAX_PATH, szfinifile);
		if(strlen(tempdir))
			DeleteFilesInDirectory(tempdir);
	}
}



STARTUPINFO ConfigInfo={sizeof(STARTUPINFO),
						NULL,NULL,NULL,
						0,0,0,0,0,0,0,
						STARTF_USESHOWWINDOW,
						SW_HIDE,
						0,NULL,
						NULL,NULL,NULL};

BOOL CreateConfig(const char * szFilename)
{
	// execute FSERVE --export %filename%
	PROCESS_INFORMATION prcConfigInfo;

	char szexecbuf[sizeof(szjavawpath)+sizeof(szFserveConfig)+15+MAX_PATH];
	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveConfig); 
	lstrcat(szexecbuf, " \"");
	lstrcat(szexecbuf, szFilename);
	lstrcat(szexecbuf, "\" --silent");

	/* following is necessary because fred looks in *current directory* for its own ini file ... ! */
	SetCurrentDirectory(szHomeDirectory);
	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &ConfigInfo, &prcConfigInfo) )
	{
		char szErrorMsg[256];
		lstrcpy(szErrorMsg, "Failed to generate a default config file:\n");
		lstrcat(szErrorMsg, szFilename);
		MessageBox(NULL, szErrorMsg, "Freenet Control Panel", MB_OK | MB_ICONERROR | MB_TASKMODAL);
		return FALSE;
	}
	else
	{
		WaitForSingleObject(prcConfigInfo.hProcess, INFINITE);
		CloseHandle(prcConfigInfo.hThread);
		CloseHandle(prcConfigInfo.hProcess);
	}

	return TRUE;
}

BOOL CopyChanges(HANDLE hFileToRead, HANDLE hFileToWrite, const char* const szINIFileToMerge)
{
	BOOL bFailed=TRUE;
	BOOL bFreenetSection = FALSE;
	char szLine[4096];
	char szValue[4096];
	DWORD dwBytesRead,dwBytesWritten;
	DWORD dwOffset=0;
	char *iNewline, *iNewline2, *iNonspace, *iEquals;

	if (hFileToRead==NULL || hFileToRead==INVALID_HANDLE_VALUE)
	{
		return FALSE;
	}

	szLine[sizeof(szLine)-1] = '\0'; // null terminate buffer to prevent buffer overruns with string api functions
	while (1)
	{
		dwBytesRead=0;
		ReadFile(hFileToRead, szLine+dwOffset, sizeof(szLine)-1-dwOffset, &dwBytesRead, NULL);
		if (dwBytesRead==0 && dwOffset==0)
		{
			// end of file, we've finished
			bFailed=FALSE;
			break;
		}
		dwOffset += dwBytesRead;

		// Skip lines of config that have already been processed
		// (since \r and \n characters get converted to \0 characters)
		if (*szLine=='\0')
		{
			--dwOffset;
			memmove(szLine, szLine+1, dwOffset);
			continue;
		}

		// Find end of first line of config file, and replace \r\n (or \n\r) with \0\0
		// (or replace just \r or just \n with just \0 if line isn't terminated by \r\n or \n\r)
		// Also find first non-space character on line at the same time:
		iNonspace = szLine + strspn(szLine, " \t");
		iNewline = strpbrk(iNonspace, "\r\n");
		if (iNewline!=NULL)
		{
			if ( (iNewline[0]=='\r' && iNewline[1]=='\n') || (iNewline[0]=='\n' && iNewline[1]=='\r'))
			{
				iNewline2=iNewline+1;
			}
			else
			{
				iNewline2=iNewline;
			}
			if (iNonspace==iNewline)
			{
				// dump entire line to output file - it's just an empty line
				goto maybe_dumpline;
			}
			*iNewline = '\0';
		}

		// Find first non-space character on line
		switch (*iNonspace)
		{
		case '#':
			// dump entire line to output file - it's just a comment
			goto maybe_dumpline;
		case '[':
			// if it's a section, see if it's the 'Freenet' section:
			// We're enter the 'Freenet' section if and only if the section header
			// is "[Freenet node]" and there's nothing after the closing ] except whitespace.
			// Otherwise we leave the 'Freenet' section (assuming it's a valid section header)
			// Valid section headers match \s*\[.*\]\s*
			{
				BOOL bNewSectionIsFreenetSection = (strnicmp(iNonspace+1, szfinisec+1, 13)==0);
				const char* mend;
				if (bNewSectionIsFreenetSection)
				{
					mend = iNonspace+13+1+strspn(iNonspace+13+1," \t");
				}
				else
				{
					const char* mbrack = strchr(iNonspace+1, ']');
					if (mbrack)
					{
						mend = mbrack+1+strspn(mbrack+1," \t");
					}
					else
					{
						// not a validly formed section header - no closing ]
						// try parsing this line as a key:value pair instead
						goto keyvalue;
					}
				}
				if (*mend!='\0')
				{
					// There's "stuff" after the section header, so the section header isn't valid
					// Try parsing this line as a key:value pair instead
					goto keyvalue;
				}
				else
				{
					// nothing but whitespace after the section header - so it's a valid
					// section header.  Do we enter or leave Freenet section?
					bFreenetSection=bNewSectionIsFreenetSection;
				}
			}
			goto maybe_dumpline;
		default:
			break;
		}

keyvalue:
		if (!bFreenetSection)
		{
			// leave things outside of the Freenet section untouched
			goto dumpline;
		}
		else if (szINIFileToMerge==NULL)
		{
			// ignore the contents of the [Freenet node] section
			// (i.e. set szINIFileToMerge to extract everything BUT the [Freenet node]
			// section
			goto seeknext;
		}

		iEquals = strchr(iNonspace, '=');
		if (iEquals!=NULL && (*iEquals)=='=')
		{
			const char* const szKey = iNonspace;
			*iEquals='\0';
			GetPrivateProfileString(szfinisec,szKey,"\xfd\xfe\xff",szValue,sizeof(szValue),szINIFileToMerge);
			if (strcmp(szValue,"\xfd\xfe\xff")!=0)
			{
				// found it - update output:
				// 1-  If default hasn't got % then output ini value ALWAYS
				// 2-  If default has got % and ini key hasn't, compare the values.
				//     and write out the default if the ini has the same value,
				//     or the ini key=value otherwise
				// Actually do case 2a first, and then everything else as an 'otherwise'
				if (*iNonspace=='%')
				{
					// default found in INI file so output default file's variant
					// (as default more up-to-date)
					goto dumpline;
				}
				
				// otherwise: output INI file values
				WriteFile(hFileToWrite,szKey,strlen(szKey),&dwBytesWritten,NULL);
				if (dwBytesWritten != strlen(szKey))
					break;
OutputINIEqualsValue:
				WriteFile(hFileToWrite,"=",1,&dwBytesWritten,NULL);
				if (dwBytesWritten != 1)
					break;
				WriteFile(hFileToWrite,szValue,strlen(szValue),&dwBytesWritten,NULL);
				if (dwBytesWritten != strlen(szValue))
					break;
				WriteFile(hFileToWrite,"\r\n",2,&dwBytesWritten,NULL);
				if (dwBytesWritten != 2)
					break;
				goto seeknext;
			}

			else
			{
				if (*iNonspace=='%')
				{
					// see if INI file overrides the key (i.e. without a %)
					GetPrivateProfileString(szfinisec,szKey+1,"\xfd\xfe\xff",szValue,sizeof(szValue),szINIFileToMerge);
					if (strcmp(szValue,"\xfd\xfe\xff")!=0)
					{
						// INI file overrides the key, see if the INI file value is the same as the default value though!
						if (strcmp(szValue,iEquals+1)==0)
						{
							// INI and default are the same so just output the default:
							goto dumpline;
						}
						else
						{
							// INI value overrides default so output what the INI file has:
							WriteFile(hFileToWrite,szKey+1,strlen(szKey+1),&dwBytesWritten,NULL);
							if (dwBytesWritten != strlen(szKey+1))
								break;
							goto OutputINIEqualsValue;
						}
					}
				}

				// couldn't find that Key:
				// INI file therefore doesn't override it so just output default variant
				goto dumpline;
			}
		}

		// still here?  Don't know what this line is, just dump it:
		goto dumpline;

maybe_dumpline:
		if (bFreenetSection && szINIFileToMerge==NULL)
		{
			// ignore the contents of the [Freenet node] section
			goto seeknext;
		}
		// else fall through to dumpline

dumpline:
		WriteFile(hFileToWrite,szLine,(iNewline-szLine),&dwBytesWritten,NULL);
		if (dwBytesWritten != (DWORD)(iNewline-szLine))
			break;
		WriteFile(hFileToWrite,"\r\n",2,&dwBytesWritten,NULL);
		if (dwBytesWritten != 2)
			break;
		/* seek for next line */
		goto seeknext;

seeknext:
		dwOffset-=((iNewline2-szLine)+1);
		memmove(szLine, iNewline2+1, dwOffset);
		continue;
	}

	// finished processing hFileToProcess
	return bFailed;
}

// Assumes maximum length of any line of text in the .ini file is no more than 4K.
BOOL MergeConfig(const char * szINIFile, const char * szDefaultsFile)
{
	char szTemp[MAX_PATH+2];
	HANDLE hDefaultsFile, hUpdatedFile, hINIFile;
	BOOL bFailed = FALSE;
	
	GetTempFileName(szHomeDirectory,"_ini",0,szTemp);
	hINIFile = CreateFile(szINIFile, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_SEQUENTIAL_SCAN, NULL);
	if (hINIFile==NULL || hINIFile==INVALID_HANDLE_VALUE)
	{
		// This is not an error.
	}
	hDefaultsFile = CreateFile(szDefaultsFile, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_SEQUENTIAL_SCAN, NULL);
	if (hDefaultsFile==NULL || hDefaultsFile==INVALID_HANDLE_VALUE)
	{
		MessageBox(NULL, "Couldn't find 'defaults' file", "MergeConfig failed", MB_OK);
		bFailed=TRUE;
	}
	hUpdatedFile = CreateFile(szTemp, GENERIC_WRITE, FILE_SHARE_READ, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL|FILE_ATTRIBUTE_TEMPORARY, NULL);
	hUpdatedFile=NULL;
	if (hUpdatedFile==NULL || hUpdatedFile==INVALID_HANDLE_VALUE)
	{
		// try again with TEMP flag, as some OSs don't support it
		hUpdatedFile = CreateFile(szTemp, GENERIC_WRITE, FILE_SHARE_READ, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
	}
	if (hUpdatedFile==NULL || hUpdatedFile==INVALID_HANDLE_VALUE)
	{
		MessageBox(NULL, "Couldn't create 'updated' file", "MergeConfig failed", MB_OK);
		bFailed=TRUE;
	}

	if (!bFailed)
	{
		bFailed = CopyChanges(hINIFile, hUpdatedFile, NULL);
	}
	if (!bFailed)
	{
		bFailed = CopyChanges(hDefaultsFile, hUpdatedFile, szINIFile);
	}

	// when we get here, we've finished.
	if (hDefaultsFile)
	{
		CloseHandle(hDefaultsFile);
		hDefaultsFile=NULL;
	}
	if (hINIFile)
	{
		CloseHandle(hINIFile);
		hINIFile=NULL;
	}

	if (!bFailed)
	{
		// Success, copy across the changes from the temp filename to the real filename
		CopyFile(szTemp, szINIFile, FALSE);
	}

	if (hUpdatedFile)
	{
		CloseHandle(hUpdatedFile);
		hUpdatedFile=NULL;
	}
	DeleteFile(szTemp);
	// DeleteFile(szTemp) will silently fail if tempfile was successfully created with FILE_ATTRIBUTE_TEMPORARY flag
	// since OS will delete the file automatically when its last handle is closed


	return bFailed;
}
