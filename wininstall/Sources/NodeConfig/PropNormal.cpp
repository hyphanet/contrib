// PropNormal.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "GetSeedDlg.h"
#include "DlgWarnPerm.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

extern CDlgWarnPerm		*pWarnPerm;
extern CPropAdvanced	*pAdvanced;


/////////////////////////////////////////////////////////////////////////////
// CPropNormal property page

IMPLEMENT_DYNCREATE(CPropNormal, CPropertyPage)

CPropNormal::CPropNormal() : CPropertyPage(CPropNormal::IDD)
{
	//{{AFX_DATA_INIT(CPropNormal)
	//}}AFX_DATA_INIT
}

CPropNormal::~CPropNormal()
{
}

void CPropNormal::DoDataExchange(CDataExchange* pDX)
{
	CPropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropNormal)
	DDX_Control(pDX, IDC_importNewNodeRef, m_importNewNodeRef);
	DDX_Text(pDX, IDC_storeSize, m_storeSize);
	DDX_Check(pDX, IDC_useDefaultNodeRefs, m_useDefaultNodeRefs);
	DDX_Check(pDX, IDC_transient, m_transient);
	DDX_Text(pDX, IDC_ipAddress, m_ipAddress);
	DDV_MaxChars(pDX, m_ipAddress, 128);
	DDX_Text(pDX, IDC_listenPort, m_listenPort);
	DDV_MinMaxUInt(pDX, m_listenPort, 1, 65535);
	DDX_Check(pDX, IDC_notTransient, m_notTransient);
	DDX_Text(pDX, IDC_storeFile, m_storeFile);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropNormal, CPropertyPage)
	//{{AFX_MSG_MAP(CPropNormal)
	ON_NOTIFY(UDN_DELTAPOS, IDC_storeCacheSize_spin, OnStoreCacheSizespin)
	ON_BN_CLICKED(IDC_notTransient, OnnotTransientClick)
	ON_BN_CLICKED(IDC_transient, OntransientClick)
	ON_WM_CREATE()
	ON_WM_SHOWWINDOW()
	ON_BN_CLICKED(IDC_importNewNodeRef, OnImportNewNodeRef)
	ON_WM_DESTROY()
	ON_WM_KILLFOCUS()
	ON_WM_CLOSE()
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropNormal message handlers

void CPropNormal::OnStoreCacheSizespin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	DWORD dwNewStoreSize = m_storeSize+pNMUpDown->iDelta;
	const DWORD dwMaxStoreSize = 0xffffffff;
	const DWORD dwMinStoreSize = 10;

	if ( pNMUpDown->iDelta > 0 && m_storeSize < dwMaxStoreSize )
	{
		if ( dwNewStoreSize < m_storeSize || dwNewStoreSize > dwMaxStoreSize )
		{
			// it went beyond dwMaxStoreSize or wrapped over 0xffffffff
			dwNewStoreSize = dwMaxStoreSize;
		}
		m_storeSize = dwNewStoreSize;
		UpdateData(FALSE);
	}
	else if (m_storeSize > dwMinStoreSize)
	{
		if ( dwNewStoreSize > m_storeSize || dwNewStoreSize < dwMinStoreSize)
		{
			// it went beyone dwMinStoreSize or wrapped over 0x00000000
			dwNewStoreSize=10;
		}
		m_storeSize = dwNewStoreSize;
		UpdateData(FALSE);
	}

	*pResult = 0;
}

void CPropNormal::OnnotTransientClick() 
{
	int result;

	UpdateData(TRUE);
	if (warnPerm)
	{
		result = pWarnPerm->DoModal();
		if (pWarnPerm->m_dontWarnPerm)
			warnPerm = FALSE;

		// user chickened out of running permanent node
		if (result == IDCANCEL)
		{
			m_notTransient = FALSE;
			UpdateData(FALSE);
			return;
		}
	}

	m_notTransient = TRUE;
	m_transient = FALSE;
	pAdvanced->m_doAnnounce = TRUE;
	showNodeAddrFields(TRUE);
	UpdateData(FALSE);
}

void CPropNormal::OntransientClick() 
{
	UpdateData(TRUE);
	m_transient = TRUE;
	m_notTransient = FALSE;
	showNodeAddrFields(FALSE);
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


int CPropNormal::OnCreate(LPCREATESTRUCT lpCreateStruct) 
{
	if (CPropertyPage::OnCreate(lpCreateStruct) == -1)
		return -1;
	
	return 0;
}

void CPropNormal::OnShowWindow(BOOL bShow, UINT nStatus) 
{
	HANDLE hfile;

	CPropertyPage::OnShowWindow(bShow, nStatus);

	// hide node ip address/port fields if transient
	showNodeAddrFields(!m_transient);

	//activate "use default seed" if the specified seed files does not exist
	if ((hfile = CreateFile(pAdvanced->m_seedFile,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,NULL,NULL)) == INVALID_HANDLE_VALUE)
	{
		m_useDefaultNodeRefs = TRUE;
	}
	else
		CloseHandle(hfile);

	UpdateData(FALSE);	
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
		CGetSeedDlg Getseeddlg(TRUE);
		Getseeddlg.DoModal();
	}

	CPropertyPage::OnDestroy();
}
