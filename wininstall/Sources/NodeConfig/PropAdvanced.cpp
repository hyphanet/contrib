// PropAdvanced.cpp : implementation file
//
//cvs


#include "stdafx.h"
#include "NodeConfig.h"
#include "PropAdvanced.h"

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
	//m_adminPassword = _T("");
	//m_bandwidthLimit = 0;
	//m_clientPort = 0;
	//m_doAnnounce = FALSE;
	//m_fcpHosts = _T("");
	//m_initialRequestHTL = 0;
	//m_inputBandwidthLimit = 0;
	//m_maxHopsToLive = 0;
	//m_maximumConnectionThreads = 0;
	//m_outputBandwidthLimit = 0;
	//m_seedNodes = _T("");
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
	DDX_Text(pDX, IDC_maximumConnectionThreads, m_maximumConnectionThreads);
	DDV_MinMaxUInt(pDX, m_maximumConnectionThreads, 1, 1024);
	DDX_Text(pDX, IDC_outputBandwidthLimit, m_outputBandwidthLimit);
	DDV_MinMaxUInt(pDX, m_outputBandwidthLimit, 0, 999999999);
	DDX_Text(pDX, IDC_seedNodes, m_seedNodes);
	DDV_MaxChars(pDX, m_seedNodes, 255);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CPropAdvanced, CPropertyPage)
	//{{AFX_MSG_MAP(CPropAdvanced)
	ON_NOTIFY(UDN_DELTAPOS, IDC_initialRequestHTL_spin, OnInitialRequestHTLspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_inputBandwidthLimit_spin, OnInputBandwidthLimitspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_maxHopsToLive_spin, OnMaxHopsToLivespin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_maximumConnectionThreads_spin, OnMaximumConnectionThreadsspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_outputBandwidthLimit_spin, OnOutputBandwidthLimitspin)
	ON_NOTIFY(UDN_DELTAPOS, IDC_bandwidthLimit_spin, OnBandwidthLimitspin)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPropAdvanced message handlers

void CPropAdvanced::OnBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		if (m_bandwidthLimit++ == 0)
			m_inputBandwidthLimit = m_outputBandwidthLimit = 0;
		UpdateData(FALSE);
	}
	else
	{
		if (m_bandwidthLimit > 0)
		{
			m_bandwidthLimit--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropAdvanced::OnOutputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		m_outputBandwidthLimit++;
		m_bandwidthLimit = 0;
		UpdateData(FALSE);
	}
	else
	{
		if (m_outputBandwidthLimit > 0)
		{
			m_outputBandwidthLimit--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropAdvanced::OnInputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		m_inputBandwidthLimit++;
		m_bandwidthLimit = 0;
		UpdateData(FALSE);
	}
	else
	{
		if (m_inputBandwidthLimit > 0)
		{
			m_inputBandwidthLimit--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropAdvanced::OnInitialRequestHTLspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		if (m_initialRequestHTL < 25)
		{
			if (++m_initialRequestHTL > m_maxHopsToLive)
				m_maxHopsToLive = m_initialRequestHTL;
			UpdateData(FALSE);
		}
	}
	else
	{
		if (m_initialRequestHTL > 0)
		{
			m_initialRequestHTL--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropAdvanced::OnMaxHopsToLivespin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		if (m_maxHopsToLive < 50)
		{
			m_maxHopsToLive++;
			UpdateData(FALSE);
		}
	}
	else
	{
		if (m_maxHopsToLive > 0)
		{
			if (--m_maxHopsToLive < m_initialRequestHTL)
				m_initialRequestHTL = m_maxHopsToLive;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}

void CPropAdvanced::OnMaximumConnectionThreadsspin(NMHDR* pNMHDR, LRESULT* pResult) 
{
	NM_UPDOWN* pNMUpDown = (NM_UPDOWN*)pNMHDR;
	UpdateData(TRUE);
	if (pNMUpDown->iDelta < 0)
	{
		if (m_maximumConnectionThreads < 1024)
		{
			m_maximumConnectionThreads++;
			UpdateData(FALSE);
		}
	}
	else
	{
		if (m_maximumConnectionThreads > 0)
		{
			m_maximumConnectionThreads--;
			UpdateData(FALSE);
		}
	}
	*pResult = 0;
}
