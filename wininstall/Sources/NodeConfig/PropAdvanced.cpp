// PropAdvanced.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropAdvanced.h"
#include "UpdateSpin.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CPropAdvanced property page

IMPLEMENT_DYNCREATE(CPropAdvanced, CPropertyPage)

CPropAdvanced::CPropAdvanced() : CPropertyPage(CPropAdvanced::IDD)
{
	//{{AFX_DATA_INIT(CPropAdvanced)
	//}}AFX_DATA_INIT
}

CPropAdvanced::~CPropAdvanced()
{
}

void CPropAdvanced::DoDataExchange(CDataExchange* pDX)
{
	CPropertyPage::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CPropAdvanced)
	DDX_Text(pDX, IDC_adminPassword, m_adminPassword);
	DDV_MaxChars(pDX, m_adminPassword, 32);
	DDX_Text(pDX, IDC_bandwidthLimit, m_bandwidthLimit);
	DDV_MinMaxUInt(pDX, m_bandwidthLimit, 0, 999999999);
	DDX_Text(pDX, IDC_clientPort, m_clientPort);
	DDV_MinMaxUInt(pDX, m_clientPort, 1, 65535);
	DDX_Check(pDX, IDC_doAnnounce, m_doAnnounce);
	DDX_Text(pDX, IDC_fcpHosts, m_fcpHosts);
	DDX_Text(pDX, IDC_initialRequestHTL, m_initialRequestHTL);
	DDV_MinMaxUInt(pDX, m_initialRequestHTL, 0, 50);
	DDX_Text(pDX, IDC_inputBandwidthLimit, m_inputBandwidthLimit);
	DDV_MinMaxUInt(pDX, m_inputBandwidthLimit, 0, 999999999);
	DDX_Text(pDX, IDC_maxHopsToLive, m_maxHopsToLive);
	DDV_MinMaxUInt(pDX, m_maxHopsToLive, 10, 50);
	DDX_Text(pDX, IDC_maximumThreads, m_maximumThreads);
	DDV_MinMaxUInt(pDX, m_maximumThreads, 1, 1024);
	DDX_Text(pDX, IDC_outputBandwidthLimit, m_outputBandwidthLimit);
	DDV_MinMaxUInt(pDX, m_outputBandwidthLimit, 0, 999999999);
	DDX_Text(pDX, IDC_SEEDFILE, m_seedFile);
	DDV_MaxChars(pDX, m_seedFile, 255);
	DDX_Text(pDX, IDC_maxNodeConnections, m_maxNodeConnections);
	DDX_Control(pDX, IDC_slider_CPUPriority, m_ctrl_Slider_CPUPriority);
	DDX_Text(pDX, IDC_Static_CPUPriority, m_str_Static_CPUPriority);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropAdvanced, CPropertyPage)
	//{{AFX_MSG_MAP(CPropAdvanced)
	ON_NOTIFY(UDN_DELTAPOS, IDC_initialRequestHTL_spin, OnInitialRequestHTLspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_inputBandwidthLimit_spin, OnInputBandwidthLimitspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_maxHopsToLive_spin, OnMaxHopsToLivespin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_maximumThreads_spin, OnMaximumThreadsspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_outputBandwidthLimit_spin, OnOutputBandwidthLimitspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_bandwidthLimit_spin, OnBandwidthLimitspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_maxNodeConnections_spin, OnchangedmaxNodeConnectionsspin)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropAdvanced message handlers

void CPropAdvanced::OnBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	UINT BandwidthLimitDiv1024 = m_bandwidthLimit/1024;
	CUpdateSpin<UINT> cus(BandwidthLimitDiv1024, 0, (0xffffffff)/1024);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		m_bandwidthLimit = BandwidthLimitDiv1024 * 1024;
		if (m_bandwidthLimit > 0)
			m_inputBandwidthLimit = m_outputBandwidthLimit = 0;
		UpdateData(FALSE);
	}
	UpdateData(FALSE);
	*pResult = 0;
}

void CPropAdvanced::OnOutputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	UINT outputBandwidthLimitDiv1024 = m_outputBandwidthLimit/1024;
	CUpdateSpin<UINT> cus(outputBandwidthLimitDiv1024, 0, (0xffffffff)/1024);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		m_outputBandwidthLimit = outputBandwidthLimitDiv1024 * 1024;
		UpdateData(FALSE);
	}
	UpdateData(FALSE);
	*pResult = 0;
}

void CPropAdvanced::OnInputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	UINT inputBandwidthLimitDiv1024 = m_inputBandwidthLimit/1024;
	CUpdateSpin<UINT> cus(inputBandwidthLimitDiv1024, 0, (0xffffffff)/1024);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		m_inputBandwidthLimit = inputBandwidthLimitDiv1024 * 1024;
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropAdvanced::OnInitialRequestHTLspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_initialRequestHTL, 1, 25);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		if (m_initialRequestHTL > m_maxHopsToLive)
		{
			m_maxHopsToLive = m_initialRequestHTL;
		}
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropAdvanced::OnMaxHopsToLivespin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_maxHopsToLive, 1, 50);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		if (m_maxHopsToLive < m_initialRequestHTL)
		{
			m_initialRequestHTL = m_maxHopsToLive;
		}
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropAdvanced::OnMaximumThreadsspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_maximumThreads, 1, 1024);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		UpdateData(FALSE);
	}
	*pResult = 0;
}

void CPropAdvanced::OnchangedmaxNodeConnectionsspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	CUpdateSpin<UINT> cus(m_maxNodeConnections, 1, 0xffffffff);
	if (cus.Update(pNMUpDown->iDelta) )
	{
		UpdateData(FALSE);
	}
	*pResult = 0;
}



