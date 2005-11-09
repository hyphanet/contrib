// PropNormal.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "GetSeedDlg.h"
//#include "DlgWarnPerm.h"
#include "UpdateSpin.h"
#include "PropGeek.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


//extern CDlgWarnPerm		*pWarnPerm;
extern CPropAdvanced	*pAdvanced;


/////////////////////////////////////////////////////////////////////////////
// CPropNormal property page

IMPLEMENT_DYNCREATE(CPropNormal, CMoveablePropertyPage)

CPropNormal::CPropNormal()
:
CMoveablePropertyPage(CPropNormal::IDD),
m_pGeek(NULL),
m_strHiddenNodeAddress("AUTOMATIC")
{
	//{{AFX_DATA_INIT(CPropNormal)
	m_transient = NOT_TRANSIENT;
	m_bAllowNodeAddressChanges = FALSE;
	m_bAutoIP = TRUE;
	//}}AFX_DATA_INIT
}

CPropNormal::~CPropNormal()
{
}

void CPropNormal::DoDataExchange(CDataExchange* pDX)
{
	CMoveablePropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropNormal)
	DDX_Control(pDX, IDC_importNewNodeRef, m_importNewNodeRef);
	DDX_Text(pDX, IDC_storeSize, m_storeSize);
	DDX_Check(pDX, IDC_useDefaultNodeRefs, m_useDefaultNodeRefs);
	DDX_Text(pDX, IDC_ipAddress, m_ipAddress);
	DDV_MaxChars(pDX, m_ipAddress, 128);
	DDX_Text(pDX, IDC_listenPort, m_listenPort);
	DDV_MinMaxUInt(pDX, m_listenPort, 1, 65535);
	DDX_Text(pDX, IDC_storeFile, m_storeFile);
	DDX_Text(pDX, IDC_tempFile, m_tempFile);
	DDX_Radio(pDX, IDC_transient, m_transient);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropNormal, CMoveablePropertyPage)
	//{{AFX_MSG_MAP(CPropNormal)
	ON_NOTIFY(UDN_DELTAPOS, IDC_storeCacheSize_spin, OnStoreCacheSizespin)
	ON_BN_CLICKED(IDC_importNewNodeRef, OnImportNewNodeRef)
	ON_WM_DESTROY()
	ON_BN_CLICKED(IDC_transient, Ontransient)
	ON_BN_CLICKED(IDC_notTransient, OnNotTransient)
	ON_WM_SHOWWINDOW()
	ON_WM_CREATE()
	ON_WM_KILLFOCUS()
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_RANDOMIZEPORT, OnRandomizePort)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropNormal message handlers

void CPropNormal::OnStoreCacheSizespin(NMHDR* pNMHDR, LRESULT* pResult)
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<DWORD> cus(m_storeSize, 10, 0xffffffff);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropNormal::Ontransient()
{
	OnNodeAvailability();
}

void CPropNormal::OnNotTransient()
{
	OnNodeAvailability();
}

void CPropNormal::OnNodeAvailability()
{
	UpdateData(TRUE);
	/*
	int result;
	if ( (m_transient==NOT_TRANSIENT) && warnPerm)
	{
		result = pWarnPerm->DoModal();
		if (pWarnPerm->m_dontWarnPerm)
			warnPerm = FALSE;
	
		// user chickened out of running permanent node
		if (result == IDCANCEL)
		{
			m_transient = TRANSIENT;
		}
	}

	if (m_transient == NOT_TRANSIENT)
	{
		pAdvanced->m_doAnnounce = TRUE;
		showNodeAddrFields(TRUE);
	}
	else
	{
		showNodeAddrFields(FALSE);
	}

	*/

	UpdateData(FALSE);
}

void CPropNormal::showNodeAddrFields(BOOL showing)
{
	int showMode = showing ? SW_SHOW : SW_HIDE;

	static CWnd *ipTitle = GetDlgItem(IDC_ipAddress_TITLE);
	static CWnd *ipFld = GetDlgItem(IDC_ipAddress);

	ipTitle->ShowWindow(showMode);
	ipFld->ShowWindow(showMode);
	UpdateData(FALSE);
}

BOOL CPropNormal::OnInitDialog()
{
	HANDLE hfile;

	//activate "use default seed" if the specified seed files does not exist
	if ((hfile = CreateFile(pAdvanced->m_seedFile,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,NULL,NULL)) == INVALID_HANDLE_VALUE)
	{
		m_useDefaultNodeRefs = TRUE;
	}
	else
		CloseHandle(hfile);

	UpdateData(FALSE);

	return TRUE;  // return TRUE unless you set the focus to a control
	              // EXCEPTION: OCX Property Pages should return FALSE

}

void CPropNormal::OnImportNewNodeRef()
{
	UpdateData(TRUE);

	// uncheck the "get default node refs"
	m_useDefaultNodeRefs = FALSE;

	UpdateData(FALSE);

	CGetSeedDlg getseeddlg(FALSE);
	getseeddlg.DoModal();
}

