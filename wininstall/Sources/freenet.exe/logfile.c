#include "stdafx.h"
#include "logfile.h"

HWND hLogFileDialogWnd = NULL;
BOOL bLogFileDialogBoxRunning = FALSE;
HANDLE hThread = NULL;

struct threadData
{
	HWND hwndDialog;
	HWND hwndEditBox;
	HWND hwndScrollBar;
	HANDLE hLogFileHandleMutex;
	ULARGE_INTEGER ulFirstLineFileOffset, ulCurrentLineFileOffset, ulLastLineFileOffset;
	ULARGE_INTEGER dwlFileSize;
	FILETIME ftLastModified;
	HANDLE hStopThreadEvent;
	char szLogFileName[MAX_PATH*4];
	int nEditLines;
} d;


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


// reads floor(dwTextBufferSize/2) bytes from before ulCurrentFilePosition and
// ceil(dwTextBufferSize/2) bytes from after (including) ulCurrentFilePosition
// Puts all data into pTextBuffer, and returns a pointer to somewhere in the
// middle of that buffer, referring to the byte actually read from CurrentFilePosition
//  (effectively char * pCurrentPointer)
// Also returns a modified ulCurrentFilePosition if the various reads went off
// the start or end of the file
char* ReadIntoFileBuffer(HANDLE hFile, ULARGE_INTEGER *pulCurrentFilePosition, char *pTextBuffer, DWORD dwTextBufferSize, /* out */ DWORD *pdwBytesReadBefore, /* out */ DWORD *pdwBytesReadAfter)
{
	char * pCurrentPointer;

	DWORD dwBytesBefore, dwBytesAfter;
	DWORD dwBytesToRead, dwBytesReadBefore, dwBytesReadAfter;
	ULARGE_INTEGER ulReadStartOffset;
	ULARGE_INTEGER ulActualCurrentPosition;
	BOOL bReadSuccess;
	char * szRunner;

	// we always want a nul-terminated buffer
	pTextBuffer[dwTextBufferSize-1]='\0';
	--dwTextBufferSize;


	dwBytesBefore = dwTextBufferSize/2;
	dwBytesAfter = dwTextBufferSize - dwBytesBefore;

	pCurrentPointer = pTextBuffer;

	dwBytesReadBefore=0;
	dwBytesToRead = dwBytesBefore;
	if (pulCurrentFilePosition->QuadPart < dwBytesBefore)
	{
		// well... read as many bytes as we can then
		dwBytesToRead = pulCurrentFilePosition->LowPart;
	}
	ulReadStartOffset.QuadPart = pulCurrentFilePosition->QuadPart - dwBytesToRead;
	if (dwBytesToRead > 0)
	{
		SetFilePointer(hFile, ulReadStartOffset.LowPart, &(ulReadStartOffset.HighPart), FILE_BEGIN);
		bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwBytesReadBefore,NULL);
	}

	if (bReadSuccess)
	{
		// find out the resulting file pointer for the block of data we've just read
		ulActualCurrentPosition.QuadPart=0;
		ulActualCurrentPosition.LowPart = SetFilePointer(hFile, 0, &(ulActualCurrentPosition.HighPart), FILE_CURRENT);
		if (ulActualCurrentPosition.LowPart!=0xffffffff || GetLastError()==NO_ERROR)
		{
			pulCurrentFilePosition->QuadPart = ulActualCurrentPosition.QuadPart;
		}
	}
	// otherwise leave *(pulCurrentFilePosition) alone... basically, we only won't
	// execute the above code if there was a file seeking problem.

	
	pCurrentPointer = pTextBuffer + dwBytesReadBefore;

	// now read from the current file position too:
	dwBytesToRead = dwBytesAfter;
	dwBytesReadAfter=0;
	ReadFile(hFile,pCurrentPointer,dwBytesToRead,&dwBytesReadAfter,NULL);

	*(pCurrentPointer + dwBytesReadAfter) = '\0'; // nul-terminate buffer
	
	if (pdwBytesReadBefore!=NULL)
	{
		*pdwBytesReadBefore = dwBytesReadBefore;
	}
	if (pdwBytesReadAfter!=NULL)
	{
		*pdwBytesReadAfter = dwBytesReadAfter;
	}

	// change any NULs embedded within log to SPACE characters
	szRunner = pTextBuffer + strlen(pTextBuffer);
	while ( szRunner< pCurrentPointer+dwBytesReadAfter )
	{
		*szRunner=' ';
		szRunner = szRunner + strlen(szRunner);
	}		
		

	return pCurrentPointer;
}
	
