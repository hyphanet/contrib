; Update.nsi
;
; This script will automatically stop the freenet node, 
; download the newest freenet.jar and start the node again
;
!include ..\webinstall.inc

; The name of the installer
Name "Update Freenet"

; The file to write
OutFile "UpdateSnapshot.exe"


InstallDir $EXEDIR       ; The default installation directory
;DirShow hide             ; Don't show the directory selection page
AutoCloseWindow true     ; Close the window after installation
ShowInstDetails show

;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "..\upx.exe -9 temp.dat"

;-----------------------------------------------------------------------

; The stuff to install
Section "Update jar (required)"
# $9 stores whether freenet ran previously so we have to start it again

# copying the download dll
SetDetailsPrint none
SetOutPath $TEMP\freenet
File ..\nsisdl.dll
SetDetailsPrint both

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
 StrCpy $1 "http://freenetproject.org/snapshots/freenet-latest.jar"
 StrCpy $2 "$INSTDIR\freenet.jar.new"
 Call DownloadFile
 
 Delete "$INSTDIR\freenet.jar"
 IfErrors 0 jardeleted
 MessageBox MB_OK "Error deleting the old jar, aborting update..."
 Abort
 jardeleted:
 Rename "$INSTDIR\freenet.jar.new" "$INSTDIR\freenet.jar"

 StrCpy $1 "http://freenetproject.org/snapshots/seednodes.ref"
 StrCpy $2 "$INSTDIR\seednodes.ref.new"
 Call DownloadFile

 Delete "$INSTDIR\seednodes.ref"
 IfErrors 0 refdeleted
 MessageBox MB_OK "Error deleting the old seednodes, aborting update..."
 Abort
 refdeleted:
 Rename "$INSTDIR\seednodes.ref.new" "$INSTDIR\seednodes.ref"  
 
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

