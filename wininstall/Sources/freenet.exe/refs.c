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

// some compilers do not have certain constants defined
#ifndef FNERR_BUFFERTOOSMALL
#define FNERR_BUFFERTOOSMALL	0x00003002
#endif


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
	MessageBox(NULL,"* mailto: freenet_bugs@beermex.com *\nDescribe precisely what you were doing when you saw this message","Freenet Tray oops", MB_OK);
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


BOOL CALLBACK __stdcall ImportProgressWndProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;

	switch (uMsg)
	{
	case WM_INITDIALOG:
		SetTimer(hwndDialog,1,330,NULL);
		return TRUE;
				
	case WM_CLOSE:
		bQuit=TRUE;
		break;

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
		DestroyWindow(hwndDialog);
		return TRUE;  //  true because the message obviously WAS processed, or else we wouldn't have set Quit flag
	}

	return FALSE;
}


STARTUPINFO StartFserveSeedInfo={sizeof(STARTUPINFO),
						NULL,NULL,NULL,
						0,0,0,0,0,0,0,
						STARTF_USESHOWWINDOW | STARTF_FORCEONFEEDBACK,
						SW_NORMAL,
						0,NULL,
						NULL,NULL,NULL};


// following should be used ONLY when calling from command line
void ImportFileWithProgress(const TCHAR * szFilename)
{
	// Display progress bar
	HANDLE hImportProgressWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_IMPORTPROGRESS), hWnd, ImportProgressWndProc);

	ImportFile(szFilename);

	SendMessage(hImportProgressWnd, WM_CLOSE, 0, 0);
}

void ImportFile(const TCHAR * szFilename)
{
	// execute FSERVE --seed %filename%
	PROCESS_INFORMATION prcFserveSeedInfo;

	char szexecbuf[MAX_PATH+1+sizeof(szFserveSeedExec)+sizeof(szFserveSeedCmdPre)+65536+sizeof(szFserveSeedCmdPost)];
	char szexecargv0[MAX_PATH+1+sizeof(szFserveSeedExec)];

	lstrcpy(szexecargv0, szHomeDirectory);
	lstrcat(szexecargv0, szBackslash);
	lstrcat(szexecargv0, szFserveSeedExec);

	lstrcpy(szexecbuf, szexecargv0);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveSeedCmdPre);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFilename); 
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveSeedCmdPost);

	if (!CreateProcess(szexecargv0, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartFserveSeedInfo, &prcFserveSeedInfo) )
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


void ImportRefs(void)
{
	// is dialog already running?
	LOCK(DIALOGBOXES);
	if (bImportDialog)
	{
		UNLOCK(DIALOGBOXES);
		// if so - set focus to it:
		SetForegroundWindow(FindWindow(NULL,szImportDialogTitlebar));
	}
	else
	{
		// else create the dialog:
		TCHAR szFile[65535];
		TCHAR* const szFileBufferEndPointer = szFile+sizeof(szFile);
		BOOL bResult;
		OPENFILENAME importDetails = staticOpenDialogParams;
		importDetails.lpstrFile = szFile;
		importDetails.nMaxFile = sizeof(szFile)/sizeof(TCHAR);
		ZeroMemory(szFile,2); // a multi-select File Open dialog box needs a zero'd buffer to initialise correctly
		bImportDialog = TRUE;
		UNLOCK(DIALOGBOXES);
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
	
		LOCK(DIALOGBOXES);
		bImportDialog = FALSE;
		UNLOCK(DIALOGBOXES);

		if (bResult)
		{
			// import the selected reference file(s)
			TCHAR szPath[65536];
			unsigned int nFiles;
			HANDLE hImportProgressWnd;
			
			// how many files are there? iterate on return buffer contents
			const TCHAR * pszFile = FindNull(szFile, szFileBufferEndPointer);
			// pszFile now points to the end of the directory in the return buffer -
			// advance by one to find the first filename
			++pszFile;

			// Display progress bar
			hImportProgressWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_IMPORTPROGRESS), hWnd, ImportProgressWndProc);

			if (pszFile<szFile+sizeof(szFile) && (*pszFile=='\0') )
			{
				// only one file was selected
				// -- so import it
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
					ImportFile(szPath);

					// advance to next filename in return buffer
					pszFile = FindNull(pszFile, szFileBufferEndPointer);
					++pszFile;
				}
			}

			SendMessage(hImportProgressWnd, WM_CLOSE, 0, 0);
		}
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

	char szexecbuf[MAX_PATH+1+sizeof(szFserveSeedExec)+sizeof(szFserveSeedCmdPre)+65536+sizeof(szFserveSeedCmdPost)];
	char szexecargv0[MAX_PATH+1+sizeof(szFserveSeedExec)];

	lstrcpy(szexecargv0, szHomeDirectory);
	lstrcat(szexecargv0, szBackslash);
	lstrcat(szexecargv0, szFserveSeedExec);

	lstrcpy(szexecbuf, szexecargv0);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveExportCmdPre);
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFilename); 
	lstrcat(szexecbuf, " ");
	lstrcat(szexecbuf, szFserveExportCmdPost);

	if (!CreateProcess(szexecargv0, (char*)(szexecbuf), NULL, NULL, FALSE, NORMAL_PRIORITY_CLASS|CREATE_NO_WINDOW, NULL, NULL, &StartFserveExportInfo, &prcFserveExportInfo) )
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