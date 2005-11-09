// GetSeedDlg.cpp : implementation file
//
#include "stdafx.h"
#include "afxinet.h"
#include "Wininet.h"
#include "NodeConfig.h"
#include "PropAdvanced.h"
#include "GetSeedDlg.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

//extern char progPath[256];
extern CPropAdvanced *pAdvanced;
/////////////////////////////////////////////////////////////////////////////
// CGetSeedDlg dialog

CGetSeedDlg::CGetSeedDlg(BOOL silent /*FALSE*/, CWnd* pParent /*NULL*/)
	: CDialog(CGetSeedDlg::IDD, pParent)
{
	m_silent = silent;
	CString str;// = progPath;
	str += (pAdvanced->m_seedFile.IsEmpty()) ? pAdvanced->m_seedFile : DEFSEEDFILE; //Create the default seednodes.ref location
	//{{AFX_DATA_INIT(CGetSeedDlg)
	m_seedURL = _T(DEFSEEDURL);
	m_seedfile = _T(str);
	//}}AFX_DATA_INIT
	// Note that LoadIcon does not require a subsequent DestroyIcon in Win32
	//m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CGetSeedDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CGetSeedDlg)
	DDX_Control(pDX, IDC_DLOADPROGRESS, m_dloadprogressbar);
	DDX_Text(pDX, IDC_SEEDURL, m_seedURL);
	DDX_Text(pDX, IDC_SEEDFILE, m_seedfile);
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CGetSeedDlg, CDialog)
	//{{AFX_MSG_MAP(CGetSeedDlg)
	ON_BN_CLICKED(IDC_GETSEED, OnGetseed)
	ON_BN_CLICKED(IDC_GETLOCALSEED, OnGetlocalseed)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CGetSeedDlg message handlers

afx_msg BOOL CGetSeedDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	//SetIcon(m_hIcon, TRUE);			// Set big icon
	//SetIcon(m_hIcon, FALSE);		// Set small icon

	// If we are in silent mode start the download automatically
	if (m_silent) SendMessage(WM_COMMAND,LOWORD(IDC_GETSEED)|HIWORD(BN_CLICKED),0);//Handle to the button);

	return TRUE;  // return TRUE  unless you set the focus to a control
}

