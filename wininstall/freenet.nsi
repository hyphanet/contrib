#
# installer generator script for both versions of Freenet:
#  - Freenet Standard (with Java built in)
#  - Freenet Lite (no Java)
#
# NOTE - if this script is run by itself, it will automatically generate just Freenet-nojava
#      - to generate Freenet-withjava, run the script 'freenet-withjava' instead
#      - the flag 'embedJava', if set, results in Freenet with Java. Default is Freenet noJava
#
# These options added by David McNab, May 2001
;uncomment the next line to produce a build that includes java
#!define embedJava

!ifdef embedJava
Name "Freenet 0.3.9.1 (with Java)"
!else
Name "Freenet 0.3.9.1 (without Java)"
!endif

# comment the next line out to produce a real build and no testing dummy
#!define debug

LicenseText "Freenet is published under the GNU general public license:"
LicenseData GNU.txt
!ifdef embedJava
OutFile Freenet-withjava-setup0.3.9.1.exe
!else
OutFile Freenet_setup0.3.9.1.exe
!endif

UninstallText "This uninstalls Freenet and all files on this node. (You may need to shut down running nodes before proceeding)"
UninstallExeName Uninstall-Freenet.exe
!ifdef embedJava
ComponentText "This will install Freenet 0.3.9.1, including Java runtime, on your system."
!else
ComponentText "This will install Freenet 0.3.9.1, without Java, on your system."
!endif

DirText "No files will be placed outside this directory (e.g. Windows\system)"

 InstType Minimal
 InstType Full

EnabledBitmap Yes.bmp
DisabledBitmap No.bmp
BGGradient
AutoCloseWindow true
SetDatablockOptimize on
;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "upx.exe -9 temp.dat"

InstallDir "$PROGRAMFILES\Freenet"
InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"
SetOverwrite on

;-----------------------------------------------------------------------------------
Section "Freenet (required)"
!ifndef debug

#First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
FindWindow "close" "TrayIconFreenetClass" ""

# Copying the actual Freenet files to the install dir
SetOutPath "$INSTDIR"
File freenet\*.*

# possibly embed full Java runtime
!ifdef embedJava
File /r jre
!endif

WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName" "Freenet"
WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString" '"$INSTDIR\Uninstall-Freenet.exe"'

HideWindow
!ifdef embedJava
# severe hack - gotta rename findjava and run it under a different name.
# if findjava is run by the name 'locjava', it automatically writes the
# local jre entries.
# yuck! but I had to do this, because the Execwait cmd doesn't seem to allow program arguments
Rename "$INSTDIR\findjava.exe" "$INSTDIR\locjava.exe"
Execwait "$INSTDIR\locjava.exe"
Delete "$INSTDIR\locjava.exe"
!else
Execwait "$INSTDIR\findjava.exe"
Delete "$INSTDIR\findjava.exe"
!endif

ExecWait "$INSTDIR\portcfg.exe"
Delete "$INSTDIR\portcfg.exe"
BringToFront
ExecWait '"$INSTDIR\cfgnode.exe" silent'
Delete "$INSTDIR\cfgnode.exe"
ExecWait "$INSTDIR\cfgclient.exe"
Delete "$INSTDIR\cfgclient.exe"

# Registering install path, so future installs will use the same path
WriteRegStr HKEY_LOCAL_MACHINE "Software\Freenet" "instpath" $INSTDIR

SectionEnd

;--------------------------------------------------------------------------------------
 Section "Startmenu and Desktop Icons"
 SectionIn 1,2

 CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
 CreateDirectory "$SMPROGRAMS\Freenet"
 CreateShortCut "$SMPROGRAMS\Freenet\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
 WriteINIStr "$SMPROGRAMS\Freenet\FN Homepage.url" "InternetShortcut" "URL" "http://www.freenetproject.org"  
 WriteINIStr "$SMPROGRAMS\Freenet\FNGuide.url" "InternetShortcut" "URL" "http://www.freenetproject.org/quickguide" 
 ;CreateShortcut "$SMPROGRAMS\Freenet\FNGuide.url" "" "" "$SYSDIR\url.dll" 0
 CreateShortCut "$SMPROGRAMS\Freenet\Uninstall.lnk" "$INSTDIR\Uninstall-Freenet.exe" "" "$INSTDIR\Uninstall-Freenet.exe" 0
 SectionEnd

