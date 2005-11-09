// GetSeedDlg.h : header file
//

#if !defined(AFX_GetSeedDLG_H__34352F26_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_)
#define AFX_GetSeedDLG_H__34352F26_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

//#define DEFSEEDURL "http://freenetproject.org/snapshots/seednodes.ref"
// Bob H : GetSeed is rarely used now, so use Emu mirror with fallback to SourceForge since Emu is faster / reliable
#define DEFSEEDURL "http://emu.freenetproject.org/downloads/seednodes/seednodes.ref"
#define DEFSEEDFILE "seednodes.ref"

/////////////////////////////////////////////////////////////////////////////
// CGetSeedDlg dialog

class CGetSeedDlg : public CDialog
{
// Construction
public:
	CGetSeedDlg(BOOL silent = FALSE, CWnd* pParent = NULL);	// standard constructor

// Dialog Data
private:
	//{{AFX_DATA(CGetSeedDlg)
	enum { IDD = IDD_GETSEEDDLG };
	CProgressCtrl	m_dloadprogressbar;
	CString	m_seedURL;
	CString	m_seedfile;
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CGetSeedDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:

	// Generated message map functions
	//{{AFX_MSG(CGetSeedDlg)
	virtual BOOL OnInitDialog();
	afx_msg void OnGetseed();
	afx_msg void OnGetlocalseed();
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
private:
	BOOL m_silent;	// should we simply retrieve the stuff without user interaction?
};


//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_GetSeedDLG_H__34352F26_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_)

