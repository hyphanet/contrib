# Microsoft Developer Studio Project File - Name="GetSeed" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 6.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Application" 0x0101

CFG=GetSeed - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "GetSeed.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "GetSeed.mak" CFG="GetSeed - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "GetSeed - Win32 Release" (based on "Win32 (x86) Application")
!MESSAGE "GetSeed - Win32 Debug" (based on "Win32 (x86) Application")
!MESSAGE 

# Begin Project
# PROP AllowPerConfigDependencies 0
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
MTL=midl.exe
RSC=rc.exe

!IF  "$(CFG)" == "GetSeed - Win32 Release"

# PROP BASE Use_MFC 6
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 6
# PROP Use_Debug_Libraries 0
# PROP Output_Dir "Release"
# PROP Intermediate_Dir "Release"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /MD /W3 /GX /O2 /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /D "_AFXDLL" /Yu"stdafx.h" /FD /c
# ADD CPP /nologo /MD /W3 /GX /O1 /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /D "_AFXDLL" /D "_MBCS" /Yu"stdafx.h" /FD /c
# ADD BASE MTL /nologo /D "NDEBUG" /mktyplib203 /win32
# ADD MTL /nologo /D "NDEBUG" /mktyplib203 /win32
# ADD BASE RSC /l 0x407 /d "NDEBUG" /d "_AFXDLL"
# ADD RSC /l 0x407 /d "NDEBUG" /d "_AFXDLL"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 /nologo /subsystem:windows /machine:I386
# ADD LINK32 Wininet.lib /nologo /subsystem:windows /machine:I386 -opt:nowin98
# SUBTRACT LINK32 /pdb:none

!ELSEIF  "$(CFG)" == "GetSeed - Win32 Debug"

# PROP BASE Use_MFC 6
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "Debug"
# PROP BASE Intermediate_Dir "Debug"
# PROP BASE Target_Dir ""
# PROP Use_MFC 6
# PROP Use_Debug_Libraries 1
# PROP Output_Dir "Debug"
# PROP Intermediate_Dir "Debug"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /MDd /W3 /Gm /GX /ZI /Od /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /D "_AFXDLL" /Yu"stdafx.h" /FD /GZ /c
# ADD CPP /nologo /MDd /W3 /Gm /GX /ZI /Od /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /D "_AFXDLL" /D "_MBCS" /Yu"stdafx.h" /FD /GZ /c
# ADD BASE MTL /nologo /D "_DEBUG" /mktyplib203 /win32
# ADD MTL /nologo /D "_DEBUG" /mktyplib203 /win32
# ADD BASE RSC /l 0x407 /d "_DEBUG" /d "_AFXDLL"
# ADD RSC /l 0x407 /d "_DEBUG" /d "_AFXDLL"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 /nologo /subsystem:windows /debug /machine:I386 /pdbtype:sept
# ADD LINK32 Wininet.lib /nologo /subsystem:windows /debug /machine:I386 /pdbtype:sept

!ENDIF 

# Begin Target

# Name "GetSeed - Win32 Release"
# Name "GetSeed - Win32 Debug"
# Begin Group "Source Files"

# PROP Default_Filter "cpp;c;cxx;rc;def;r;odl;idl;hpj;bat"
# Begin Source File

SOURCE=.\hlp\Freenet.hpj

!IF  "$(CFG)" == "GetSeed - Win32 Release"

# PROP Ignore_Default_Tool 1
# Begin Custom Build - Making help file...
OutDir=.\Release
InputPath=.\hlp\Freenet.hpj

"$(OutDir)\freenet.hlp" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	start /wait hcw /C /E /M "hlp\freenet.hpj" 
	if errorlevel 1 goto :Error 
	if not exist "hlp\freenet.hlp" goto :Error 
	copy "hlp\freenet.hlp" $(OutDir) 
	goto :done 
	:Error 
	echo hlp\freenet.hpj(1) : error: 
	type "hlp\freenet.log" 
	:done 
	
# End Custom Build

!ELSEIF  "$(CFG)" == "GetSeed - Win32 Debug"

# PROP Ignore_Default_Tool 1
# Begin Custom Build - Making help file...
OutDir=.\Debug
InputPath=.\hlp\Freenet.hpj

"$(OutDir)\freenet.hlp" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	start /wait hcw /C /E /M "hlp\freenet.hpj" 
	if errorlevel 1 goto :Error 
	if not exist "hlp\freenet.hlp" goto :Error 
	copy "hlp\freenet.hlp" $(OutDir) 
	goto :done 
	:Error 
	echo hlp\freenet.hpj(1) : error: 
	type "hlp\freenet.log" 
	:done 
	
# End Custom Build

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\GetSeed.cpp
# End Source File
# Begin Source File

SOURCE=.\GetSeed.rc
# End Source File
# Begin Source File

SOURCE=.\GetSeedDlg.cpp
# End Source File
# Begin Source File

SOURCE=.\StdAfx.cpp
# ADD CPP /Yc"stdafx.h"
# End Source File
# End Group
# Begin Group "Header Files"

# PROP Default_Filter "h;hpp;hxx;hm;inl"
# Begin Source File

SOURCE=.\GetSeed.h
# End Source File
# Begin Source File

SOURCE=.\GetSeedDlg.h
# End Source File
# Begin Source File

SOURCE=.\Resource.h
# End Source File
# Begin Source File

SOURCE=.\StdAfx.h
# End Source File
# End Group
# Begin Group "Resource Files"

# PROP Default_Filter "ico;cur;bmp;dlg;rc2;rct;bin;rgs;gif;jpg;jpeg;jpe"
# Begin Source File

SOURCE=.\res\GetSeed.ico
# End Source File
# Begin Source File

SOURCE=.\res\GetSeed.rc2
# End Source File
# End Group
# Begin Group "Help files"

# PROP Default_Filter "*.rtf,*.cnt"
# Begin Source File

SOURCE=.\hlp\Freenet.cnt

!IF  "$(CFG)" == "GetSeed - Win32 Release"

# Begin Custom Build - copying .cnt file
OutDir=.\Release
InputPath=.\hlp\Freenet.cnt

"$(OutDir)\freenet.cnt" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	copy "hlp\freenet.cnt" $(OutDir)

# End Custom Build

!ELSEIF  "$(CFG)" == "GetSeed - Win32 Debug"

# Begin Custom Build - copying .cnt file
OutDir=.\Debug
InputPath=.\hlp\Freenet.cnt

"$(OutDir)\freenet.cnt" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	copy "hlp\freenet.cnt" $(OutDir)

# End Custom Build

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\hlp\Freenet.rtf
# End Source File
# End Group
# Begin Source File

SOURCE=.\ReadMe.txt
# End Source File
# End Target
# End Project
