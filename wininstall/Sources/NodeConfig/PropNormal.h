#if !defined(AFX_PROPNORMAL_H__3B664B71_8D33_4528_B407_2E0AC79986F0__INCLUDED_)
#define AFX_PROPNORMAL_H__3B664B71_8D33_4528_B407_2E0AC79986F0__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// PropNormal.h : header file
//
//cvs


/////////////////////////////////////////////////////////////////////////////
// CPropNormal dialog

class CPropNormal : public CPropertyPage
{
	DECLARE_DYNCREATE(CPropNormal)

// Construction
public:
	CPropNormal();
	~CPropNormal();
	void	showNodeAddrFields(BOOL showing);
	BOOL	warnPerm;

// Dialog Data
	//{{AFX_DATA(CPropNormal)
	enum { IDD = IDD_PP_NORMAL };
	CButton	m_importNewNodeRef;
	UINT	m_storeCacheSize;
	CString	m_storePath;
	BOOL	m_useDefaultNodeRefs;
	BOOL	m_transient;
	CString	m_ipAddress;
	UINT	m_listenPort;
	BOOL	m_notTransient;
	//}}AFX_DATA


// Overrides
	// ClassWizard generate virtual function overrides
	//{{AFX_VIRTUAL(CPropNormal)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CPropNormal)
	afx_msg void OnStoreCacheSizespin(NMHDR* pNMHDR, LRESULT* pResult);
	afx_msg void OnnotTransientClick();
	afx_msg void OntransientClick();
	afx_msg int OnCreate(LPCREATESTRUCT lpCreateStruct);
	afx_msg void OnShowWindow(BOOL bShow, UINT nStatus);
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()

};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_PROPNORMAL_H__3B664B71_8D33_4528_B407_2E0AC79986F0__INCLUDED_)
