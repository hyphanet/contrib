// ConfigFile.h: interface for the CConfigFile class.
//
//////////////////////////////////////////////////////////////////////

#if !defined(AFX_CONFIGFILE_H__9D0B8A69_3261_4870_8847_536A11CDA17D__INCLUDED_)
#define AFX_CONFIGFILE_H__9D0B8A69_3261_4870_8847_536A11CDA17D__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

class CConfigFile
{
public:
	CConfigFile();
	virtual	~CConfigFile();
	void	Load();
	void	Save();
	CString	FileName;
	CString FLaunchIniFileName;
	CString GetUnknowns();

private:
	void	processItem(char *tok, char *val);
	char	*splitLine(char *buf);
	BOOL	atobool(char *buf);
	void	ReadFLaunchIni(void);
	void	UpdateFLaunchIni(void);
};

typedef BOOL (WINAPI GETDISKFREESPACEEX_) (LPCTSTR,PULARGE_INTEGER,PULARGE_INTEGER,PULARGE_INTEGER);

#endif // !defined(AFX_CONFIGFILE_H__9D0B8A69_3261_4870_8847_536A11CDA17D__INCLUDED_)