char* FindStartOfCurrentLine(char * pTextBuffer, const char * const pAbsoluteStartOfTextBuffer)
{
	while (pTextBuffer>pAbsoluteStartOfTextBuffer)
	{
		--pTextBuffer;
		if (*pTextBuffer=='\n')
		{
			++pTextBuffer;
			break;
		}
	}
	return pTextBuffer;
}

char* FindEndOfCurrentLine(char * pTextBuffer, const char * const pAbsoluteEndOfTextBuffer)
{
	while (pTextBuffer<pAbsoluteEndOfTextBuffer)
	{
		if (*pTextBuffer=='\n')
		{
			break;
		}
		++pTextBuffer;
	}
	return pTextBuffer;
}


// Modifies pTextBuffer and returns a pointer into it, such that there are (at most) nLines of
// text between the pointer returned and the NUL terminating character.
// The first line is either pStartLookingHere-nLines or the start of the text buffer (pAbsoluteStartOfTextBuffer)
// The last line is either pStartLookingHere or pStartLookingHere+ at most nLines or the end of the text buffer (pAbsoluteEndOfTextBuffer)
char* ReadPrevNLines( /* in,out */ int *pnLines, char * pStartLookingHere, char * pAbsoluteStartOfTextBuffer, char * pAbsoluteEndOfTextBuffer, /* out */ char **ppEndOfLastLine)
{
	char * pLastLine;
	char * pFirstLine = pStartLookingHere;
	int nLinesFound=0, nLinesToFind=*pnLines;

	if ( (nLinesToFind==0) ||
		 (pAbsoluteStartOfTextBuffer==pAbsoluteEndOfTextBuffer) ||
		 (pStartLookingHere < pAbsoluteStartOfTextBuffer) ||
		 (pStartLookingHere > pAbsoluteEndOfTextBuffer) )
	{
		*pStartLookingHere = '\0';
		if (ppEndOfLastLine)
		{
			*ppEndOfLastLine = pStartLookingHere;
		}
		return pStartLookingHere;
	}

	*pAbsoluteEndOfTextBuffer = '\0';
	if (pStartLookingHere==pAbsoluteEndOfTextBuffer)
	{
		--pStartLookingHere;
	}
	--pAbsoluteEndOfTextBuffer;
	
	// find start and end of current line
	pLastLine = FindEndOfCurrentLine(pStartLookingHere, pAbsoluteEndOfTextBuffer);
	pFirstLine = FindStartOfCurrentLine(pStartLookingHere, pAbsoluteStartOfTextBuffer);

	// find nLines, backwards, including this line
	--nLinesToFind;
	++nLinesFound;
	if (pFirstLine > pAbsoluteStartOfTextBuffer)
	{
		while (	nLinesToFind>0 )
		{
			--nLinesToFind;
			++nLinesFound;
			--pFirstLine;
			pFirstLine = FindStartOfCurrentLine(pFirstLine, pAbsoluteStartOfTextBuffer);
			if (pFirstLine==pAbsoluteStartOfTextBuffer)
			{
				break;
			}
		}
	}

	if (nLinesToFind>0)
	{
		// ok, try to find remainder of lines after this line
		if (pLastLine < pAbsoluteEndOfTextBuffer)
		{
			while (	nLinesToFind>0 )
			{
				--nLinesToFind;
				++nLinesFound;
				++pLastLine;
				pLastLine = FindEndOfCurrentLine(pLastLine, pAbsoluteEndOfTextBuffer);
				if (pLastLine==pAbsoluteEndOfTextBuffer)
				{
					break;
				}
			}
		}
	}

	if (ppEndOfLastLine)
	{
		*ppEndOfLastLine = pLastLine;
	}

	if (*pLastLine=='\n')
	{
		*pLastLine='\0';
		if (pLastLine > pAbsoluteStartOfTextBuffer)
		{
			--pLastLine;
			if (*pLastLine=='\r')
			{
				*pLastLine='\0';
				if (pLastLine > pAbsoluteStartOfTextBuffer)
				{
					--pLastLine;
				}
			}
		}
	}

	*pnLines = nLinesFound;
	return pFirstLine;
}

