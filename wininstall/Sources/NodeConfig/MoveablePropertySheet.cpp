// MoveablePropertySheet1.cpp : implementation file
//

#include "stdafx.h"
#include "MoveablePropertySheet.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertySheet

IMPLEMENT_DYNAMIC(CMoveablePropertySheet, CPropertySheet)

CMoveablePropertySheet::CMoveablePropertySheet(UINT nIDCaption, CWnd* pParentWnd, UINT iSelectPage)
:
m_bDragging(false),
CPropertySheet(nIDCaption, pParentWnd, iSelectPage)
{
	//
}

CMoveablePropertySheet::CMoveablePropertySheet(LPCTSTR pszCaption, CWnd* pParentWnd, UINT iSelectPage)
:
m_bDragging(false),
CPropertySheet(pszCaption, pParentWnd, iSelectPage)
{
	//
}

CMoveablePropertySheet::~CMoveablePropertySheet()
{
	// nothing to do here
}


BEGIN_MESSAGE_MAP(CMoveablePropertySheet, CPropertySheet)
	//{{AFX_MSG_MAP(CMoveablePropertySheet)
	ON_WM_RBUTTONDOWN()
	ON_WM_RBUTTONUP()
	ON_WM_MOUSEMOVE()
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertySheet message handlers

void CMoveablePropertySheet::OnRButtonDown(UINT nFlags, CPoint point) 
{
	BeginDrag(point);
	CPropertySheet::OnRButtonDown(nFlags, point);
}

void CMoveablePropertySheet::OnRButtonUp(UINT nFlags, CPoint point) 
{
	EndDrag();
	CPropertySheet::OnRButtonUp(nFlags, point);
}

void CMoveablePropertySheet::OnMouseMove(UINT nFlags, CPoint point) 
{
	if ( (nFlags & MK_RBUTTON) || (nFlags & MK_LBUTTON) )
	{
		if (!m_bDragging)
		{
			BeginDrag(point);
		}
		RECT windowrect;
		GetWindowRect(&windowrect);
		SetWindowPos(NULL,windowrect.left+point.x-m_Point.x, windowrect.top+point.y-m_Point.y,0,0,SWP_NOOWNERZORDER | SWP_NOSIZE | SWP_NOZORDER);
	}
	else
	{
		if (m_bDragging)
		{
			EndDrag();
		}
	}
	
	CPropertySheet::OnMouseMove(nFlags, point);
}


void CMoveablePropertySheet::BeginDrag(CPoint & point)
{
	m_bDragging = true;
	RECT windowrect;
	GetWindowRect(&windowrect);
	m_Point = point;
	SetCapture();
}

void CMoveablePropertySheet::EndDrag(void)
{
	m_bDragging = false;
	ReleaseCapture();
}
