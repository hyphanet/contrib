/*		launchthread.c
		Dave Hooper (@1 May 2001)

		This thread handles actually loading and running the freenet node.
		As it is a separate thread the tray icon never fails to respond to user input
		Also it's better this way as you can get proper notification (through tooltips)
		of what the freenet node is actually doing at any one time */

<<<<<<< launchthread.c
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>
#include <winsock2.h>
#include <stdlib.h>
#include <string.h>

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


const char sztempdir1[]="FECTempDir"; /* ie FECTempDir=C:\windows\temp\freenet */
const char sztempdir2[]="mainport.params.servlet.1.params.tempDir"; /* see sztempdir1 */
const char sztempdir3[]="tempDir"; /* see sztempdir1 */

const char szServices[]="services";
const char szMainport[]="mainport";
const char szMainportPort[]="mainport.port"; // the port mainport listens on

extern const char szfinifile[];
extern const char szfinisec[];

/* handles, etc. */
PROCESS_INFORMATION FredPrcInfo;	/* handles to java interpreter running freenet node - process handle, thread handle, and identifiers of both */


BOOL FredIsRunning(void)
{
	// talk to the fred port
	return TRUE;
}

// NOT re-entrant ... just a very basic state machine
BOOL TestGateway(void)
{
	// returns TRUE if gateway (fproxy etc) is running
	// FALSE if not

	// Currently just a stub until function is implemented
	static BOOL bGateway = FALSE;
	static SOCKET sock;
	static BOOL bProbing = FALSE;
	static int i=0;
	WSAPROTOCOL_INFO p;

	if (bProbing) return bGateway;

	//sock = WSASocket(FROM_PROTOCOL_INFO,FROM_PROTOCOL_INFO,FROM_PROTOCOL_INFO,
	if (i<20) { ++i; return FALSE;}
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
		case FREENET_STARTING:
			// we're supposed to be 'monitoring' the node - Fserve thread is allegedly running - check that it still is!
			// wait for either a posted thread message, or the prcInfo.hProcess object being signalled,
			// (wait until either Fserve dies or WE receive a thread message telling us to do something different)
			phobjectlist[0] = FredPrcInfo.hProcess;
			if (nFreenetMode==FREENET_STARTING)
			{
				dwTimeout = KAnimationSpeed; // animation speed
			}
			else
			{
				// New - use a timeout of 2000 milliseconds i.e. two seconds
				// This allows us to perform our regular checks on the internet connection and on the gateway
				// and update the status icon accordingly
				dwTimeout = 2000;
			}
			break;
		
		case FREENET_CANNOT_START:
			// Fserve failed to load and run - 
			// just wait for a threadmessage or a KAnimationSpeed(ms) timeout
			// This generates our timeout-driven flashing icon
			phobjectlist[0] = hAlwaysUnsignalled;
			dwTimeout = KAnimationSpeed;
			break;

		case FREENET_STOPPING:
		case FREENET_RESTARTING: /* just another kind of 'stopping' really */
			// as above but allow for animated icons
			phobjectlist[0] = hAlwaysUnsignalled;
			dwTimeout = KAnimationSpeed;
			break;

		case FREENET_STOPPED:
		default:
			// Freenet is either not running, or shutting down, or starting up, etc
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
					case FREENET_RUNNING:
						if (!TestInternetConnection())
						{
							//nFreenetMode=FREENET_RUNNING_NO_INTERNET;
						}
						else if (!TestGateway())
						{
							//nFreenetMode=FREENET_RUNNING_NO_GATEWAY;
						}
						break;

					case FREENET_STARTING:
						if ( TestGateway() && FredIsRunning() )
						{
							nFreenetMode=FREENET_RUNNING;
						}
						break;

					default:
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
	DWORD dwWaitError;
	int i;

	/* get the window handle from the process ID by matching all
	   known windows against it (not really ideal but no alternative) */
	EnumWindows(KillWindowByProcessId, (LPARAM)(prcinfo->dwProcessId) );

	/* wait for the process to shutdown (we give it five seconds) */
	for (i=0; i<5000; i+=KAnimationSpeed)
	{
		dwWaitError = WaitForSingleObject(prcinfo->hProcess,KAnimationSpeed);
		if (dwWaitError!=WAIT_TIMEOUT)
			break;

		// keep icon animation while we wait
		ModifyIcon();
	}
	if (dwWaitError==WAIT_TIMEOUT)
	{
		/* OH MY, nothing worked - ok then, brutally terminate the process: */
		TerminateProcess(prcinfo->hProcess,0);
		// wait indefinitely for process to end.  keep icon animation while we wait
		while ( (dwWaitError = WaitForSingleObject(prcinfo->hProcess,KAnimationSpeed))==WAIT_TIMEOUT)
		{
			ModifyIcon();
		}
	}
	CloseHandle(prcinfo->hThread);
	CloseHandle(prcinfo->hProcess);
	prcinfo->hThread=NULL;
	prcinfo->hProcess=NULL;
	prcinfo->dwThreadId=0;
	prcinfo->dwProcessId=0;
}