// Modifies pTextBuffer and returns a pointer into it, such that there are (at most) nLines of
// text between the pointer returned and the NUL terminating character.
// The last line is either pStartLookingHere+nLines or the end of the text buffer (pAbsoluteEndOfTextBuffer)
// The first line is either pStartLookingHere or pStartLookingHere- at most nLines or the start of the text buffer (pAbsoluteStartOfTextBuffer)
char* ReadNextNLines( /* in,out */ int *pnLines, char * pStartLookingHere, char * pAbsoluteStartOfTextBuffer, char * pAbsoluteEndOfTextBuffer, /* out */ char **ppEndOfLastLine)
{
	char * pLastLine;
	char * pFirstLine = pStartLookingHere;
	int nLinesFound=0, nLinesToFind=*pnLines;

	if ( (nLinesToFind==0) ||
		 (pAbsoluteStartOfTextBuffer==pAbsoluteEndOfTextBuffer) ||
		 (pStartLookingHere < pAbsoluteStartOfTextBuffer) ||
		 (pStartLookingHere > pAbsoluteEndOfTextBuffer) )
	{
		*pStartLookingHere = '\0';
		if (ppEndOfLastLine)
		{
			*ppEndOfLastLine = pStartLookingHere;
		}
		return pStartLookingHere;
	}

	*pAbsoluteEndOfTextBuffer = '\0';
	if (pStartLookingHere==pAbsoluteEndOfTextBuffer)
	{
		--pStartLookingHere;
	}
	--pAbsoluteEndOfTextBuffer;
	
	// find start and end of current line
	pLastLine = FindEndOfCurrentLine(pStartLookingHere, pAbsoluteEndOfTextBuffer);
	pFirstLine = FindStartOfCurrentLine(pStartLookingHere, pAbsoluteStartOfTextBuffer);

	// find nLines, forwards, including this line
	--nLinesToFind;
	++nLinesFound;
	if (pLastLine < pAbsoluteEndOfTextBuffer)
	{
		while (	nLinesToFind>0 )
		{
			--nLinesToFind;
			++nLinesFound;
			++pLastLine;
			pLastLine = FindEndOfCurrentLine(pLastLine, pAbsoluteEndOfTextBuffer);
			if (pLastLine==pAbsoluteEndOfTextBuffer)
			{
				break;
			}
		}
	}

	if (nLinesToFind>0)
	{
		// ok, try to find remainder of lines before this line
		if (pFirstLine > pAbsoluteStartOfTextBuffer)
		{
			while (	nLinesToFind>0 )
			{
				--nLinesToFind;
				++nLinesFound;
				--pFirstLine;
				pFirstLine = FindStartOfCurrentLine(pFirstLine, pAbsoluteStartOfTextBuffer);
				if (pFirstLine==pAbsoluteStartOfTextBuffer)
				{
					break;
				}
			}
		}
	}

	if (ppEndOfLastLine)
	{
		*ppEndOfLastLine = pLastLine;
	}

	if (*pLastLine=='\n')
	{
		*pLastLine='\0';
		if (pLastLine > pAbsoluteStartOfTextBuffer)
		{
			--pLastLine;
			if (*pLastLine=='\r')
			{
				*pLastLine='\0';
				if (pLastLine > pAbsoluteStartOfTextBuffer)
				{
					--pLastLine;
				}
			}
		}
	}

	*pnLines = nLinesFound;
	return pFirstLine;
}