;-------------------------------------------------------------------------------
#Plugin for Internet-Explorer
#Section "IE browser plugin"
#SectionIn 2
#SetOutPath $INSTDIR
#File freenet\IEplugin\*.*
#WriteRegStr HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet CLSID {CDDCA3BE-697E-4BEB-BCE4-5650C1580BCE}
#WriteRegStr HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet '' 'freenet: Asychronous Pluggable Protocol Handler'
#WriteRegStr HKEY_CLASSES_ROOT freenet '' 'URL:freenet protocol'
#WriteRegStr HKEY_CLASSES_ROOT freenet 'URL Protocol' ''
#RegDLL $INSTDIR\IEFreenetPlugin.dll
#SectionEnd

#Section "Mozilla plugin"
#SectionIn 2
#SetOutPath $TEMP
# The next files are not yet deleted anywhere, need to do this somewhere!
#File freenet\NSplugin\launch.exe
#File freenet\NSplugin\mozinst.html
#File freenet\NSplugin\protozilla-0.3-other.xpi
#Exec '"$TEMP\launch.exe" Mozilla "$TEMP\mozinst.html"'
##need to delete the tempfiles again
#SectionEnd
;---------------------------------------------------------------------------------------
Section "Launch Freenet on Startup"
SectionIn 2
# WriteRegStr HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server" '"$INSTDIR\fserve.exe"'
CreateShortCut "$SMSTARTUP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
SectionEnd

;---------------------------------------------------------------------------------------
Section "Launch Freenet node now"
SectionIn 2
Exec "$INSTDIR\freenet.exe"
SectionEnd

;---------------------------------------------------------------------------------------
Section "View Readme.txt"
SectionIn 2
ExecShell "open" "$INSTDIR\Readme.txt"
SectionEnd
;---------------------------------------------------------------------------------------
!endif
#------------------------------------------------------------------------------------------
Section -PostInstall

HideWindow
# Don't make that message to long, or the installer will be corrupted
;MessageBox MB_OK|MB_ICONINFORMATION|MB_TOPMOST `We get a lot of feedback that Freenet wouldn't start. Have a look in your system tray after starting and you'll see Hops, the Freenet rabbit sitting there. Double-click or right-click him for further action.` 0 0
BringToFront
SectionEnd

#------------------------------------------------------------------------------------------
# Uninstall part begins here:
!ifndef debug
Section Uninstall

#First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
FindWindow "close" "TrayIconFreenetClass" ""

#DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString"
#DeleteRegValue HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName"
DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
#DeleteRegValue HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server"

# F... W2K doesn't provide deltree anymore, telling the user to delete their fucking dir themselves
ExecWait 'command.com /c rd "$INSTDIR\.freenet"'
MessageBox MB_OK|MB_ICONINFORMATION|MB_TOPMOST `Please delete .freenet in your freenet directory manually.` 0 0

# Now deleting the rest
# RMDir /r $INSTDIR\.freenet
RMDir /r $INSTDIR

# remove IE plugin
UnRegDLL $INSTDIR\IEplugin\IEFreenetPlugin.dll
Delete $INSTDIR\IEplugin\*.*
RMDir $INSTDIR\IEplugin
DeleteRegKey HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet
DeleteRegKey HKEY_CLASSES_ROOT freenet

Delete "$SMSTARTUP\Freenet.lnk"
Delete "$DESKTOP\Freenet.lnk"
Delete "$SMPROGRAMS\Freenet\Freenet.lnk"
Delete "$SMPROGRAMS\Freenet\FN Homepage.url"
Delete "$SMPROGRAMS\Freenet\FNGuide.url"
Delete "$SMPROGRAMS\Freenet\Uninstall.lnk" 
RMDir "$SMPROGRAMS\Freenet"

#delete "$SMPROGRAMS\Start FProxy.lnk"
#Delete "$QUICKLAUNCH\Start FProxy.lnk"
SectionEnd
;-----------------------------------------------------------------------------------------
!endif
;-----------------------------------------------------------------------------------------