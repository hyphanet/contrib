# installer generator script for Freenet:
!define VERSION "0.4-10102001-snapshot"

Name "Freenet ${VERSION}"
OutFile "Freenet_setup-${VERSION}.exe"
ComponentText "This will install Freenet ${VERSION} on your system."

LicenseText "Freenet is published under the GNU general public license:"
LicenseData GNU.txt

UninstallText "This uninstalls Freenet and all files on this node. (You may need to shut down running nodes before proceeding)"
UninstallExeName Uninstall-Freenet.exe

DirText "No files will be placed outside this directory (e.g. Windows\system)"

 InstType Minimal
 InstType Full

EnabledBitmap Yes.bmp
DisabledBitmap No.bmp
BGGradient
AutoCloseWindow true
;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "upx.exe -9 temp.dat"

InstallDir "$PROGRAMFILES\Freenet0.4"
;InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"

;-----------------------------------------------------------------------------------
Function DetectJava
# this function detects Sun Java from registry and calls the JavaFind utility otherwise

  # First look for the current version of Java and get the correct path in $2,
  # then test if its empty (nonexisting)
StartCheck:
  StrCpy $0 "SOFTWARE\JavaSoft\Java Runtime Environment\1.4"	; JRE key into $0
  ReadRegStr $2 HKLM $0 "JavaHome"				; read JRE path in $2
  StrCmp $2 "" 0 EndCheck

  StrCpy $0 "SOFTWARE\JavaSoft\Java Runtime Environment\1.3"	; JRE key into $0
  ReadRegStr $2 HKLM $0 "JavaHome"				; read JRE path in $2
  StrCmp $2 "" 0 EndCheck

  # Check for 1.2
  StrCpy $0 "SOFTWARE\JavaSoft\Java Runtime Environment\1.2"	; JRE key into $0
  ReadRegStr $2 HKLM $0 "JavaHome"				; read JRE path in $2
  StrCmp $2 "" RunJavaFind

EndCheck:
  StrCpy $3 "$2\bin\java.exe"
  StrCpy $4 "$2\bin\javaw.exe"

  IfFileExists $3 0 RunJavaFind
  WriteINIStr "$INSTDIR\FLaunch.ini" "Freenet Launcher" "JavaExec" $3
  IfFileExists $4 0 RunJavaFind
  WriteINIStr "$INSTDIR\FLaunch.ini" "Freenet Launcher" "Javaw" $4

  #jump to the end if we did the Java recognition correctly
  Goto End

RunJavaFind:
  # running the good ol' Java detection utility on unsuccess
  MessageBox MB_YESNO "I did not find Sun's Java Runtime Environment which is needed for Freenet.$\r$\nHit 'Yes' to open the download page for Java (http://java.sun.com),$\r$\n'No' to look for an alternative Java interpreter on your disks." IDYES GetJava
  Execwait "$INSTDIR\findjava.exe"
  Goto End

GetJava:
  # Open the download page for Sun's Java
  ExecShell "open" "http://javasoft.com/"
  Sleep 5000
  MessageBox MB_OKCANCEL "Press OK to continue the Freenet installation AFTER having installed Java,$\r$\nCANCEL to abort the installation." IDOK StartCheck
  Abort

