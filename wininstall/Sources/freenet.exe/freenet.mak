# Microsoft Developer Studio Generated NMAKE File, Based on freenet.dsp
!IF "$(CFG)" == ""
CFG=freenet - Win32 Debug
!MESSAGE No configuration specified. Defaulting to freenet - Win32 Debug.
!ENDIF 

!IF "$(CFG)" != "freenet - Win32 Release" && "$(CFG)" !=\
 "freenet - Win32 Debug"
!MESSAGE Invalid configuration "$(CFG)" specified.
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "freenet.mak" CFG="freenet - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "freenet - Win32 Release" (based on "Win32 (x86) Application")
!MESSAGE "freenet - Win32 Debug" (based on "Win32 (x86) Application")
!MESSAGE 
!ERROR An invalid configuration is specified.
!ENDIF 

!IF "$(OS)" == "Windows_NT"
NULL=
!ELSE 
NULL=nul
!ENDIF 

!IF  "$(CFG)" == "freenet - Win32 Release"

OUTDIR=.\Release
INTDIR=.\Release
# Begin Custom Macros
OutDir=.\Release
# End Custom Macros

!IF "$(RECURSE)" == "0" 

ALL : "$(OUTDIR)\freenet.exe"

!ELSE 

ALL : "$(OUTDIR)\freenet.exe"

!ENDIF 

CLEAN :
	-@erase "$(INTDIR)\freenet.obj"
	-@erase "$(INTDIR)\launchthread.obj"
	-@erase "$(INTDIR)\rsrc.res"
	-@erase "$(INTDIR)\vc50.idb"
	-@erase "$(OUTDIR)\freenet.exe"
	-@erase "$(OUTDIR)\freenet.map"

"$(OUTDIR)" :
    if not exist "$(OUTDIR)/$(NULL)" mkdir "$(OUTDIR)"

CPP=cl.exe
CPP_PROJ=/nologo /G3 /Gr /Zp1 /MD /W3 /vd0 /Ox /Oa /Ow /Og /Oi /Os /Ob2 /Gf /Gy\
 /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /D "VC_EXTRALEAN" /D "WIN32_LEAN_AND_MEAN"\
 /FAcs /Fa"$(INTDIR)\\" /Fp"$(INTDIR)\freenet.pch" /YX /Fo"$(INTDIR)\\"\
 /Fd"$(INTDIR)\\" /FD /c 
CPP_OBJS=.\Release/
CPP_SBRS=.

