// PropDiagnostics.cpp : implementation file
//

#include "stdafx.h"
#include "nodeconfig.h"
#include "PropDiagnostics.h"
#include "UpdateSpin.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropDiagnostics property page

IMPLEMENT_DYNCREATE(CPropDiagnostics, CMoveablePropertyPage)

CPropDiagnostics::CPropDiagnostics() : CMoveablePropertyPage(CPropDiagnostics::IDD)
{
	//{{AFX_DATA_INIT(CPropDiagnostics)
	m_nFailureTableEntries = 0;
	m_nFailureTableTimeSeconds = 0;
	//}}AFX_DATA_INIT
}

CPropDiagnostics::~CPropDiagnostics()
{
}

void CPropDiagnostics::DoDataExchange(CDataExchange* pDX)
{
	CMoveablePropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropDiagnostics)
	DDX_Check(pDX, IDC_NODEINFOSERVLET, m_nodeinfoservlet);
	DDX_Text(pDX, IDC_NODEINFOCLASS, m_nodeinfoclass);
	DDX_Text(pDX, IDC_NODEINFOPORT, m_nodeinfoport);
	DDX_Text(pDX, IDC_logFile, m_logFile);
	DDX_Text(pDX, IDC_logFormat, m_logFormat);
	DDX_CBString(pDX, IDC_logLevel, m_logLevel);
	DDX_Text(pDX, IDC_diagnosticsPath, m_diagnosticsPath);
	DDX_Check(pDX, IDC_doDiagnostics, m_doDiagnostics);
	DDX_Check(pDX, IDC_BINBOUNDCONTACTS, m_bLogInboundContacts);
	DDX_Check(pDX, IDC_BINBOUNDREQUESTS, m_bLogInboundRequests);
	DDX_Check(pDX, IDC_BOUTBOUNDCONTACTS, m_bLogOutboundContacts);
	DDX_Check(pDX, IDC_BOUTBOUNDREQUESTS, m_bLogOutboundRequests);
	DDX_Text(pDX, IDC_FAILURETABENTRIES, m_nFailureTableEntries);
	DDV_MinMaxUInt(pDX, m_nFailureTableEntries, 1, 32768);
	DDX_Text(pDX, IDC_FAILURETABTIME, m_nFailureTableTimeSeconds);
	DDV_MinMaxUInt(pDX, m_nFailureTableTimeSeconds, 1, 14400);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropDiagnostics, CMoveablePropertyPage)
	//{{AFX_MSG_MAP(CPropDiagnostics)
	ON_BN_CLICKED(IDC_NODEINFOSERVLET, OnNodeinfoservlet)
	ON_NOTIFY(UDN_DELTAPOS, IDC_FAILURETABENTRIESSPIN, OnFailureTabEntriesSpin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_FAILURETABTIMESPIN, OnFailureTabTimeSpin)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropDiagnostics message handlers

void CPropDiagnostics::OnNodeinfoservlet()
{
	UpdateData(TRUE);
	GetDlgItem(IDC_NODEINFOCLASS)->EnableWindow(m_nodeinfoservlet);
	GetDlgItem(IDC_NODEINFOPORT)->EnableWindow(m_nodeinfoservlet);
}

BOOL CPropDiagnostics::OnSetActive()
{
	UpdateData(TRUE);
	GetDlgItem(IDC_NODEINFOCLASS)->EnableWindow(m_nodeinfoservlet);
	GetDlgItem(IDC_NODEINFOPORT)->EnableWindow(m_nodeinfoservlet);
	return CMoveablePropertyPage::OnSetActive();
}

void CPropDiagnostics::OnFailureTabEntriesSpin(NMHDR* pNMHDR, LRESULT* pResult)
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_nFailureTableEntries, 1, 32768);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropDiagnostics::OnFailureTabTimeSpin(NMHDR* pNMHDR, LRESULT* pResult)
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_nFailureTableTimeSeconds, 1, 14400);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		UpdateData(FALSE);
	}
	*pResult = 0;
}