void FormatFileSize(char *pBuffer,DWORDLONG dwlFileSize)
{
	char* szunit;
	DWORD shramount;

	if (dwlFileSize >= Int64ShllMod32(1,20) )
	{
		// Megabytes or gigabytes
		if (dwlFileSize >= Int64ShllMod32(1,30) )
		{
			// Gigabytes
			shramount=30;
			szunit="GB";
		}
		else
		{
			// Megabytes
			shramount=20;
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
			szunit="KB";
		}
		else
		{
			shramount=0;
		}
	}
	if (shramount==0)
	{
		wsprintf(pBuffer, "%lu bytes", dwlFileSize);
	}
	else
	{
		DWORD dwForDec = (DWORD)Int64ShraMod32(dwlFileSize,shramount);
		DWORD dwForRem = (DWORD)(((float)((DWORD)(Int64ShraMod32(dwlFileSize,shramount-10)-Int64ShllMod32(dwForDec,10))))/10.24);
		wsprintf(pBuffer, "%lu.%02u %s", dwForDec, dwForRem, szunit);
	}
}


void UpdateLogfileWindow (HWND hwndEditBox, HWND hwndScrollBar, const char * pTextPointer, int nLines, int nEditLines, DWORDLONG dwlFileSize, DWORDLONG ulFirstLineFilePosition, DWORDLONG ulLastLineFilePosition)
{
	char szBuffer[32767];
	size_t szBufferLen;
	char *szRunner = szBuffer;

	strncpy(szBuffer,pTextPointer,32766);
	szBuffer[32766]='\0';
	szBufferLen = 1+strlen(szBuffer);
	while (szRunner<szBuffer+32766 && *szRunner)
	{
		char * szNewline = strchr(szRunner, '\n');
		if (szNewline==NULL || *szNewline!='\n')
			break;
		if ( (szRunner==szNewline) || (*(szNewline-1) != '\r') )
		{
			szRunner = szNewline;
			if (szBufferLen < 32766)
			{
				memmove(szRunner+1, szRunner, szBufferLen);
				++szBufferLen;
			}
			else
			{
				memmove(szRunner+1, szRunner, szBufferLen-1);
				szBuffer[32766]='\0';
			}
			*szRunner = '\r';
			++szRunner;
		}
		++szRunner;
	}

	SetWindowText(hwndEditBox, szBuffer);

	if (nLines < nEditLines)
	{
		EnableScrollBar(hwndScrollBar,SB_CTL, ESB_DISABLE_BOTH);
	}
	else
	{
		SCROLLINFO si;
		si.nMin=0;
		si.cbSize = sizeof(SCROLLINFO);
		if (dwlFileSize <= 0x7fffffff )
		{
			si.nMax = (int)dwlFileSize;
			si.nPos = (int)(ulFirstLineFilePosition);
			si.nPage = (int)(ulLastLineFilePosition-ulFirstLineFilePosition);
		}
		else
		{
			float posPerByte = ((float)0x7fffffff)/((float)(LONGLONG)dwlFileSize);
			si.nMax = 0x7fffffff;
			si.nPos = (int)((float)(LONGLONG)ulFirstLineFilePosition*posPerByte);
			si.nPage = (int)((float)(LONGLONG)(ulLastLineFilePosition-ulFirstLineFilePosition)*posPerByte);
		}
		si.fMask = SIF_DISABLENOSCROLL | SIF_PAGE | SIF_POS | SIF_RANGE;
		EnableScrollBar(hwndScrollBar,SB_CTL, ESB_ENABLE_BOTH);
		SetScrollInfo(hwndScrollBar,SB_CTL,&si,TRUE);
	}
}

