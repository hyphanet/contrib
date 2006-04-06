# Trivial JRE wrapper
# Bob Hayes 
#
# Exists purely to work around the stupid JRE redistribution license, we're probably
# not allowed to redist the bare JRE even if it's only 07wrapper.nsi that downloads it.
# So we put it in this NSI and UPX it, then it's 'bundled with a value-added software program'
# as per Sun's requirements.

!include "MUI.nsh"           # various wizard stuff

!define JAVAINSTALLER jre-1_5_0_06-windows-i586-p.exe   # JRE installer to bundle

# Extra installer compression, requires upx.exe is in $PATH
!packhdr temp.dat "upx.exe -9 temp.dat"

;--------------------------------
;Configuration

;General
Name "Java runtime"
!define PRODUCT_NAME "Java runtime"
!define PRODUCT_VERSION "1_50_06"  # bundled version of Java

;Installer name:
OutFile "jre_wrapper_latest.exe"

InstallDir "$PROGRAMFILES\${PRODUCT_NAME}"  # we don't actually install anything

;--------------------------------
;Modern UI Configuration

XPStyle on
!define MUI_ICON ".\Freenet-CD.ico"
!define MUI_UNICON ".\Freenet-CD.ico"
!define MUI_SPECIALBITMAP ".\Freenet-Panel.bmp"
!define MUI_PROGRESSBAR smooth
    
!insertmacro MUI_PAGE_WELCOME

!insertmacro MUI_PAGE_INSTFILES  

#!insertmacro MUI_PAGE_FINISH
   
!define MUI_ABORTWARNING
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
  
;--------------------------------
# Languages, hmm should be internationalised
!insertmacro MUI_LANGUAGE "English" 
;--------------------------------
# Language Strings
LangString DESC_Install ${LANG_ENGLISH} "Installs Sun Microsystem's(c) Java(tm) runtime environment ${PRODUCT_VERSION}"


Section "Install"
  
!ifdef JAVAINSTALLER    # If built with bundled Java, extract and call the installer
  DetailPrint "Lauching Sun's Java Runtime Environment installation..."
  GetFullPathName /SHORT $R1 $TEMP # get (user's) TEMP dir into $R1
  SetOutPath "$R1"
  File ${JAVAINSTALLER}                   # unpack JRE installer to user's temp
  ExecWait "$R1\${JAVAINSTALLER}"         # run it, block  
  Delete "$R1\${JAVAINSTALLER}"           # delete
!else
  MessageBox MB_OK "Java installer not defined!.$\r$\n$\r$\nPlease report this error to devl@freenetproject.org, thanks."
!endif

SectionEnd 
