/*		launchthread.c
		Dave Hooper (@1 May 2001)

		This thread handles actually loading and running the freenet node.
		As it is a separate thread the tray icon never fails to respond to user input
		Also it's better this way as you can get proper notification (through tooltips)
		of what the freenet node is actually doing at any one time */

#include "windows.h"
#include "types.h"
#include "launchthread.h"
#include "shared_data.h"

/******************************************************
 *  G L O B A L S                                     *
 ******************************************************
 *	Global Const data:                                *
 *		null-terminated strings, etc ...              *
 ******************************************************/

const char szerrMsg[]=	"Couldn't start the node,\n"
						"make sure FLaunch.ini has an entry javaw= pointing to javaw.exe or\n"
						"an entry Javaexec= pointing to a Java Runtime binary (jview.exe/java.exe)";
const char szerrTitle[]="Error starting node";

const char szFCPerrMsg[]=	"Couldn't launch FCPProxy\n";
const char szFCPerrTitle[]=	"Error launching FCPProxy";

/* handles, etc. */
PROCESS_INFORMATION FredPrcInfo;	/* handles to java interpreter running freenet node - process handle, thread handle, and identifiers of both */
PROCESS_INFORMATION FCPProxyPrcInfo;	/* handles to FCPProxy console app, and identifiers */


// forward references:  (for this not external and not in launchthread.h)
void LoadFCPProxy(void);


BOOL TestGateway(void)
{
	// returns TRUE if gateway (fproxy etc) is running
	// FALSE if not
	// Currently just a stub until function is implemented
	return TRUE;
}

BOOL TestInternetConnection(void)
{
	// returns TRUE if we successfully got data through from the internet,
	// FALSE if not
	// Currently just a stub
	// Will probably use a combination of any or all of the following:
	//		Calls to InetIsOffline(0)   (freenet.c has already got a proc handle for this function)
	//		Trying to ping or open TCP/IP connection to one of 'n' sites
	//					e.g. sites/ports chosen from i.p addresses in datastore
	//							
	return TRUE;
}

DWORD WINAPI _stdcall MonitorThread(LPVOID null)
{
	MSG msg;
	DWORD dwWait;
	bool bQuitThreadNow; 
	HANDLE hAlwaysUnsignalled;
	HANDLE phobjectlist[2] = {NULL,NULL};
	DWORD dwTimeout;


	/*	create a thread message queue - used to control communication between this
		thread and the main (message pump / WndProc) thread */
	PeekMessage(&msg, (HWND)(-1), 0, 0, PM_NOREMOVE);

	/*	following flag is set to true by the WM_QUITMONITORINGTHREAD
		message handler below in this function */
	bQuitThreadNow=false;

	/* paradigm - an object that is always unsignalled - for use with "MsgWait..." functions */
	hAlwaysUnsignalled = CreateEvent(NULL, TRUE, FALSE, NULL);
	
	
	/* main thread loop - repeat until instructed to quit */
	while (!bQuitThreadNow)
	{

		switch (nFreenetMode)
		{
		case FREENET_RUNNING:
		case FREENET_RUNNING_NO_GATEWAY:
		case FREENET_RUNNING_NO_INTERNET:
			// we're supposed to be 'monitoring' the node - Fserve thread is allegedly running - check that it still is!
			// wait for either a posted thread message, or the prcInfo.hProcess object being signalled,
			// (wait until either Fserve dies or WE receive a thread message telling us to do something different)
			//
			// New - use a timeout of 2000 milliseconds i.e. two seconds
			// This allows us to perform our regular checks on the internet connection and on the gateway
			// and update the status icon accordingly
			phobjectlist[0] = FredPrcInfo.hProcess;
			dwTimeout = 2000;
			break;
		
		case FREENET_CANNOT_START:
		case FREENET_NOT_RUNNING_NO_INTERNET:
			// Fserve failed to load and run - 
			// so just wait for a threadmessage or a 500ms timeout
			// This generates our timeout-driven flashing icon
			phobjectlist[0] = hAlwaysUnsignalled;
			dwTimeout = 500;
			break;

		case FREENET_STOPPING:
		case FREENET_RESTARTING: /* just another kind of 'stopping' */
		case FREENET_STOPPED:
		default:
			// Freenet is either not running, or shutting down
			// so just wait for a threadmessage - no monitoring ... no flashing icon ...
			phobjectlist[0] = hAlwaysUnsignalled;
			dwTimeout = INFINITE;
			break;

		}

		dwWait = MsgWaitForMultipleObjects(1,phobjectlist,FALSE, dwTimeout, QS_POSTMESSAGE);
		switch (dwWait)
		{
			case 0xffffffff:
				// This means MsgWait... had an error ... better give up and die
				break;

			case WAIT_OBJECT_0+1:
			case WAIT_ABANDONED_0+1:
				// we have received a posted thread message : deal with it
				while ( PeekMessage(&msg,(HWND)(-1),0,0,PM_REMOVE) )
				{
					switch (msg.message)
					{
					case WM_BEGINMONITORING:
						/* fire up the node! */
						MonitorThreadRunFserve();
						break;

					case WM_ENDMONITORING:
						MonitorThreadKillFserve();
						break;

					case WM_QUITMONITORINGTHREAD:
						bQuitThreadNow=true;
						break;

					case WM_TESTMESSAGE:
					default:
						// do nothing
						break;
					}


				}
				break;

			case WAIT_OBJECT_0:
			case WAIT_ABANDONED_0:
			default:
				{
					// thread has died - begin flashing icon:
					DWORD dwError;
					GetExitCodeProcess(FredPrcInfo.hProcess, &dwError);
					nFreenetMode=FREENET_CANNOT_START;
				}
				//break;  NO BREAK - FALL THROUGH!

			case WAIT_TIMEOUT:
				
				LOCK(NFREENETMODE);
				switch (nFreenetMode)
				{
				
					case FREENET_CANNOT_START:
						// period timeout fired - change the icon
						break;
	
					case FREENET_RUNNING:
					case FREENET_RUNNING_NO_GATEWAY:
						if (!TestInternetConnection())
						{
							nFreenetMode=FREENET_RUNNING_NO_INTERNET;
						}
						break;
				
					case FREENET_RUNNING_NO_INTERNET:
					case FREENET_NOT_RUNNING_NO_INTERNET:
						if (TestInternetConnection())
						{
							if (nFreenetMode==FREENET_NOT_RUNNING_NO_INTERNET)
							{
								nFreenetMode=FREENET_STOPPED;
							}
							else if (TestGateway())
							{
								nFreenetMode=FREENET_RUNNING;
							}
							else
							{
								nFreenetMode=FREENET_RUNNING_NO_GATEWAY;
							}
						}
						break;
				}
				UNLOCK(NFREENETMODE);
				ModifyIcon();
						
				break;

		} // switch

	} // while !bQuitThreadNow

	// when we get here, it's because we got a WM_QUITMONITORINGTHREAD message
	CloseHandle(hAlwaysUnsignalled);

	return 0;
}

