#if !defined(AFX_MOVEABLEPROPERTYPAGE_H__BDA01251_8184_4806_9246_39F6B8E0ACAA__INCLUDED_)
#define AFX_MOVEABLEPROPERTYPAGE_H__BDA01251_8184_4806_9246_39F6B8E0ACAA__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000
// MoveablePropertyPage.h : header file
//

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertyPage dialog

class CMoveablePropertyPage : public CPropertyPage
{
	DECLARE_DYNCREATE(CMoveablePropertyPage)

// Construction
public:
	CMoveablePropertyPage( );
	CMoveablePropertyPage( UINT nIDTemplate, UINT nIDCaption = 0 );
	CMoveablePropertyPage( LPCTSTR lpszTemplateName, UINT nIDCaption = 0 );
	~CMoveablePropertyPage();

protected:
	bool m_bDragging;
	CPoint m_Point;
private:
	void BeginDrag(CPoint & point);
	void EndDrag(void);

// Implementation
protected:
	// Generated message map functions
	//{{AFX_MSG(CMoveablePropertyPage)
	afx_msg void OnMouseMove(UINT nFlags, CPoint point);
	afx_msg void OnRButtonDown(UINT nFlags, CPoint point);
	afx_msg void OnRButtonUp(UINT nFlags, CPoint point);
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()

};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_MOVEABLEPROPERTYPAGE_H__BDA01251_8184_4806_9246_39F6B8E0ACAA__INCLUDED_)
