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

char			progPath[256];		//executable program path (including ending '\')

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
	ON_COMMAND(ID_HELP, CWinApp::OnHelpIndex)
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp construction

CNodeConfigApp::CNodeConfigApp()
{
	// Derive pathname of executable program's directory
	char *exename;
	lstrcpyn(progPath, _pgmptr,256);
    exename = strrchr(progPath, '\\'); // point to slash between path and filename
    *++exename = '\0'; // point to filename partand split the string
}

/////////////////////////////////////////////////////////////////////////////
// The one and only CNodeConfigApp object

CNodeConfigApp theApp;

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp initialization

BOOL CNodeConfigApp::InitInstance()
{

	#ifdef _AFXDLL
	Enable3dControls();			// Call this when using MFC in a shared DLL
	#else
	Enable3dControlsStatic();	// Call this when linking to MFC statically
	#endif

	if (!AfxSocketInit())
	{
		AfxMessageBox(IDP_SOCKETS_INIT_FAILED);
	}

	// Changing the help file to point to "/docs/freenet.hlp"
	//The string is allocated before InitInstance is called.
	free ((void*)m_pszHelpFilePath);	//delete the old help file string
	m_pszHelpFilePath = (char*)malloc(strlen(progPath) + 17); // reserve mem for the path + "docs\freenet.hlpNULL"
	strcpy ((char*)m_pszHelpFilePath,progPath);
	strcat ((char*)m_pszHelpFilePath,"docs\\freenet.hlp");
	

	//CNodeConfigDlg dlg;
	CPropertySheet propdlg(IDS_TITLE);

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

	propdlg.m_psh.dwFlags	|= PSH_NOAPPLYNOW|PSH_USEHICON;
	propdlg.m_psh.hInstance = m_hInstance;
	propdlg.m_psh.hIcon	= LoadIcon(IDR_MAINFRAME);

	// Set up file class
	CConfigFile *pConfigFile = new CConfigFile;
	pConfigFile->FileName = progPath;
	pConfigFile->FileName += "\\freenet.ini";

	// Load existing configuration from file
	pConfigFile->Load();

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
