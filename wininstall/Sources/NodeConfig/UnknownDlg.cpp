// UnknownDlg.cpp : implementation file
//

#include "stdafx.h"
#include "nodeconfig.h"
#include "UnknownDlg.h"
#include "ConfigFile.h"


#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

extern CString UnknownParms;

/////////////////////////////////////////////////////////////////////////////
// CUnknownDlg dialog


CUnknownDlg::CUnknownDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CUnknownDlg::IDD, pParent)
{
	//{{AFX_DATA_INIT(CUnknownDlg)
	m_addthis = _T("");
	//}}AFX_DATA_INIT
}


void CUnknownDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CUnknownDlg)
	DDX_Control(pDX, IDC_ADDIT, m_addbutton);
	DDX_Control(pDX, IDC_LISTOFUNKNOWN, m_listbox);
	DDX_Text(pDX, IDC_ADDTHIS, m_addthis);
	//}}AFX_DATA_MAP
}


BEGIN_MESSAGE_MAP(CUnknownDlg, CDialog)
	//{{AFX_MSG_MAP(CUnknownDlg)
	ON_BN_CLICKED(IDC_ADDIT, OnAddit)
	ON_BN_CLICKED(IDC_DELETE, OnDelete)
	ON_LBN_SELCHANGE(IDC_LISTOFUNKNOWN, OnSelchangeListofunknown)
	ON_BN_CLICKED(IDC_MOVEDOWN, OnMovedown)
	ON_BN_CLICKED(IDC_MOVEUP, OnMoveup)
	ON_BN_CLICKED(IDC_CANCEL, OnCancel)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CUnknownDlg message handlers

/* Unknown Settings Dialog
by Reuben Balik -- cybrguyrsb@yahoo.com
February 24, 2002 */

BOOL CUnknownDlg::OnInitDialog() 
{
	CDialog::OnInitDialog();
	//Loads in unknown settings
	CString unknowns;
	int pos;
	int cur=0;
	CString item;
	unknowns = m_unknowns;
	while(cur != -1)
	{
		pos = unknowns.Find("\n",cur);
		if(pos != -1)
		{
			item=unknowns.Mid(cur,pos-cur);
			m_listbox.AddString(item);
			cur = pos+1;
		}
		else
		{
			cur = -1;
		}
	}
	UpdateData(FALSE);

	if (m_listbox.GetCount()==0)
	{
		m_listbox.SetCurSel(-1);
		GetDlgItem(IDC_MOVEUP)->EnableWindow(FALSE);
		GetDlgItem(IDC_MOVEDOWN)->EnableWindow(FALSE);
		GetDlgItem(IDC_DELETE)->EnableWindow(FALSE);
	}
	else
	{
		m_listbox.SetCurSel(0);
	}

	return TRUE;  // return TRUE unless you set the focus to a control
	              // EXCEPTION: OCX Property Pages should return FALSE
}

void CUnknownDlg::OnAddit() 
{
	// Adds an item.
	UpdateData(TRUE);
	// Makes sure the setting has an = because CConfigFile
	// Only loads stuff in with an =
	if(m_addthis.Find("=") == -1)
	{
		MessageBox("There must be an = somewhere in the setting!", "Error!");
	}
	else
	{
		// see if there's already a string matching this one:
		CString key = m_addthis.Left(m_addthis.Find("=")-1);
		int nFirstIndex = m_listbox.FindString(-1,key);
		int nIndex = nFirstIndex;
		while ( nIndex != LB_ERR )
		{
			m_listbox.DeleteString(nIndex);
			nIndex = m_listbox.FindString(-1,key);
		}
		nIndex = m_listbox.InsertString(nFirstIndex,m_addthis);
		m_listbox.SetCurSel(nIndex); // select the item we just added
		GetDlgItem(IDC_MOVEUP)->EnableWindow(TRUE);
		GetDlgItem(IDC_MOVEDOWN)->EnableWindow(TRUE);
		GetDlgItem(IDC_DELETE)->EnableWindow(TRUE);
		m_addthis.Empty();
		UpdateData(FALSE);
	}
	
}

void CUnknownDlg::OnDelete() 
{
	// Deletes the selected item.
	UpdateData(TRUE);
	m_listbox.GetText(m_listbox.GetCurSel(), m_addthis);
	int nIndex = m_listbox.GetCurSel();
	m_listbox.DeleteString(nIndex);
	if (m_listbox.GetCount()==0)
	{
		m_listbox.SetCurSel(-1);
		GetDlgItem(IDC_MOVEUP)->EnableWindow(FALSE);
		GetDlgItem(IDC_MOVEDOWN)->EnableWindow(FALSE);
		GetDlgItem(IDC_DELETE)->EnableWindow(FALSE);
	}
	else
	{
		if (nIndex < m_listbox.GetCount())
		{
			m_listbox.SetCurSel(nIndex);
		}
		else
		{
			m_listbox.SetCurSel(m_listbox.GetCount());
		}
	}
	UpdateData(FALSE);
}

void CUnknownDlg::OnOK() 
{
	// The user clicked ok so we will copy the list box
	// contents into m_unknowns
	CString temp;
	m_unknowns.Empty();
	for(int num = 0; num < m_listbox.GetCount(); num++)
	{
		m_listbox.GetText(num, temp);
		m_unknowns+=temp;
		m_unknowns+="\n";
	}
	CDialog::OnOK();
}

void CUnknownDlg::OnCancel() 
{
	// m_unknowns is unchanged

	CDialog::OnCancel();
}

void CUnknownDlg::OnSelchangeListofunknown() 
{
	// Puts the currently selected item into the selected box.
	UpdateData(TRUE);
	m_listbox.GetText(m_listbox.GetCurSel(), m_addthis);
	GetDlgItem(IDC_MOVEUP)->EnableWindow(TRUE);
	GetDlgItem(IDC_MOVEDOWN)->EnableWindow(TRUE);
	GetDlgItem(IDC_DELETE)->EnableWindow(TRUE);
	UpdateData(FALSE);
}

void CUnknownDlg::OnMovedown() 
{
	// Moves up the selected item in the list box
	UpdateData(TRUE);
	CString temp;
	int sel = m_listbox.GetCurSel();	
	if(m_listbox.GetCount() > 1 && sel != m_listbox.GetCount() - 1)
	{
		m_listbox.GetText(sel, temp);
		m_listbox.DeleteString(sel);
		m_listbox.InsertString(sel+1, temp);
		m_listbox.SetCurSel(sel+1);
	}
	UpdateData(FALSE);
	
}

void CUnknownDlg::OnMoveup() 
{
	// Moves down the selected item in the list box
	UpdateData(TRUE);
	CString temp;
	int sel = m_listbox.GetCurSel();
	if(m_listbox.GetCount() > 1 && sel != 0)
	{
		m_listbox.GetText(sel-1, temp);
		m_listbox.InsertString(sel+1, temp);
		m_listbox.DeleteString(sel-1);
		m_listbox.SetCurSel(sel-1);
	}
	UpdateData(FALSE);
	
}