HANDLE UpdateFileDetails(struct threadData *d)
{
	HANDLE hLogFile;
	DWORD dwError;

	d->dwlFileSize.QuadPart=0;
	d->ftLastModified.dwLowDateTime=0;
	d->ftLastModified.dwHighDateTime=0;

	hLogFile = CreateFile(d->szLogFileName,
						  GENERIC_READ, 
						  FILE_SHARE_DELETE | FILE_SHARE_READ | FILE_SHARE_WRITE,
						  NULL,
						  OPEN_EXISTING,
						  FILE_FLAG_RANDOM_ACCESS,
						  NULL);

	if (hLogFile==NULL || hLogFile==INVALID_HANDLE_VALUE)
	{
		// something went wrong... perhaps the operating system
		// doesn't support FILE_SHARE_DELETE  (e.g. Win9x)
		hLogFile = CreateFile(d->szLogFileName,
							  GENERIC_READ, 
							  FILE_SHARE_READ | FILE_SHARE_WRITE,
							  NULL,
							  OPEN_EXISTING,
							  FILE_FLAG_RANDOM_ACCESS,
							  NULL);
	}
	if ( hLogFile==NULL || hLogFile==INVALID_HANDLE_VALUE)
	{
		dwError = GetLastError();
		return NULL;
	}

	d->dwlFileSize.LowPart = GetFileSize( hLogFile, &(d->dwlFileSize.HighPart) );
	if (d->dwlFileSize.LowPart==0xffffffff)
	{
		dwError = GetLastError();
		if (dwError != ERROR_SUCCESS)
		{
			CloseHandle( hLogFile);
			d->dwlFileSize.QuadPart=0;
			return NULL;
		}
	}
	
	if (!GetFileTime(hLogFile,NULL,NULL,&(d->ftLastModified) ))
	{
		dwError = GetLastError();
		d->ftLastModified.dwLowDateTime=0;
		d->ftLastModified.dwHighDateTime=0;
		CloseHandle(hLogFile);
		return NULL;
	}

	return hLogFile;
}

