# Freenet 0.7 (testing) JRE wrapper installer
# Bob Hayes 
#
# - Run Java detection
# - If (suitable) JRE installed, invoke NextGen$ installer
#   currently at http://emu.freenetproject.org/~nextgens/freenet.jnlp
# - Otherwise install JRE, then invoke it.
# - Don't have an "already have Java" installer like for 0.5x since in that case user
#   is directed to just open the jnlp
#
# Continued web-dependence arguably not such a great thing :/  Could we just embed jars?

# !include "webinstall.inc"    # download functions
!include "MUI.nsh"           # various wizard stuff

!define JAVAINSTALLER jre-1_5_0_06-windows-i586-p.exe   # JRE installer to bundle
# JNLP that invokes the real 0.7 installer
!define JNLP_PATH "http://emu.freenetproject.org/~nextgens/freenet.jnlp"

# Extra installer compression, requires upx.exe is in $PATH
!packhdr temp.dat "upx.exe -9 temp.dat"

;--------------------------------
;Configuration

;General
Name "Freenet 0.7 pre-alpha"
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
# define MUI_UNINSTALLER
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
  # We couldn't find Java installed, offer to install
  MessageBox MB_YESNO "You don't seem to have Java installed, which is needed by Freenet.$\r$\n$\r$\nInstall it now?" IDYES InstallJava

  # They don't want to install Java, abort
  MessageBox MB_OK "Installation cancelled.$\r$\nJava is neccessary for Freenet to work."
  GoTo InstallDone

InstallJava:
# Should be built with bundled Java, so extract and call the installer
!ifdef JAVAINSTALLER
  DetailPrint "Lauching Sun's Java Runtime Environment installation..."
  GetFullPathName /SHORT $R1 $TEMP # get (user's) TEMP dir into $R1
  SetOutPath "$R1"
  File ${JAVAINSTALLER}                   # unpack JRE installer to user's temp
  ExecWait "$R1\${JAVAINSTALLER}"         # run it, block  
  Delete "$R1\${JAVAINSTALLER}"           # delete

  GoTo InstallStart    # Should now have Java installed so try to detect again
!else
  MessageBox MB_OK "Error: JAVAINSTALLER variable not set, no JRE bundled!"
!endif

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