void WaitForFServe(void)
{
	// blocker
	WaitForSingleObject(FredPrcInfo.hProcess,INFINITE);
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

void MonitorThreadRunFserve()
{
	char szexecbuf[sizeof(szjavawpath)+sizeof(szfservecliexec)+2];

	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szfservecliexec); 

	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, nPriorityClass, NULL, NULL, &FredStartInfo, &FredPrcInfo) )
	{
		MessageBox(NULL, szerrMsg, szerrTitle, MB_OK | MB_ICONERROR | MB_TASKMODAL);
		nFreenetMode=FREENET_CANNOT_START;
		ModifyIcon();
	}
	else
	{
		/* (... the process object will be 'watched' to check that the java interpreter launched correctly
			and that the freenet node is indeed running... ) */
		DWORD dwThreadPriority;
		HANDLE hDupThread;
		DWORD dwError;
		if (!DuplicateHandle(GetCurrentProcess(), FredPrcInfo.hThread, GetCurrentProcess(), &hDupThread, PROCESS_ALL_ACCESS | THREAD_ALL_ACCESS, TRUE, 0))
		{
			DWORD dwError = GetLastError();
		}
		if (!SetThreadPriority(hDupThread, nPriority))
		{
			DWORD dwError = GetLastError();
		}
		dwThreadPriority = GetThreadPriority(FredPrcInfo.hThread);
		dwError = GetLastError();
		CloseHandle(hDupThread);
		nFreenetMode=FREENET_STARTING;
		ModifyIcon();
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

void DeleteFilesInDirectory(char* directory)
{
	HANDLE find;
	WIN32_FIND_DATA file;
	bool created_directory = true;
	char search[MAX_PATH];

	if(strlen(directory) == 0)
		return;	// empty directory name

	//TODO: automatically use win2k security if in win2k
	//TODO: recursively make directories
	if(!CreateDirectory(directory, NULL))
		created_directory = false; 

	// search all files in directory

	lstrcpyn(search, directory, MAX_PATH - 4);
	if(directory[strlen(directory) - 1] != '\\' || directory[strlen(directory) - 1] != '/') // append a '\'
		lstrcat(search, "\\");
    lstrcat(search, "*.*");
	find = FindFirstFile(search, &file);
	if(find == INVALID_HANDLE_VALUE)
		if(!created_directory)
			MessageBox(NULL, "Unable to create temporary file directory", "Error", MB_OK | MB_ICONERROR | MB_TASKMODAL);
		else
			MessageBox(NULL, "Created direcotory but couldn't find any files", "Error", MB_OK | MB_ICONERROR | MB_TASKMODAL);

	do {
		char* filename = malloc(sizeof(directory) + sizeof(file.cFileName) + 1);
		if(!filename) return; //we have bigger problems then, so don't bother
		lstrcpyn(filename, directory, MAX_PATH - 3);
		lstrcat(filename, file.cFileName);
		DeleteFile(filename);
		free(filename);
	} while(FindNextFile(find, &file));
	FindClose(find);
}

void ClearTempDirectories(void)
{
	// this should be done every time the node is started or stopped (while the node is not running preferably)
	char tempdir1[MAX_PATH], tempdir2[MAX_PATH], tempdir3[MAX_PATH];

	SetCurrentDirectory(szHomeDirectory);

	// FIXME: this shoudln't be committed liek this
	GetPrivateProfileString(szfinisec, sztempdir1, szempty, tempdir1, MAX_PATH, szfinifile);
	GetPrivateProfileString(szfinisec, sztempdir2, szempty, tempdir2, MAX_PATH, szfinifile);
	GetPrivateProfileString(szfinisec, sztempdir3, szempty, tempdir3, MAX_PATH, szfinifile);

	if(strlen(tempdir1))
		DeleteFilesInDirectory(tempdir1);
	if(strlen(tempdir2))
		DeleteFilesInDirectory(tempdir2);
	if(strlen(tempdir3))
		DeleteFilesInDirectory(tempdir3);
}
