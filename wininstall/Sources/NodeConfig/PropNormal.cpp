// PropNormal.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"
#include "PropAdvanced.h"
#include "GetSeedDlg.h"
#include "DlgWarnPerm.h"
#include "UpdateSpin.h"

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
	m_transient = NOT_TRANSIENT;
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
	DDX_Text(pDX, IDC_ipAddress, m_ipAddress);
	DDV_MaxChars(pDX, m_ipAddress, 128);
	DDX_Text(pDX, IDC_listenPort, m_listenPort);
	DDV_MinMaxUInt(pDX, m_listenPort, 1, 65535);
	DDX_Text(pDX, IDC_storeFile, m_storeFile);
	DDX_Radio(pDX, IDC_transient, m_transient);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropNormal, CPropertyPage)
	//{{AFX_MSG_MAP(CPropNormal)
	ON_NOTIFY(UDN_DELTAPOS, IDC_storeCacheSize_spin, OnStoreCacheSizespin)
	ON_WM_CREATE()
	ON_WM_SHOWWINDOW()
	ON_BN_CLICKED(IDC_importNewNodeRef, OnImportNewNodeRef)
	ON_WM_DESTROY()
	ON_WM_KILLFOCUS()
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_transient, Ontransient)
	ON_BN_CLICKED(IDC_notTransient, OnNotTransient)
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
	int result;

	UpdateData(TRUE);
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
	showNodeAddrFields(m_transient==NOT_TRANSIENT);

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