End:
FunctionEnd
;-----------------------------------------------------------------------------------
Section "Freenet (required)"

  # Copying the Freenet files to the install dir
  SetOutPath "$INSTDIR\docs"
  File "freenet\docs\*.*"
  SetOutPath "$INSTDIR"
  # copying the temporary install and config utilities now
  File freenet\tools\*.*
  # copying the real Freenet files now
  File freenet\*.*
  CopyFiles "$INSTDIR\fserve.exe" "$INSTDIR\frequest.exe" 6
  CopyFiles "$INSTDIR\fserve.exe" "$INSTDIR\finsert.exe" 6
  CopyFiles "$INSTDIR\fserve.exe" "$INSTDIR\fclient.exe" 6
  CopyFiles "$INSTDIR\fserve.exe" "$INSTDIR\cfgnode.exe" 6
  CopyFiles "$INSTDIR\fserve.exe" "$INSTDIR\fsrvcli.exe" 6

  # possibly embed full Java runtime
  !ifdef embedJava
  File /r jre
  !endif

  HideWindow
  Call DetectJava

  BringToFront
  # create the configuration file now
  # but get a current seed file first
  ExecWait "$INSTDIR\GetSeed"
  ClearErrors
  ExecWait '"$INSTDIR\cfgnode.exe" freenet.ini --silent'
  IfErrors CfgnodeError
  # delete the set diskstoresize in a hackish manner, we set our own proposal lateron
  WriteINIStr "$INSTDIR\freenet.ini" "Freenet Node" "storeCacheSize" "0"
  # and set other winspecific settings
  WriteINIStr "$INSTDIR\freenet.ini" "Freenet Node" "transient" "true"
  ExecWait "$INSTDIR\cfgclient.exe"
  IfErrors ConfigError

  # now calling the GUI configurator
  ExecWait "$INSTDIR\NodeConfig.exe"
  # No, we don't want to start FProxy by default
  ExecWait '"$INSTDIR\cfgnode.exe" freenet.ini --silent'
  ;  replace the above line with the one below if you want to start fproxy automatically
  ;  ExecWait '"$INSTDIR\cfgnode.exe" freenet.ini --silent --services fproxy'
    
  Delete "$INSTDIR\findjava.exe"
  Delete "$INSTDIR\cfgnode.exe"
  Delete "$INSTDIR\cfgclient.exe"
  Delete "$INSTDIR\GetSeed.exe"
  
 Seed:
  # seeding the initial references
  #we need to check the existence of seed.ref here and fail if it does not exist.
  # do the seeding and export our own ref file
  ClearErrors
  ExecWait "$INSTDIR\fsrvcli --seed seed.ref"
  IfErrors SeedError NoSeedError
  SeedError:
  MessageBox MB_OK "There was an error while seeding your node. This might mean that you can´t connect to other nodes lateron."
  NoSeedError:
  ExecWait "$INSTDIR\fsrvcli --export myOwn.ref"
  IfErrors ExportError NoExportError
  ExportError:
  MessageBox MB_OK "There was an error while exporting your own reference file. This is a noncritical error."
  NoExportError:
  
  # successfully finished all the stuff in here, leave now
  Goto End

 CfgnodeError:
  BringToFront
  MessageBox MB_OK "There was an error while creating the Freenet configuration file. Do you really have Java already installed? Aborting installation now!"
  Abort
 
 ConfigError:
  BringToFront
  MessageBox MB_OK "There was an error while configuring Freenet.$\r$\nDo you really have Java already installed?$\r$\nAborting installation!"
  Abort
 
 Abort_inst:
  BringToFront
  Abort

 End:
SectionEnd
;--------------------------------------------------------------------------------------

Section "FCPProxy (web browser based access)"
SectionIn 1,2

  SetOutPath "$INSTDIR"
  File "freenet\fcpproxy\*.*"

SectionEnd
;--------------------------------------------------------------------------------------
 
Section "Startmenu and Desktop Icons"
SectionIn 1,2

   CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
   CreateDirectory "$SMPROGRAMS\Freenet0.4"
   CreateShortCut "$SMPROGRAMS\Freenet0.4\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
   CreateShortCut "$SMPROGRAMS\Freenet0.4\Fcpproxy.lnk" "$INSTDIR\fcpproxy.exe" "" "$INSTDIR\fcpproxy.exe" 0
   WriteINIStr "$SMPROGRAMS\Freenet0.4\FN Homepage.url" "InternetShortcut" "URL" "http://www.freenetproject.org"  
   ;WriteINIStr "$SMPROGRAMS\Freenet0.4\FNGuide.url" "InternetShortcut" "URL" "http://www.freenetproject.org/quickguide" 
   ;CreateShortcut "$SMPROGRAMS\Freenet0.4\FNGuide.url" "" "" "$SYSDIR\url.dll" 0
   CreateShortCut "$SMPROGRAMS\Freenet0.4\Uninstall.lnk" "$INSTDIR\Uninstall-Freenet.exe" "" "$INSTDIR\Uninstall-Freenet.exe" 0
 SectionEnd
