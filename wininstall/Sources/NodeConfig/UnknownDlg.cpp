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
	m_selected = _T("");
	//}}AFX_DATA_INIT
}


void CUnknownDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CUnknownDlg)
	DDX_Control(pDX, IDC_ADDIT, m_addbutton);
	DDX_Control(pDX, IDC_LISTOFUNKNOWN, m_listbox);
	DDX_Text(pDX, IDC_ADDTHIS, m_addthis);
	DDX_Text(pDX, IDC_SELECTED, m_selected);
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
	int pos, cur;	
	CString item;
	CConfigFile thisOne;
	unknowns = thisOne.GetUnknowns();
	while(!unknowns.IsEmpty())
	{
		pos = unknowns.Find('\n');
		item.Empty();
		if(pos != -1)
		{
			for(cur = 0; cur < pos; cur++)
				item.Insert(cur, unknowns[cur]);
			unknowns.Delete(0, pos + 1);
			m_listbox.AddString(item);
		}	
		
	}
	UpdateData(FALSE);
	
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
		m_listbox.AddString(m_addthis);
		m_addthis.Empty();
		UpdateData(FALSE);
	}
	
}

void CUnknownDlg::OnDelete() 
{
	// Deletes the selected item.
	UpdateData(TRUE);
	m_listbox.GetText(m_listbox.GetCurSel(), m_addthis);
	m_listbox.DeleteString(m_listbox.GetCurSel());
	m_selected.Empty();
	UpdateData(FALSE);
}

void CUnknownDlg::OnOK() 
{
	// The user clicked ok so we will copy the list box
	// contents into UnknownParms
	CString temp;
	UnknownParms.Empty();
	for(int num = 0; num < m_listbox.GetCount(); num++)
	{
		m_listbox.GetText(num, temp);
		UnknownParms+=temp;
		UnknownParms+="\n";
	}
	CDialog::OnOK();
}

void CUnknownDlg::OnCancel() 
{
	// TODO: Add your control notification handler code here
	CDialog::OnCancel();
}

void CUnknownDlg::OnSelchangeListofunknown() 
{
	// Puts the currently selected item into the selected box.
	UpdateData(TRUE);
	m_listbox.GetText(m_listbox.GetCurSel(), m_selected);
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
		m_listbox.InsertString(sel+2, temp);
		m_listbox.DeleteString(sel);
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
