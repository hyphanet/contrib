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
	m_n_Slider_CPUPriority = 0;
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
	DDX_Slider(pDX, IDC_slider_CPUPriority, m_n_Slider_CPUPriority);
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
	ON_NOTIFY(NM_CUSTOMDRAW, IDC_slider_CPUPriority, OnCustomdrawsliderCPUPriority)
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

void CPropAdvanced::SetCPUPrioritySlider(DWORD dwPriority, DWORD dwPriorityClass)
{
	DWORD mostLikelyDiff = 0xffffffff;
	int mostLikelyIndex = 0;

	const DWORD dwPriorityIndex = GetPriorityIndex(dwPriority, dwPriorityClass);

	for (int i=0; i<NUMPRIORITIES; i++)
	{
		DWORD dwThisPriorityIndex = GetPriorityIndex(m_dwPriority[i],m_dwPriorityClass[i]);
		if (dwThisPriorityIndex == dwPriorityIndex)
		{
			mostLikelyIndex = i;
			break;
		}
		else if (dwThisPriorityIndex > dwPriorityIndex)
		{
			if ( (dwThisPriorityIndex - dwPriorityIndex) < mostLikelyDiff)
			{
				mostLikelyDiff = dwThisPriorityIndex - dwPriorityIndex;
				mostLikelyIndex = i;
			}
		}
		else
		{
			if ( (dwPriorityIndex - dwThisPriorityIndex) < mostLikelyDiff)
			{
				mostLikelyDiff = dwPriorityIndex - dwThisPriorityIndex;
				mostLikelyIndex = i;
			}
		}
	}

	m_n_Slider_CPUPriority = mostLikelyIndex;
	m_str_Static_CPUPriority = m_szPriorityDescription[mostLikelyIndex];
}

void CPropAdvanced::GetCPUPrioritySlider(DWORD &dwPriority, DWORD &dwPriorityClass) const
{
	dwPriority = m_dwPriority[m_n_Slider_CPUPriority];
	dwPriorityClass = m_dwPriorityClass[m_n_Slider_CPUPriority];
}

/*static*/ const DWORD CPropAdvanced::GetPriorityIndex(DWORD dwPriority, DWORD dwPriorityClass)
{
	DWORD dwPriorityIndex;

	if (dwPriorityClass == REALTIME_PRIORITY_CLASS)
	{
		switch (dwPriority)
		{
		case THREAD_PRIORITY_IDLE:
			return 16;
		case THREAD_PRIORITY_LOWEST:
			return 22;
		case THREAD_PRIORITY_BELOW_NORMAL:
			return 23;
		case THREAD_PRIORITY_NORMAL:
		default:
			return 24;
		case THREAD_PRIORITY_ABOVE_NORMAL:
			return 25;
		case THREAD_PRIORITY_HIGHEST:
			return 26;
		case THREAD_PRIORITY_TIME_CRITICAL:
			return 31;
		}
	}

	if (dwPriority==THREAD_PRIORITY_IDLE)
	{
		return 1;
	}

	switch (dwPriorityClass)
	{
	case IDLE_PRIORITY_CLASS:
		dwPriorityIndex = 4;
		break;
	case BELOW_NORMAL_PRIORITY_CLASS:
		dwPriorityIndex = 6;
		break;
	case NORMAL_PRIORITY_CLASS:
	default:
		dwPriorityIndex = 7;
		break;
	case ABOVE_NORMAL_PRIORITY_CLASS:
		dwPriorityIndex = 10;
		break;
	case HIGH_PRIORITY_CLASS:
		dwPriorityIndex = 13;
		break;
	}

	switch (dwPriority)
	{
	case THREAD_PRIORITY_LOWEST:
		dwPriorityIndex -= 2;
		break;
	case THREAD_PRIORITY_BELOW_NORMAL:
		dwPriorityIndex -= 1;
		break;
	case THREAD_PRIORITY_NORMAL:
	default:
		break;
	case THREAD_PRIORITY_ABOVE_NORMAL:
		dwPriorityIndex += 1;
		break;
	case THREAD_PRIORITY_HIGHEST:
		dwPriorityIndex += 2;
		break;
	case THREAD_PRIORITY_TIME_CRITICAL:
		dwPriorityIndex += 3;
		break;
	}

	return dwPriorityIndex;
}



/*static*/ const DWORD CPropAdvanced::m_dwPriority[NUMPRIORITIES] = 
{THREAD_PRIORITY_NORMAL,
 THREAD_PRIORITY_BELOW_NORMAL,
 THREAD_PRIORITY_BELOW_NORMAL,
 THREAD_PRIORITY_NORMAL,
 THREAD_PRIORITY_ABOVE_NORMAL,
 THREAD_PRIORITY_NORMAL,
 THREAD_PRIORITY_ABOVE_NORMAL};

/*static*/ const DWORD CPropAdvanced::m_dwPriorityClass[NUMPRIORITIES] = 
{IDLE_PRIORITY_CLASS,
 BELOW_NORMAL_PRIORITY_CLASS,
 NORMAL_PRIORITY_CLASS,
 NORMAL_PRIORITY_CLASS,
 NORMAL_PRIORITY_CLASS,
 ABOVE_NORMAL_PRIORITY_CLASS,
 ABOVE_NORMAL_PRIORITY_CLASS};

/*static*/ const char * const CPropAdvanced::m_szPriorityDescription[NUMPRIORITIES] = 
{ "<<< IDLE",
  "<< LOW",
  "< NORMAL",
  "NORMAL",
  "> NORMAL",
  ">> HIGH",
  ">>> V.HIGH"};

void CPropAdvanced::OnCustomdrawsliderCPUPriority(NMHDR* pNMHDR, LRESULT* pResult) 
{
	UpdateData(TRUE);	
	m_str_Static_CPUPriority = m_szPriorityDescription[m_n_Slider_CPUPriority];
	UpdateData(FALSE);
	
	*pResult = 0;
}


BOOL CPropAdvanced::OnSetActive() 
{
	// need this to ensure the slider control gets set up properly
	// (There is no such thing as an OnInitialUpdate() override for propertypages)
	m_ctrl_Slider_CPUPriority.SetTic(1);
	m_ctrl_Slider_CPUPriority.SetTicFreq(1);
	m_ctrl_Slider_CPUPriority.SetRange(0,NUMPRIORITIES-1,TRUE);

	return CPropertyPage::OnSetActive();
}
