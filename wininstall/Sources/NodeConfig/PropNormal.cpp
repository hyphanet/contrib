// PropNormal.cpp : implementation file
//

#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"
#include "PropAdvanced.h"

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
	m_storeCacheSize = 0;
	m_storePath = _T("");
	m_useDefaultNodeRefs = FALSE;
	m_transient = FALSE;
	m_ipAddress = _T("");
	m_listenPort = 0;
	m_notTransient = FALSE;
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
	DDX_Text(pDX, IDC_storeCacheSize, m_storeCacheSize);
	DDV_MinMaxUInt(pDX, m_storeCacheSize, 10, 2047);
	DDX_Text(pDX, IDC_storePath, m_storePath);
	DDX_Check(pDX, IDC_useDefaultNodeRefs, m_useDefaultNodeRefs);
	DDX_Check(pDX, IDC_transient, m_transient);
	DDX_Text(pDX, IDC_ipAddress, m_ipAddress);
	DDV_MaxChars(pDX, m_ipAddress, 128);
	DDX_Text(pDX, IDC_listenPort, m_listenPort);
	DDV_MinMaxUInt(pDX, m_listenPort, 1, 65535);
	DDX_Check(pDX, IDC_notTransient, m_notTransient);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropNormal, CPropertyPage)
	//{{AFX_MSG_MAP(CPropNormal)
	ON_NOTIFY(UDN_DELTAPOS, IDC_storeCacheSize_spin, OnStoreCacheSizespin)
	ON_BN_CLICKED(IDC_notTransient, OnnotTransientClick)
	ON_BN_CLICKED(IDC_transient, OntransientClick)
	ON_WM_CREATE()
	ON_WM_SHOWWINDOW()
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropNormal message handlers

void CPropNormal::OnStoreCacheSizespin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		if (m_storeCacheSize < 2047)
		{
			m_storeCacheSize++;
			UpdateData(FALSE);
		}
	}
	else
	{
		if (m_storeCacheSize > 10)
		{
			m_storeCacheSize--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropNormal::OnnotTransientClick() 
{
	int result;

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
	static CWnd *lpTitle = GetDlgItem(IDC_listenPort_TITLE);
	static CWnd *lpFld = GetDlgItem(IDC_listenPort);

	ipTitle->ShowWindow(showMode);
	ipFld->ShowWindow(showMode);
	lpTitle->ShowWindow(showMode);
	lpFld->ShowWindow(showMode);
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
	CPropertyPage::OnShowWindow(bShow, nStatus);
	
	// hide node ip address/port fields if transient
	showNodeAddrFields(!m_transient);
	
}