void CGetSeedDlg::OnGetseed()
//returns TRUE on success
{
	HINTERNET hInternet=NULL, hhttpFile=NULL;
	HANDLE hfile;
	CString str;
	BYTE buf[512];
	BOOL success = TRUE, doDialUp = FALSE;
	DWORD filesize = 0;
	DWORD fsize_bufsize = 4; //size of filesize buffer in bytes
	DWORD dwFlags = INTERNET_CONNECTION_MODEM; //check for existing connection flags
	DWORD read;
	DWORD written;

	

	// DCH 26 Sep 2002: now uses run-time linking to WinInet instead of load-time linking
	// Solves NodeConfig program not running on base-level Win95 boxes.

	typedef BOOL (WINAPI *fnINTERNETGETCONNECTEDSTATE)(LPDWORD lpdwFlags, DWORD dwReserved);

	typedef BOOL (WINAPI *fnINTERNETAUTODIAL)(DWORD dwFlags, DWORD dwReserved);

	typedef BOOL (WINAPI *fnINTERNETAUTODIALHANGUP)(DWORD dwReserved);

	typedef HINTERNET (WINAPI *fnINTERNETOPEN)(LPCSTR lpszAgent, DWORD dwAccessType, LPCSTR lpszProxyName, LPCSTR lpszProxyBypass, DWORD dwFlags);

	typedef HINTERNET (WINAPI *fnINTERNETOPENURL)(HINTERNET hInternetSession, LPCSTR lpszUrl, LPCSTR lpszHeaders, DWORD dwHeadersLength, DWORD dwFlags, DWORD dwContext);

	typedef BOOL (WINAPI *fnHTTPQUERYINFO)(HINTERNET hHttpRequest, DWORD dwInfoLevel, LPVOID lpvBuffer, LPDWORD lpdwBufferLength, LPDWORD lpdwIndex);

	typedef BOOL (WINAPI *fnINTERNETREADFILE)(HINTERNET hFile, LPVOID lpBuffer, DWORD dwNumberOfBytesToRead, LPDWORD lpNumberOfBytesRead);

	typedef BOOL (WINAPI *fnINTERNETCLOSEHANDLE)(HINTERNET hInet);



	HINSTANCE hWinInet = LoadLibrary("WinInet.DLL");

	if (hWinInet==NULL)

	{

		str.LoadString(IDS_ERR_NOWININET);

		MessageBox(str,NULL,MB_OK|MB_ICONERROR);

		return;

//		return FALSE;

	}



	fnINTERNETGETCONNECTEDSTATE pInternetGetConnectedState = (fnINTERNETGETCONNECTEDSTATE)GetProcAddress(hWinInet, "InternetGetConnectedState");

	if (pInternetGetConnectedState==NULL) pInternetGetConnectedState = (fnINTERNETGETCONNECTEDSTATE)GetProcAddress(hWinInet, "InternetGetConnectedStateA");

	fnINTERNETAUTODIAL pInternetAutodial = (fnINTERNETAUTODIAL)GetProcAddress(hWinInet, "InternetAutodial");

	if (pInternetAutodial==NULL) pInternetAutodial = (fnINTERNETAUTODIAL)GetProcAddress(hWinInet, "InternetAutodialA");

	fnINTERNETAUTODIALHANGUP pInternetAutodialHangup = (fnINTERNETAUTODIALHANGUP)GetProcAddress(hWinInet, "InternetAutodialHangup");

	if (pInternetAutodialHangup==NULL) pInternetAutodialHangup = (fnINTERNETAUTODIALHANGUP)GetProcAddress(hWinInet, "InternetAutodialHangupA");

	fnINTERNETOPEN pInternetOpen = (fnINTERNETOPEN)GetProcAddress(hWinInet, "InternetOpen");

	if (pInternetOpen==NULL) pInternetOpen = (fnINTERNETOPEN)GetProcAddress(hWinInet, "InternetOpenA");

	fnINTERNETOPENURL pInternetOpenUrl = (fnINTERNETOPENURL)GetProcAddress(hWinInet, "InternetOpenUrl");

	if (pInternetOpenUrl==NULL) pInternetOpenUrl = (fnINTERNETOPENURL)GetProcAddress(hWinInet, "InternetOpenUrlA");

	fnHTTPQUERYINFO pHttpQueryInfo = (fnHTTPQUERYINFO)GetProcAddress(hWinInet, "HttpQueryInfo");

	if (pHttpQueryInfo==NULL) pHttpQueryInfo = (fnHTTPQUERYINFO)GetProcAddress(hWinInet, "HttpQueryInfoA");

	fnINTERNETREADFILE pInternetReadFile = (fnINTERNETREADFILE)GetProcAddress(hWinInet, "InternetReadFile");

	if (pInternetReadFile==NULL) pInternetReadFile = (fnINTERNETREADFILE)GetProcAddress(hWinInet, "InternetReadFileA");

	fnINTERNETCLOSEHANDLE pInternetCloseHandle = (fnINTERNETCLOSEHANDLE)GetProcAddress(hWinInet, "InternetCloseHandle");

	if (pInternetCloseHandle==NULL) pInternetCloseHandle = (fnINTERNETCLOSEHANDLE)GetProcAddress(hWinInet, "InternetCloseHandleA");



	if (pInternetOpen==NULL || pInternetOpenUrl==NULL || pHttpQueryInfo==NULL || pInternetReadFile==NULL || pInternetCloseHandle==NULL)

	{

		// exit if the 'vital' APIs aren't available.

		str.LoadString(IDS_ERR_NOWININET);

		MessageBox(str,NULL,MB_OK|MB_ICONERROR);

		FreeLibrary(hWinInet);

		hWinInet=NULL;

		return;

//		return FALSE;

	}




	//InternetGetConnectedState returns FALSE if there is no Internet connection
	if ((pInternetGetConnectedState == NULL) || (pInternetGetConnectedState(&dwFlags,0)==FALSE) )
	{
		str.LoadString(IDS_NOTCONNECTED);
		if (MessageBox(str,NULL,MB_OKCANCEL) == IDCANCEL)
		{

			//abort the download and quit the function

			FreeLibrary(hWinInet);

			hWinInet=NULL;
			return;
//			return FALSE;
		}
		doDialUp = TRUE;
	}


	UpdateData(TRUE);
	hInternet = pInternetOpen(AfxGetApp()->m_pszAppName, INTERNET_OPEN_TYPE_PRECONFIG , NULL, NULL, NULL /*dwFlags*/); // returns NULL on failure

	if (hInternet) hhttpFile = pInternetOpenUrl(hInternet, m_seedURL, NULL, NULL, INTERNET_FLAG_NO_UI|INTERNET_FLAG_NO_COOKIES, NULL); //return FAIL on error
	if (hhttpFile)

	{

		success = pHttpQueryInfo(hhttpFile,HTTP_QUERY_CONTENT_LENGTH|HTTP_QUERY_FLAG_NUMBER,(LPVOID)&filesize,&fsize_bufsize,NULL);
		if (success && filesize)
		{	//Set progress bar control step size
			m_dloadprogressbar.SetStep((int)(100*512/filesize));
		}
		success = ((hfile = CreateFile(m_seedfile, GENERIC_WRITE, 0, 0, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, 0)) != INVALID_HANDLE_VALUE) && success;

		while (success)

		{
			success = pInternetReadFile(hhttpFile, buf, 512, &read) && success;

			if (success && read==0) break;
			success = WriteFile(hfile, buf, read, &written, 0) && success;
			success = (written == read) && success;
			if (filesize && written && success) m_dloadprogressbar.StepIt();

		};

	}


	// Cleanup now
	if (hhttpFile) pInternetCloseHandle(hhttpFile);	// close the remote http file
	if (hInternet) pInternetCloseHandle(hInternet);	// close the Internet handle

	if (doDialUp && pInternetAutodialHangup) {pInternetAutodialHangup(0);}



	if (hfile) CloseHandle(hfile);

//	FreeLibrary(hWinInet);  // Microsoft bug - calling FreeLibrary on WinInet.DLL causes application to hang!

	hWinInet=NULL;


	if (success)
		EndDialog(1);
	else
	{
		DeleteFile(m_seedfile);
		//	str.LoadString(IDS_ERR_GETSEED);
		//	MessageBox(str,NULL,MB_OK|MB_ICONERROR);

		// Seednode mirror sites
		CString strSFSeednodesMirror = "http://freenetproject.org/snapshots/seednodes.ref";
		CString strEmuSeednodesMirror = "http://emu.freenetproject.org/downloads/seednodes/seednodes.ref";
		
		// Bob H : Download has failed for some reason.
		// If not using SourceForge mirror, offer to retry with it
		if( m_seedURL.Find( strEmuSeednodesMirror ) > -1 )
		{
			CString strPrompt = "Downloading seednodes from site #1 failed.\n";
			strPrompt += "Would you like to try switching to a mirror site?\n";
			if( IDYES == AfxMessageBox( strPrompt, MB_YESNO|MB_ICONQUESTION ) )
			{
				m_seedURL = strSFSeednodesMirror;
				UpdateData( false );
				AfxMessageBox("Switched to mirror site #2.\nPress \"Download References\" to start.");
			}
		}

		// Converse (SourceForge --> Emu) ... obviously this logic only works for 2 mirror sites
		else if( m_seedURL.Find( strSFSeednodesMirror ) > -1 )
		{
			CString strPrompt = "Downloading seednodes from site #2 failed.\n";
			strPrompt += "Would you like to try switching to a mirror site?\n";
			if( IDYES == AfxMessageBox( strPrompt, MB_YESNO|MB_ICONQUESTION ) )
			{
				m_seedURL = strEmuSeednodesMirror;
				UpdateData( false );
				AfxMessageBox("Switched to mirror site #1.\nPress \"Download References\" to start.");
			}
		}

	}
	return;
//	return success;
}

void CGetSeedDlg::OnGetlocalseed()
{
	BOOL success;
	CString str;

	CFileDialog m_ldFile(TRUE, NULL, NULL,OFN_FILEMUSTEXIST|OFN_HIDEREADONLY,"Freenet reference file (*.ref)|*.ref|All Files (*.*)|*.*||",GetParent());

	m_ldFile.DoModal();
	success = CopyFile(m_ldFile.GetFileName(),m_seedfile,FALSE);	// return TRUE on success
	if (!success)
	{
		str.LoadString(IDS_ERR_GETSEED);
		MessageBox(str,NULL,MB_OK|MB_ICONERROR);
	}
	else EndDialog(1);

	return;
//	return success;
}

