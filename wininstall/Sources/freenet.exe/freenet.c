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
#include "windows.h"
#include "shellapi.h"
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
const char szStopString[]="&Stop Freenet";
const char szStartString[]="&Start Freenet";
const char szGatewayString[]="Open &Gateway";
const char szConfigureString[]="&Configure";
const char szExitString[]="E&xit";

/* tooltip text - matches order of FREENET_MODE enums */
const char *szFreenetTooltipText[]=
{
	"Freenet Is Stopped",
	"Freenet Is Running",
	"Freenet Is Restarting... Please Wait",
	"Freenet Is Stopping... Please Wait",
	"Freenet Is Having Problems"
};

/* string constants for use with the FLaunch.ini file */
const char szflfile[]="./FLaunch.ini"; /* ie name of file */
const char szflsec[]="Freenet Launcher"; /* ie [Freenet Launcher] subsection text */
const char szjavakey[]="Javaexec"; /* ie Javaexec=java.exe */
const char szjavawkey[]="Javaw"; /* ie Javaw=g:\winnt\system32\jview.exe */
const char szfserveexeckey[]="fserve"; /* ie Fserve=Freenet.node.gui.GUINode */
const char szfconfigexeckey[]="fconfig"; /* ie Fconfig=Freenet.node.gui.Config */
const char szfservedefaultexec[]="Freenet.node.gui.GUINode";
const char szfconfigdefaultexec[]="-cp freenet.jar Freenet.node.gui.Config freenet.ini";


/* string constants for use with the freenet.ini file */
const char szfinifile[]="./freenet.ini"; /* ie name of file */
const char szfinisec[]="Freenet node"; /* ie [Freenet node] subsection text */
const char szfprxkey[]="services.fproxy.port"; /* ie services.fproxy.port=8081 */

/* for launching configuration dll */
const char szConfigDLLName[]="config.dll"; /* ie name of file */
const char szConfigProcName[]="Config"; /* ie name of Config function */



/******************************************************
 *	Global Variables data:                            *
 ******************************************************/
/*		strings, etc... */
char szjavawpath[JAVAWMAXLEN];		/* used to read Javaw= definition out of FLaunch.ini */
char szfserveexec[BUFLEN];			/* used to read Fserve= definition out of FLaunch.ini */
char szfconfigexec[BUFLEN];			/* used to read Fconfig= definition out of FLaunch.ini */
char szgatewayURI[GATEWLEN];		/* used to store "http://127.0.0.1:8081" after the 8081 bit has been read from freenet.ini */

/*		flags, etc... */
FREENET_MODE nFreenetMode=FREENET_STOPPED;
bool bOpenGatewayOnStartup=false;	/* was freenet.exe called with the -open option?  */

/*		handles, etc... */
PROCESS_INFORMATION prcInfo;		/* handles to java interpreter running freenet node - process handle, thread handle, and identifiers of both */
HANDLE hSemaphore=NULL;				/* unique handle used to guarantee only one instance of freenet.exe app is ever running at one time */
DWORD dwMonitorThreadId;			/* thread identifier for the background 'flasher' thread - global so we can PostThreadMessage to it */
HICON hHopsEnabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hHopsDisabledIcon=NULL;		/* icon handle - resource loaded during initialisation code */
HICON hAlertIcon=NULL;				/* icon handle - resource loaded during initialisation code */
HICON hRestartingIcon=NULL;			/* icon handle - resource loaded during initialisation code */
HMENU hPopupMenu=NULL;				/* handle to Popup Menu (right click on systray icon) */
HWND hWnd=NULL;						/* main window handle  */
HINSTANCE hInstance=NULL;			/* handle to the main application instance */
LPSTR lpszAppCommandLine;			/* global pointer to the command line passed to this app */
HANDLE pSystray;					/* handle to a mutex object used to synchronise access to the shared systray structure below */


