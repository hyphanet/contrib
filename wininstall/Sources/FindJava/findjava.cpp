
h:\freenet\CVSWORK\Contrib\wininstall\Sources\FindJava>perl -e "while(<>)" -e "{" -e "s/\x0d[^\x0a]/\x0a/;" -e "s/\x0d\x0a/\x0a/;" -e "print $_;" -e "}" -e "" 
/*/////////////////////////////////////////////////////////////////////////////

	file : findjava.cpp
	author : Lee Benjamin Burhans (LBurhans@hotmail.com, LeaveMeHigh@aol.com)
	date : October 5, 2000
	purpose : 

	Modification History:

	Date			Author									 Version
	================================================================
	11-Dec-2000		Tim Buchalka - buchalka@hotmail.com			v1.1
	09-Jan-2001		Tim Buchalka - buchalka@hotmail.com			v1.2
    20-Apr-2001     David McNab - david@rebirthing.co.nz        v1.3							

	Items enhanced/added to version 1.1

	1. Cancel button is always active (user can abort process at any time).
	2. "Done" button text and referring text changed to "Update Settings"
	3. Put extra test in to ensure CD-ROM drives not searched (to avoid error when no cd).
	4. Allow user to exit as soon as one interpreter is found.
	5. When an interpreter is found, select the first entry (index 0) by default to avoid
	   an error in the update where if nothing is selected it does not save anything.

	Items enhanced/added to version 1.2

	1. Consistent code formatting throughout application
	2. Registry is checked prior to looking for an interpreter.  If one is found, and the
	   user chooses to accept it, then Flaunch.ini is updated and the program exits, 
	   otherwise the normal hard drive search is performed.
	3. Program looks for windows, winnt, and win folders on each drive as a quick way to
	   find an interpreter before commencing the full build.

     N.B. Code compiles with a warning - conversion from long to char - Should not effect
	 operation of program but a future version should probably address this.

    Items added to version 1.3

    1. Disallowed use of Windows java interpreters (this eliminating a whole class of hassles)
	2. If the program's filename is 'locjava.exe' instead of 'findjava.exe', then it won't search
	   anywhere for a java interpreter - it will assume that on exists at ./jre/bin/java.exe and
	   write that path to the flaunch.ini file.


*//////////////////////////////////////////////////////////////////////////////


#ifndef STRICT
#define STRICT

#endif

#include <windows.h>
#include <stdio.h>
#include <commctrl.h>
#include "resource.h"
#include <stdlib.h>

///////////////////////////////////////////////////////////////////////////////
// function prototypes

DWORD WINAPI    doDeepSearch(LPVOID lpvParameter);
int             recursiveTraversal(char *cDirectory);
int             searchIn(char *cPath);
int             parsePath(char *cPathEnvironment);
int             updateConfigFiles(void);
BOOL CALLBACK	dlgProc(HWND hWndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);
void			lookSpecificFolder(char *cDirectory);
void			CheckWindowsFolders(char *cDrive);

int				searchDone = 0;


// 
const int MATCH_FOUND = 2;
bool bUpdateBeforeTraverseComplete = false;
#define MAXSTR 512

///////////////////////////////////////////////////////////////////////////////
// globals

struct DialogControls
    {
		HWND hWndMain;
		HWND hWndActionText;
		HWND hWndResultList;
		HWND hWndStatusText;
		HWND hWndCloseButton;    
		HWND hWndCancelButton;
    };

DialogControls dc;

///////////////////////////////////////////////////////////////////////////////

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpszCmdLine, int nCmdShow)
    {
		char *exename;
		char *progpath;

		// determine the directory in which this executable is running
		progpath = strdup(_pgmptr); // 'pgmptr' is a global windows-specific string
		exename = strrchr(progpath, '\\'); // point to slash between path and filename
		*exename++ = '\0'; // split the string and point to filename part

	    InitCommonControls();

		return DialogBox(hInstance,
			             MAKEINTRESOURCE(IDD_DIALOG),
				         GetDesktopWindow(),
					     dlgProc);
    }

