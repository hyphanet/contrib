#if !defined(AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_)
#define AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#include "stdafx.h"
#include "resource.h"
#include "MoveablePropertyPage.h"


/////////////////////////////////////////////////////////////////////////////
// CPropAdvanced dialog
#define NUMPRIORITIES (7)

class CPropAdvanced : public CMoveablePropertyPage
{
	DECLARE_DYNCREATE(CPropAdvanced)

// Construction
public:
	CPropAdvanced();
	~CPropAdvanced();

	void SetCPUPrioritySlider(DWORD dwPriority, DWORD dwPriorityClass);
	void GetCPUPrioritySlider(DWORD &dwPriority, DWORD &dwPriorityClass) const;

// Dialog Data
	//{{AFX_DATA(CPropAdvanced)
	enum { IDD = IDD_PP_ADVANCED };
	CString m_adminPassword;
	UINT	m_bandwidthLimit;
	UINT	m_clientPort;
	BOOL	m_doAnnounce;
	CString	m_fcpHosts;
	UINT	m_initialRequestHTL;
	UINT	m_inputBandwidthLimit;
	UINT	m_maxHopsToLive;
	UINT	m_maximumThreads;
	UINT	m_outputBandwidthLimit;
	CString	m_seedFile;
	UINT	m_maxNodeConnections;
	CSliderCtrl	m_ctrl_Slider_CPUPriority;
	CString	m_str_Static_CPUPriority;
	int		m_n_Slider_CPUPriority;
	//}}AFX_DATA


// Overrides
	// ClassWizard generate virtual function overrides
	//{{AFX_VIRTUAL(CPropAdvanced)
	public:
	virtual BOOL OnSetActive();
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	static const DWORD m_dwPriority[NUMPRIORITIES];
	static const DWORD m_dwPriorityClass[NUMPRIORITIES];
	static const char * const m_szPriorityDescription[NUMPRIORITIES];

	static const DWORD CPropAdvanced::GetPriorityIndex(DWORD dwPriority, DWORD dwPriorityClass);

	// Generated message map functions
	//{{AFX_MSG(CPropAdvanced)
	afx_msg void OnInitialRequestHTLspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnInputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnMaxHopsToLivespin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnMaximumThreadsspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnOutputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnchangedmaxNodeConnectionsspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnCustomdrawsliderCPUPriority(NMHDR* pNMHDR, LRESULT* pResult);
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()


};


//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_)
