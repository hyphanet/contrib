// Dialog boxes for configuring import / export of references

#include "windows.h"
#include "types.h"
#include "commdlg.h"
#include "shared_data.h"
#include "refs.h"
#include "rsrc.h"

static const char szFileExtensions[] = "Freenet references (*.ref)\0*.REF\0All files (*.*)\0*.*\0\0";
static const char szImportDialogTitlebar[] = "Import Freenet References";
static const char szExportDialogTitlebar[] = "Export Freenet References";
static const char szBackslash[] = "\\";
static BOOL bImportDialog = FALSE;
static BOOL bExportDialog = FALSE;

HWND hImportProgressWnd=NULL;


// some compilers do not have certain constants defined
#ifndef FNERR_BUFFERTOOSMALL
#define FNERR_BUFFERTOOSMALL	0x00003002
#endif

#define U_FILENAME			(WM_USER+1)
#define U_UPDATESCROLLIES	(WM_USER+2)
#define U_BROWSE			(WM_USER+3)


OPENFILENAME staticOpenDialogParams = {	sizeof(OPENFILENAME),
										NULL,
										NULL,
										szFileExtensions,
										NULL,0,0, // can't be bothered preserving user choice - they're ALWAYS going to use .ref anyway!
										NULL,0, // remainder will be set up by calling code
										NULL,0,
										NULL,
										szImportDialogTitlebar,
										OFN_EXPLORER | OFN_ALLOWMULTISELECT | /*OFN_ENABLESIZING | */OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_HIDEREADONLY,
										0,0,
										"ref", // append the .ref extension by default
										0,NULL,NULL };

OPENFILENAME staticSaveAsDialogParams ={sizeof(OPENFILENAME),
										NULL,
										NULL,
										szFileExtensions,
										NULL,0,0, // can't be bothered preserving user choice - they're ALWAYS going to use .ref anyway!
										NULL,0, // remainder will be set up by calling code
										NULL,0,
										NULL,
										szExportDialogTitlebar,
										OFN_EXPLORER | /*OFN_ENABLESIZING | */OFN_PATHMUSTEXIST | OFN_OVERWRITEPROMPT,
										0,0,
										"ref", // append the .ref extension by default
										0,NULL,NULL };


void Bug(void)
{
	MessageBox(NULL,"* mailto: support@freenetproject.org *\nDescribe precisely what you were doing when you saw this message","Freenet Tray oops", MB_OK);
}

const TCHAR* FindNull(const TCHAR* szBuffer, const TCHAR* const szEndPointer)
{
	// finds next null character.
	// returns immediately if current character is a null
	while ( (szBuffer < szEndPointer) && (*szBuffer != '\0') )
	{
		szBuffer++;
	}
	return szBuffer;
}

DWORD WINAPI ImportProgressTimerProc(LPVOID lpvParam)
{
	HWND hwndDialog = (HWND)lpvParam;
	int i=0;
	while (1)
	{
		Sleep(330);
		i++;
		SendNotifyMessage(hwndDialog,U_UPDATESCROLLIES,0,0);
		if (GetLastError() != 0)
		{
			return 1;
		}
	}
	return 0;
}
	

