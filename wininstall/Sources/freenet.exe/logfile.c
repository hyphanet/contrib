#include "windows.h"
#include "types.h"
#include "shared_data.h"
#include "logfile.h"
#include "rsrc.h"

HWND hLogFileDialogWnd = NULL;
BOOL bLogFileDialogBoxRunning = FALSE;
HANDLE hThread = NULL;

struct threadData
{
	HWND hwndDialog;
	HWND hwndEditBox;
	HWND hwndScrollBar;
	HANDLE hLogFile;
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


// THIS CODE ONLY WORKS IF TextBufferSize < 4GB ....  but that's very LIKELY
// (default size of TextBufferSize is just 4KB ... )
char* ReadPrevNLines(int *pnLines, HANDLE hFile, ULARGE_INTEGER ulCurrentFilePosition, ULARGE_INTEGER *pulFirstLineFileOffset, ULARGE_INTEGER *pulLastLineFileOffset, ULARGE_INTEGER *pulCurrentLineFileOffset, char *pTextBuffer, DWORD TextBufferSize)
{
	char * pFirstOfNLines;
	char * pLastOfNLines;
	DWORD dwRead;
	int nLines = *pnLines;
	DWORD dwBytesToRead;
	ULARGE_INTEGER ulReadStartOffset;
	BOOL bGotStartOfCurrentLineFileOffset=FALSE;
	ULARGE_INTEGER ulFirstLineFileOffset,ulLastLineFileOffset,ulCurrentLineFileOffset;
	BOOL bCurrentFilePointerIsEndOfLine=FALSE;
	BOOL bReadSuccess;

	*pnLines = 0;
	pulFirstLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulLastLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulCurrentLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pTextBuffer[0]='\0';

	// if asked to return 0 lines then do nothing but
	// return a pointer to an empty buffer
	if (nLines==0)
	{
		return pTextBuffer;
	}


	dwRead=0;
	// we want to read as many bytes before the current file position as possible
	if (ulCurrentFilePosition.QuadPart >= TextBufferSize-1)
	{
		// we are at least buffersize bytes into the file
		// read from (here-buffersize) to here
		dwBytesToRead = TextBufferSize-1;
		ulReadStartOffset.QuadPart = ulCurrentFilePosition.QuadPart - (TextBufferSize-1);
	}
	else
	{
		// we are less than buffersize bytes into the file, so just
		// read from the start of the file to here
		dwBytesToRead = ulCurrentFilePosition.LowPart;
		ulReadStartOffset.QuadPart=0;
	}
	// Now, the above might mean we should read in zero bytes
	// Check for this...  if the above calculation works out at zero bytes
	// then don't do anything - we've got all we can from the file.
	SetFilePointer(hFile, ulReadStartOffset.LowPart, &(ulReadStartOffset.HighPart), FILE_BEGIN);
	if (dwBytesToRead!=0)
	{
		bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
		if (bReadSuccess  &&  dwRead==0)
		{
			// we're at end of file...  or beyond it (maybe the file shrank?)
			// and so we couldn't read any bytes -
			// so reset file pointer to (end of file) minus buffersize and try again
			if ((int)(SetFilePointer(hFile,0,NULL,FILE_END))<(int)(TextBufferSize-1))
			{
				// file smaller than TextBufferSize-1 bytes!
				// so try again, at the start of the file
				SetFilePointer(hFile, 0, NULL, FILE_BEGIN);
			}
			else
			{
				SetFilePointer(hFile,-(int)(TextBufferSize-1),NULL,FILE_END);
			}
			// and fill the buffer
			dwBytesToRead = TextBufferSize-1;
			bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
		}
		if (!bReadSuccess)
		{
			pTextBuffer[0] = '\0';
			return pTextBuffer;
		}
	}

	// find out the resulting file pointer for the block of data we've just read
	ulCurrentFilePosition.QuadPart=0;
	ulCurrentFilePosition.LowPart = SetFilePointer(hFile, 0, &(ulCurrentFilePosition.HighPart), FILE_CURRENT);
	if (ulCurrentFilePosition.LowPart==0xffffffff && GetLastError()!=NO_ERROR)
	{
		pTextBuffer[0] = '\0';
		return pTextBuffer;
	}

	// runner used to find first of N lines of text
	pFirstOfNLines = pTextBuffer+dwRead-1;
	// runner used to find last of N lines of text
	pLastOfNLines = pFirstOfNLines;
	
	// if we didn't read in a full TextBuffer then read more now:
	// e.g. if TextBufferSize=4KB, and we were only 1KB into file, the above will
	// have read that first 1KB, so now we need to read an additional 3KB
	if (dwRead < TextBufferSize-1)
	{
		DWORD dwAlsoRead=0;
		bReadSuccess = ReadFile(hFile,pTextBuffer+dwRead,(TextBufferSize-1)-dwRead,&dwAlsoRead,NULL);
		if (!bReadSuccess)
		{
			pTextBuffer[0] = '\0';
			return pTextBuffer;
		}
		dwRead+=dwAlsoRead;
	}

	// if we failed to read any bytes, return an empty nul-terminated buffer
	if (dwRead==0)
	{
		pTextBuffer[0] = '\0';
		return pTextBuffer;
	}


	// null-terminate end of buffer
	pTextBuffer[dwRead]='\0';

	// Find FIRST of N lines
	// This code also determines the file pointer that points to the start of
	// the current line (e.g. may be ulCurrentFilePosition or may be a bit less
	// if ulCurrentFilePosition happens to point to somewhere in the middle of a
	// line of text).
	// (if we don't find N lines before the pointer then look for (N-found) lines after pointer too)
	ulFirstLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;
	ulLastLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;
	ulCurrentLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;


	// if last characters read was \n then skip backwards past it
	// and start the counting of linefeeds from BEFORE them.
	if (*pFirstOfNLines=='\n')
	{
		--pFirstOfNLines;
		--ulCurrentLineFileOffset.QuadPart;
		--ulFirstLineFileOffset.QuadPart;
		bCurrentFilePointerIsEndOfLine=TRUE;
	}
	else
	{
		// we will need to find the end of the line we're on...
		bCurrentFilePointerIsEndOfLine=FALSE;
	}


	// count number of linefeeds, backwards from end,
	// and stop when we've counted the number we're looking for
	while ( (pFirstOfNLines>=pTextBuffer) && (nLines>0) )
	{
		if (*pFirstOfNLines=='\n')
		{
			--nLines;
			++(*pnLines);
			if (!bGotStartOfCurrentLineFileOffset)
			{
				++ulCurrentLineFileOffset.QuadPart;
				bGotStartOfCurrentLineFileOffset=TRUE;
			}
			if (nLines==0)
			{
				++pFirstOfNLines;
				++ulFirstLineFileOffset.QuadPart;
				break;
			}
		}
		--pFirstOfNLines;
		--ulFirstLineFileOffset.QuadPart;
		if (!bGotStartOfCurrentLineFileOffset)
		{
			--ulCurrentLineFileOffset.QuadPart;
		}
	}
	// fix up if we ran off the start of the buffer
	if (pFirstOfNLines < pTextBuffer)
	{
		// count this as another complete line
		++(*pnLines);
		while (pFirstOfNLines<pTextBuffer)
		{
			++pFirstOfNLines;
			++ulFirstLineFileOffset.QuadPart;
			if (!bGotStartOfCurrentLineFileOffset)
			{
				++ulCurrentLineFileOffset.QuadPart;
			}
		}
	}
	bGotStartOfCurrentLineFileOffset=TRUE;
	// When we get here we have pFirstOfNLines pointing to the start of the
	// first line that we want to present back to the caller (which is at most
	// nLines before the file position pointed to by ulCurrentLineFileOffset)
	// And we have a filepointer in ulFirstLineFilePointer which points to the
	// position in the file of the character pointed to by pFirstOfNLines
	// We also have a filepointer that points to the start
	// of the current line (i.e. same as ulCurrentFilePointer passed in
	// if it happened to point to one character after a \n or if it happened
	// to point to the start of the file, otherwise ulCurrentFilePointer minus
	// a bit, to get it to the start of a line of text)

	// if we didn't find nLines full before (up to and including) ulCurrentFilePointer
	// then we now need to do the same sorta thing to find the last line needed
	if ( (nLines!=0) || (!bCurrentFilePointerIsEndOfLine) )
	{
		// we couldn't find enough lines of text before the current file pointer
		// so look in the part of the buffer containing data read AFTER the current file pointer
		// (currently pointed to by nLastOfNLines)

		if (!bCurrentFilePointerIsEndOfLine)
		{
			// we need to find the end of THIS line ...
			// effectively just another pass through proceeding code
			// We decrement pnLines because the preceeding code already
			// incremented it, and we haven't  REALLY  found the whole line yet...
			++nLines;
			--(*pnLines);
		}
		else
		{
			// if last characters read at current file pointer was not \n and
			// the next character read after current file pointer is \n then skip over the \n
			// and start the counting of linefeeds from AFTER that.
			if ( (pLastOfNLines>pTextBuffer) && (*(pLastOfNLines-1)!='\n') )
			{
				if (*pLastOfNLines=='\n')
				{
					++pLastOfNLines;
					++ulLastLineFileOffset.QuadPart;
				}
				else
				{
					// the code for finding the FirstOfNLines has already counted this line...
					// one fewer line terminating \n to look for then
					--nLines; 
				}
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
			++ulLastLineFileOffset.QuadPart;
		}
		// fix up if we've run off the end of the buffer
		if ( (pLastOfNLines>=pTextBuffer+dwRead) || (*pLastOfNLines=='\0') )
		{
			// count this as another complete line
			++(*pnLines);
			while ( (pLastOfNLines>=pTextBuffer+dwRead) || (*pLastOfNLines=='\0') )
			{
				--pLastOfNLines;
				--ulLastLineFileOffset.QuadPart;
			}
		}
		// when finished, pLastOfNLines will either point to the last non-nul character in buffer
		// or last \n of Nth line (with pFirstOfNLines pointing to first character of 1st line)
		// And ulLastLineFileOffset is the file pointer to the character pointed to by pLastOfNLines
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

	pulCurrentLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulFirstLineFileOffset->QuadPart = ulFirstLineFileOffset.QuadPart;
	pulLastLineFileOffset->QuadPart = ulLastLineFileOffset.QuadPart;

	return pFirstOfNLines;
}


// THIS CODE ONLY WORKS IF TextBufferSize < 4GB ....  but that's very LIKELY
// (default size of TextBufferSize is just 4KB ... )
char* ReadNextNLines(int *pnLines, HANDLE hFile, ULARGE_INTEGER ulCurrentFilePosition, ULARGE_INTEGER *pulFirstLineFileOffset, ULARGE_INTEGER *pulLastLineFileOffset, ULARGE_INTEGER *pulCurrentLineFileOffset, char *pTextBuffer, DWORD TextBufferSize)
{
	char * pFirstOfNLines;
	char * pLastOfNLines;
	DWORD dwRead;
	int nLines = *pnLines;
	DWORD dwBytesToRead;
	ULARGE_INTEGER ulReadStartOffset;
	BOOL bGotStartOfCurrentLineFileOffset=FALSE;
	ULARGE_INTEGER ulFirstLineFileOffset,ulLastLineFileOffset,ulCurrentLineFileOffset;
	BOOL bCurrentFilePointerIsStartOfLine=FALSE;
	BOOL bReadSuccess;

	*pnLines = 0;
	pulFirstLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulLastLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulCurrentLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pTextBuffer[0]='\0';

	// if asked to return 0 lines then do nothing but
	// return a pointer to an empty buffer
	if (nLines==0)
	{
		return pTextBuffer;
	}


	// we want to read as many bytes after the current file position as possible
	dwBytesToRead = TextBufferSize-1;
	ulReadStartOffset.QuadPart = ulCurrentFilePosition.QuadPart;
	SetFilePointer(hFile, ulReadStartOffset.LowPart, &(ulReadStartOffset.HighPart), FILE_BEGIN);
	bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
	if (bReadSuccess  &&  dwRead==0)
	{
		// we're at end of file...  or beyond it (maybe the file shrank?)
		// and so we couldn't read any bytes -
		// so reset file pointer to (end of file) minus buffersize and try again
		if ((int)(SetFilePointer(hFile,0,NULL,FILE_END))<(int)(TextBufferSize-1))
		{
			// file smaller than TextBufferSize-1 bytes!
			// so try again, at the start of the file
			ulCurrentFilePosition.QuadPart=0;
			ulCurrentFilePosition.LowPart = SetFilePointer(hFile, 0, &(ulCurrentFilePosition.HighPart), FILE_BEGIN);
		}
		else
		{
			ulCurrentFilePosition.QuadPart=0;
			ulCurrentFilePosition.LowPart = SetFilePointer(hFile,-(int)(TextBufferSize-1),&(ulCurrentFilePosition.HighPart),FILE_END);
		}
		// and fill the buffer
		dwBytesToRead = TextBufferSize-1;
		bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwRead,NULL);
	}
	if (!bReadSuccess)
	{
		pTextBuffer[0] = '\0';
		return pTextBuffer;
	}

	// runner used to find last of N lines of text
	pLastOfNLines = pTextBuffer;
	// runner used to find first of N lines of text
	pFirstOfNLines = pLastOfNLines;
	
	// if we didn't read in a full TextBuffer then read more now:
	// e.g. if TextBufferSize=4KB, and we only had 1KB left of file, the above will
	// have read that last 1KB, so now we need to read 3KB from BEFORE the file pointer
	if (dwRead < TextBufferSize-1)
	{
		DWORD dwAlsoRead;
		dwBytesToRead = (TextBufferSize-1)-dwRead;
		memmove(pTextBuffer+dwBytesToRead,pTextBuffer,dwRead);
		pLastOfNLines+=dwBytesToRead;
		pFirstOfNLines+=dwBytesToRead;
		if (ulCurrentFilePosition.QuadPart >= dwBytesToRead)
		{
			// we are at least buffersize bytes into the file
			// read from (here-buffersize) to here
			ulReadStartOffset.QuadPart = ulCurrentFilePosition.QuadPart - (dwBytesToRead);
		}
		else
		{
			// we are less than buffersize bytes into the file, so just
			// read from the start of the file to here
			dwBytesToRead = ulCurrentFilePosition.LowPart;
			ulReadStartOffset.QuadPart=0;
		}
		// Now, the above might mean we should read in zero bytes
		// Check for this...  if the above calculation works out at zero bytes
		// then don't do anything - we've got all we can from the file.
		SetFilePointer(hFile, ulReadStartOffset.LowPart, &(ulReadStartOffset.HighPart), FILE_BEGIN);
		if (dwBytesToRead!=0)
		{
			bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwAlsoRead,NULL);
			if (bReadSuccess  &&  dwAlsoRead==0)
			{
				// we're at end of file...  or beyond it (maybe the file shrank?)
				// and so we couldn't read any bytes -
				// so reset file pointer to (end of file) minus buffersize and try again
				if ((int)(SetFilePointer(hFile,0,NULL,FILE_END))<(int)(TextBufferSize-1))
				{
					// file smaller than TextBufferSize-1 bytes!
					// so try again, at the start of the file
					SetFilePointer(hFile, 0, NULL, FILE_BEGIN);
				}
				else
				{
					SetFilePointer(hFile,-(int)(TextBufferSize-1),NULL,FILE_END);
				}
				bReadSuccess = ReadFile(hFile,pTextBuffer,dwBytesToRead,&dwAlsoRead,NULL);
			}
			if (!bReadSuccess)
			{
				pTextBuffer[0] = '\0';
				return pTextBuffer;
			}
			if (dwAlsoRead < dwBytesToRead)
			{
				memmove(pTextBuffer,pTextBuffer+(dwBytesToRead-dwAlsoRead),dwAlsoRead);
			}
		}

		dwRead+=dwAlsoRead;
	}

	// if we failed to read any bytes, return an empty nul-terminated buffer
	if (dwRead==0)
	{
		pTextBuffer[0] = '\0';
		return pTextBuffer;
	}


	// null-terminate end of buffer
	pTextBuffer[dwRead]='\0';

	// Find LAST of N lines
	// (if we don't find N lines before the pointer then look for (N-found) lines after pointer too)
	ulFirstLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;
	ulLastLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;
	ulCurrentLineFileOffset.QuadPart = ulCurrentFilePosition.QuadPart;

	// if the last character read BEFORE ulCurrentFilePosition was not \n then it
	// means ulCurrentFilePosition is somewhere in the middle of a line so we will
	// later need to find the start of that line
	if ( (pFirstOfNLines>pTextBuffer) && (*(pFirstOfNLines-1)!='\n') )
	{
		// we will need to find the start of the line we're on...
		bCurrentFilePointerIsStartOfLine=FALSE;
		bGotStartOfCurrentLineFileOffset=FALSE;
	}
	else
	{
		bCurrentFilePointerIsStartOfLine=TRUE;
		bGotStartOfCurrentLineFileOffset=TRUE;
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
		++ulLastLineFileOffset.QuadPart;
	}
	// fix up if we've run off the end of the buffer
	if ( (pLastOfNLines>=pTextBuffer+dwRead) || (*pLastOfNLines=='\0') )
	{
		// count this as another complete line
		++(*pnLines);
		while ( (pLastOfNLines>=pTextBuffer+dwRead) || (*pLastOfNLines=='\0') )
		{
			--pLastOfNLines;
			--ulLastLineFileOffset.QuadPart;
		}
	}
	// when finished, pLastOfNLines will either point to the last non-nul character in buffer
	// or last \n of Nth line (with pFirstOfNLines pointing to first character of 1st line)
	// And ulLastLineFileOffset is the file pointer to the character pointed to by pLastOfNLines
	
	// get rid of trailing \n or \r\n in block of text if any
	if (*pLastOfNLines=='\n')
	{
		*pLastOfNLines='\0';
		if ( pLastOfNLines>pTextBuffer )
		{
			pLastOfNLines--;
			if ( *pLastOfNLines=='\r' )
			*pLastOfNLines='\0';
		}
	}
	else if (*pLastOfNLines=='\r')
	{
		*pLastOfNLines='\0';
	}


	// When we get here we have pLastOfNLines pointing to the end of the
	// last line that we want to present back to the caller (which is at most
	// nLines after the file position pointed to by ulCurrentLineFileOffset)
	// And we have a filepointer in ulLastLineFilePointer which points to the
	// position in the file of the character pointed to by pLastOfNLines

	// if we didn't find nLines full before (up to and including) ulCurrentFilePointer
	// then we now need to do the same sorta thing to find the last line needed
	if ( (nLines!=0) || (!bCurrentFilePointerIsStartOfLine) )
	{
		// count number of linefeeds, backwards from end,
		// and stop when we've counted the number we're looking for
		while ( (pFirstOfNLines>=pTextBuffer) && (nLines>0) )
		{
			if (*pFirstOfNLines=='\n')
			{
				--nLines;
				++(*pnLines);
				if (!bGotStartOfCurrentLineFileOffset)
				{
					++ulCurrentLineFileOffset.QuadPart;
					bGotStartOfCurrentLineFileOffset=TRUE;
				}
				if (nLines==0)
				{
					++pFirstOfNLines;
					++ulFirstLineFileOffset.QuadPart;
					break;
				}
			}
			--pFirstOfNLines;
			--ulFirstLineFileOffset.QuadPart;
			if (!bGotStartOfCurrentLineFileOffset)
			{
				--ulCurrentLineFileOffset.QuadPart;
			}
		}
		// fix up if we ran off the start of the buffer
		if (pFirstOfNLines < pTextBuffer)
		{
			// count this as another complete line
			++(*pnLines);
			while (pFirstOfNLines<pTextBuffer)
			{
				++pFirstOfNLines;
				++ulFirstLineFileOffset.QuadPart;
				if (!bGotStartOfCurrentLineFileOffset)
				{
					++ulCurrentLineFileOffset.QuadPart;
				}
			}
		}
		bGotStartOfCurrentLineFileOffset=TRUE;
	}
	// When we get here we have pFirstOfNLines pointing to the start of the
	// first line that we want to present back to the caller (which is at most
	// nLines before the file position pointed to by ulCurrentLineFileOffset)
	// And we have a filepointer in ulFirstLineFilePointer which points to the
	// position in the file of the character pointed to by pFirstOfNLines
	// We also have a filepointer that points to the start
	// of the current line (i.e. same as ulCurrentFilePointer passed in
	// if it happened to point to one character after a \n or if it happened
	// to point to the start of the file, otherwise ulCurrentFilePointer minus
	// a bit, to get it to the start of a line of text)

	pulCurrentLineFileOffset->QuadPart = ulCurrentFilePosition.QuadPart;
	pulFirstLineFileOffset->QuadPart = ulFirstLineFileOffset.QuadPart;
	pulLastLineFileOffset->QuadPart = ulLastLineFileOffset.QuadPart;

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


void UpdateLogfileWindow (HWND hwndEditBox, HWND hwndScrollBar, const char * pTextPointer, int nLines, int nEditLines, DWORDLONG dwlFileSize, DWORDLONG ulCurrentFilePosition)
{
	SetWindowText(hwndEditBox, pTextPointer);

	if (nLines < nEditLines)
	{
		EnableScrollBar(hwndScrollBar,SB_CTL, ESB_DISABLE_BOTH);
	}
	else
	{
		int estNumberOfLinesInFile;
		int estLineWeAreOnNow;
		SCROLLINFO si;
		if (dwlFileSize > UInt32x32To64((int)(0x7fffffff),80) )
		{
			estNumberOfLinesInFile = (int)(0x7fffffff);
		}
		else
		{
			estNumberOfLinesInFile = (int)(dwlFileSize/80);
		}
		if (ulCurrentFilePosition >= dwlFileSize-1)
		{
			estLineWeAreOnNow = estNumberOfLinesInFile;
		}
		else if (ulCurrentFilePosition > UInt32x32To64((int)(0x7fffffff),80) )
		{
			estLineWeAreOnNow = (int)(0x7fffffff);
		}
		else
		{
			estLineWeAreOnNow = (int)(ulCurrentFilePosition/80);
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
		si.nPos = estLineWeAreOnNow;
		si.nPage = nEditLines;
		si.cbSize = sizeof(SCROLLINFO);
		si.fMask = SIF_DISABLENOSCROLL | SIF_PAGE | SIF_POS | SIF_RANGE;
		EnableScrollBar(hwndScrollBar,SB_CTL, ESB_ENABLE_BOTH);
		SetScrollInfo(hwndScrollBar,SB_CTL,&si,TRUE);
	}
}

void UpdateFileDetails(struct threadData *d)
{
	DWORD dwError;

	if ( d->hLogFile==NULL || d->hLogFile==INVALID_HANDLE_VALUE)
	{
		d->hLogFile = CreateFile(d->szLogFileName,
		GENERIC_READ, 
		FILE_SHARE_DELETE | FILE_SHARE_READ | FILE_SHARE_WRITE,
		NULL,
		OPEN_EXISTING,
		FILE_FLAG_RANDOM_ACCESS,
		NULL);
	}
	if ( d->hLogFile==NULL || d->hLogFile==INVALID_HANDLE_VALUE)
	{
		dwError = GetLastError();
	}

	if ( d->hLogFile!=NULL && d->hLogFile!=INVALID_HANDLE_VALUE)
	{
		d->dwlFileSize.LowPart = GetFileSize( d->hLogFile, &(d->dwlFileSize.HighPart) );
		if (d->dwlFileSize.LowPart==0xffffffff)
		{
			dwError = GetLastError();
			if (dwError != ERROR_SUCCESS)
			{
				d->dwlFileSize.QuadPart=0;
				CloseHandle( d->hLogFile);
				d->hLogFile=NULL;
			}
		}
	}
	
	if ( d->hLogFile!=NULL && d->hLogFile!=INVALID_HANDLE_VALUE)
	{
		if (!GetFileTime(d->hLogFile,NULL,NULL,&(d->ftLastModified) ))
		{
			dwError = GetLastError();
			d->ftLastModified.dwLowDateTime=0;
			d->ftLastModified.dwHighDateTime=0;
			CloseHandle(d->hLogFile);
			d->hLogFile=NULL;
		}
	}
}

DWORD CALLBACK __stdcall LogFileNotifyProc(LPVOID lpvParam)
{
	struct threadData * d = (struct threadData *)lpvParam;

	HANDLE hFindChanges = FindFirstChangeNotification(szHomeDirectory, FALSE, FILE_NOTIFY_CHANGE_SIZE);
	const HANDLE hEvents[] = {d->hStopThreadEvent,hFindChanges};
	DWORD dwWaitResult;
	char pTextBuffer[4097];
	char * pTextPointer;
	BOOL bQuit=FALSE;
	BOOL bTrackChanges;
	DWORDLONG dwlPrevFileSize = 0;
	char szFileSize[64];
	int nLines;
	ULARGE_INTEGER ulFromHereFilePosition;
	FILETIME ftLastModified;

	char cszWindowTitle[64];
	char szWindowTitle[128];
	GetWindowText(d->hwndDialog,cszWindowTitle,sizeof(cszWindowTitle));

	WaitForSingleObject(d->hLogFileHandleMutex,INFINITE);

	UpdateFileDetails(d);
	memcpy(&(d->ftLastModified),&ftLastModified,sizeof(FILETIME));
	FormatFileSize(szFileSize,d->dwlFileSize.QuadPart);
	wsprintf(szWindowTitle, "%s (%s)", cszWindowTitle, szFileSize);
	SetWindowText(d->hwndDialog, szWindowTitle);
	ulFromHereFilePosition.QuadPart = d->ulCurrentLineFileOffset.QuadPart;
	nLines = d->nEditLines;
	pTextPointer = ReadPrevNLines(&nLines, d->hLogFile, ulFromHereFilePosition, &(d->ulFirstLineFileOffset), &(d->ulLastLineFileOffset), &(d->ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
	UpdateLogfileWindow(d->hwndEditBox, d->hwndScrollBar, pTextPointer, nLines, d->nEditLines, d->dwlFileSize.QuadPart, d->ulLastLineFileOffset.QuadPart);
	
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

				UpdateFileDetails(d);
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
						ulFromHereFilePosition.QuadPart = d->ulCurrentLineFileOffset.QuadPart;
					}
					nLines = d->nEditLines;
					pTextPointer = ReadPrevNLines(&nLines, d->hLogFile, ulFromHereFilePosition, &(d->ulFirstLineFileOffset), &(d->ulLastLineFileOffset), &(d->ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
					UpdateLogfileWindow(d->hwndEditBox, d->hwndScrollBar, pTextPointer, nLines, d->nEditLines, d->dwlFileSize.QuadPart, d->ulLastLineFileOffset.QuadPart);
				}
				
				ReleaseMutex(d->hLogFileHandleMutex);

			}
			break;
		}
	}

	FindCloseChangeNotification(hFindChanges);
	WaitForSingleObject(d->hLogFileHandleMutex, INFINITE);
	CloseHandle (d->hLogFile);
	d->hLogFile=NULL;
	ReleaseMutex(d->hLogFileHandleMutex);
	return 0;
}




	
BOOL CALLBACK __stdcall LogFileViewerProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;

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

			// move file pointer to end of file ready to display last n lines
			UpdateFileDetails(&d);
			d.ulCurrentLineFileOffset.QuadPart = d.dwlFileSize.QuadPart;

			hThread = CreateThread(NULL,0,LogFileNotifyProc,(LPVOID)&d,0,&dwThreadID);
			SetClassLong(hwndDialog, GCL_HICON, (LONG)(hHopsEnabledIcon) );

			return TRUE;
		}

