// PropDiagnostics.cpp : implementation file
//

#include "stdafx.h"
#include "nodeconfig.h"
#include "PropDiagnostics.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropDiagnostics property page

IMPLEMENT_DYNCREATE(CPropDiagnostics, CPropertyPage)

CPropDiagnostics::CPropDiagnostics() : CPropertyPage(CPropDiagnostics::IDD)
{
	//{{AFX_DATA_INIT(CPropDiagnostics)
	//}}AFX_DATA_INIT
}

CPropDiagnostics::~CPropDiagnostics()
{
}

void CPropDiagnostics::DoDataExchange(CDataExchange* pDX)
{
	CPropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropDiagnostics)
	DDX_Check(pDX, IDC_NODEINFOSERVLET, m_nodeinfoservlet);
	DDX_Text(pDX, IDC_NODEINFOCLASS, m_nodeinfoclass);
	DDX_Text(pDX, IDC_NODEINFOPORT, m_nodeinfoport);
	DDX_Text(pDX, IDC_logFile, m_logFile);
	DDX_Text(pDX, IDC_logFormat, m_logFormat);
	DDX_CBString(pDX, IDC_logLevel, m_logLevel);
	DDX_Text(pDX, IDC_diagnosticsPath, m_diagnosticsPath);
	DDX_Check(pDX, IDC_doDiagnostics, m_doDiagnostics);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropDiagnostics, CPropertyPage)
	//{{AFX_MSG_MAP(CPropDiagnostics)
	ON_BN_CLICKED(IDC_NODEINFOSERVLET, OnNodeinfoservlet)
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
	return CPropertyPage::OnSetActive();
}