/* icon array - must match order in FREENET_MODE in types.h */
const HICON *hFreenetIcons[] = 
{
	&hHopsDisabledIcon,
	&hHopsEnabledIcon,
	&hRestartingIcon,
	&hHopsDisabledIcon,
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

	/* pSystray is used to synchronise access to the shared systray NOTIFYICONDATA structure */
	pSystray = CreateMutex(NULL,FALSE,NULL);

	/* Create a separate thread to handle flashing the systray icon */
	hMonitorThread = CreateThread(NULL,1, MonitorThread, NULL, 0, &dwMonitorThreadId);
	/* wait for thread message to be created by MonitorThread:
	   we NEED to do this before we do post any messages to the thread message queue - 
	   so that BeginMonitoring works immediately on startup
	   and the app closes down correctly if it can't load an icon resource */
	while (PostThreadMessage(dwMonitorThreadId, WM_TESTMESSAGE, 0,0) == FALSE);

	
	/* did everything work so far? */
	if (hMonitorThread!=NULL &&
		pSystray!=NULL &&
		hRestartingIcon!=NULL &&
		hAlertIcon!=NULL &&
		hHopsDisabledIcon!=NULL &&
		hHopsEnabledIcon!=NULL)
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

	if (hMonitorThread)
	{
		/* CreateThread could have failed so only do these steps if CreateThread WORKED:  */
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

	if (hSemaphore) CloseHandle(hSemaphore);
	if (pSystray) CloseHandle(pSystray);

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


void Initialise(void)
{
	char szbuffer[MAX_PATH+1];
	LPSTR szCommandLinePtr, szEndPointer;
	
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
			*bufferpointer='\0';
			break;
		}
		--bufferpointer;
	}
	
	/* set the current directory to the path name so we can use GetProfile commands in the current directory */
	SetCurrentDirectory(szbuffer);

	/* Get the Javaw and Java info from flaunch.ini */
	if ( !GetPrivateProfileString(szflsec, szjavawkey, szempty, szjavawpath, JAVAWMAXLEN, szflfile) )
	{
		GetPrivateProfileString(szflsec, szjavakey, szempty, szjavawpath, JAVAWMAXLEN, szflfile);
	}

	/* get the fserve launch string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfserveexeckey, szfservedefaultexec, szfserveexec, BUFLEN, szflfile);

	/* get the fconfig launch string from flaunch.ini */
	GetPrivateProfileString(szflsec, szfconfigexeckey, szfconfigdefaultexec, szfconfigexec, BUFLEN, szflfile);

	/* form the gateway string - the "http://127.0.0.1:" is constant */
	lstrcpy(szgatewayURI, szgatewayURIdef);
	/* then append the port number of fproxy, looked up from freenet.ini */
	GetPrivateProfileString(szfinisec, szfprxkey, szempty, szgatewayURI+lstrlen(szgatewayURI), 6, szfinifile);


	
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
			while ( (szCommandLinePtr = GetNextToken(szCommandLinePtr, szEndPointer))!=NULL )
			{
				if (lstrcmpi(szCommandLinePtr, "-open")==0)
				{
					bOpenGatewayOnStartup=true;
				}
			}
		}
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
	nFreenetMode=FREENET_RESTARTING;
	ModifyIcon();
	ExitFserve();
	Initialise();
	StartFserve();
}







/* helper function to set appropriate Hops icon in systray at appropriate times
   also sets the tooltip text appropriately */
void ModifyIcon(void)
{
	LockObject(pSystray);

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
	UnlockObject(pSystray);
}






/*	Launches the configuration DLL, or, if that isn't available, the standard
	freenet node java configurator */
bool StartConfig(void)
{
	HINSTANCE hConfigDLL = LoadLibrary(szConfigDLLName);
	LPCONFIGPROC pProcAddress = NULL;
	if (hConfigDLL)
	{
		pProcAddress = (LPCONFIGPROC)GetProcAddress(hConfigDLL,szConfigProcName);
		if (pProcAddress)
		{
			(pProcAddress)(NULL);
		}

		/* Bug in config.dll - function ISN'T found because it has the wrong name */
		pProcAddress = (LPCONFIGPROC)(-1);
	}

	/* Unload the DLL */
	FreeLibrary(hConfigDLL);

	if ((!hConfigDLL) || (!pProcAddress) )
	{
		return StartConfigOrig();
	}
	
	return true;
}


