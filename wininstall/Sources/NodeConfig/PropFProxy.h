#if !defined(AFX_PROPFPROXY_H__FA700397_2E69_43E4_BA22_738B913CD06A__INCLUDED_)
#define AFX_PROPFPROXY_H__FA700397_2E69_43E4_BA22_738B913CD06A__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// PropFProxy.h : header file
//
//cvs


/////////////////////////////////////////////////////////////////////////////
// CPropFProxy dialog

class CPropFProxy : public CPropertyPage
{
	DECLARE_DYNCREATE(CPropFProxy)

// Construction
public:
	CPropFProxy();
	~CPropFProxy();

// Dialog Data
	//{{AFX_DATA(CPropFProxy)
	enum { IDD = IDD_PP_FPROXY };
	CString	m_fproxyclass;
	UINT	m_fproxyport;
	UINT	m_fproxyrequesthtl;
	UINT	m_fproxyinserthtl;
	BOOL	m_bfproxyfilter;
	BOOL	m_bfproxyservice;
	CString	m_strfproxyallowedmime;
	UINT	m_fproxy_splitinchtl;
	UINT	m_fproxy_splitretries;
	UINT	m_fproxy_splitthreads;
	BOOL	m_fproxy_pollDroppedConnection;
	//}}AFX_DATA


// Overrides
	// ClassWizard generate virtual function overrides
	//{{AFX_VIRTUAL(CPropFProxy)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CPropFProxy)
		// NOTE: the ClassWizard will add member functions here
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()

};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_PROPFPROXY_H__FA700397_2E69_43E4_BA22_738B913CD06A__INCLUDED_)