DWORD CALLBACK __stdcall LogFileNotifyProc(LPVOID lpvParam)
{
	struct threadData * d = (struct threadData *)lpvParam;

	HANDLE hFindChanges = FindFirstChangeNotification(szHomeDirectory,
						  FALSE,  //  only monitor this directory, i.e. not subdirs
						  FILE_NOTIFY_CHANGE_FILE_NAME|FILE_NOTIFY_CHANGE_LAST_WRITE);
	const HANDLE hEvents[] = {d->hStopThreadEvent,hFindChanges};
	DWORD dwWaitResult;
	char pTextBuffer[32767];
	char * pTextPointer;
	BOOL bQuit=FALSE;
	BOOL bTrackChanges;
	DWORDLONG dwlPrevFileSize = 0;
	char szFileSize[64];
	int nLines;
	ULARGE_INTEGER ulFromHereFilePosition;
	FILETIME ftLastModified;
	HANDLE hLogFile;
	DWORD dwBytesReadBefore, dwBytesReadAfter;
	char * pStartOfFirstLinePointer, * pEndOfLastLinePointer;

	char cszWindowTitle[64];
	char szWindowTitle[128];
	GetWindowText(d->hwndDialog,cszWindowTitle,sizeof(cszWindowTitle));

	WaitForSingleObject(d->hLogFileHandleMutex,INFINITE);
	hLogFile = UpdateFileDetails(d);
	memcpy(&ftLastModified,&(d->ftLastModified),sizeof(FILETIME));
	FormatFileSize(szFileSize,d->dwlFileSize.QuadPart);
	wsprintf(szWindowTitle, "%s (%s)", cszWindowTitle, szFileSize);
	SetWindowText(d->hwndDialog, szWindowTitle);
	ulFromHereFilePosition.QuadPart = d->ulCurrentLineFileOffset.QuadPart;
	nLines = d->nEditLines;
	pTextPointer = ReadIntoFileBuffer(hLogFile, &ulFromHereFilePosition, pTextBuffer, sizeof(pTextBuffer), &dwBytesReadBefore, &dwBytesReadAfter);
	pStartOfFirstLinePointer = ReadPrevNLines( &nLines, pTextPointer, pTextPointer-dwBytesReadBefore, pTextPointer+dwBytesReadAfter, &pEndOfLastLinePointer);
	d->ulFirstLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) - (pTextPointer-pStartOfFirstLinePointer);
	d->ulLastLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) + (pEndOfLastLinePointer-pTextPointer);
	UpdateLogfileWindow(d->hwndEditBox, d->hwndScrollBar, pStartOfFirstLinePointer, nLines, d->nEditLines, d->dwlFileSize.QuadPart, d->ulFirstLineFileOffset.QuadPart, d->ulLastLineFileOffset.QuadPart);
	if (hLogFile)
	{
		CloseHandle(hLogFile);
		hLogFile=NULL;
	}
	ReleaseMutex(d->hLogFileHandleMutex);

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
				ftLastModified = d->ftLastModified;
				WaitForSingleObject(d->hLogFileHandleMutex,INFINITE);
				hLogFile = UpdateFileDetails(d);
				if (CompareFileTime(&ftLastModified, &(d->ftLastModified)) != 0)
				{
					memcpy(&(d->ftLastModified),&ftLastModified,sizeof(FILETIME));

					FormatFileSize(szFileSize,d->dwlFileSize.QuadPart);
					wsprintf(szWindowTitle, "%s (%s)", cszWindowTitle, szFileSize);
					SetWindowText(d->hwndDialog, szWindowTitle);

					bTrackChanges = (IsDlgButtonChecked(d->hwndDialog, IDC_TRACKCHANGES) == BST_CHECKED );
					if (bTrackChanges)
					{
						ulFromHereFilePosition.QuadPart = d->dwlFileSize.QuadPart;
					}
					else
					{
						ulFromHereFilePosition.QuadPart = d->ulLastLineFileOffset.QuadPart;
					}
					nLines = d->nEditLines;
					pTextPointer = ReadIntoFileBuffer(hLogFile, &ulFromHereFilePosition, pTextBuffer, sizeof(pTextBuffer), &dwBytesReadBefore, &dwBytesReadAfter);
					pStartOfFirstLinePointer = ReadPrevNLines( &nLines, pTextPointer, pTextPointer-dwBytesReadBefore, pTextPointer+dwBytesReadAfter, &pEndOfLastLinePointer);
					d->ulFirstLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) - (pTextPointer-pStartOfFirstLinePointer);
					d->ulLastLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) + (pEndOfLastLinePointer-pTextPointer);
					UpdateLogfileWindow(d->hwndEditBox, d->hwndScrollBar, pStartOfFirstLinePointer, nLines, d->nEditLines, d->dwlFileSize.QuadPart, d->ulFirstLineFileOffset.QuadPart, d->ulLastLineFileOffset.QuadPart);
				}
				if (hLogFile)
				{
					CloseHandle(hLogFile);
					hLogFile=NULL;
				}
				ReleaseMutex(d->hLogFileHandleMutex);

			}
			break;
		}
	}

	FindCloseChangeNotification(hFindChanges);
	return 0;
}




	
BOOL CALLBACK __stdcall LogFileViewerProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;
	HANDLE hLogFile;
	DWORD dwBytesReadBefore,dwBytesReadAfter;
	char * pStartOfFirstLinePointer, * pEndOfLastLinePointer;


	switch (uMsg)
	{
	case WM_INITDIALOG:
		{
			DWORD dwThreadID;
			d.hwndDialog = hwndDialog;
			d.hwndEditBox = GetDlgItem(hwndDialog,IDC_EDIT1);
			d.hwndScrollBar = GetDlgItem(hwndDialog,IDC_SCROLLBAR1);
			d.hStopThreadEvent = CreateEvent(NULL,TRUE,FALSE,NULL);
			d.hLogFileHandleMutex = CreateMutex(NULL,FALSE,NULL);
			d.dwlFileSize.QuadPart = 0;
			d.ulCurrentLineFileOffset.QuadPart = 0;
			d.ulFirstLineFileOffset.QuadPart = 0;
			d.ulLastLineFileOffset.QuadPart = 0;
			d.ftLastModified.dwLowDateTime = 0;
			d.ftLastModified.dwHighDateTime = 0;

			// form filename of freenet logfile
			lstrcpy(d.szLogFileName, szHomeDirectory);
			lstrcat(d.szLogFileName, "\\freenet.log");

			// get number of lines that edit box can display on screen...
			d.nEditLines = GetEditLines(GetDlgItem(hwndDialog,IDC_EDIT1));

			// set "autoscroll to new messages" by default
			CheckDlgButton(hwndDialog,IDC_TRACKCHANGES,BST_CHECKED);

			// move file pointer to end of file ready to display last n lines
			hLogFile = UpdateFileDetails(&d);
			d.ulCurrentLineFileOffset.QuadPart = d.dwlFileSize.QuadPart;
			if (hLogFile)
			{
				CloseHandle(hLogFile);
				hLogFile=NULL;
			}
			hThread = CreateThread(NULL,0,LogFileNotifyProc,(LPVOID)&d,0,&dwThreadID);
			SetClassLong(hwndDialog, GCL_HICON, (LONG)(hHopsEnabledIcon) );

			return TRUE;
		}

	case WM_VSCROLL:
		{
			char pTextBuffer[32767];
			char * pTextPointer;
			ULARGE_INTEGER ulFromHereFilePosition;
			int nLines;
			BOOL bDoNothing=FALSE;
			BOOL bForward=FALSE;

			WaitForSingleObject(d.hLogFileHandleMutex,INFINITE);
			hLogFile = UpdateFileDetails(&d);


			switch (LOWORD(wParam))
			{
			case SB_TOP:
				ulFromHereFilePosition.QuadPart = 0;
				break;
			case SB_BOTTOM:
				ulFromHereFilePosition.QuadPart = d.dwlFileSize.QuadPart;
				break;
			case SB_LINEUP:
				// read up one line -
				// implemented as read nLines down from (first line file offset)-1
				if (d.ulFirstLineFileOffset.QuadPart>=1)
				{
					ulFromHereFilePosition.QuadPart = d.ulFirstLineFileOffset.QuadPart-1;
				}
				else
				{
					ulFromHereFilePosition.QuadPart = 0;
				}
				bForward=TRUE;
				break;
			case SB_LINEDOWN:
				// read down one line -
				// implemented as read nLines up from (last line file offset)+1
				ulFromHereFilePosition.QuadPart = d.ulLastLineFileOffset.QuadPart+1;
				break;
			case SB_PAGEUP:
				// read up one page -
				// implemented as read nLines up from (first line file offset)-1
				if (d.ulFirstLineFileOffset.QuadPart>=1)
				{
					ulFromHereFilePosition.QuadPart = d.ulFirstLineFileOffset.QuadPart-1;
				}
				else
				{
					ulFromHereFilePosition.QuadPart = 0;
				}
				break;
			case SB_PAGEDOWN:
				// read down one page -
				// implemented as read nLines down from (last line file offset)+1
				ulFromHereFilePosition.QuadPart = d.ulLastLineFileOffset.QuadPart+1;
				bForward=TRUE;
				break;
			case SB_THUMBPOSITION:
			case SB_THUMBTRACK:
				{
					// Call GetScrollInfo to get current tracking 
					// Because WM_VSCROLL only contains 16 bits
					SCROLLINFO si;
					ZeroMemory(&si, sizeof(SCROLLINFO));
					si.cbSize = sizeof(SCROLLINFO);
		            si.fMask = SIF_TRACKPOS | SIF_RANGE;
					bForward=TRUE;

					if (GetScrollInfo(d.hwndScrollBar, SB_CTL, &si) )
					{
						if (si.nMax <= 0x7fffffff )
						{
							ulFromHereFilePosition.QuadPart = si.nTrackPos;
						}
						else
						{
							float bytePerPos = (((float)(LONGLONG)d.dwlFileSize.QuadPart)/(float)0x7fffffff);
							ulFromHereFilePosition.QuadPart = (DWORDLONG)(si.nTrackPos*bytePerPos);
						}
					}
					else
					{
						bDoNothing=TRUE;
					}
				}
	            break;

			default:
				bDoNothing=TRUE;
				break;
			}

			if (!bDoNothing)
			{
				nLines=d.nEditLines;
				pTextPointer = ReadIntoFileBuffer(hLogFile, &ulFromHereFilePosition, pTextBuffer, sizeof(pTextBuffer), &dwBytesReadBefore, &dwBytesReadAfter);
				if (bForward)
				{
					pStartOfFirstLinePointer = ReadNextNLines( &nLines, pTextPointer, pTextPointer-dwBytesReadBefore, pTextPointer+dwBytesReadAfter, &pEndOfLastLinePointer);
					d.ulFirstLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) - (pTextPointer-pStartOfFirstLinePointer);
					d.ulLastLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) + (pEndOfLastLinePointer-pTextPointer);
				}
				else
				{
					pStartOfFirstLinePointer = ReadPrevNLines( &nLines, pTextPointer, pTextPointer-dwBytesReadBefore, pTextPointer+dwBytesReadAfter, &pEndOfLastLinePointer);
					d.ulFirstLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) - (pTextPointer-pStartOfFirstLinePointer);
					d.ulLastLineFileOffset.QuadPart = (ulFromHereFilePosition.QuadPart) + (pEndOfLastLinePointer-pTextPointer);
				}
				UpdateLogfileWindow(d.hwndEditBox, d.hwndScrollBar, pStartOfFirstLinePointer, nLines, d.nEditLines, d.dwlFileSize.QuadPart, d.ulFirstLineFileOffset.QuadPart, d.ulLastLineFileOffset.QuadPart);
			}

			if (hLogFile)
			{
				CloseHandle(hLogFile);
				hLogFile=NULL;
			}
			
			ReleaseMutex(d.hLogFileHandleMutex);
		}
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

	case WM_DESTROY:
		LOCK(LOGFILEDIALOGBOX);
		bLogFileDialogBoxRunning=FALSE;
		UNLOCK(LOGFILEDIALOGBOX);
		return FALSE;

	default:
		return FALSE;
	}


	if (bQuit)
	{
		DestroyWindow(hwndDialog);
		// Quit flag was set in above message processing
		// destroy and cleanup
		if (hThread)
		{
			if (d.hStopThreadEvent)
			{
				SetEvent(d.hStopThreadEvent);
			}
			WaitForSingleObject(hThread,INFINITE);
			CloseHandle(hThread);
		}
		if (d.hStopThreadEvent)
		{
			CloseHandle(d.hStopThreadEvent);
		}

		return TRUE;  //  true because the message obviously WAS processed, or else we wouldn't have set Quit flag
	}

	return FALSE;
}


void CreateLogfileViewer(HWND hWnd)
{
	// if logfile thread is already created, highlight it
	LOCK(LOGFILEDIALOGBOX);
	if (bLogFileDialogBoxRunning && hLogFileDialogWnd!=NULL)
	{
		UNLOCK(LOGFILEDIALOGBOX);
		// if so - set focus to it:
		SetForegroundWindow(hLogFileDialogWnd);
	}
	else
	{
		// else create the dialog box and its thread:
		hLogFileDialogWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_LOGFILEVIEWER), hWnd, LogFileViewerProc);
		bLogFileDialogBoxRunning = (hLogFileDialogWnd != NULL);
		UNLOCK(LOGFILEDIALOGBOX);
	}
}