DWORD WINAPI ImportRefsThread(LPVOID lpvParam)
{
	HWND hwndDialog = (HWND)lpvParam;
	TCHAR szFile[65535];
	TCHAR* const szFileBufferEndPointer = szFile+sizeof(szFile);
	BOOL bResult;
	OPENFILENAME importDetails = staticOpenDialogParams;
	importDetails.lpstrFile = szFile;
	importDetails.nMaxFile = sizeof(szFile)/sizeof(TCHAR);
	ZeroMemory(szFile,2); // a multi-select File Open dialog box needs a zero'd buffer to initialise correctly
	bResult = GetOpenFileName(&importDetails);
	if (bResult==FALSE)
	{
		DWORD dwError = CommDlgExtendedError();
		switch (dwError)
		{
		case 0:
			// no error - user hit cancel
			break;
		case FNERR_BUFFERTOOSMALL:
			MessageBox(NULL,"Sorry to be a pain ... \nYou selected too many files and this caused Windows® to\nmess around with your attempt to import Freenet Reference files.\nPlease try to select fewer files next time.","Import Freenet References - We Love Windows®",MB_OK);
			break;
		default:
			Bug();
			break;
		}
	}

	if (bResult)
	{
		// import the selected reference file(s)
		TCHAR szPath[65536];
		unsigned int nFiles;
		
		// how many files are there? iterate on return buffer contents
		const TCHAR * pszFile = FindNull(szFile, szFileBufferEndPointer);
		// pszFile now points to the end of the directory in the return buffer -
		// advance by one to find the first filename
		++pszFile;

		// Display progress bar
		ShowWindow(hImportProgressWnd,SW_SHOW);
		SetForegroundWindow(hImportProgressWnd);
		ShowWindow(hImportProgressWnd,SW_SHOW);

		if (pszFile<szFile+sizeof(szFile) && (*pszFile=='\0') )
		{
			// only one file was selected
			// -- so import it
			SendMessage(hImportProgressWnd, U_FILENAME, 0, (LPARAM)szFile);
			ImportFile(szFile);
		}
		else
		{
			for (nFiles=0; ((pszFile<szFile+sizeof(szFile)) && (*pszFile!='\0')); nFiles++)
			{
				// loop counts number of nul-terminated strings in return buffer
				pszFile = FindNull(pszFile, szFileBufferEndPointer);
				++pszFile;
			}
			// when we get here, nFiles is set up.

			// process each file in turn
			pszFile = FindNull(szFile, szFileBufferEndPointer);
			++pszFile;
			while (nFiles--)
			{
				// copy in the directory name
				lstrcpy(szPath,szFile);
				// append a backslash (the API rightly doesn't do it automatically)
				lstrcat(szPath,szBackslash);
				// append the filename
				lstrcat(szPath,pszFile);

				// process the file whose name is now in szPath buffer
				SendMessage(hImportProgressWnd, U_FILENAME, 0, (LPARAM)szPath);
				ImportFile(szPath);

				// advance to next filename in return buffer
				pszFile = FindNull(pszFile, szFileBufferEndPointer);
				++pszFile;
			}
		}

		SendMessage(hImportProgressWnd, WM_CLOSE, 0, 0);
	}

	return 0;
}



BOOL CALLBACK ImportProgressWndProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;

	switch (uMsg)
	{
	case WM_INITDIALOG:
		{
			DWORD dwThreadID;
			HANDLE hTimerThread = CreateThread(NULL,0,ImportProgressTimerProc,(LPVOID)(hwndDialog),0,&dwThreadID);
			CloseHandle(hTimerThread);
		}
		return TRUE;
				
	case WM_CLOSE:
		bQuit=TRUE;
		break;

	case U_BROWSE:
		{
			DWORD dwThreadID;
			HANDLE hImportRefsThread = CreateThread(NULL,0,ImportRefsThread,(LPVOID)(hwndDialog),0,&dwThreadID);
			CloseHandle(hImportRefsThread);
		}
		return TRUE;

	
	case U_FILENAME:
		SetWindowText(GetDlgItem(hwndDialog,IDC_S_IMPORTFILENAME), (LPCTSTR)(lParam));
		UpdateWindow(GetDlgItem(hwndDialog,IDC_S_IMPORTFILENAME));
		return TRUE;

	case U_UPDATESCROLLIES:
		{
			if (IsWindowVisible(GetDlgItem(hwndDialog,IDC_TEXT1)))
			{
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT2),SW_SHOW);
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT1),SW_HIDE);
				UpdateWindow(GetDlgItem(hwndDialog,IDC_TEXT2));
			}
			else if (IsWindowVisible(GetDlgItem(hwndDialog,IDC_TEXT2)))
			{
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT3),SW_SHOW);
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT2),SW_HIDE);
				UpdateWindow(GetDlgItem(hwndDialog,IDC_TEXT3));
			}
			else
			{
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT1),SW_SHOW);
				ShowWindow(GetDlgItem(hwndDialog,IDC_TEXT3),SW_HIDE);
				UpdateWindow(GetDlgItem(hwndDialog,IDC_TEXT1));
			}
		}
		return TRUE;


	case WM_SYSCOMMAND:
		switch (wParam)
		{
		case SC_CLOSE:
			bQuit=TRUE;
			break;
		default:
			return FALSE;
		}
		break;

	default:
		return FALSE;
	}


	if (bQuit)
	{
		// Quit flag was set in above message processing
		// destroy and cleanup
		LOCK(DIALOGBOXES);
		hImportProgressWnd=NULL;
		bImportDialog = FALSE;
		DestroyWindow(hwndDialog);
		UNLOCK(DIALOGBOXES);
		return TRUE;  //  true because the message obviously WAS processed, or else we wouldn't have set Quit flag
	}

	return FALSE;
}