;-------------------------------------------------------------------------------
 SectionDivider
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
Section "Launch Freenet on each Startup"
SectionIn 2
  # WriteRegStr HKEY_CURRENT_USER "Software\Microsoft\Windows\CurrentVersion\Run" "Freenet server" '"$INSTDIR\fserve.exe"'
  CreateShortCut "$SMSTARTUP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
SectionEnd

;---------------------------------------------------------------------------------------

Section "View Readme.txt"
SectionIn 2
  ExecShell "open" "$INSTDIR\docs\Readme.txt"
SectionEnd
;---------------------------------------------------------------------------------------

Section -PostInstall

  # Register .ref files to be added to seed.ref with a double-click
  WriteRegStr HKEY_CLASSES_ROOT ".ref" "" "Freenet_node_ref"
  WriteRegStr HKEY_CLASSES_ROOT "Freenet_node_ref\shell\open\command" "" '"$INSTDIR\freenet.exe" -import "%1"'
  WriteRegStr HKEY_CLASSES_ROOT "Freenet_node_ref\DefaultIcon" "" "$INSTDIR\freenet.exe,0"


  # Registering install path, so future installs will use the same path
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Freenet" "instpath" $INSTDIR

  # Registering the unistall information
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName" "Freenet"
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString" '"$INSTDIR\Uninstall-Freenet.exe"'

  MessageBox MB_YESNO "Congratulations, you have finished the installation of Freenet successfully.$\r$\nDo you want to start your Freenet node now?" IDNO StartedNode
  Exec "$INSTDIR\freenet.exe"
StartedNode:
SectionEnd
;------------------------------------------------------------------------------------------

# Uninstall part begins here:
Section Uninstall

  #First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
 ShutDown:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 StillRunning NotRunning 
 StillRunning:
  # Closing Freenet
  SendMessage $0 16 0 0
  MessageBox MB_YESNO "You are still running Freenet, trying to shut it down now. Should install proceed?" IDYES ShutDown
  Abort
 NotRunning:

  # Unregister .ref files
  DeleteRegKey HKEY_CLASSES_ROOT ".ref"
  DeleteRegKey HKEY_CLASSES_ROOT "Freenet_node_ref"

  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Freenet"

  # Now deleting the rest
  RMDir /r $INSTDIR

  # remove IE plugin
  UnRegDLL $INSTDIR\IEplugin\IEFreenetPlugin.dll
  Delete $INSTDIR\IEplugin\*.*
  RMDir $INSTDIR\IEplugin
  DeleteRegKey HKEY_CLASSES_ROOT PROTOCOLS\Handler\freenet
  DeleteRegKey HKEY_CLASSES_ROOT freenet

  # remove the desktop and startmenu icons
  Delete "$SMSTARTUP\Freenet.lnk"
  Delete "$DESKTOP\Freenet.lnk"
  RMDir /r "$SMPROGRAMS\Freenet0.4"

  #delete "$SMPROGRAMS\Start FProxy.lnk"
  #Delete "$QUICKLAUNCH\Start FProxy.lnk"
SectionEnd
;-----------------------------------------------------------------------------------------

Function .onInit
  #First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
 ShutDown:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 StillRunning NotRunning 
 StillRunning:
  # Closing Freenet
  SendMessage $0 16 0 0
  MessageBox MB_YESNO "You are still running Freenet, trying to shut it down now. Should install proceed?" IDYES ShutDown
  Abort
 NotRunning:
FunctionEnd
;-----------------------------------------------------------------------------------------

Function .onInstFailed
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Freenet"
  RMDir /r $INSTDIR
FunctionEnd

;-----------------------------------------------------------------------------------------