// NodeConfigDlg.h : header file
//

#if !defined(AFX_NODECONFIGDLG_H__7BB2C228_E71A_47C4_B099_DD8711E1EC84__INCLUDED_)
#define AFX_NODECONFIGDLG_H__7BB2C228_E71A_47C4_B099_DD8711E1EC84__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigDlg dialog

class CNodeConfigDlg : public CDialog
{
// Construction
public:
	CNodeConfigDlg(CWnd* pParent = NULL);	// standard constructor

// Dialog Data
	//{{AFX_DATA(CNodeConfigDlg)
	enum { IDD = IDD_NODECONFIG_DIALOG };
		// NOTE: the ClassWizard will add data members here
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CNodeConfigDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	//{{AFX_MSG(CNodeConfigDlg)
	virtual BOOL OnInitDialog();
	afx_msg void OnSysCommand(UINT nID, LPARAM lParam);
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_NODECONFIGDLG_H__7BB2C228_E71A_47C4_B099_DD8711E1EC84__INCLUDED_)