void KillProcessNicely(PROCESS_INFORMATION *prcinfo)
{
	/* get the window handle from the process ID by matching all
	   known windows against it (not really ideal but no alternative) */
	EnumWindows(KillWindowByProcessId, (LPARAM)(prcinfo->dwProcessId) );

	/* wait for the process to shutdown (we give it five seconds) */
	if (WaitForSingleObject(prcinfo->hProcess,5000) == WAIT_TIMEOUT)
	{
		/* OH MY, nothing worked - ok then, brutally terminate the process: */
		TerminateProcess(prcinfo->hProcess,0);
		WaitForSingleObject(prcinfo->hProcess,INFINITE);
	}
	CloseHandle(prcinfo->hThread);
	CloseHandle(prcinfo->hProcess);
	prcinfo->hThread=NULL;
	prcinfo->hProcess=NULL;
	prcinfo->dwThreadId=0;
	prcinfo->dwProcessId=0;
}

/* If this looks confusing see article Q178893 in MSDN.
   All I'm doing is enumerating ALL the top-level windows in the system and matching
   against the Freenet Node process handle.  I send a swift WM_CLOSE to any that match
   and sit back and wait for the results */
BOOL CALLBACK KillWindowByProcessId(HWND hWnd, LPARAM lParam)
{

#ifdef _DEBUG
	DWORD dwError;
#endif

	/* called for each window in system */
	/* we're using it to hunt for windows created by the freenet node process */
	/* First find out if this window was created by the freenet node process: */
	DWORD dwProcessId=0;
	DWORD dwWndProc=0;

	GetWindowThreadProcessId(hWnd, &dwProcessId);
	if (dwProcessId != (DWORD)lParam)
	{
		/* This window was NOT created by the process... keep enumerating */
		return TRUE;
	}

	/* This window WAS created by the process - try and close the window */
	/* Is this a console window?  Console windows are generally characterised by the
	   fact that they have no WindowProc.  We can find this out! */
	dwWndProc = GetWindowLong(hWnd, GWL_WNDPROC);
	if (dwWndProc==0)
	{
		#ifdef _DEBUG
		dwError = GetLastError();
		#endif
		dwWndProc = GetClassLong(hWnd, GCL_WNDPROC);

		if (dwWndProc==0)
		{
			#ifdef _DEBUG
			dwError=GetLastError();
			#endif
		
			// This window has no WndProc ... so probably not much use sending
			// it a WM_CLOSE message then.  Chances are it's a console window.  Keep enumerating
			return TRUE;
		}
	}

	/* This window has a WndProc so send it a swift "WM_SYSCOMMAND(SC_CLOSE)" message to tell
		the window to close the underlying app */
	SendMessage(hWnd, WM_SYSCOMMAND, SC_CLOSE, 0);
	
	/* return true to keep enumerating - there may be more windows that need shutting down */
	return TRUE;
}
 



