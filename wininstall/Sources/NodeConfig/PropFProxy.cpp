// PropFProxy.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropFProxy.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropGeek property page

IMPLEMENT_DYNCREATE(CPropFProxy, CPropertyPage)

CPropFProxy::CPropFProxy() : CPropertyPage(CPropFProxy::IDD)
{
	//{{AFX_DATA_INIT(CPropFProxy)
	m_fproxyrequesthtl = 0;
	m_fproxyinserthtl = 0;
	m_bfproxyfilter = FALSE;
	m_bfproxyservice = FALSE;
	m_strfproxyallowedmime = _T("");
	//}}AFX_DATA_INIT
}

CPropFProxy::~CPropFProxy()
{
}

void CPropFProxy::DoDataExchange(CDataExchange* pDX)
{
	CPropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropFProxy)
	DDX_Text(pDX, IDC_fproxy_class, m_fproxyclass);
	DDX_Text(pDX, IDC_fproxy_port, m_fproxyport);
	DDX_Text(pDX, IDC_fproxy_requesthtl, m_fproxyrequesthtl);
	DDX_Text(pDX, IDC_fproxy_inserthtl, m_fproxyinserthtl);
	DDX_Check(pDX, IDC_FPROXYFILTER, m_bfproxyfilter);
	DDX_Check(pDX, IDC_FPROXYSERVICE, m_bfproxyservice);
	DDX_Text(pDX, IDC_FPROXYALLOWEDMIME, m_strfproxyallowedmime);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropFProxy, CPropertyPage)
	//{{AFX_MSG_MAP(CPropFProxy)
		// NOTE: the ClassWizard will add message map macros here
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropFProxy message handlers
