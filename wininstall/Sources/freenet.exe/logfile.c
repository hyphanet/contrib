#include "windows.h"
#include "types.h"
#include "shared_data.h"
#include "logfile.h"
#include "rsrc.h"

HWND hLogFileDialogWnd = NULL;
BOOL bLogFileDialogBoxRunning = FALSE;
HANDLE hStopThreadEvent = NULL;
HANDLE hThread = NULL;
DWORD dwThreadID = 0;

int GetEditLines(HWND hwndEditBox)
{
	// simply works out how many lines of text will fit in the edit box
	RECT rect;
	TEXTMETRIC textMetric;
	HANDLE hFont = (HANDLE)SendMessage(hwndEditBox, WM_GETFONT, 0, 0);
	HDC hdc = CreateDC("DISPLAY",NULL,NULL,NULL);
	HGDIOBJ hOldObject = SelectObject(hdc, (HGDIOBJ)hFont);
	GetTextMetrics(hdc, &textMetric);
	SelectObject(hdc, hOldObject);
	DeleteDC(hdc);
	SendMessage(hwndEditBox, EM_GETRECT, 0, (LPARAM)(&rect) );
	return (int)(((float)(rect.bottom-rect.top)/(float)(textMetric.tmHeight))+.5);
}


// THIS CODE ONLY WORKS IF TextBufferSize < 4GB ....  but that's very LIKELY
// (default size of TextBufferSize is just 4KB ... )
char* ReadPrevNLines(int *pnLines, HANDLE hFile, ULARGE_INTEGER ulCurrentFilePosition, ULARGE_INTEGER *pulResultingFilePosition, char *pTextBuffer, DWORD TextBufferSize)
{
	char * pFirstOfNLines;
	char * pLastOfNLines;
	DWORD dwRead;
	int nLines = *pnLines;
	DWORD dwBytesToRead;
	ULARGE_INTEGER ulReadStartOffset;

	*pnLines = 0;
	if (nLines==0)
	{
		*pTextBuffer='\0';
		return pTextBuffer;
	}

	if (ulCurrentFilePosition.QuadPart < TextBufferSize-1)
	{
		dwBytesToRead = ulCurrentFilePosition.LowPart;
		ulReadStartOffset.QuadPart=0;
	}
	else
	{
		dwBytesToRead = TextBufferSize-1;
		ulReadStartOffset.QuadPart = ulCurrentFilePosition.QuadPart - (TextBufferSize-1);
	}

	SetFilePointer(hFile, ulReadStartOffset.LowPart, &(ulReadStartOffset.HighPart), FILE_BEGIN);
	ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
	if (GetLastError() == 0  &&  dwRead==0)
	{
		// we're at end of file...  or beyond it (if file shrunk?)
		// so reset file pointer to end of file and try again
		SetFilePointer(hFile, -(int)(TextBufferSize-1), NULL, FILE_END);
		if (GetLastError()== ERROR_NEGATIVE_SEEK)
		{
			// file smaller than TextBufferSize-1 bytes so try again, at the start of the file!
			SetFilePointer(hFile, 0, NULL, FILE_BEGIN);
		}
		dwBytesToRead = TextBufferSize-1;
		ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
	}
	// runner used to find first of N lines of text
	pFirstOfNLines = pTextBuffer+dwRead-1;
	// runner used to find last of N lines of text
	pLastOfNLines = pFirstOfNLines;
	
	// if we didn't read in a full TextBuffer then read more now:
	// e.g. if we're only 1KB into file, the above will have read that first 1KB,
	//      so now we need to read an additional 3KB (assuming TextBufferSize == 4KB)
	if (dwRead < TextBufferSize-1)
	{
		DWORD dwAlsoRead=0;
		ReadFile(hFile,pTextBuffer+dwRead,(TextBufferSize-1)-dwRead,&dwAlsoRead,NULL);
		dwRead+=dwAlsoRead;
	}

	pulResultingFilePosition->QuadPart = 0;
	pulResultingFilePosition->LowPart = SetFilePointer(hFile, 0, &(pulResultingFilePosition->HighPart), FILE_CURRENT);

	// if we failed to read any bytes, return an empty nul-terminated buffer
	if (dwRead==0)
	{
		*pTextBuffer='\0';
		return pTextBuffer;
	}


	// null-terminate end of buffer
	pTextBuffer[dwRead]='\0';

	// Find FIRST of N lines
	// (if we don't find N lines before the pointer then look for (N-found) lines after pointer too)


	// if last characters read was \n then skip backwards past it
	// and start the counting of linefeeds from BEFORE them.
	if (*pFirstOfNLines=='\n')
	{
		--pFirstOfNLines;
	}

	// count number of linefeeds, backwards from end,
	// and stop when we've counted the number we're looking for
	while ( (pFirstOfNLines>=pTextBuffer) && (nLines>0) )
	{
		if (*pFirstOfNLines=='\n')
		{
			--nLines;
			++(*pnLines);
			if (nLines==0)
			{
				++pFirstOfNLines;
				break;
			}
		}
		--pFirstOfNLines;
	}
	if (pFirstOfNLines<pTextBuffer)
	{
		pFirstOfNLines=pTextBuffer;
		++(*pnLines);
	}

	if ( nLines != 0 )
	{
		// we couldn't find enough lines of text before the current file pointer
		// so look in the part of the buffer containing data read AFTER the current file pointer
		// (currently pointed to by nLastOfNLines)

		// if last characters read at current file pointer was not \n and the next character read after
		// current file pointer is \n then skip over the \n
		// and start the counting of linefeeds from AFTER that.
		if ( (pLastOfNLines>pTextBuffer) && (*(pLastOfNLines-1)!='\n') )
		{
			if (*pLastOfNLines=='\n')
			{
				++pLastOfNLines;
			}
			else
			{
				// the code for finding the FirstOfNLines has already counted this line...
				// one fewer line terminating \n to look for then
				--nLines; 
			}
		}

		
		// count number of linefeeds, forwards from here to end of buffer,
		// and stop when we've counted the number we're looking for
		while ( (pLastOfNLines<pTextBuffer+dwRead) && (*pLastOfNLines) && (nLines>0) )
		{
			if (*pLastOfNLines=='\n')
			{
				--nLines;
				++(*pnLines);
			}
			++pLastOfNLines;
		}
		if ( (pLastOfNLines>=pTextBuffer+dwRead) || (*pLastOfNLines=='\0') )
		{
			pLastOfNLines=pTextBuffer+dwRead-1;
			++(*pnLines);
		}
		// when finished, pLastOfNLines will either point to last non-nul character in buffer
		// or last \n of Nth line (with pFirstOfNLines pointing to first character of 1st line)
	}

	// get rid of trailing \n or \r\n in block of text if any
	if (*pLastOfNLines=='\n')
	{
		*pLastOfNLines='\0';
		pLastOfNLines--;
		if ( (pLastOfNLines>=pTextBuffer) && (*pLastOfNLines=='\r') )
		{
			*pLastOfNLines='\0';
		}
	}
	else if (*pLastOfNLines=='\r')
	{
		*pLastOfNLines='\0';
	}

	return pFirstOfNLines;
}