.c{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.c{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

MTL=midl.exe
MTL_PROJ=/nologo /D "NDEBUG" /mktyplib203 /o NUL /win32 
RSC=rc.exe
RSC_PROJ=/l 0x809 /fo"$(INTDIR)\rsrc.res" /d "NDEBUG" 
BSC32=bscmake.exe
BSC32_FLAGS=/nologo /o"$(OUTDIR)\freenet.bsc" 
BSC32_SBRS= \
	
LINK32=link.exe
LINK32_FLAGS=kernel32.lib user32.lib shell32.lib /nologo /version:1.0\
 /subsystem:windows /incremental:no /pdb:"$(OUTDIR)\freenet.pdb"\
 /map:"$(INTDIR)\freenet.map" /machine:IX86 /out:"$(OUTDIR)\freenet.exe"\
 /ALIGN:4096 
LINK32_OBJS= \
	"$(INTDIR)\freenet.obj" \
	"$(INTDIR)\launchthread.obj" \
	"$(INTDIR)\rsrc.res"

"$(OUTDIR)\freenet.exe" : "$(OUTDIR)" $(DEF_FILE) $(LINK32_OBJS)
    $(LINK32) @<<
  $(LINK32_FLAGS) $(LINK32_OBJS)
<<

!ELSEIF  "$(CFG)" == "freenet - Win32 Debug"

OUTDIR=.\Debug
INTDIR=.\Debug
# Begin Custom Macros
OutDir=.\Debug
# End Custom Macros

!IF "$(RECURSE)" == "0" 

ALL : "$(OUTDIR)\freenet.exe"

!ELSE 

ALL : "$(OUTDIR)\freenet.exe"

!ENDIF 

CLEAN :
	-@erase "$(INTDIR)\freenet.obj"
	-@erase "$(INTDIR)\launchthread.obj"
	-@erase "$(INTDIR)\rsrc.res"
	-@erase "$(INTDIR)\vc50.idb"
	-@erase "$(INTDIR)\vc50.pdb"
	-@erase "$(OUTDIR)\freenet.exe"
	-@erase "$(OUTDIR)\freenet.ilk"
	-@erase "$(OUTDIR)\freenet.map"
	-@erase "$(OUTDIR)\freenet.pdb"

"$(OUTDIR)" :
    if not exist "$(OUTDIR)/$(NULL)" mkdir "$(OUTDIR)"

CPP=cl.exe
CPP_PROJ=/nologo /MLd /W3 /Gm /GX /Zi /Od /D "WIN32" /D "_DEBUG" /D "_WINDOWS"\
 /Fp"$(INTDIR)\freenet.pch" /YX /Fo"$(INTDIR)\\" /Fd"$(INTDIR)\\" /FD /c 
CPP_OBJS=.\Debug/
CPP_SBRS=.

.c{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(CPP_OBJS)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.c{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(CPP_SBRS)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

MTL=midl.exe
MTL_PROJ=/nologo /D "_DEBUG" /mktyplib203 /o NUL /win32 
RSC=rc.exe
RSC_PROJ=/l 0x809 /fo"$(INTDIR)\rsrc.res" /d "_DEBUG" 
BSC32=bscmake.exe
BSC32_FLAGS=/nologo /o"$(OUTDIR)\freenet.bsc" 
BSC32_SBRS= \
	
LINK32=link.exe
LINK32_FLAGS=kernel32.lib user32.lib shell32.lib /nologo /version:1.0\
 /subsystem:windows /incremental:yes /pdb:"$(OUTDIR)\freenet.pdb"\
 /map:"$(INTDIR)\freenet.map" /debug /machine:I386 /out:"$(OUTDIR)\freenet.exe"\
 /pdbtype:sept 
LINK32_OBJS= \
	"$(INTDIR)\freenet.obj" \
	"$(INTDIR)\launchthread.obj" \
	"$(INTDIR)\rsrc.res"

"$(OUTDIR)\freenet.exe" : "$(OUTDIR)" $(DEF_FILE) $(LINK32_OBJS)
    $(LINK32) @<<
  $(LINK32_FLAGS) $(LINK32_OBJS)
<<

!ENDIF 


!IF "$(CFG)" == "freenet - Win32 Release" || "$(CFG)" ==\
 "freenet - Win32 Debug"
SOURCE=.\freenet.c

!IF  "$(CFG)" == "freenet - Win32 Release"

DEP_CPP_FREEN=\
	".\freenet_tray.h"\
	".\rsrc.h"\
	".\types.h"\
	

"$(INTDIR)\freenet.obj" : $(SOURCE) $(DEP_CPP_FREEN) "$(INTDIR)"


!ELSEIF  "$(CFG)" == "freenet - Win32 Debug"

DEP_CPP_FREEN=\
	".\freenet_tray.h"\
	".\launchthread.h"\
	".\rsrc.h"\
	".\shared_data.h"\
	".\types.h"\
	

"$(INTDIR)\freenet.obj" : $(SOURCE) $(DEP_CPP_FREEN) "$(INTDIR)"


!ENDIF 

SOURCE=.\launchthread.c

!IF  "$(CFG)" == "freenet - Win32 Release"

DEP_CPP_LAUNC=\
	".\launchthread.h"\
	".\shared_data.h"\
	".\types.h"\
	

"$(INTDIR)\launchthread.obj" : $(SOURCE) $(DEP_CPP_LAUNC) "$(INTDIR)"


!ELSEIF  "$(CFG)" == "freenet - Win32 Debug"

DEP_CPP_LAUNC=\
	".\launchthread.h"\
	".\shared_data.h"\
	".\types.h"\
	

"$(INTDIR)\launchthread.obj" : $(SOURCE) $(DEP_CPP_LAUNC) "$(INTDIR)"


!ENDIF 

SOURCE=.\rsrc.rc
DEP_RSC_RSRC_=\
	".\alert.ico"\
	".\Freenet.ico"\
	".\noFnet.ico"\
	".\restart.ico"\
	".\rsrc.h"\
	

"$(INTDIR)\rsrc.res" : $(SOURCE) $(DEP_RSC_RSRC_) "$(INTDIR)"
	$(RSC) $(RSC_PROJ) $(SOURCE)



!ENDIF 

