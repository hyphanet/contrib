// GetSeedDlg.cpp : implementation file
//

#include "stdafx.h"
#include "afxinet.h"
#include "Wininet.h"
#include "GetSeed.h"
#include "GetSeedDlg.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CGetSeedDlg dialog

CGetSeedDlg::CGetSeedDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CGetSeedDlg::IDD, pParent)
{
	CString str = _pgmptr;
	str = str.Left(str.ReverseFind('\\')+1) + DEFSEEDFILE; //Take the executable Program path
		//{{AFX_DATA_INIT(CGetSeedDlg)
	m_seedURL = _T(DEFSEEDURL);
	m_seedfile = _T(str);
	//}}AFX_DATA_INIT
	// Note that LoadIcon does not require a subsequent DestroyIcon in Win32
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
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

BOOL CGetSeedDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon
	
	// TODO: Add extra initialization here
	
	return TRUE;  // return TRUE  unless you set the focus to a control
}

BOOL CGetSeedDlg::OnGetseed()
//returns TRUE on success
{
	HINTERNET hInternet, hhttpFile;
	HANDLE hfile;
	CString str;
	char buf[512];
	BOOL success = 1, doDialUp = 0;
	DWORD filesize = 0;
	DWORD fsize_bufsize = 4; //size of filesize buffer in bytes
	DWORD dwFlags = INTERNET_CONNECTION_MODEM; //check for existing connection flags
	DWORD read;
	DWORD written;

	//DWORD InternetHangUp(DWORD dwConnection,DWORD dwReserved);
	
	//vvvv FALSE if there is no Internet connection
	if (!InternetGetConnectedState(&dwFlags,0))
	{
		str.LoadString(IDS_NOTCONNECTED);
		if (MessageBox(str,NULL,MB_OKCANCEL) == IDCANCEL)
		{ //abort the download and quit the function
			return FALSE;
		}
		doDialUp = TRUE;
	}
	UpdateData(TRUE);
	hInternet = InternetOpen(AfxGetApp()->m_pszAppName, INTERNET_OPEN_TYPE_PRECONFIG , NULL, NULL, NULL /*dwFlags*/); // returns NULL on failure
	hhttpFile = InternetOpenUrl(hInternet, m_seedURL, NULL, NULL, INTERNET_FLAG_NO_UI|INTERNET_FLAG_NO_COOKIES, NULL); //return FAIL on error
	HttpQueryInfo(hhttpFile,HTTP_QUERY_CONTENT_LENGTH|HTTP_QUERY_FLAG_NUMBER,(LPVOID)&filesize,&fsize_bufsize,NULL);
	if (filesize) 
	{	//Set progress bar control step size
		m_dloadprogressbar.SetStep((int)(100*512/filesize));
	}
	success = ((hfile = CreateFile(m_seedfile, GENERIC_WRITE, 0, 0, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, 0)) != INVALID_HANDLE_VALUE) && success;
	do 
	{
		success = InternetReadFile(hhttpFile, buf, 512, &read) && success;
		success = WriteFile(hfile, buf, read, &written, 0) && success;
		success = (written == read) && success;
		if (filesize && written && success) m_dloadprogressbar.StepIt();
	} while (written && success);

	// Cleanup now
	InternetCloseHandle(hhttpFile);	// close the remote http file
	InternetCloseHandle(hInternet);	// close the Internet handle
	if (doDialUp) {InternetAutodialHangup(0);}
	CloseHandle(hfile);
	if (success)
		EndDialog(1);
	else
	{
		DeleteFile(m_seedfile);
		str.LoadString(IDS_ERR_GETSEED);
		MessageBox(str,NULL,MB_OK|MB_ICONERROR);
	}
		
	return success;
}

BOOL CGetSeedDlg::OnGetlocalseed() 
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

	return success;
}

