// PropGeek.cpp : implementation file
//


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropGeek.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropGeek property page

IMPLEMENT_DYNCREATE(CPropGeek, CPropertyPage)

CPropGeek::CPropGeek() : CPropertyPage(CPropGeek::IDD)
{
	//{{AFX_DATA_INIT(CPropGeek)
	//m_announcementAttempts = 0;
	//m_announcementDelay = 0;
	//m_announcementDelayBase = 0;
	//m_announcementPeers = 0;
	//m_authTimeout = 0;
	//m_blockSize = 0;
	//m_checkPointInterval = 0;
	//m_connectionTimeout = 0;
	//m_diagnosticsPath = _T("");
	//m_doDiagnostics = FALSE;
	//m_hopTimeDeviation = 0;
	//m_hopTimeExpected = 0;
	//m_initialRequests = 0;
	//m_localAnnounceTargets = _T("");
	//m_logFile = _T("");
	//m_messageStoreSize = 0;
	//m_minCacheCount = 0;
	//m_routeConnectTimeout = 0;
	//m_rtMaxNodes = 0;
	//m_rtMaxRefs = 0;
	//m_storeCacheFile = _T("");
	//m_storeDataFile = _T("");
	//m_streamBufferSize = 0;
	//m_logLevel = _T("");
	//m_logVerbosity = _T("");
	//}}AFX_DATA_INIT
}

CPropGeek::~CPropGeek()
{
}

void CPropGeek::DoDataExchange(CDataExchange* pDX)
{
	CPropertyPage::DoDataExchange(pDX);
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
	DDX_Text(pDX, IDC_blockSize, m_blockSize);
	DDV_MinMaxUInt(pDX, m_blockSize, 256, 1048576);
	DDX_Text(pDX, IDC_checkPointInterval, m_checkPointInterval);
	DDV_MinMaxUInt(pDX, m_checkPointInterval, 100, 60000);
	DDX_Text(pDX, IDC_connectionTimeout, m_connectionTimeout);
	DDV_MinMaxUInt(pDX, m_connectionTimeout, 5000, 3600000);
	DDX_Text(pDX, IDC_diagnosticsPath, m_diagnosticsPath);
	DDV_MaxChars(pDX, m_diagnosticsPath, 256);
	DDX_Check(pDX, IDC_doDiagnostics, m_doDiagnostics);
	DDX_Text(pDX, IDC_hopTimeDeviation, m_hopTimeDeviation);
	DDV_MinMaxUInt(pDX, m_hopTimeDeviation, 100, 999999);
	DDX_Text(pDX, IDC_hopTimeExpected, m_hopTimeExpected);
	DDV_MinMaxUInt(pDX, m_hopTimeExpected, 1000, 180000);
	DDX_Text(pDX, IDC_initialRequests, m_initialRequests);
	DDV_MinMaxUInt(pDX, m_initialRequests, 1, 999);
	DDX_Text(pDX, IDC_localAnnounceTargets, m_localAnnounceTargets);
	DDX_Text(pDX, IDC_logFile, m_logFile);
	DDV_MaxChars(pDX, m_logFile, 256);
	DDX_Text(pDX, IDC_messageStoreSize, m_messageStoreSize);
	DDV_MinMaxUInt(pDX, m_messageStoreSize, 128, 1048576);
	DDX_Text(pDX, IDC_minCacheCount, m_minCacheCount);
	DDV_MinMaxUInt(pDX, m_minCacheCount, 1, 1048576);
	DDX_Text(pDX, IDC_routeConnectTimeout, m_routeConnectTimeout);
	DDV_MinMaxUInt(pDX, m_routeConnectTimeout, 1000, 300000);
	DDX_Text(pDX, IDC_rtMaxNodes, m_rtMaxNodes);
	DDV_MinMaxUInt(pDX, m_rtMaxNodes, 1, 999999);
	DDX_Text(pDX, IDC_rtMaxRefs, m_rtMaxRefs);
	DDV_MinMaxUInt(pDX, m_rtMaxRefs, 1, 99999);
	DDX_Text(pDX, IDC_storeCacheFile, m_storeCacheFile);
	DDV_MaxChars(pDX, m_storeCacheFile, 256);
	DDX_Text(pDX, IDC_storeDataFile, m_storeDataFile);
	DDV_MaxChars(pDX, m_storeDataFile, 256);
	DDX_Text(pDX, IDC_streamBufferSize, m_streamBufferSize);
	DDV_MinMaxUInt(pDX, m_streamBufferSize, 1024, 1048576);
	DDX_CBString(pDX, IDC_logLevel, m_logLevel);
	DDV_MaxChars(pDX, m_logLevel, 6);
	DDX_CBString(pDX, IDC_logVerbosity, m_logVerbosity);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropGeek, CPropertyPage)
	//{{AFX_MSG_MAP(CPropGeek)
		// NOTE: the ClassWizard will add message map macros here
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropGeek message handlers
