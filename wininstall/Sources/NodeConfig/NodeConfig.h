// NodeConfig.h : main header file for the NODECONFIG application
//

#if !defined(AFX_NODECONFIG_H__90BB8677_5BCB_4520_98BD_729C2A4705A0__INCLUDED_)
#define AFX_NODECONFIG_H__90BB8677_5BCB_4520_98BD_729C2A4705A0__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#ifndef __AFXWIN_H__
	#error include 'stdafx.h' before including this file for PCH
#endif

#include "resource.h"		// main symbols

/////////////////////////////////////////////////////////////////////////////
// CNodeConfigApp:
// See NodeConfig.cpp for the implementation of this class
//

class CNodeConfigApp : public CWinApp
{
public:
	CNodeConfigApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CNodeConfigApp)
	public:
	virtual BOOL InitInstance();
	virtual int ExitInstance();
	//}}AFX_VIRTUAL

// Implementation

	//{{AFX_MSG(CNodeConfigApp)
		// NOTE - the ClassWizard will add and remove member functions here.
		//    DO NOT EDIT what you see in these blocks of generated code !
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
private:
	BOOL clickedOK; // used to decide on the return value of the app
};


/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_NODECONFIG_H__90BB8677_5BCB_4520_98BD_729C2A4705A0__INCLUDED_)
