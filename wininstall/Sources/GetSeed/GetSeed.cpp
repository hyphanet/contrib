// GetSeed.cpp : Defines the class behaviors for the application.
//

#include "stdafx.h"
#include "GetSeed.h"
#include "GetSeedDlg.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CGetSeedApp

BEGIN_MESSAGE_MAP(CGetSeedApp, CWinApp)
	//{{AFX_MSG_MAP(CGetSeedApp)
	//}}AFX_MSG_MAP
	ON_COMMAND(ID_HELP, CWinApp::OnHelp)
 	ON_COMMAND(ID_HELP_FINDER, CWinApp::OnHelpFinder)
	ON_COMMAND(ID_CONTEXT_HELP, CWinApp::OnContextHelp)
	ON_COMMAND(ID_DEFAULT_HELP, CWinApp::OnHelpFinder)
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CGetSeedApp construction

CGetSeedApp::CGetSeedApp()
{
	// TODO: add construction code here,
	// Place all significant initialization in InitInstance
}

/////////////////////////////////////////////////////////////////////////////
// The one and only CGetSeedApp object

CGetSeedApp theApp;

/////////////////////////////////////////////////////////////////////////////
// CGetSeedApp initialization

BOOL CGetSeedApp::InitInstance()
{
	if (!AfxSocketInit())
	{
		AfxMessageBox(IDP_SOCKETS_INIT_FAILED);
		return FALSE;
	}

#ifdef _AFXDLL
	Enable3dControls();			// Call this when using MFC in a shared DLL
#else
	Enable3dControlsStatic();	// Call this when linking to MFC statically
#endif
	
	//Change the name of the .HLP file.
	//First free the string allocated by MFC at CWinApp startup.
	free((void*)m_pszHelpFilePath);
	//The CWinApp destructor will free the memory.
	m_pszHelpFilePath=_tcsdup(_T(HELPFILE));


	CGetSeedDlg dlg;
	m_pMainWnd = &dlg;
	int nResponse = dlg.DoModal();
	if (nResponse == IDOK)
	{
		// TODO: Place code here to handle when the dialog is
		//  dismissed with OK
	}
	else if (nResponse == IDCANCEL)
	{
		// TODO: Place code here to handle when the dialog is
		//  dismissed with Cancel
	}

	// Since the dialog has been closed, return FALSE so that we exit the
	//  application, rather than start the application's message pump.
	return FALSE;
}