/* note - ONLY TO BE CALLED FROM ASYNCHRONOUS THREAD LEVEL - i.e. Monitor Thread */

STARTUPINFO FredStartInfo={	sizeof(STARTUPINFO),
							NULL,NULL,NULL,
							0,0,0,0,0,0,0,
							STARTF_USESHOWWINDOW | STARTF_FORCEONFEEDBACK,
							SW_HIDE,
							0,NULL,
							NULL,NULL,NULL};

STARTUPINFO FCPProxyStartInfo=
						{	sizeof(STARTUPINFO),
							NULL,NULL,NULL,
							0,0,0,0,0,0,0,
							STARTF_USESHOWWINDOW | STARTF_FORCEONFEEDBACK,
							SW_HIDE,
							0,NULL,
							NULL,NULL,NULL};

void MonitorThreadRunFserve()
{
	char szexecbuf[sizeof(szjavawpath)+sizeof(szfservecliexec)+2];

	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szfservecliexec); 

	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS, NULL, NULL, &FredStartInfo, &FredPrcInfo) )
	{
		MessageBox(NULL, szerrMsg, szerrTitle, MB_OK | MB_ICONERROR | MB_TASKMODAL);
		nFreenetMode=FREENET_CANNOT_START;
		ModifyIcon();
	}
	else
	{
		/* (... the process object will be 'watched' to check that the java interpreter launched correctly
			and that the freenet node is indeed running... ) */
		nFreenetMode=FREENET_RUNNING;
		ModifyIcon();
		LoadFCPProxy();
	}
}

/* note - ONLY TO BE CALLED FROM ASYNCHRONOUS THREAD LEVEL - i.e. Monitor Thread */
void MonitorThreadKillFserve()
{
	if ( (FredPrcInfo.hProcess!=NULL) || (FredPrcInfo.hThread!=NULL) )
	{
		/* set nFreenetMode to FREENET_STOPPING only if we didn't get here because of RestartFserve */
		LOCK(NFREENETMODE);
		if (nFreenetMode!=FREENET_RESTARTING)
		{
			nFreenetMode=FREENET_STOPPING;
		}
		UNLOCK(NFREENETMODE);
		ModifyIcon();

		KillProcessNicely(&FredPrcInfo);
	}
	LOCK(NFREENETMODE);
	if (nFreenetMode!=FREENET_RESTARTING)
	{
		nFreenetMode=FREENET_STOPPED;
		ModifyIcon();
	}
	UNLOCK(NFREENETMODE);

	if ( (FCPProxyPrcInfo.hProcess != NULL)  || (FCPProxyPrcInfo.hThread != NULL) )
	{
		KillProcessNicely(&FCPProxyPrcInfo);
	}		
}



void LoadFCPProxy(void)
{
	// should FCPProxy be loaded:
	// ONLY IF Fproxy is not supposed to be loaded.
	if (!bUsingFProxy)
	{
		char szexecbuf[sizeof(szHomeDirectory)+32];

		lstrcpy(szexecbuf, szHomeDirectory);
		lstrcat(szexecbuf, "\\fcpproxy.exe");

		if (!CreateProcess(szexecbuf, NULL, NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS | CREATE_NEW_CONSOLE | CREATE_NO_WINDOW, NULL, NULL, &FCPProxyStartInfo, &FCPProxyPrcInfo) )
		{
			DWORD dwError = GetLastError();
			// if error was FileNotFound then that's ok, it means the user didn't install FCPProxy
			if (dwError != ERROR_FILE_NOT_FOUND && dwError != ERROR_PATH_NOT_FOUND)
			{
				MessageBox(NULL, szFCPerrMsg, szFCPerrTitle, MB_OK | MB_ICONERROR | MB_TASKMODAL);
			}
		}
	}
	else
	{
		ZeroMemory(&FCPProxyPrcInfo, sizeof(PROCESS_INFORMATION));
	}
}