void FormatFileSize(char *pBuffer,DWORDLONG dwlFileSize)
{
	char* szunit;
	DWORD shramount,dwForDec,dwForRem;
	float divamount;

	if (dwlFileSize >= Int64ShllMod32(1,20) )
	{
		// Megabytes or gigabytes
		if (dwlFileSize >= Int64ShllMod32(1,30) )
		{
			// Gigabytes
			shramount=30;
			divamount=10.24f;
			szunit="GB";
		}
		else
		{
			// Megabytes
			shramount=20;
			divamount=10.24f;
			szunit="MB";
		}
	}
	else
	{
		// bytes or kilobytes
		if (dwlFileSize >= Int64ShllMod32(1,10) )
		{
			// Kilobytes
			shramount=10;
			divamount=10.24f;
			szunit="KB";
		}
		else
		{
			shramount=0;
			divamount=1.0f;
			szunit="bytes";
		}
	}
	dwForDec = (DWORD)Int64ShraMod32(dwlFileSize,shramount);
	dwForRem = (DWORD)(((float)((DWORD)(dwlFileSize-Int64ShllMod32(dwForDec,shramount))))/divamount);
	if (dwForRem>99)
	{
		dwForRem=99;
	}
	if (shramount==0)
	{
		wsprintf(pBuffer, "%lu %s", dwForDec, szunit);
	}
	else
	{
		wsprintf(pBuffer, "%lu.%02u %s", dwForDec, dwForRem, szunit);
	}
}


