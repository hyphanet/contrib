#if !defined(AFX_DLGWARNPERM_H__23661592_1ED1_4590_B388_DA9E3972541C__INCLUDED_)
#define AFX_DLGWARNPERM_H__23661592_1ED1_4590_B388_DA9E3972541C__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// DlgWarnPerm.h : header file
//
//cvs


/////////////////////////////////////////////////////////////////////////////
// CDlgWarnPerm dialog

class CDlgWarnPerm : public CDialog
{
// Construction
public:
	CDlgWarnPerm(CWnd* pParent = NULL);   // standard constructor

// Dialog Data
	//{{AFX_DATA(CDlgWarnPerm)
	enum { IDD = IDD_DLG_PERM_WARN };
	BOOL	m_dontWarnPerm;
	//}}AFX_DATA


// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CDlgWarnPerm)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:

	// Generated message map functions
	//{{AFX_MSG(CDlgWarnPerm)
		// NOTE: the ClassWizard will add member functions here
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_DLGWARNPERM_H__23661592_1ED1_4590_B388_DA9E3972541C__INCLUDED_)
