// PropNormal.cpp : implementation file
//

#include "stdafx.h"
#include "NodeConfig.h"
#include "PropNormal.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropNormal property page

IMPLEMENT_DYNCREATE(CPropNormal, CPropertyPage)

CPropNormal::CPropNormal() : CPropertyPage(CPropNormal::IDD)
{
	//{{AFX_DATA_INIT(CPropNormal)
	m_storeCacheSize = 0;
	m_storePath = _T("");
	m_useDefaultNodeRefs = FALSE;
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
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropNormal, CPropertyPage)
	//{{AFX_MSG_MAP(CPropNormal)
	ON_NOTIFY(UDN_DELTAPOS, IDC_storeCacheSize_spin, OnStoreCacheSizespin)
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
