#if !defined(AFX_UNKNOWNDLG_H__C3F55C80_2319_11D6_BD2D_00A0CCE04BC8__INCLUDED_)
#define AFX_UNKNOWNDLG_H__C3F55C80_2319_11D6_BD2D_00A0CCE04BC8__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#include "stdafx.h"
#include "resource.h"

// UnknownDlg.h : header file
//

/////////////////////////////////////////////////////////////////////////////
// CUnknownDlg dialog

class CUnknownDlg : public CDialog
{
// Construction
public:
	CUnknownDlg(CWnd* pParent = NULL);   // standard constructor

// Dialog Data
	//{{AFX_DATA(CUnknownDlg)
	enum { IDD = IDD_EDITUNKNOWN };
	CButton	m_addbutton;
	CListBox	m_listbox;
	CString	m_addthis;
	CString	m_selected;
	//}}AFX_DATA


// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CUnknownDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CUnknownDlg)
	virtual BOOL OnInitDialog();
	afx_msg void OnAddit();
	afx_msg void OnDelete();
	virtual void OnOK();
	afx_msg void OnCancel();
	afx_msg void OnSelchangeListofunknown();
	afx_msg void OnMovedown();
	afx_msg void OnMoveup();
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_UNKNOWNDLG_H__C3F55C80_2319_11D6_BD2D_00A0CCE04BC8__INCLUDED_)
