#include "stdafx.h"
#include "stdio.h"

HWND hAboutDialogWnd = NULL;
BOOL bAboutDialogBoxRunning = FALSE;
BOOL CALLBACK __stdcall AboutProc(HWND hwndDialog, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	BOOL bQuit=FALSE;
	
	switch (uMsg)
	{
	case WM_INITDIALOG:
		{
			char szNodeInfo[32767];
			sprintf(szNodeInfo, "Number of nodes:%lu\r\nRunning as %s\r\nDefault browser: %s", 1, "user process (this user)", "(default)");
			SetWindowText(GetDlgItem(hwndDialog, IDC_STATIC_ABOUT_NODEINFO), szNodeInfo);
			SetClassLong(hwndDialog, GCL_HICON, (LONG)(hHopsEnabledIcon) );
		}
		return TRUE;

	case WM_CLOSE:
		bQuit=TRUE;
		break;
	
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDC_LAUNCH_WWW_SUPPORT_PAGE:
			ShellExecute(hWnd, "open", "http://www.freenetproject.org", NULL, NULL, SW_SHOWNORMAL);
			return TRUE;
		case IDOK:
			bQuit=TRUE;
			break;
		}
		break;

	case WM_DESTROY:
		LOCK(ABOUTDIALOGBOX);
		bAboutDialogBoxRunning=FALSE;
		UNLOCK(ABOUTDIALOGBOX);
		return FALSE;

	default:
		return FALSE;
	}


	if (bQuit)
	{
		DestroyWindow(hwndDialog);
		return TRUE;
	}

	return FALSE;
}

void CreateAboutBox(HWND hWnd)
{
	// if about box is already created, highlight it
	LOCK(ABOUTDIALOGBOX);
	if (bAboutDialogBoxRunning && hAboutDialogWnd!=NULL)
	{
		UNLOCK(ABOUTDIALOGBOX);
		// if so - set focus to it:
		SetForegroundWindow(hAboutDialogWnd);
	}
	else
	{
		// else create the dialog box and its thread:
		hAboutDialogWnd = CreateDialog(hInstance, MAKEINTRESOURCE(IDD_ABOUT), NULL, AboutProc);
		bAboutDialogBoxRunning = (hAboutDialogWnd != NULL);
		UNLOCK(ABOUTDIALOGBOX);
	}
}

