// GetSeed.h : main header file for the GetSeed application
//

#if !defined(AFX_GetSeed_H__34352F24_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_)
#define AFX_GetSeed_H__34352F24_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#ifndef __AFXWIN_H__
	#error include 'stdafx.h' before including this file for PCH
#endif

#include "resource.h"		// main symbols

#define HELPFILE "docs\\freenet.hlp"
/////////////////////////////////////////////////////////////////////////////
// CGetSeedApp:
// See GetSeed.cpp for the implementation of this class
//

class CGetSeedApp : public CWinApp
{
public:
	CGetSeedApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CGetSeedApp)
	public:
	virtual BOOL InitInstance();
	//}}AFX_VIRTUAL

// Implementation

	//{{AFX_MSG(CGetSeedApp)
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_GetSeed_H__34352F24_A86D_11D5_AE5A_4854E82A0E4E__INCLUDED_)
