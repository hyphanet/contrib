Name "Freenet 0.3.2"
LicenseText "Freenet is published under the GNU general public license:"
LicenseData freenet/COPYING.txt
# Icon main.ico
OutFile Freenet_setup0.3.2.exe
UninstallText "This uninstalls Freenet and all files on this node. SHUT THE RUNNING NODE DOWN BEFORE UNINSTALLING!"
UninstallExeName Uninstall-Freenet.exe
ComponentText "This will install Freenet 0.3.2 on your system. Select which options you want to set up."
DirText "Select a directory to install Freenet in."
# InstType Normal

EnabledBitmap Yes.bmp
DisabledBitmap No.bmp

InstallDir "$PROGRAMFILES\Freenet"
InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"
SetOverwrite on

Section "Freenet 0.3.2 (required)"

# FindWindow prompt "FreenetWindowTitleName" "Freenet is currently running. Please close before installing."

# Copying the actual Freenet files to the install dir
SetOutPath $INSTDIR\
File freenet\*.*

# optional paths can be included like this:
# SetOutPath $INSTDIR\Program\Res\3.0_maxi_player
# File Program\Res\3.0_maxi_player\*.*

WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName" "Freenet 0.3.2 (remove only)"
WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString" '"$INSTDIR\Uninstall-Freenet.exe"'

Exec wait '"$INSTDIR\findjava.exe"'
Delete "$INSTDIR\findjava.exe"

# naah, we don't wanna write more than uninstall info in the registry.
# WriteRegStr HKEY_LOCAL_MACHINE "Software\Freenet" "instpath" $INSTDIR

# WriteRegDword HKEY_CURRENT_USER "Software\Spinner Plus\spinner" "Registered" 0
# WriteRegStr HKEY_CURRENT_USER "Software\Spinner Plus\spinner\Options" "SpinnerInstallSuccessful" "1"
# WriteRegStr HKEY_CURRENT_USER "Software\Spinner Plus\spinner\Options" "application/vnd.spinnerplus" "$INSTDIR\Program\spinner.exe"
# WriteRegStr HKEY_CLASSES_ROOT ".spn" "" "spnFile"
# WriteRegStr HKEY_CLASSES_ROOT ".spn" "Content Type" "application/vnd.spinnerplus"
# WriteRegStr HKEY_CLASSES_ROOT "spnFile" "" "spnFile"
# WriteRegBin HKEY_CLASSES_ROOT "spnFile" "EditFlags" 00000100
# WriteRegStr HKEY_CLASSES_ROOT "spnFile\Shell\open\command" "" '"$INSTDIR\Program\spinner.exe" %1'
# WriteRegStr HKEY_CURRENT_USER "Software\Netscape\Netscape Navigator\Viewers" "application/vnd.spinnerplus" "$INSTDIR\Program\spinner.exe"

 Section "Startmenu and Desktop Icons"
 SectionIn 1
 CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\fserve.exe" "" "$INSTDIR\freenet.ico" 0
 CreateShortCut "$STARTMENU\Freenet.lnk" "$INSTDIR\fserve.exe" "" "$INSTDIR\freenet.ico" 0
 #CreateShortCut "$QUICKLAUNCH\Freenet.lnk" 'start /min "$INSTDIR\fserve.exe"' "" "$INSTDIR\freenet.ico" 0
 
# Section "System tray support"
# SectionIn 1
# SetOutPath $INSTDIR\trayit!
# File freenet\trayit!\*.*
# (I still have to include the setup of trayit!)

Section "Launch Freenet node now"
SectionIn 1
Exec 'start /min "$INSTDIR\fserve.exe"'


Section "Launch Freenet on Startup"
# WriteRegStr HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server" '"$INSTDIR\fserve.exe"'
CreateShortCut "$STARTMENU\Autostart\Freenet.lnk" "$INSTDIR\fserve.exe" "" "$INSTDIR\freenet.ico" 0

Section "View Readme.txt"
SectionIn 1
Exec '"$WINDIR\notepad.exe" "$INSTDIR\Readme.txt"'


#------------------------------------
# Uninstall part begins here:
Section Uninstall
# DeleteRegKey HKEY_CURRENT_USER "Software\Spinner Plus"
# DeleteRegKey HKEY_CLASSES_ROOT "spnFile"
# DeleteRegKey HKEY_CLASSES_ROOT ".spn"
# DeleteRegValue HKEY_CURRENT_USER "Software\Netscape\Netscape Navigator\Viewers" "application/vnd.spinnerplus"

DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString"
DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName"
DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
DeleteRegValue HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server"

Exec wait 'command.com /c deltree /Y "$INSTDIR\.freenet"'
# Delete $INSTDIR\.freenet\*.*
# RMDir $INSTDIR\.freenet
Delete $INSTDIR\*.*
RMDir $INSTDIR

Delete "$STARTMENU\Freenet.lnk"
Delete "$DESKTOP\Freenet.lnk"
Delete "$STARTMENU\Autostart\Freenet.lnk"
#Delete "$QUICKLAUNCH\Freenet.lnk"
