// DlgWarnPerm.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "nodeconfig.h"
#include "DlgWarnPerm.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CDlgWarnPerm dialog


CDlgWarnPerm::CDlgWarnPerm(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgWarnPerm::IDD, pParent)
{
	//{{AFX_DATA_INIT(CDlgWarnPerm)
	m_dontWarnPerm = FALSE;
	//}}AFX_DATA_INIT
}


void CDlgWarnPerm::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CDlgWarnPerm)
	DDX_Check(pDX, IDC_DONT_WARN_PERM, m_dontWarnPerm);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CDlgWarnPerm, CDialog)
	//{{AFX_MSG_MAP(CDlgWarnPerm)
		// NOTE: the ClassWizard will add message map macros here
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CDlgWarnPerm message handlers