bool StartConfigOrig(void)
{
	STARTUPINFO StartConfigInfo={	sizeof(STARTUPINFO),
							NULL,NULL,NULL,
							0,0,0,0,0,0,0,
							STARTF_USESHOWWINDOW,
							SW_MINIMIZE,
							0,NULL,
							NULL,NULL,NULL};
	PROCESS_INFORMATION prcConfigInfo;

	char szexecbuf[sizeof(szjavawpath)+sizeof(szfconfigexec)+2];

	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szfconfigexec); 

	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS, NULL, NULL, &StartConfigInfo, &prcConfigInfo) )
	{
		MessageBox(NULL, "Unable to launch configurator for Freenet", "Cannot Config", MB_OK | MB_ICONERROR | MB_TASKMODAL);
	}
	else
	{
		WaitForSingleObject(prcConfigInfo.hProcess, INFINITE);
		CloseHandle(prcConfigInfo.hProcess);
		CloseHandle(prcConfigInfo.hThread);
	}

	// return true if we need to restart freenet node to take account of new settings
	// ... since we don't know we MUST return true!
	return true;
}



/* Helper functions: */
void LockObject(HANDLE pMutex)
{
	WaitForSingleObject(pMutex,INFINITE);
}
void UnlockObject(HANDLE pMutex)
{
	ReleaseMutex(pMutex);
}




/*	WindowProc - implements the popup menu, the mouse handling, and
	the obvious windows events (WM_CREATE / WM_DESTROY)	*/
LRESULT CALLBACK WndProc( HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{

	POINT mousepos;
	GetCursorPos(&mousepos);

	switch (message)
	{
		case WM_CREATE:

			hPopupMenu = CreatePopupMenu();
			AppendMenu(hPopupMenu,MF_STRING|MF_GRAYED,IDM_GATEWAY,szGatewayString);
			AppendMenu(hPopupMenu,MF_STRING,IDM_STARTSTOP,szStartString);
			AppendMenu(hPopupMenu,MF_STRING,IDM_CONFIGURE,szConfigureString);
			AppendMenu(hPopupMenu,MF_SEPARATOR,0, NULL);
			AppendMenu(hPopupMenu,MF_STRING,IDM_EXIT,szExitString);
			        
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
							
							if (StartConfig())
							{
								/* we get here after the configuration app has run */
								if (nFreenetMode==FREENET_RUNNING) // restart the server
								{
									RestartFserve();
								}
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
								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND,IDM_STARTSTOP,szStopString);
								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND,IDM_GATEWAY,szGatewayString);
								break;

							case FREENET_STOPPED:
							case FREENET_CANNOT_START:
							case FREENET_STOPPING:
								/* Node is stopped */
								/* or Node is stopping - but I'm allowing you to queue up a restart command */
								/* Cannot view gateway */
								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND,IDM_STARTSTOP,szStartString);
								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_GRAYED,IDM_GATEWAY,szGatewayString);
								break;

							case FREENET_RESTARTING:
							default:
								/* Node is restarting - essentially a different kind of 'stopping' with
								   additional state.  I'm allowing you to queue up a stop command */
								/* Cannot view gateway */
								ModifyMenu(hPopupMenu,IDM_STARTSTOP,MF_BYCOMMAND,IDM_STARTSTOP,szStartString);
								ModifyMenu(hPopupMenu,IDM_GATEWAY,MF_BYCOMMAND|MF_GRAYED,IDM_GATEWAY,szGatewayString);
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
			// Let windows handle all messages we choose to ignore.
			return(DefWindowProc(hWnd, message, wParam, lParam));

	} // switch (message)

	return 0;
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