///////////////////////////////////////////////////////////////////////////////

BOOL CALLBACK dlgProc(HWND hWndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
    static DWORD  dwThreadID;
    static HANDLE hThread;

    switch (uMsg)
	{
        case WM_INITDIALOG:             
		{
            dc.hWndMain        = hWndDlg;
            dc.hWndActionText  = GetDlgItem(hWndDlg, IDC_STATIC_ACTION);
            dc.hWndStatusText  = GetDlgItem(hWndDlg, IDC_STATIC_PATHNAME);
            dc.hWndResultList  = GetDlgItem(hWndDlg, IDC_LIST);
            dc.hWndCloseButton = GetDlgItem(hWndDlg, IDC_BUTTON_CLOSE);
			dc.hWndCancelButton = GetDlgItem(hWndDlg, IDC_BUTTON_CANCEL);

            hThread = CreateThread(NULL, 0, doDeepSearch, NULL, 0, &dwThreadID);
            return TRUE;
		}

        case WM_COMMAND:
        {
			switch (LOWORD(wParam))
			{
				case IDC_BUTTON_CLOSE:
				{
					//if (!searchDone) /* disabled this, we do want to be able to update before finishing the search*/
					//	return FALSE;

					if(bUpdateBeforeTraverseComplete)
						MessageBox(dc.hWndMain,
						"Java interpreter found and updated.\n",
						"Complete.",
						MB_OK | MB_ICONINFORMATION);
						
                    updateConfigFiles();
                    CloseHandle(hThread);
                    EndDialog(hWndDlg, 1);
                    return TRUE;
				}

				case IDC_BUTTON_CANCEL:
				{
					// If user decide to use the java in the registry, don't bother searching the hard 
					// drive(s) for an interpreter.

					if(MessageBox(dc.hWndMain, 
						"Are you sure you wish to cancel?\n"
						"without letting the search for\n"
						"a java interpreter complete?\n"
						"You may have to edit the flaunch.ini\n"
						"file manually or re-run the installation\n"
						"to get Freenet to work.",
						"Abort?",
						MB_YESNO | MB_ICONQUESTION) == IDYES)
						{
							CloseHandle(hThread);
							EndDialog(hWndDlg, 1);
							return TRUE;
						}
				}

			}

            break;
			}
        }

		return FALSE;
	}

///////////////////////////////////////////////////////////////////////////////

int updateConfigFiles(void)
{
	char    *cJavaExec;
    int     iSelected, iSize;

    iSelected = SendMessage(dc.hWndResultList, LB_GETCURSEL, 0, 0);

    if (iSelected == LB_ERR)
        return 0;

    iSize = SendMessage(dc.hWndResultList, LB_GETTEXTLEN, iSelected, 0);

    if (iSize == LB_ERR)
        return 0;

    cJavaExec = new char[iSize + 1];

    SendMessage(dc.hWndResultList, LB_GETTEXT, iSelected, (LPARAM)cJavaExec);

    WritePrivateProfileString("Freenet Launcher", "javaexec", cJavaExec, ".\\FLaunch.ini");
    delete [] cJavaExec;

    return 1;    
}

///////////////////////////////////////////////////////////////////////////////