DWORD CALLBACK __stdcall LogFileNotifyProc(LPVOID lpvParam)
{
	HWND hwndDialog = (HWND)lpvParam;
	HWND hwndEditBox = GetDlgItem(hwndDialog,IDC_EDIT1);
	HANDLE hFindChanges = FindFirstChangeNotification(szHomeDirectory, FALSE, FILE_NOTIFY_CHANGE_SIZE);
	const HANDLE hEvents[] = {hStopThreadEvent,hFindChanges};
	DWORD dwWaitResult;
	char pTextBuffer[4097];
	char * pTextPointer;
	BOOL bQuit=FALSE;
	HANDLE hLogFile=NULL;
	BOOL bTrackChanges;
	DWORDLONG dwlPrevFileSize = 0;
	char szFileSize[64];
	ULARGE_INTEGER dwlFileSize;
	ULARGE_INTEGER ulCurrentFilePosition, ulResultingFilePosition;
	int nEditLines, nLines;
	char cszWindowTitle[64];
	char szWindowTitle[128];
	char szLogFileName[MAX_PATH];

	HWND hwndscrollbar = GetDlgItem(hwndDialog,IDC_SCROLLBAR1);

	GetWindowText(hwndDialog,cszWindowTitle,sizeof(cszWindowTitle));

	// get number of lines that edit box can display on screen...
	nEditLines = GetEditLines(hwndEditBox);

	// form filename of freenet logfile
	strcpy(szLogFileName, szHomeDirectory);
	strcat(szLogFileName, "\\freenet.log");
	
	if (hLogFile==NULL || hLogFile==INVALID_HANDLE_VALUE)
	{
			hLogFile = CreateFile(szLogFileName,
			  GENERIC_READ, 
			  FILE_SHARE_DELETE | FILE_SHARE_READ | FILE_SHARE_WRITE,
			  NULL,
			  OPEN_EXISTING,
			  FILE_FLAG_SEQUENTIAL_SCAN,
			  NULL);
	}

	// set file pointer to zero, initially
	ulCurrentFilePosition.QuadPart = 0;

////// SAME AS BELOW but with bTrackChanges implicitly true here
	dwlFileSize.LowPart = GetFileSize(hLogFile, &dwlFileSize.HighPart);
	if (GetLastError() == ERROR_INVALID_HANDLE)
	{
		dwlFileSize.QuadPart=0;
		CloseHandle(hLogFile);
		hLogFile=NULL;
	}
	FormatFileSize(szFileSize,dwlFileSize.QuadPart);
	wsprintf(szWindowTitle, "%s (%s)", cszWindowTitle, szFileSize);
	SetWindowText(hwndDialog, szWindowTitle);
	dwlPrevFileSize = dwlFileSize.QuadPart;

	ulCurrentFilePosition.QuadPart = dwlFileSize.QuadPart;

	if (hLogFile!=NULL && hLogFile!=INVALID_HANDLE_VALUE)
	{
		nLines = nEditLines;
		pTextPointer = ReadPrevNLines(&nLines, hLogFile, ulCurrentFilePosition, &ulResultingFilePosition, pTextBuffer, sizeof(pTextBuffer) );
	}
	else
	{
		pTextBuffer[0]='\0';
		pTextPointer = pTextBuffer;
		nLines=0;
		ulResultingFilePosition.QuadPart=0;
	}

	SetWindowText(GetDlgItem(hwndDialog, IDC_EDIT1), pTextPointer);

	if (nLines < nEditLines)
	{
		EnableScrollBar(hwndscrollbar,SB_CTL, ESB_DISABLE_BOTH);
	}
	else
	{
		int estNumberOfLinesInFile;
		int estLineWeAreOnNow;
		SCROLLINFO si;
		if (dwlFileSize.QuadPart > UInt32x32To64((int)(0x7fffffff),80) )
		{
			estNumberOfLinesInFile = (int)(0x7fffffff);
		}
		else
		{
			estNumberOfLinesInFile = (int)((dwlFileSize.QuadPart)/80);
		}
		if (ulCurrentFilePosition.QuadPart >= dwlFileSize.QuadPart-1)
		{
			estLineWeAreOnNow = estNumberOfLinesInFile;
		}
		else if (ulCurrentFilePosition.QuadPart > UInt32x32To64((int)(0x7fffffff),80) )
		{
			estLineWeAreOnNow = (int)(0x7fffffff);
		}
		else
		{
			estLineWeAreOnNow = (int)((ulCurrentFilePosition.QuadPart)/80);
			if (estLineWeAreOnNow>nEditLines)
			{
				estLineWeAreOnNow-=nEditLines;
			}
			else
			{
				estLineWeAreOnNow=0;
			}
		}
		si.nMin = 0;
		si.nMax = estNumberOfLinesInFile;
		//si.nPos = si.nMax-estLineWeAreOnNow; // because 0 at BOTTOM of scrollbar and MAX at TOP of scrollbar
		si.nPos = estLineWeAreOnNow;
		si.nPage = nEditLines;
		si.cbSize = sizeof(SCROLLINFO);
		si.fMask = SIF_DISABLENOSCROLL | SIF_PAGE | SIF_POS | SIF_RANGE;
		EnableScrollBar(hwndscrollbar,SB_CTL, ESB_ENABLE_BOTH);
		SetScrollInfo(hwndscrollbar,SB_CTL,&si,TRUE);
	}
//// ^^^^ SAME AS BELOW


	while(!bQuit)
	{
		dwWaitResult = WaitForMultipleObjects(2, hEvents, FALSE /* i.e. WAIT_ANY */, 5000);
		switch (dwWaitResult)
		{
		case WAIT_OBJECT_0:
		case WAIT_ABANDONED_0:
		default:
			// Quit Thread - either quit event was signalled or there was an error
			bQuit = TRUE;
			break;

		case WAIT_OBJECT_0+1:
		case WAIT_ABANDONED_0+1:
			// Directory change - interpret this as a change in freenet.log within the directory
			// if freenet.log has not grown then the following code will fairly efficiently do nothing.
			FindNextChangeNotification(hFindChanges);
			// and fall through to:

		case WAIT_TIMEOUT:
			{
				if (hLogFile==NULL || hLogFile==INVALID_HANDLE_VALUE)
				{
						hLogFile = CreateFile(szLogFileName,
						  GENERIC_READ, 
						  FILE_SHARE_DELETE | FILE_SHARE_READ | FILE_SHARE_WRITE,
						  NULL,
						  OPEN_EXISTING,
						  FILE_FLAG_SEQUENTIAL_SCAN,
						  NULL);
				}

				dwlFileSize.LowPart = GetFileSize(hLogFile, &dwlFileSize.HighPart);
				if (GetLastError() == ERROR_INVALID_HANDLE)
				{
					dwlFileSize.QuadPart=0;
					CloseHandle(hLogFile);
					hLogFile=NULL;
				}
				if (dwlFileSize.QuadPart!=dwlPrevFileSize)
				{
					FormatFileSize(szFileSize,dwlFileSize.QuadPart);
					wsprintf(szWindowTitle, "%s (%s)", cszWindowTitle, szFileSize);
					SetWindowText(hwndDialog, szWindowTitle);
					dwlPrevFileSize = dwlFileSize.QuadPart;

					bTrackChanges = (IsDlgButtonChecked(hwndDialog, IDC_TRACKCHANGES) == BST_CHECKED );
					if (bTrackChanges)
					{
						ulCurrentFilePosition.QuadPart = dwlFileSize.QuadPart;
					}

					if (hLogFile!=NULL && hLogFile!=INVALID_HANDLE_VALUE)
					{
						nLines = nEditLines;
						pTextPointer = ReadPrevNLines(&nLines, hLogFile, ulCurrentFilePosition, &ulResultingFilePosition, pTextBuffer, sizeof(pTextBuffer) );
					}
					else
					{
						pTextBuffer[0]='\0';
						pTextPointer = pTextBuffer;
						nLines=0;
						ulResultingFilePosition.QuadPart=0;
					}

					SetWindowText(GetDlgItem(hwndDialog, IDC_EDIT1), pTextPointer);

					if (nLines < nEditLines)
					{
						EnableScrollBar(hwndscrollbar,SB_CTL, ESB_DISABLE_BOTH);
					}
					else
					{
						int estNumberOfLinesInFile;
						int estLineWeAreOnNow;
						SCROLLINFO si;
						if (dwlFileSize.QuadPart > UInt32x32To64((int)(0x7fffffff),80) )
						{
							estNumberOfLinesInFile = (int)(0x7fffffff);
						}
						else
						{
							estNumberOfLinesInFile = (int)((dwlFileSize.QuadPart)/80);
						}
						if (ulCurrentFilePosition.QuadPart >= dwlFileSize.QuadPart-1)
						{
							estLineWeAreOnNow = estNumberOfLinesInFile;
						}
						else if (ulCurrentFilePosition.QuadPart > UInt32x32To64((int)(0x7fffffff),80) )
						{
							estLineWeAreOnNow = (int)(0x7fffffff);
						}
						else
						{
							estLineWeAreOnNow = (int)((ulCurrentFilePosition.QuadPart)/80);
							if (estLineWeAreOnNow>nEditLines)
							{
								estLineWeAreOnNow-=nEditLines;
							}
							else
							{
								estLineWeAreOnNow=0;
							}
						}
						si.nMin = 0;
						si.nMax = estNumberOfLinesInFile;
						//si.nPos = si.nMax-estLineWeAreOnNow; // because 0 at BOTTOM of scrollbar and MAX at TOP of scrollbar
						si.nPos = estLineWeAreOnNow;
						si.nPage = nEditLines;
						si.cbSize = sizeof(SCROLLINFO);
						si.fMask = SIF_DISABLENOSCROLL | SIF_PAGE | SIF_POS | SIF_RANGE;
						EnableScrollBar(hwndscrollbar,SB_CTL, ESB_ENABLE_BOTH);
						SetScrollInfo(hwndscrollbar,SB_CTL,&si,TRUE);
					}
				}
			}
			break;
		}
	}

	FindCloseChangeNotification(hFindChanges);
	CloseHandle(hLogFile);
	return 0;
}




	
BOOL CALLBACK __stdcall LogFileViewerProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;

	switch (uMsg)
	{
	case WM_INITDIALOG:
		hStopThreadEvent = CreateEvent(NULL,TRUE,FALSE,NULL);
		hThread = CreateThread(NULL,0,LogFileNotifyProc,(LPVOID)(hwndDialog),0,&dwThreadID);
		SetClassLong(hwndDialog, GCL_HICON, (LONG)(hHopsEnabledIcon) );
		return TRUE;
	
	case WM_CLOSE:
		bQuit=TRUE;
		break;
	
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDCOPY:
			SetFocus(GetDlgItem(hwndDialog,IDC_EDIT1));
			return TRUE;
		case IDOK:
			bQuit=TRUE;
			break;
		}
		break;

	default:
		return FALSE;
	}


	if (bQuit)
	{
		// Quit flag was set in above message processing
		// destroy and cleanup
		if (hThread)
		{
			if (hStopThreadEvent)
			{
				SetEvent(hStopThreadEvent);
			}
			WaitForSingleObject(hThread,INFINITE);
			CloseHandle(hThread);
		}
		if (hStopThreadEvent)
		{
			CloseHandle(hStopThreadEvent);
		}

		DestroyWindow(hwndDialog);
		LOCK(LOGFILEDIALOGBOX);
		bLogFileDialogBoxRunning=FALSE;
		UNLOCK(LOGFILEDIALOGBOX);
		return TRUE;  //  true because the message obviously WAS processed, or else we wouldn't have set Quit flag
	}

	return FALSE;
}


void CreateLogfileViewer(HWND hWnd)
{
	// if logfile thread is already created, highlight it
	LOCK(LOGFILEDIALOGBOX);
	if (bLogFileDialogBoxRunning)
	{
		UNLOCK(LOGFILEDIALOGBOX);
		// if so - set focus to it:
		SetForegroundWindow(hLogFileDialogWnd);
	}
	else
	{
		// else create the dialog box and its thread:
		// ...
		hLogFileDialogWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_LOGFILEVIEWER), hWnd, LogFileViewerProc);
		if (hLogFileDialogWnd != NULL)
		{
			bLogFileDialogBoxRunning=true;
		}
		UNLOCK(LOGFILEDIALOGBOX);
	}
}

