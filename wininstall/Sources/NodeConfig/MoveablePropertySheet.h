#if !defined(AFX_MOVEABLEPROPERTYSHEET1_H__FD47A452_9D71_4EC5_9F32_81E86460387B__INCLUDED_)
#define AFX_MOVEABLEPROPERTYSHEET1_H__FD47A452_9D71_4EC5_9F32_81E86460387B__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// MoveablePropertySheet1.h : header file
//

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertySheet

class CMoveablePropertySheet : public CPropertySheet
{
	DECLARE_DYNAMIC(CMoveablePropertySheet)

// Construction
public:
	CMoveablePropertySheet(UINT nIDCaption, CWnd* pParentWnd = NULL, UINT iSelectPage = 0);
	CMoveablePropertySheet(LPCTSTR pszCaption, CWnd* pParentWnd = NULL, UINT iSelectPage = 0);

// Attributes
public:
protected:
	bool m_bDragging;
	CPoint m_Point;

// Operations
public:
private:
	void BeginDrag(CPoint & point);
	void EndDrag(void);

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CMoveablePropertySheet)
	//}}AFX_VIRTUAL

// Implementation
public:
	virtual ~CMoveablePropertySheet();

	// Generated message map functions
protected:
	//{{AFX_MSG(CMoveablePropertySheet)
	afx_msg void OnRButtonDown(UINT nFlags, CPoint point);
	afx_msg void OnRButtonUp(UINT nFlags, CPoint point);
	afx_msg void OnMouseMove(UINT nFlags, CPoint point);
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_MOVEABLEPROPERTYSHEET1_H__FD47A452_9D71_4EC5_9F32_81E86460387B__INCLUDED_)
