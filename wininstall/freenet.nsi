Name "Freenet 0.3.6.1"
LicenseText "Freenet is published under the GNU general public license:"
LicenseData GNU.txt
OutFile Freenet_setup0.3.6.1.exe
UninstallText "This uninstalls Freenet and all files on this node. (You may need to shut down running nodes before proceeding)"
UninstallExeName Uninstall-Freenet.exe
ComponentText "This will install Freenet 0.3.6.1 on your system."
DirText "Select a directory to install Freenet in."
 InstType Minimal
 InstType Full

EnabledBitmap Yes.bmp
DisabledBitmap No.bmp

InstallDir "$PROGRAMFILES\Freenet"
InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"
SetOverwrite on

Section "Freenet 0.3.6.1 (required)"

#First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
FindWindow "close" "TrayIconFreenetClass" ""

# Copying the actual Freenet files to the install dir
SetOutPath $INSTDIR\
File freenet\*.*

#FindRegJava is now incorporated into Findjava, no need for it
#File freenet\misc\FindRegJava.exe
File freenet\findjava.exe
File freenet\cfgclient.exe
File freenet\portcfg.exe

WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName" "Freenet 0.3.5 (remove only)"
WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString" '"$INSTDIR\Uninstall-Freenet.exe"'

#ExecWait "$INSTDIR\FindRegJava.exe"
Execwait "$INSTDIR\findjava.exe"
#Delete "$INSTDIR\FindRegJava.exe"
Delete "$INSTDIR\findjava.exe"
ExecWait "$INSTDIR\portcfg.exe"
Delete "$INSTDIR\portcfg.exe"
#ExecWait '"$INSTDIR\cfgnode.exe" simple'
# Delete "$INSTDIR\cfgnode.exe"
ExecWait "$INSTDIR\cfgclient.exe"
Delete "$INSTDIR\cfgclient.exe"

# Registering install path, so future installs will use the same path
WriteRegStr HKEY_LOCAL_MACHINE "Software\Freenet" "instpath" $INSTDIR


 Section "Startmenu and Desktop Icons"
 SectionIn 1,2
 CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
 CreateShortCut "$SMPROGRAMS\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0


Section "Launch Freenet node now"
SectionIn 1,2
Exec "$INSTDIR\freenet.exe"

Section "IE browser plugin"
SectionIn 2
SetOutPath $INSTDIR
File freenet\IEplugin\*.*
WriteRegStr HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet CLSID {CDDCA3BE-697E-4BEB-BCE4-5650C1580BCE}
WriteRegStr HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet '' 'freenet: Asychronous Pluggable Protocol Handler'
WriteRegStr HKEY_CLASSES_ROOT freenet '' 'URL:freenet protocol'
WriteRegStr HKEY_CLASSES_ROOT freenet 'URL Protocol' ''
RegDLL $INSTDIR\FreenetProtocol.dll

#Section "NS6/Mozilla plugin"
#SectionIn 2
#SetOutPath $INSTDIR\NSplugin
#File freenet\NSplugin\*.*

Section "Launch Freenet on Startup"
SectionIn 1,2
# WriteRegStr HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server" '"$INSTDIR\fserve.exe"'
CreateShortCut "$SMSTARTUP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0


Section "View Readme.txt"
SectionIn 1,2
Exec '"$WINDIR\notepad.exe" "$INSTDIR\Readme.txt"'



#------------------------------------
# Uninstall part begins here:
Section Uninstall

#First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
FindWindow "close" "TrayIconFreenetClass" ""

DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString"
DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName"
DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
#DeleteRegValue HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server"

# F... W2K doesn't provide deltree anymore, telling the user to delete their fucking dir themselves
ExecWait 'command.com /c rd "$INSTDIR\.freenet"'
MessageBox MB_OK|MB_ICONINFORMATION|MB_TOPMOST `Please delete .freenet in your freenet directory manually.`

# remove IE plugin
UnRegDLL $INSTDIR\IEplugin\FreenetProtocol.dll
DeleteRegKey HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet
DeleteRegKey HKEY_CLASSES_ROOT freenet

# Now deleting the rest
# Delete $INSTDIR\.freenet\*.*
# RMDir $INSTDIR\.freenet
Delete $INSTDIR\NSplugin\*.*
RMDir $INSTDIR\NSplugin
Delete $INSTDIR\*.*
RMDir $INSTDIR

Delete "$SMPROGRAMS\Freenet.lnk"
Delete "$SMSTARTUP\Freenet.lnk"
Delete "$DESKTOP\Freenet.lnk"

#delete "$SMPROGRAMS\Start FProxy.lnk"
#Delete "$QUICKLAUNCH\Start FProxy.lnk"