void Pump(HANDLE hThread)
{
	/* =================================== */
	/* Message pump:                       */
	/* Loop until thread dies    */
	/* =================================== */
	MSG msg;
	DWORD dwGetMessageResult;
	DWORD dwWaitResult;
	while (1)
	{

		dwWaitResult = MsgWaitForMultipleObjects(1,&hThread,0,INFINITE,QS_ALLINPUT);
		// has a message been put into message queue?
		if (dwWaitResult!=WAIT_OBJECT_0+1)
		{
			// no ... in which case MsgWaitForMultipleObjects either returned
			// because  (1)  The thread has finished (in which case exit)
			// or       (2)  There was an error  (in which case exit)
			break;
		}
		else
		{
			// Thread hasn't finished yet and we got a message to process:
			while (PeekMessage(&msg,NULL,0,0,PM_NOREMOVE))
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

	}
}

// following function is intended to be used only when calling
// freenet.exe from command line with --import command
// because it creates its own message pump (necessary to handle the
// updates to the progress bar window)
void ImportFileWithProgressPump(const TCHAR * szFilename)
{
	HANDLE hThread;
	HWND hProgressWnd;
	
	// Display progress bar
	hProgressWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_IMPORTPROGRESS), hWnd, ImportProgressWndProc);
	SendMessage(hProgressWnd, U_FILENAME, 0, (LPARAM)szFilename);
	ShowWindow(hProgressWnd, SW_SHOW);
	SetForegroundWindow(hProgressWnd);
	ShowWindow(hProgressWnd, SW_SHOW);

	hThread = ImportFileAsync(szFilename);

	Pump(hThread);

	CloseHandle(hThread);
}


// Synchronous and asynchronous functions to import a single .ref file
// Asynchronous is just a nasty hack on top of the synchronous function to
// turn it into a thread
STARTUPINFO StartFserveSeedInfo={sizeof(STARTUPINFO),
						NULL,NULL,NULL,
						0,0,0,0,0,0,0,
						STARTF_USESHOWWINDOW | STARTF_FORCEONFEEDBACK,
						SW_NORMAL,
						0,NULL,
						NULL,NULL,NULL};

void ImportFile(const TCHAR * szFilename)
{
	// execute FSERVE --seed %filename%
	PROCESS_INFORMATION prcFserveSeedInfo;

	char szexecbuf[MAX_PATH+1+sizeof(szjavawpath)+sizeof(szfservecliexec)+2+sizeof(szFserveSeedCmdPre)+65536+sizeof(szFserveSeedCmdPost)];

	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szfservecliexec);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveSeedCmdPre);
	if(lstrlen(szFserveSeedCmdPre))
		lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, "\"");
	lstrcat(szexecbuf, szFilename); 
	lstrcat(szexecbuf, "\"");
	if(lstrlen(szFserveSeedCmdPost))
		lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveSeedCmdPost);

	/* following is necessary because fred looks in *current directory* for its own ini file ... ! */
	SetCurrentDirectory(szHomeDirectory);
	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartFserveSeedInfo, &prcFserveSeedInfo) )
	{
		char szErrorMsg[256];
		lstrcpy(szErrorMsg, "Failed to import reference file:\n");
		lstrcat(szErrorMsg, szFilename);
		MessageBox(NULL, szErrorMsg, "Freenet Control Panel", MB_OK | MB_ICONERROR | MB_TASKMODAL);
	}
	else
	{
		WaitForSingleObject(prcFserveSeedInfo.hProcess, INFINITE);
		CloseHandle(prcFserveSeedInfo.hThread);
		CloseHandle(prcFserveSeedInfo.hProcess);
	}

}
DWORD WINAPI ImportFileThread(LPVOID lpvData)
{
	const TCHAR * szFilename= (const TCHAR *) lpvData;
	ImportFile(szFilename);
	return 0;
}
HANDLE ImportFileAsync(const TCHAR * szFilename)
{
	DWORD dwThreadID;
	HANDLE hThread = CreateThread(NULL,0,ImportFileThread,(LPVOID)szFilename,0,&dwThreadID);
	return hThread;
}



