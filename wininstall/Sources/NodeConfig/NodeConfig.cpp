// NodeConfig.cpp : Defines the class behaviors for the application.
//
//cvs


#include "stdafx.h"

#include "NodeConfig.h"
#include "NodeConfigDlg.h"

#include "PropNormal.h"
#include "PropAdvanced.h"
#include "PropGeek.h"

#include "DlgWarnPerm.h"

#include "ConfigFile.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

///////////////////////////////////
//
// EXPORTS
//

char			progPath[256];

CPropNormal		*pNormal;
CPropAdvanced	*pAdvanced;
CPropGeek		*pGeek;
CDlgWarnPerm	*pWarnPerm;


/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp

BEGIN_MESSAGE_MAP(CNodeConfigApp, CWinApp)
	//{{AFX_MSG_MAP(CNodeConfigApp)
		// NOTE - the ClassWizard will add and remove mapping macros here.
		//    DO NOT EDIT what you see in these blocks of generated code!
	//}}AFX_MSG
	ON_COMMAND(ID_HELP, CWinApp::OnHelp)
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp construction

CNodeConfigApp::CNodeConfigApp()
{
}

/////////////////////////////////////////////////////////////////////////////
// The one and only CNodeConfigApp object

CNodeConfigApp theApp;

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp initialization

BOOL CNodeConfigApp::InitInstance()
{
	if (!AfxSocketInit())
	{
		AfxMessageBox(IDP_SOCKETS_INIT_FAILED);
		return FALSE;
	}

	AfxEnableControlContainer();

	// Standard initialization
	// If you are not using these features and wish to reduce the size
	//  of your final executable, you should remove from the following
	//  the specific initialization routines you do not need.

#ifdef _AFXDLL
	Enable3dControls();			// Call this when using MFC in a shared DLL
#else
	Enable3dControlsStatic();	// Call this when linking to MFC statically
#endif

	CNodeConfigDlg dlg;
	CPropertySheet propdlg;

	CPropNormal Normal;
	CPropAdvanced Advanced;
	CPropGeek Geek;
	CDlgWarnPerm	WarnPerm;

	pNormal = &Normal;
	pAdvanced = &Advanced;
	pGeek = &Geek;
	pWarnPerm = &WarnPerm;

	propdlg.AddPage(pNormal);
	propdlg.AddPage(pAdvanced);
	propdlg.AddPage(pGeek);

	propdlg.SetTitle("Freenet Node Properties", 0);

    // Derive pathname of executable program's directory
	char *exename;
    strcpy(progPath, _pgmptr);
    exename = strrchr(progPath, '\\'); // point to slash between path and filename
    *exename++ = '\0'; // split the string and point to filename part

	// Set up file class
	CConfigFile *pConfigFile = new CConfigFile;
	pConfigFile->FileName = progPath;
	pConfigFile->FileName += "\\freenet.ini";

	// Load existing configuration from file
	pConfigFile->Load();

	//pNormal->m_storeCacheSize = 5;

	// Launch the UI
	if (propdlg.DoModal() == IDOK)
	{
		pConfigFile->Save();
		clickedOK=TRUE;
	}
	else
	{
		clickedOK=FALSE;
	}

	return TRUE;
}

int CNodeConfigApp::ExitInstance() 
{
	return (clickedOK) ? 0 : 1; // return 0 if we clicked OK and 1 otherwise (canceled dialog)
}