DWORD WINAPI doDeepSearch(LPVOID lpvParameter)
{
    char *cDriveStrings, *cCurrentDrive;
    int iSize, iDriveType, iNumFound;

    iSize = GetLogicalDriveStrings(0, NULL);
    cDriveStrings = new char[iSize + 1];
    GetLogicalDriveStrings(iSize, cDriveStrings);
    
    cCurrentDrive = cDriveStrings;
	
	// Initial search should look for the java executable in the windows
	// directory (to reduce the time taken to find the interpreter)

////////////////////////////////////////////////////
//
// search of windows folders disabled - davidmcnab
//
//	while (*cCurrentDrive)
//	{
//		iDriveType = GetDriveType(cCurrentDrive);
//
//		if (iDriveType == DRIVE_FIXED && (iDriveType != DRIVE_CDROM))
//			CheckWindowsFolders(cCurrentDrive);
//
//      cCurrentDrive += strlen(cCurrentDrive) + 1;
//	}
////////////////////////////////////////////////////

	// Now do a full search of all files on all drives, remembering that
	// the user can abort the search at any time.

	cCurrentDrive = cDriveStrings;

    while (*cCurrentDrive)
	{
        iDriveType = GetDriveType(cCurrentDrive);

        if (iDriveType == DRIVE_FIXED && (iDriveType != DRIVE_CDROM))
            recursiveTraversal(cCurrentDrive);

        cCurrentDrive += strlen(cCurrentDrive) + 1;
	}

    delete [] cDriveStrings;

    SetWindowText(dc.hWndActionText, "Finished.");

	// The user did not select to update prior to the process finishing
	// so we don't want to display an extra dialog box.
	
	bUpdateBeforeTraverseComplete = false;

	// Display the done button.

    ShowWindow(dc.hWndCloseButton, SW_SHOW);

	// Hide the cancel window since it is no longer appropriate as an option

	ShowWindow(dc.hWndCancelButton, SW_HIDE);

    SetWindowText(dc.hWndStatusText, "Press Update Settings to continue.");

    iNumFound = SendMessage(dc.hWndResultList, LB_GETCOUNT, 0, 0);

    if (iNumFound == 1)
    {
		MessageBox(dc.hWndMain, 
			"Congratulations!  A Java interpreter has been found.\n\n"
			"Please click Update Settings to continue.",
			"Success", 
			MB_OK | MB_ICONINFORMATION);        
	}
    else if (iNumFound > 1)
	{
		MessageBox(dc.hWndMain, 
			"Congratulations!  Java interpreters have been found.\n\n"
            "You may choose one from the list and click Update Settings to continue.",
            "Success", 
            MB_OK | MB_ICONINFORMATION);
	}
    else
    {
	    SetWindowText(dc.hWndStatusText, "Java not found. Press Close to exit.");

		SetWindowText(dc.hWndCloseButton, "&Close");

		MessageBox(dc.hWndMain, 
			"A Java interpreter (java.exe) could not be found.\n\n"
			"Please install a suitable Java Runtime Environment.\n"
            "For more information visit http://java.sun.com and search for\n"

			"\"JRE Windows SDK\".  The latest version at time of writing is\n"

			"the Java(TM) 2 Runtime Environment JRE 1.3.1 .\n\n"

			"Note - Sun JRE 1.1.x does NOT contain a compatible java.exe\n",
            "Search failed", 
            MB_OK | MB_ICONSTOP);

		searchDone = 1; // doh - please be consistent, the search IS finished!
		return 0;
	}

	searchDone = 1;		// bad hack - use this to stop user pushing 'update settings' before search finishes

    SendMessage(dc.hWndResultList, LB_SETCURSEL, 0, 0);
    return 1;
}

