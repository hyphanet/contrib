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
private:
	void	processItem(char *tok, char *val);
	char	*splitLine(char *buf);
	BOOL	atobool(char *buf);
	UINT freeDiskSpaceMB;
};

#endif // !defined(AFX_CONFIGFILE_H__9D0B8A69_3261_4870_8847_536A11CDA17D__INCLUDED_)