void CPropNormal::OnDestroy()
{
	if (m_useDefaultNodeRefs)
	{
		// Bob H : We get here if NodeConfig can't find a seednodes.ref .
		// Used to be Getseeddlg(TRUE) which put Getseeddlg in "silent mode", where it automatically downloads seednodes.
		// At least on win2k and XP this doesn't work correctly, the dialog never inits properly and is invisible,
		// so I changed it to FALSE; user now has to press the download button. Also, it didn't use to prompt if they wanted
		// to download. This caused a long-standing "hanging" problem during install after OK'ing the initial NodeConfig,
		// because NodeConfig.exe was initially in a different directory to where the installer downloaded the seednodes
		// and therefore always invisibly downloaded them AGAIN itself! (I fixed this in the wininstaller.)
		// This happened even when the user chose NOT to download seednodes because they already had some e.g. from a friend,
		// not a good thing (hi freenet-china.) For extra fun recently SourceForge has been randomly throttling big 
		// freenetproject.org downloads to 0.2Kb/s - 0.3Kb/s, making the invisible download hang the installer for HOURS.

		CString prompt = "The Freenet node configuration utility couldn't find \"seednodes.ref\".";
		prompt += "\nThis file is needed for freenet to work\n(it provides it with addresses";
		prompt += " of other nodes to connect to initially.)\n\n";
		prompt += "** If you chose NOT to download seednodes.ref, this is normal! **\n";
		prompt += "Please answer NO to this, place your seednodes.ref in the directory\n";
		prompt += "where freenet is installed (e.g. c:\\Program Files\\Freenet)";
		prompt += "\nand then run Freenet.\n\n";  // because compressed so it should be faster, even though dl jars too
		prompt += "We recommend you try \"Update Snapshot\" (once Freenet is\n";
		prompt += "installed) to fix this.\n";
		prompt += "Answer NO, then do Start->Programs->Freenet->Update Snapshot.\n\n";
		prompt += "If that doesn't work, I can try to download the seednodes myself.\n";
		prompt += "Do you want me to try to download them myself now?";

		if( IDYES == AfxMessageBox( prompt, MB_YESNO|MB_ICONQUESTION ) )
		{
			// Cheesey I know ... presumably some way to trigger download that works properly
			AfxMessageBox("Please press \"Download References\" on the next screen.\nThe download may take a while.",
												MB_OK|MB_ICONINFORMATION );
			CGetSeedDlg Getseeddlg(FALSE);
		Getseeddlg.DoModal();
	}
	}

	if (m_pGeek)
	{
		m_bAutoIP = m_pGeek->m_bAutoIP;
		m_bAllowNodeAddressChanges = m_pGeek->m_bAllowNodeAddressChanges;
	}
	if (m_bAutoIP)
	{
		m_ipAddress="AUTOMATIC";
	}
	else
	{
		m_strHiddenNodeAddress=m_ipAddress;
	}

	CMoveablePropertyPage::OnDestroy();
}

void CPropNormal::OnShowWindow(BOOL bShow, UINT nStatus) 
{
	CMoveablePropertyPage::OnShowWindow(bShow, nStatus);
	
	// TODO: Add your message handler code here
	if (m_pGeek)
	{
		m_bAutoIP = m_pGeek->m_bAutoIP;
		m_bAllowNodeAddressChanges = m_pGeek->m_bAllowNodeAddressChanges;
	}
	//GetDlgItem(IDC_STATIC_NODEAVAILABILITY_FRAME)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_ipAddress_TITLE)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_listenPort_TITLE)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_listenPort)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_RANDOMIZEPORT)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_STATIC_NODEADDRESS_COLON)->EnableWindow(m_bAllowNodeAddressChanges);
	GetDlgItem(IDC_notTransient)->EnableWindow(FALSE); // Transitivity WILL NOT BE EDITABLE FROM NODECONFIG
	GetDlgItem(IDC_transient)->EnableWindow(FALSE); // Transitivity WILL NOT BE EDITABLE FROM NODECONFIG
	GetDlgItem(IDC_ipAddress)->EnableWindow(m_bAllowNodeAddressChanges && !m_bAutoIP); // IP still not editable, when Auto IP Detection is enabled
	if (m_bAutoIP)
	{
		m_ipAddress="AUTOMATIC";
	}
	else
	{
		if (bShow)
		{
			m_ipAddress=m_strHiddenNodeAddress;
		}
		else
		{
			m_strHiddenNodeAddress=m_ipAddress;
		}
	}
	UpdateData(FALSE);
}

void CPropNormal::OnRandomizePort() 
{
	// TODO: Add your control notification handler code here
	m_listenPort = rand() + 1024;	// random port number
	UpdateData(FALSE);
}
