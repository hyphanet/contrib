# Freenet 0.7 (testing) JRE wrapper installer
# Bob Hayes 
#
# Can now be built with or without Java bundled.
# If it is, installs it if neccessary. If not, downloads + installs it if neccessary.
# (What's actually downloaded is a wrapped/compressed JRE to comply with redist license.)
# Then just invokes the funky Java installer via javaws which does the real work.

!include "webinstall.inc"    # download functions
!include "MUI.nsh"           # various wizard stuff

#!define JAVAINSTALLER jre-1_5_0_06-windows-i586-p.exe   # JRE installer to bundle
# JNLP that invokes the real 0.7 installer
!define JNLP_PATH "http://downloads.freenetproject.org/alpha/installer/freenet.jnlp"

# Extra installer compression, requires upx.exe is in $PATH
!packhdr temp.dat "upx.exe -9 temp.dat"

;--------------------------------
;Configuration

;General
Name "Freenet 0.7 alpha"
!define PRODUCT_NAME "Freenet07alpha"
!define PRODUCT_VERSION "pre-07-alpha"

;Installer name:
OutFile "freenet-${PRODUCT_VERSION}.exe"

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
LangString DESC_Install ${LANG_ENGLISH} "Install the Freenet 0.7 Node software, and Java if neccessary"


Section "Install"
  
InstallStart:
  Call GetJWS   # Get javaws.exe location
  Pop $R0

  # Jump if no JRE was found
  StrCmp $R0 "NOT FOUND" JWSnotFound

  IfFileExists "$R0" JWSfileExists
  # If we get here there SEEMED to be a JRE installed but javaws.exe wasn't there for some reason,
  # so jump to offering Java (re)installation
  GoTo JWSnotFound

JWSfileExists:
  DetailPrint "Calling JWS at $R0"
 
  # Use javaws to launch java installer
  StrCpy $0 '"$R0" ${JNLP_PATH}' 
  SetOutPath $EXEDIR
  Exec $0
  GoTo InstallDone

JWSnotFound:
!ifndef JAVAINSTALLER
  # Couldn't find javaws and not bundled with Java, offer to get it
  GoTo DownloadAndInstallJava
!endif
  # We couldn't find Java installed but seem to be bundled with it, offer to install
  MessageBox MB_YESNO "You don't seem to have Java installed, which is needed by Freenet.$\r$\n$\r$\nInstall it now?" IDYES InstallJava

  # They don't want to install Java, abort
  MessageBox MB_OK "Installation cancelled.$\r$\nJava is neccessary for Freenet to work."
  GoTo InstallDone

InstallJava:
!ifdef JAVAINSTALLER    # If built with bundled Java, extract and call the installer
  DetailPrint "Lauching Sun's Java Runtime Environment installation..."
  GetFullPathName /SHORT $R1 $TEMP # get (user's) TEMP dir into $R1
  SetOutPath "$R1"
  File ${JAVAINSTALLER}                   # unpack JRE installer to user's temp
  ExecWait "$R1\${JAVAINSTALLER}"         # run it, block  
  Delete "$R1\${JAVAINSTALLER}"           # delete

  GoTo InstallStart    # Should now have Java installed so try to detect again
!else
DownloadAndInstallJava:
  # If they don't want to download/install Java, finish
  MessageBox MB_YESNO "You don't seem to have Java installed, which is needed by Freenet.$\r$\n$\r$\nDo you want me to download and install Java now?" IDNO JavaInstallAborted
  # Otherwise fetch JRE to user's temp and run it
  GetFullPathName /SHORT $R1 $TEMP # get (user's) TEMP dir into $R1
  SetOutPath "$R1"
  Push "http://downloads.freenetproject.org/jre_wrapper_latest.exe"
  Push "$R1"
  Push "jre_wrapper_latest.exe"
  Call RetryableDownload
  ExecWait "$R1\jre_wrapper_latest.exe"         # run it, block  
  Delete "$R1\jre_wrapper_latest.exe"           # delete

  GoTo InstallStart    # Should now have Java installed so try to detect again
!endif

GoTo InstallDone  # normally skip the following message

JavaInstallAborted:
DetailPrint "Java installation cancelled, Freenet installation cannot continue."
DetailPrint "Java is required by Freenet."
DetailPrint "If the installer has trouble downloading it, you should be able"
DetailPrint "to download it from www.java.com"
MessageBox MB_OK "Installation of Freenet was cancelled.$\r$\n$\r$\nFreenet requires Java. You can let this installer get it,$\r$\nor download it yourself from www.java.com"

InstallDone:
SectionEnd 


Function GetJWS
#
#  Find Java Web Start (javaws.exe)
#  - in JAVA_HOME environment variable
#  - in the registry (JRE / JDK)
 
  Push $R0
  Push $R1
 
  ClearErrors
  ReadRegStr $R1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  ReadRegStr $R0 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$R1" "JavaHome"
  StrCpy $R0 "$R0\bin\javaws.exe"
  IfErrors 0 JreFound

  # If we get here then no normal JRE found, but perhaps they have a JDK. We use the
  # same approach as above i.e. try to get the CurrentVersion then look up the key.
  # (0.5x wininstaller currently looks up hardcoded 1.4 / 1.5 keys instead, shouldn't ..)
  ReadRegStr $R1 HKLM "SOFTWARE\JavaSoft\Java Development Kit" "CurrentVersion"  # get version
  ReadRegStr $R0 HKLM "SOFTWARE\JavaSoft\Java Development Kit\$R1" "JavaHome"    # get path
  StrCpy $R0 "$R0\bin\javaws.exe"
  IfErrors 0 JreFound

  # Last resort, look in $JAVA_HOME. This isn't set by default on windows, but if
  # they have the weird situation of a JRE not installed normally then it might be
  ClearErrors
  ReadEnvStr $R0 "JAVA_HOME"
  StrCpy $R0 "$R0\bin\javaws.exe"
  IfErrors 0 JreFound

 # Couldn't find a JRE
 StrCpy $R0 "NOT FOUND"
       
 # Jump here if we appear to have found a JRE, path is "returned" in $R0
 JreFound:
  Pop $R1
  Exch $R0
FunctionEnd
