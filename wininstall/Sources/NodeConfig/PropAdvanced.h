#if !defined(AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_)
#define AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// PropAdvanced.h : header file
//
//cvs


/////////////////////////////////////////////////////////////////////////////
// CPropAdvanced dialog

class CPropAdvanced : public CPropertyPage
{
	DECLARE_DYNCREATE(CPropAdvanced)

// Construction
public:
	CPropAdvanced();
	~CPropAdvanced();

// Dialog Data
	//{{AFX_DATA(CPropAdvanced)
	enum { IDD = IDD_PP_ADVANCED };
	CString	m_adminPassword;
	UINT	m_bandwidthLimit;
	UINT	m_clientPort;
	BOOL	m_doAnnounce;
	CString	m_fcpHosts;
	UINT	m_initialRequestHTL;
	UINT	m_inputBandwidthLimit;
	UINT	m_maxHopsToLive;
	UINT	m_maximumThreads;
	UINT	m_outputBandwidthLimit;
	CString	m_seedNodes;
	BOOL	m_nodestatusservlet;
	UINT	m_nodestatusport;
	CString	m_nodestatusclass;
	//}}AFX_DATA


// Overrides
	// ClassWizard generate virtual function overrides
	//{{AFX_VIRTUAL(CPropAdvanced)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CPropAdvanced)
	afx_msg void OnInitialRequestHTLspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnInputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnMaxHopsToLivespin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnMaximumThreadsspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnOutputBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnBandwidthLimitspin(NMHDR* pNMHDR, LRESULT* pResult);
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()

};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_PROPADVANCED_H__3E5F124D_8506_466A_BD9E_E54AED139FBE__INCLUDED_)
