#if !defined(AFX_PROPDIAGNOSTICS_H__40632700_1499_11D6_AE5B_4854E82A0E4E__INCLUDED_)
#define AFX_PROPDIAGNOSTICS_H__40632700_1499_11D6_AE5B_4854E82A0E4E__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// PropDiagnostics.h : header file
//

/////////////////////////////////////////////////////////////////////////////
// CPropDiagnostics dialog

class CPropDiagnostics : public CPropertyPage
{
	DECLARE_DYNCREATE(CPropDiagnostics)

// Construction
public:
	CPropDiagnostics();
	~CPropDiagnostics();

// Dialog Data
	//{{AFX_DATA(CPropDiagnostics)
	enum { IDD = IDD_PP_DIAGNOSTICS };
	BOOL	m_nodestatusservlet;
	CString	m_nodestatusclass;
	UINT	m_nodestatusport;
	CString	m_logFile;
	CString	m_logFormat;
	CString	m_logLevel;
	CString	m_diagnosticsPath;
	BOOL	m_doDiagnostics;
	//}}AFX_DATA


// Overrides
	// ClassWizard generate virtual function overrides
	//{{AFX_VIRTUAL(CPropDiagnostics)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CPropDiagnostics)
		// NOTE: the ClassWizard will add member functions here
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()

};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_PROPDIAGNOSTICS_H__40632700_1499_11D6_AE5B_4854E82A0E4E__INCLUDED_)
