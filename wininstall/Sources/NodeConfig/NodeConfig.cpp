// NodeConfig.cpp : Defines the class behaviors for the application.
//
//cvs

#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "PropGeek.h"
#include "PropFProxy.h"
#include "PropDiagnostics.h"
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
CPropFProxy		*pFProxy;
CPropDiagnostics *pDiagnostics;

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

	// Try to load the language .dll and fallback to English if not existing
	// This curious code actually finds the current thread locale (i.e. current user locale)
	// and looks for files named localres_0809_.dll (if the user locale is 0809).
	// The match would also find the file localres_0409_0809_0201_.dll if it existed.
	// If no resource dll matching user locale can be found, the default user locale is
	// tried instead, followed by the default system locale.  If a matching resource dll
	// still cannot be found the default (English) is used.
	HINSTANCE hResourceLibrary = NULL;
	DWORD dwLocaleID;
	{
		CString strLocaleResFile;
		HANDLE hFind=NULL;
		WIN32_FIND_DATA sFileData;
		for (int langidSource=0; (langidSource<2) && (hResourceLibrary==NULL); ++langidSource)
		{
			switch (langidSource)
			{
			case 0:
				dwLocaleID = LANGIDFROMLCID(GetThreadLocale());
				break;
			case 1:
				dwLocaleID = GetUserDefaultLCID();
				break;
			case 2:
			default:
				dwLocaleID = GetSystemDefaultLCID();
				break;
			}
			strLocaleResFile.Format("%slocalres*_%04x_*.dll",progPath,dwLocaleID);
			hFind = FindFirstFile( LPCTSTR(strLocaleResFile), &sFileData);
			if ( (hFind!=NULL) && (hFind!=INVALID_HANDLE_VALUE))
			{
				// found resource file, so load it
				CString strResFile(progPath);
				strResFile+=sFileData.cFileName;
				hResourceLibrary = LoadLibrary( LPCTSTR(strResFile) );
			}
			FindClose(hFind);
		}
	}
	// did we find an appropriate matching language resource DLL?
	if (hResourceLibrary == NULL)
	{
		// no - see if there is a patch to the default language resource DLL
		// (this is Seb's original code)
		hResourceLibrary = LoadLibrary("localres.dll");
	}
	if (hResourceLibrary != NULL)
	{
		AfxSetResourceHandle(hResourceLibrary);
	}

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
	CPropFProxy FProxy;
	CPropDiagnostics Diagnostics;

	pNormal = &Normal;
	pAdvanced = &Advanced;
	pGeek = &Geek;
	pWarnPerm = &WarnPerm;
	pFProxy = &FProxy;
	pDiagnostics = &Diagnostics;

	propdlg.AddPage(pNormal);
	propdlg.AddPage(pAdvanced);
	propdlg.AddPage(pGeek);
	propdlg.AddPage(pFProxy);
	propdlg.AddPage(pDiagnostics);

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

	delete pConfigFile;

	// Since the dialog has been closed, return FALSE so that we exit the
	//  application, rather than start the application's message pump.
	return FALSE;
}

int CNodeConfigApp::ExitInstance() 
{
	return (clickedOK) ? 0 : 1; // return 0 if we clicked OK and 1 otherwise (canceled dialog)
}