void CheckWindowsFolders(char *cDrive)
{
	// Does a quick search of various potential windows
	// pathes for the java interpreter

	char cWindowsTest[MAX_PATH+1];


	// Looking in drive:\windows\ folder

	strcpy(cWindowsTest, cDrive);
	strcat(cWindowsTest, "windows\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\windows\system32\ folder

	strcat(cWindowsTest, "system32\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\windows\system\ folder

	strcpy(cWindowsTest, cDrive);
	strcat(cWindowsTest, "windows\\system\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\winnt\ folder

	strcpy(cWindowsTest, cDrive);
	strcat(cWindowsTest, "winnt\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\winnt\system32\ folder

	strcat(cWindowsTest, "system32\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\win folder
	strcpy(cWindowsTest, cDrive);
	strcat(cWindowsTest, "win\\");
	lookSpecificFolder(cWindowsTest);

	// Look in drive:\win\system32 folder

	strcat(cWindowsTest, "system32\\");
	lookSpecificFolder(cWindowsTest);

	// Lastly look in drive:\win\system folder
	
	strcpy(cWindowsTest, cDrive);
	strcat(cWindowsTest, "win\\system\\");
	lookSpecificFolder(cWindowsTest);
}


void lookSpecificFolder(char *cDirectory)
{
	if(searchIn(cDirectory) == MATCH_FOUND)
	{
		// Select the first index entry in the list.
		SendMessage(dc.hWndResultList, LB_SETCURSEL, (WPARAM)0, 0);
		ShowWindow(dc.hWndCloseButton, SW_SHOW);
		// Global variable so we can popup a window before exiting
		bUpdateBeforeTraverseComplete = true;
	}
}

///////////////////////////////////////////////////////////////////////////////

int recursiveTraversal(char *cDirectory)
{
    WIN32_FIND_DATA w32findFileData;    
    char cMask[MAX_PATH + 1];
    HANDLE hFind;
    int iRet;

	// Display the Update settings window if a match is found
    if(searchIn(cDirectory) == MATCH_FOUND)
	{
		// Select the first index entry in the list.
		SendMessage(dc.hWndResultList, LB_SETCURSEL, (WPARAM)0, 0);
		ShowWindow(dc.hWndCloseButton, SW_SHOW);
		// Global variable so we can popup a window before exiting
		bUpdateBeforeTraverseComplete = true;
	}

    strcpy(cMask, cDirectory);
    strcat(cMask, "*");

    iRet = 0;
    hFind = FindFirstFile(cMask, &w32findFileData);

    if (hFind == INVALID_HANDLE_VALUE) 
        return 0;
    
    do
	{
        if (w32findFileData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
		{
            char cSubDir[MAX_PATH];

            if (0 == strcmp(".", w32findFileData.cFileName) || 
                0 == strcmp("..", w32findFileData.cFileName))

                continue;

            strcpy(cSubDir, cDirectory);
            strcat(cSubDir, w32findFileData.cFileName);
            strcat(cSubDir, "\\");

            iRet += recursiveTraversal(cSubDir);
		}
	} while (FindNextFile(hFind, &w32findFileData));

    FindClose(hFind);
    return iRet;
}

///////////////////////////////////////////////////////////////////////////////

int parsePath(char *cPathEnvironment)
{
    int iStringLen;
    char *p;

    if (!cPathEnvironment)
        return 0;

    // replace entry separators ';' with '\0'
    //

    iStringLen = strlen(cPathEnvironment);
    p = cPathEnvironment;

    while (*p != '\0')
	{
        if (*p == ';')
            *p = '\0';

        p++;
	}

    // rescan through entries, checking for files
    //

    p = cPathEnvironment;
    while (p - cPathEnvironment < iStringLen)
    {
		searchIn(p);
        p += strlen(p) + 1;
	}

    return 0;
}

///////////////////////////////////////////////////////////////////////////////

int searchIn(char *cPath)
{
    int     iSearchFilesCount = 1;
//    char    *cSearchFiles[]   = { "jview.exe", "java.exe" };
    char    *cSearchFiles[]   = { "java.exe" }; // removed jview.exe from Java candidates - davidmcnab
    char    *cFullPath;
    int     iRet, i;
	bool	bFound = false;

    SetWindowText(dc.hWndStatusText, cPath);

    for (i = 0; i < iSearchFilesCount; i++)
	{
        cFullPath = new char[strlen(cPath) + strlen(cSearchFiles[i]) + 1];
        strcpy(cFullPath, cPath);
        strcat(cFullPath, cSearchFiles[i]);

        iRet = GetFileAttributes(cFullPath);

        if (iRet != -1 && iRet != FILE_ATTRIBUTE_DIRECTORY)
		{
            SendMessage(dc.hWndResultList, LB_ADDSTRING, 0, (LPARAM)cFullPath);
			bFound = true;
		}

        delete [] cFullPath;
	}

	// Indicate whether we have found a match, so that the calling
	// process can exit before searching the entire hard drive

    if (bFound)
		return 2;
	else
		return 1;
}
///////////////////////////////////////////////////////////////////////////////
// eof
///////////////////////////////////////////////////////////////////////////////

