// MoveablePropertyPage.cpp : implementation file
//

#include "stdafx.h"
#include "nodeconfig.h"
#include "MoveablePropertyPage.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertyPage property page

IMPLEMENT_DYNCREATE(CMoveablePropertyPage, CPropertyPage)

CMoveablePropertyPage::CMoveablePropertyPage()
:
m_bDragging(false),
CPropertyPage()
{
	//
}

CMoveablePropertyPage::CMoveablePropertyPage( UINT nIDTemplate, UINT nIDCaption )
:
m_bDragging(false),
CPropertyPage(nIDTemplate, nIDCaption)
{
	//
}

CMoveablePropertyPage::CMoveablePropertyPage( LPCTSTR lpszTemplateName, UINT nIDCaption )
:
m_bDragging(false),
CPropertyPage(lpszTemplateName, nIDCaption)
{
	//
}

CMoveablePropertyPage::~CMoveablePropertyPage()
{
	// nothing to do here
}

BEGIN_MESSAGE_MAP(CMoveablePropertyPage, CPropertyPage)
	//{{AFX_MSG_MAP(CMoveablePropertyPage)
	ON_WM_MOUSEMOVE()
	ON_WM_RBUTTONDOWN()
	ON_WM_RBUTTONUP()
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CMoveablePropertyPage message handlers

void CMoveablePropertyPage::OnMouseMove(UINT nFlags, CPoint point) 
{
	if ( (nFlags & MK_RBUTTON) || (nFlags & MK_LBUTTON) )
	{
		if (!m_bDragging)
		{
			BeginDrag(point);
		}
		CWnd * pdlg = GetParent();
		RECT windowrect;
		pdlg->GetWindowRect(&windowrect);
		pdlg->SetWindowPos(NULL,windowrect.left+point.x-m_Point.x, windowrect.top+point.y-m_Point.y,0,0,SWP_NOOWNERZORDER | SWP_NOSIZE | SWP_NOZORDER);
	}
	else
	{
		if (m_bDragging)
		{
			EndDrag();
		}
	}
	
	CPropertyPage::OnMouseMove(nFlags, point);
}


void CMoveablePropertyPage::BeginDrag(CPoint & point)
{
	m_bDragging = true;
	RECT windowrect;
	GetWindowRect(&windowrect);
	m_Point = point;
	SetCapture();
}

void CMoveablePropertyPage::EndDrag(void)
{
	m_bDragging = false;
	ReleaseCapture();
}

void CMoveablePropertyPage::OnRButtonDown(UINT nFlags, CPoint point) 
{
	BeginDrag(point);
	CPropertyPage::OnRButtonDown(nFlags, point);
}

void CMoveablePropertyPage::OnRButtonUp(UINT nFlags, CPoint point) 
{
	EndDrag();
	CPropertyPage::OnRButtonUp(nFlags, point);
}