	case WM_VSCROLL:
		{
			char pTextBuffer[4097];
			char * pTextPointer;
			ULARGE_INTEGER ulFromHereFilePosition;
			int nLines;
			BOOL bDoNothing=FALSE;
			BOOL bForward=FALSE;

			WaitForSingleObject(d.hLogFileHandleMutex,INFINITE);
			UpdateFileDetails(&d);


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
				// implemented as read one line up from (first line file offset)-1
				// then read nLines down from (that offset)
				if (d.ulFirstLineFileOffset.QuadPart>=1)
				{
					ulFromHereFilePosition.QuadPart = d.ulFirstLineFileOffset.QuadPart-1;
				}
				else
				{
					ulFromHereFilePosition.QuadPart = 0;
				}
				nLines=1;
				pTextPointer = ReadPrevNLines(&nLines, d.hLogFile, ulFromHereFilePosition, &(d.ulFirstLineFileOffset), &(d.ulLastLineFileOffset), &(d.ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
				ulFromHereFilePosition.QuadPart = d.ulFirstLineFileOffset.QuadPart;
				bForward=TRUE;
				break;
			case SB_LINEDOWN:
				// read down one line -
				// implemented as read one line down from (last line file offset)+1
				// then read nLines up from (that offset)
				ulFromHereFilePosition.QuadPart = d.ulLastLineFileOffset.QuadPart+1;
				nLines=1;
				pTextPointer = ReadNextNLines(&nLines, d.hLogFile, ulFromHereFilePosition, &(d.ulFirstLineFileOffset), &(d.ulLastLineFileOffset), &(d.ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
				ulFromHereFilePosition.QuadPart = d.ulLastLineFileOffset.QuadPart;
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
			default:
				bDoNothing=TRUE;
				break;
			}

			if (!bDoNothing)
			{
				nLines=d.nEditLines;
				if (bForward)
				{
					pTextPointer = ReadNextNLines(&nLines, d.hLogFile, ulFromHereFilePosition, &(d.ulFirstLineFileOffset), &(d.ulLastLineFileOffset), &(d.ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
				}
				else
				{
					pTextPointer = ReadPrevNLines(&nLines, d.hLogFile, ulFromHereFilePosition, &(d.ulFirstLineFileOffset), &(d.ulLastLineFileOffset), &(d.ulCurrentLineFileOffset), pTextBuffer, sizeof(pTextBuffer) );
				}
				UpdateLogfileWindow(d.hwndEditBox, d.hwndScrollBar, pTextPointer, nLines, d.nEditLines, d.dwlFileSize.QuadPart, d.ulCurrentLineFileOffset.QuadPart);
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
		if (d.hLogFile)
		{
			CloseHandle(d.hLogFile);
		}

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