void ImportRefs(void)
{
	// is dialog already running?
	LOCK(DIALOGBOXES);
	if (bImportDialog)
	{
		HWND hOpenFile = FindWindow(NULL,szImportDialogTitlebar);
		UNLOCK(DIALOGBOXES);
		// if so - set focus to it:
		if (hImportProgressWnd)
		{
			SetForegroundWindow(hImportProgressWnd);
		}
		if (hOpenFile)
		{
			SetForegroundWindow(hOpenFile);
		}
	}
	else
	{
		// else create the dialog and its thread:
		hImportProgressWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_IMPORTPROGRESS), hWnd, ImportProgressWndProc);
		if (hImportProgressWnd!=NULL)
		{
			bImportDialog=TRUE;
			SendMessage(hImportProgressWnd,U_BROWSE,0,0);
		}
		UNLOCK(DIALOGBOXES);
	}
}



STARTUPINFO StartFserveExportInfo={sizeof(STARTUPINFO),
						NULL,NULL,NULL,
						0,0,0,0,0,0,0,
						STARTF_USESHOWWINDOW | STARTF_FORCEONFEEDBACK,
						SW_NORMAL,
						0,NULL,
						NULL,NULL,NULL};

void ExportFile(const TCHAR * szFilename)
{
	// execute FSERVE --export %filename%
	PROCESS_INFORMATION prcFserveExportInfo;

	char szexecbuf[sizeof(szjavawpath)+sizeof(szfservecliexec)+2+sizeof(szFserveSeedExec)+sizeof(szFserveExportCmdPre)+65536+sizeof(szFserveExportCmdPost)];
	lstrcpy(szexecbuf, szjavawpath);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szfservecliexec); 
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveExportCmdPre);
	if(lstrlen(szFserveExportCmdPre))
		lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, "\"");
	lstrcat(szexecbuf, szFilename); 
	lstrcat(szexecbuf, "\"");
	if(lstrlen(szFserveExportCmdPost))
		lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveExportCmdPost);

	/* following is necessary because fred looks in *current directory* for its own ini file ... ! */
	SetCurrentDirectory(szHomeDirectory);
	if (!CreateProcess(szjavawpath, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartFserveExportInfo, &prcFserveExportInfo) )
	{
		char szErrorMsg[256];
		lstrcpy(szErrorMsg, "Failed to export references to file:\n");
		lstrcat(szErrorMsg, szFilename);
		MessageBox(NULL, szErrorMsg, "Freenet Control Panel", MB_OK | MB_ICONERROR | MB_TASKMODAL);
	}
	else
	{
		WaitForSingleObject(prcFserveExportInfo.hProcess, INFINITE);
		CloseHandle(prcFserveExportInfo.hThread);
		CloseHandle(prcFserveExportInfo.hProcess);
	}

}


void ExportRefs(void)
{
	// is dialog already running?
	LOCK(DIALOGBOXES);
	if (bExportDialog)
	{
		UNLOCK(DIALOGBOXES);
		// if so - set focus to it:
		SetForegroundWindow(FindWindow(NULL,szExportDialogTitlebar));
	}
	else
	{
		// else create the dialog:
		TCHAR szFile[65535];
		TCHAR* const szFileBufferEndPointer = szFile+sizeof(szFile);
		BOOL bResult;
		OPENFILENAME exportDetails = staticSaveAsDialogParams;
		exportDetails.lpstrFile = szFile;
		exportDetails.nMaxFile = sizeof(szFile)/sizeof(TCHAR);
		ZeroMemory(szFile,2);
		bExportDialog = TRUE;
		UNLOCK(DIALOGBOXES);
		bResult = GetSaveFileName(&exportDetails);
		if (bResult==FALSE)
		{
			DWORD dwError = CommDlgExtendedError();
			switch (dwError)
			{
			case 0:
				// no error - user hit cancel
				break;
			default:
				Bug();
				break;
			}
		}
	
		LOCK(DIALOGBOXES);
		bExportDialog = FALSE;
		UNLOCK(DIALOGBOXES);

		if (bResult)
		{
			// export the system references to the named file
			ExportFile(szFile);
		}
	}
}
