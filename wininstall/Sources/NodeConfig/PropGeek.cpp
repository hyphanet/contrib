// PropGeek.cpp : implementation file
//

#include "stdafx.h"
#include "NodeConfig.h"
#include "PropGeek.h"
#include "UnknownDlg.h"
#include "ConfigFile.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropGeek property page

IMPLEMENT_DYNCREATE(CPropGeek, CMoveablePropertyPage)

CPropGeek::CPropGeek() : CMoveablePropertyPage(CPropGeek::IDD)
{
	//{{AFX_DATA_INIT(CPropGeek)
	m_strNumUnknowns = _T("");
	//}}AFX_DATA_INIT
}

CPropGeek::~CPropGeek()
{

}

void CPropGeek::DoDataExchange(CDataExchange* pDX)
{
	CMoveablePropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropGeek)
	DDX_Text(pDX, IDC_announcementAttempts, m_announcementAttempts);
	DDV_MinMaxUInt(pDX, m_announcementAttempts, 1, 256);
	DDX_Text(pDX, IDC_announcementDelay, m_announcementDelay);
	DDV_MinMaxUInt(pDX, m_announcementDelay, 3000, 86400000);
	DDX_Text(pDX, IDC_announcementDelayBase, m_announcementDelayBase);
	DDV_MinMaxUInt(pDX, m_announcementDelayBase, 2, 32);
	DDX_Text(pDX, IDC_announcementPeers, m_announcementPeers);
	DDV_MinMaxUInt(pDX, m_announcementPeers, 1, 256);
	DDX_Text(pDX, IDC_authTimeout, m_authTimeout);
	DDV_MinMaxUInt(pDX, m_authTimeout, 1000, 1800000);
	DDX_Text(pDX, IDC_checkPointInterval, m_checkPointInterval);
	DDV_MinMaxUInt(pDX, m_checkPointInterval, 100, 60000);
	DDX_Text(pDX, IDC_connectionTimeout, m_connectionTimeout);
	DDV_MinMaxUInt(pDX, m_connectionTimeout, 5000, 3600000);
	DDX_Text(pDX, IDC_hopTimeDeviation, m_hopTimeDeviation);
	DDV_MinMaxUInt(pDX, m_hopTimeDeviation, 100, 999999);
	DDX_Text(pDX, IDC_hopTimeExpected, m_hopTimeExpected);
	DDV_MinMaxUInt(pDX, m_hopTimeExpected, 1000, 180000);
	DDX_Text(pDX, IDC_initialRequests, m_initialRequests);
	DDV_MinMaxUInt(pDX, m_initialRequests, 1, 999);
	DDX_Text(pDX, IDC_localAnnounceTargets, m_localAnnounceTargets);
	DDX_Text(pDX, IDC_messageStoreSize, m_messageStoreSize);
	DDV_MinMaxUInt(pDX, m_messageStoreSize, 128, 1048576);
	DDX_Text(pDX, IDC_blockSize, m_blockSize);
	DDX_Text(pDX, IDC_routeConnectTimeout, m_routeConnectTimeout);
	DDV_MinMaxUInt(pDX, m_routeConnectTimeout, 1000, 300000);
	DDX_Text(pDX, IDC_rtMaxNodes, m_rtMaxNodes);
	DDV_MinMaxUInt(pDX, m_rtMaxNodes, 1, 999999);
	DDX_Text(pDX, IDC_rtMaxRefs, m_rtMaxRefs);
	DDV_MinMaxUInt(pDX, m_rtMaxRefs, 1, 99999);
	DDX_Text(pDX, IDC_storeType, m_storeType);
	DDV_MaxChars(pDX, m_storeType, 32);
	DDX_Text(pDX, IDC_storeDataFile, m_storeDataFile);
	DDV_MaxChars(pDX, m_storeDataFile, 256);
	DDX_Text(pDX, IDC_streamBufferSize, m_streamBufferSize);
	DDV_MinMaxUInt(pDX, m_streamBufferSize, 1024, 1048576);
	DDX_Text(pDX, IDC_storeCipherName, m_storeCipherName);
	DDX_Text(pDX, IDC_storeCipherWidth, m_storeCipherWidth);
	DDX_Text(pDX, IDC_maximumPadding, m_maximumPadding);
	DDX_Text(pDX, IDC_NUMUNKNOWN, m_strNumUnknowns);
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CPropGeek, CMoveablePropertyPage)
	//{{AFX_MSG_MAP(CPropGeek)
	ON_BN_CLICKED(IDC_UNKNOWN, OnUnknown)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropGeek message handlers

//The following functions are for the unknown settings dialog

void CPropGeek::OnUnknown()
{
	int temp;
	UpdateData(TRUE);
	CUnknownDlg pUnknownDlg;
	pUnknownDlg.m_unknowns = m_unknowns;
	temp = pUnknownDlg.DoModal();
	m_unknowns = pUnknownDlg.m_unknowns;
	UpdateNumofUnknowns();
}

BOOL CPropGeek::OnInitDialog()
{
	CMoveablePropertyPage::OnInitDialog();
	UpdateNumofUnknowns();

	return TRUE;  // return TRUE unless you set the focus to a control
	              // EXCEPTION: OCX Property Pages should return FALSE
}

void CPropGeek::UpdateNumofUnknowns()
{
	// Updates the number of unknowns displayed on the Geek Page
	UpdateData(TRUE);
	int nUnknowns = 0;
	int lastfound = 0;
	while(m_unknowns.Find("\n", lastfound) != -1)
	{
		lastfound = m_unknowns.Find("\n", lastfound);
		lastfound++;
		nUnknowns++;
	}
	m_strNumUnknowns.Format("%d", nUnknowns);
	UpdateData(FALSE);
}
