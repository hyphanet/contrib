# Microsoft Developer Studio Project File - Name="ezFCPlib" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 6.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Static Library" 0x0104

CFG=ezFCPlib - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "ezFCPlib.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "ezFCPlib.mak" CFG="ezFCPlib - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "ezFCPlib - Win32 Release" (based on "Win32 (x86) Static Library")
!MESSAGE "ezFCPlib - Win32 Debug" (based on "Win32 (x86) Static Library")
!MESSAGE 

# Begin Project
# PROP AllowPerConfigDependencies 0
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
RSC=rc.exe

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 0
# PROP Output_Dir "Release"
# PROP Intermediate_Dir "Release"
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /GX /O2 /D "WIN32" /D "NDEBUG" /D "_MBCS" /D "_LIB" /YX /FD /c
# ADD CPP /nologo /W3 /O1 /D "WIN32" /D "NDEBUG" /D "_LIB" /D "WINDOWS" /YX /FD /LD /GD /c
# ADD BASE RSC /l 0x409 /d "NDEBUG"
# ADD RSC /l 0x409 /d "NDEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LIB32=link.exe -lib
# ADD BASE LIB32 /nologo
# ADD LIB32 /nologo
# Begin Special Build Tool
SOURCE="$(InputPath)"
PostBuild_Desc=copy header file
PostBuild_Cmds=copy ezFCPlib.h Release
# End Special Build Tool

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "Debug"
# PROP BASE Intermediate_Dir "Debug"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 1
# PROP Output_Dir "Debug"
# PROP Intermediate_Dir "Debug"
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /Gm /GX /ZI /Od /D "WIN32" /D "_DEBUG" /D "_MBCS" /D "_LIB" /YX /FD /GZ /c
# ADD CPP /nologo /MTd /W3 /Gm /GX /ZI /Od /I ".." /I "..\include" /D "WIN32" /D "_DEBUG" /D "_MBCS" /D "_LIB" /D "WINDOWS" /D "NEW_PARSER" /FR /YX /FD /GZ /c
# ADD BASE RSC /l 0x409 /d "_DEBUG"
# ADD RSC /l 0x409 /d "_DEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LIB32=link.exe -lib
# ADD BASE LIB32 /nologo
# ADD LIB32 /nologo /out:"ezFCPlib.lib"

!ENDIF 

# Begin Target

# Name "ezFCPlib - Win32 Release"
# Name "ezFCPlib - Win32 Debug"
# Begin Group "Source Files"

# PROP Default_Filter "cpp;c;cxx;rc;def;r;odl;idl;hpj;bat"
# Begin Source File

SOURCE=.\_fcpGlobals.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\_fcpLog.c
# End Source File
# Begin Source File

SOURCE=.\_fcpPutSplit.c
# End Source File
# Begin Source File

SOURCE=.\_fcpReadBlk.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\_fcpRecvResponse.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\_fcpSock.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\_fcpUtil.c
# End Source File
# Begin Source File

SOURCE=.\fcpCloseKey.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpCreateHandle.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpDestroyHandle.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpGetKeyToFile.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpGetKeyToMem.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpInitHandle.c
# End Source File
# Begin Source File

SOURCE=.\fcpMakChkFromMem.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpMakeChkFromFile.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpMakeSvkKeypair.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpOpenKey.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpOpenKeyIndex.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpPutKeyFromFile.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpPutKeyFromMem.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpRawMode.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpReadKey.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpReadKeyIndex.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpSetParam.c
# End Source File
# Begin Source File

SOURCE=.\fcpStartup.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpWriteKey.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\fcpWriteKeyIndex.c

!IF  "$(CFG)" == "ezFCPlib - Win32 Release"

# ADD CPP /MTd
# SUBTRACT CPP /YX

!ELSEIF  "$(CFG)" == "ezFCPlib - Win32 Debug"

!ENDIF 

# End Source File
# Begin Source File

SOURCE=.\metaParse.c
# End Source File
# Begin Source File

SOURCE=.\mimetype.c
# End Source File
# Begin Source File

SOURCE=.\safeMalloc.c
# End Source File
# End Group
# Begin Group "Header Files"

# PROP Default_Filter "h;hpp;hxx;hm;inl"
# End Group
# Begin Source File

SOURCE=.\doc\spec.html
# End Source File
# End Target
# End Project
