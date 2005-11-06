; Update.nsi
;
; This script will automatically stop the freenet node, 
; download the newest freenet.jar and seednodes.ref and 
; start the node again
;

# Bob H, Nov 2005 : Made it grab and decompress zipped seednodes instead of raw ones.

!include ..\webinstall.inc
!include "MUI.nsh"
!include "WinMessages.nsh"

!define MUI_PRODUCT "Freenet"
!define MUI_VERSION "Updater"

;--------------------------------
;Configuration

Name "Freenet Updater"
OutFile "UpdateSnapshot.exe"

InstallDir $EXEDIR  
;DirShow hide       
;AutoCloseWindow true 
ShowInstDetails show

!packhdr temp.dat "..\upx.exe -9 temp.dat"

;--------------------------------
;Modern UI Configuration

XPStyle on

!define MUI_ICON "..\Freenet-NET.ico"
!define MUI_UNICON "..\Freenet-NET.ico"
!define MUI_SPECIALBITMAP "..\Freenet-Panel.bmp"

!define MUI_PROGRESSBAR smooth
    
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\GNU.txt"

!insertmacro MUI_PAGE_INSTFILES  

;!define MUI_FINISHPAGE_RUN "$INSTDIR\freenet.exe"   
!insertmacro MUI_PAGE_FINISH
   
!define MUI_ABORTWARNING

  
;--------------------------------
;Languages
 
!insertmacro MUI_LANGUAGE "English"
LangString DESC_SecFreenetNode ${LANG_ENGLISH} "Downloads the latest Freenet Node software"


;--------------------------------
;Data
LicenseData "..\GNU.txt"




Section "Update Freenet (required)"

SetOutPath $EXEDIR

CloseFreenet:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 0 ClosedFreenet
  DetailPrint "Closing Freenet now"
  SendMessage $0 273 1003;16 0 ;WM_COMMAND IDM_EXIT Close a program
  Strcpy $9 1
  Sleep 2000
  Goto CloseFreenet

ClosedFreenet:
 StrCpy $1 "http://freenetproject.org/snapshots/freenet.jar"
 StrCpy $2 "$INSTDIR\freenet.jar.new"
 Call DownloadFile

 Delete "$INSTDIR\freenet.jar"
 Rename "$INSTDIR\freenet.jar.new" "$INSTDIR\freenet.jar"

 StrCpy $1 "http://freenetproject.org/snapshots/seednodes.zip"
 StrCpy $2 "$INSTDIR\seednodes.zip"
 Call DownloadFile

 Delete "$INSTDIR\seednodes.ref"
# Rename "$INSTDIR\seednodes.ref.new" "$INSTDIR\seednodes.ref"  

# Bob H : Unzip seednodes (to seednodes.ref)
ZipDLL::extractall "$INSTDIR\seednodes.zip" "$INSTDIR"

 
 # update finished, starting the node if it ran before
 IntCmp $9 1 0 StartedFreenet
 DetailPrint "Starting Freenet now"
 ClearErrors
 Exec "freenet.exe"
 IfErrors 0 StartedFreenet
 MessageBox MB_OK "An error occured when trying to start Freenet after the update."

StartedFreenet:
  RMDir /r $TEMP\freenet

SectionEnd

;--------------------------------------------------------------------
Function .onInstFailed
 RMDir /r $TEMP\freenet
 Delete "$INSTDIR\freenet.jar.new" 
 Delete "$INSTDIR\seednodes.ref.new" 
FunctionEnd

