# installer generator script for Freenet:
#!define VERSION "20020225"
!define WEBINSTALL
#!define LANGUAGE "Dutch"

# include language specific installer data
!ifdef LANGUAGE
  !include "${LANGUAGE}.inc"
!else
  !include "Def_lang.inc"
!endif

# are we including Java?
!ifdef embedJava
  OutFile "freenet-Java-${VERSION}.exe"
!else
  !ifdef WEBINSTALL
    OutFile "freenet-webinstall.exe"
  !else
    OutFile "freenet-${VERSION}.exe"
  !endif
!endif

# webinstall specific stuff
!ifdef WEBINSTALL
  Name "Freenet webinstaller"
  !include "webinstall.inc"
!else
  Name "Freenet ${VERSION}"
!endif

LicenseData GNU.txt

 InstType Minimal
 InstType Normal

;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "upx.exe -9 temp.dat"

InstallDir "$PROGRAMFILES\Freenet0.4"
InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"
ShowInstDetails show
InstProgressFlags smooth
CheckBitmap "checked.bmp"
BGGradient
AutoCloseWindow true
;-----------------------------------------------------------------------------------
Function DetectJava
# this function detects Sun Java from registry and calls the JavaFind utility otherwise

  # Save $0, $2, $5 and $6
  Push $0
  Push $2
  Push $5
  Push $6
  
  # This is the first time we run the JavaFind
  StrCpy $5 "No"

StartCheck:
  # First get the installed version (if any) in $2
  # then get the path in $6

  StrCpy $0 "SOFTWARE\JavaSoft\Java Runtime Environment"
  # Get JRE installed version
  ReadRegStr $2 HKLM $0 "CurrentVersion"
  StrCmp $2 "" DetectTry2

  # Get JRE path
  ReadRegStr $6 HKLM "$0\$2" "JavaHome"
  StrCmp $6 "" DetectTry2
  
  #We seem to have a JRE now
  Goto GetJRE
  
 DetectTry2:
  # we did not get a JRE, but there might be a SDK installed
  StrCpy $0 "Software\JavaSoft\Java Development Kit"
  # Get JRE installed version
  ReadRegStr $2 HKLM $0 "CurrentVersion"
  StrCmp $2 "" RunJavaFind

  # Get JRE path
  ReadRegStr $6 HKLM "$0\$2" "JavaHome"
  StrCmp $6 "" RunJavaFind
  
 GetJRE:
  StrCpy $3 "$6\bin\java.exe"
  StrCpy $4 "$6\bin\javaw.exe"

  # Check if files exists and write paths
  IfFileExists $3 0 RunJavaFind
  WriteINIStr "$INSTDIR\FLaunch.ini" "Freenet Launcher" "JavaExec" $3
  IfFileExists $4 0 RunJavaFind
  WriteINIStr "$INSTDIR\FLaunch.ini" "Freenet Launcher" "Javaw" $4

  # Jump to the end if we did the Java recognition correctly
  Goto End

 RunJavaFind:
 
  # If RunJavaFind has been already launched, abort installation
  StrCmp $5 "Yes" AbortJava

  # Put 'Yes' in $5 to state that RunJavaFind was launched
  StrCpy $5 "Yes"

 !ifdef embedJava
    # Install Java runtime only if not found
    DetailPrint "Lauching Sun's Java Runtime Environment installation..."
    SetOutPath "$TEMP"
    File ${JAVAINSTALLER}
    ExecWait "$TEMP\${JAVAINSTALLER}"
    Delete "$TEMP\${JAVAINSTALLER}"
    Goto StartCheck
 !else
  # running the good ol' Java detection utility on unsuccess
  MessageBox MB_YESNO "I did not find Sun's Java Runtime Environment which is needed for Freenet.$\r$\nHit 'Yes' to open the download page for Java (http://java.sun.com),$\r$\n'No' to look for an alternative Java interpreter on your disks." IDYES GetJava
  Execwait "$INSTDIR\findjava.exe"
  ExecWait "$INSTDIR\cfgclient.exe"
  Delete "$INSTDIR\cfgclient.exe"
  
  # Get the Java path from the updated FLaunch.ini
  ReadINIStr $3 "$INSTDIR\FLaunch.ini" "Freenet Launcher" "JavaExec"
  ReadINIStr $4 "$INSTDIR\FLaunch.ini" "Freenet Launcher" "Javaw"

  # Check if files exist
  IfFileExists $3 0 RunJavaFind
  IfFileExists $4 0 RunJavaFind
  Goto End
  
 GetJava:
  # Open the download page for Sun's Java
  ExecShell "open" "http://javasoft.com/"
  Sleep 5000
  MessageBox MB_OKCANCEL "Press OK to continue the Freenet installation AFTER having installed Java,$\r$\nCANCEL to abort the installation." IDOK StartCheck
  Abort
!endif

 AbortJava:
  MessageBox MB_OK|MB_ICONSTOP "I still can't find any Java interpreter. Did you really installed the JRE?$\r$\nInstallation will now stop."
  Abort
  
End:
  # Restore $0, $2, $5 and $6
  Pop $6
  Pop $5
  Pop $2
  Pop $0
FunctionEnd
;---------------------------------------------------------------------------------------

Section
# This is the initial section in which we copy all necessary files, in the following
# sections come the localization parts and *then* we can start the actual
# setup/configuration of Freenet

  # First of all see if we need to install the mfc42.dll
  # Each Win user should have it anyway
  IfFileExists "$SYSDIR\Mfc42.dll" MfcDLLExists
  !ifdef WEBINSTALL
    MessageBox MB_OK "You need to have Mfc42.dll in the directory $SYSDIR.$\r$\nIt is not included in this installer, so please download a regular snapshot first.$\r$\nAborting now..."
    Abort
  !else
    DetailPrint "Installing Mfc42.dll"
    SetOutPath "$SYSDIR"
    File "Mfc42.dll"
    ClearErrors
  !endif
  MfcDLLExists:
  
  # create the configuration file now
  # set the diskstoresize to 0 to tell NodeConfig, to propose a value lateron
  IfFileExists "$INSTDIR\freenet.ini" iniFileExisted
  WriteINIStr "$INSTDIR\freenet.ini" "Freenet Node" "storeCacheSize" "0"
  iniFileExisted:
  
  # Copying the Freenet files to the install dir
  DetailPrint "Copying Freenet files"
  SetOutPath "$INSTDIR\docs"
  File "freenet\docs\*.*"
  SetOutPath "$INSTDIR"
  # copying the temporary install and config utilities now
  File freenet\tools\*.*
  # copying the real Freenet files now
  File freenet\*.*
  WriteUninstaller "Uninstall-Freenet.exe"
  
  # look if there is a freenet.jar and an ext-freenet.jar in the same directory and use this
  IfFileExists "$EXEDIR\lib\freenet.jar" 0 checkedForFiles

  # copy the local files and use these instead
  CopyFiles "$EXEDIR\lib\freenet.jar" "$INSTDIR\freenet.jar"
  CopyFiles "$EXEDIR\lib\freenet-ext.jar" "$INSTDIR\freenet-ext.jar"
  CopyFiles "$EXEDIR\seednodes.ref" "$INSTDIR\seednodes.ref"

  Goto getFilesDone

 checkedForFiles:
  !ifdef WEBINSTALL
    # download the necessary files
    AddSize 950 ; add 950kb for Freenet.jar
    AddSize 100 ; add 100kb for freenet-ext.jar
    SetOutPath "$TEMP\Freenet"
    File nsisdl.dll
    SetOutPath "$INSTDIR"
    StrCpy $1 "http://freenetproject.org/snapshots/freenet-latest.jar"
    StrCpy $2 "$INSTDIR\freenet.jar"
    Call DownloadFile
    StrCpy $1 "http://freenetproject.org/snapshots/freenet-ext.jar"
    StrCpy $2 "$INSTDIR\freenet-ext.jar"
    Call DownloadFile
  !else
    # copying the .jar files now
    File freenet\jar\*.*
  !endif

  getFilesDone:

  CopyFiles /silent "$INSTDIR\fserve.exe" "$INSTDIR\fclient.exe" 6
  SetDetailsPrint none
  CopyFiles /silent "$INSTDIR\fserve.exe" "$INSTDIR\cfgnode.exe" 6
  CopyFiles /silent "$INSTDIR\fserve.exe" "$INSTDIR\fservew.exe" 6
  SetDetailsPrint both

SectionEnd 
;--------------------------------------------------------------------------------------

Section "Startmenu and Desktop Icons"
SectionIn 1,2

   # Desktop icon
   CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
   
   # Start->Programs->Freenet
   CreateDirectory "$SMPROGRAMS\Freenet0.4"
   CreateShortCut "$SMPROGRAMS\Freenet0.4\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
   WriteINIStr "$SMPROGRAMS\Freenet0.4\FN Homepage.url" "InternetShortcut" "URL" "http://www.freenetproject.org"  
   ;WriteINIStr "$SMPROGRAMS\Freenet0.4\FNGuide.url" "InternetShortcut" "URL" "http://www.freenetproject.org/quickguide" 
   CreateShortcut "$SMPROGRAMS\Freenet0.4\Update Snapshot.lnk" "$INSTDIR\UpdateSnapshot" "" "" 0
   CreateShortCut "$SMPROGRAMS\Freenet0.4\Uninstall.lnk" "$INSTDIR\Uninstall-Freenet.exe" "" "$INSTDIR\Uninstall-Freenet.exe" 0
 SectionEnd
 
 ;---------------------------------------------------------------------------------------
#temporary out of order
#Section "German Localization (Deutsch)"

#  SetOutPath "$INSTDIR\"
#  File  /oname=localres.dll "Freenet\localres\DE-res.dll"
#SectionEnd 

;-----------------------------------------------------------------------------------

Section
# This is the invisible 'core' section which does all the install/config stuff


  Call DetectJava

  # turn on FProxy by default
  ClearErrors
  ExecWait '"$INSTDIR\cfgnode.exe" freenet.ini --silent'
  IfErrors CfgnodeError
  # now calling the GUI configurator
  ExecWait "$INSTDIR\NodeConfig.exe"
  
 
  # Seeding the initial references
  # we need to check the existence of seed.ref here and fail if it does not exist.
  # do the seeding and export our own ref file
  #ClearErrors
  #ExecWait "$INSTDIR\fserve --seed seed.ref"
  #IfErrors SeedError NoSeedError
  #SeedError:
  #MessageBox MB_OK "There was an error while seeding your node. This might mean that you can´t connect to other nodes lateron."
  #NoSeedError:
  DetailPrint "Exporting the node reference to MyOwn.ref"
  ExecWait "$INSTDIR\fservew --export myOwn.ref"
  IfErrors ExportError NoExportError
  ExportError:
  MessageBox MB_OK "There was an error while exporting your own reference file. This is a noncritical error."
  NoExportError:
  
  # successfully finished all the stuff in here, leave now
  Goto End

 CfgnodeError:
  BringToFront
  MessageBox MB_OK "There was an error while creating the Freenet configuration file. Do you really have Java already installed? Aborting installation now!"
  BringToFront
  Abort
 
 End:
SectionEnd
;---------------------------------------------------------------------------------------
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

;--------------------------------------------------------------------------------------

Section -PostInstall

  # Register .ref files to be added to seed.ref with a double-click
  WriteRegStr HKEY_CLASSES_ROOT ".ref" "" "Freenet_node_ref"
  WriteRegStr HKEY_CLASSES_ROOT "Freenet_node_ref\shell\open\command" "" '"$INSTDIR\freenet.exe" -import "%1"'
  WriteRegStr HKEY_CLASSES_ROOT "Freenet_node_ref\DefaultIcon" "" "$INSTDIR\freenet.exe,7"


  # Registering install path, so future installs will use the same path
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Freenet" "instpath" $INSTDIR

  # Registering the unistall information
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "DisplayName" "Freenet"
  WriteRegStr HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet" "UninstallString" '"$INSTDIR\Uninstall-Freenet.exe"'

  MessageBox MB_YESNO "Congratulations, you have finished the installation of Freenet successfully.$\r$\nDo you want to start your Freenet node now?" IDNO StartedNode
  Exec "$INSTDIR\freenet.exe"
  
StartedNode:
  Delete "$INSTDIR\cfgnode.exe"
  Delete "$INSTDIR\cfgclient.exe"      
  Delete "$INSTDIR\findjava.exe"
  RMDir /r "$TEMP/Freenet"
  MessageBox MB_OK "After starting Freenet a little rabbit icon will appear in the lower right corner of your screen (system tray)$\r$\n$\r$\nFreenet is running in this case, you can use it by:$\r$\n  1) Using special applications (i.e. Frost)$\r$\n  2) Right-clicking on the icon and select from the menu$\r$\n  3) Double-click on the icon to open the web gateway (same as Open Gateway from the menu)"
SectionEnd
;------------------------------------------------------------------------------------------

# Uninstall part begins here:
Section Uninstall

  #First trying to shut down the node, the system tray Window class is called: TrayIconFreenetClass
 ;ShutDown:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 StillRunning NotRunning 
 StillRunning:
  ;# Closing Freenet
  ;SendMessage $0 16 0 0
  MessageBox MB_OK "You are still running Freenet, please shut it down first"
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
  # show splashscreen
  ;SetOutPath $TEMP
  ;File /oname=spltmp.bmp "splash.bmp"
  ;File /oname=spltmp.wav "splash.wav"
  ;File /oname=spltmp.exe "splash.exe"
  ;ExecWait '"$TEMP\spltmp.exe" 1000 $HWNDPARENT $TEMP\spltmp'
  ;Delete $TEMP\spltmp.exe
  ;Delete $TEMP\spltmp.bmp
  ;Delete $TEMP\spltmp.wav

  #Is the node still running? The system tray Window class is called: TrayIconFreenetClass
 ;ShutDown:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 StillRunning NotRunning 
 StillRunning:
  # Closing Freenet
  ;SendMessage $0 16 0 0
  MessageBox MB_OK "You are still running Freenet, please shut it down first"
  Abort
 NotRunning:
FunctionEnd
;-----------------------------------------------------------------------------------------

Function .onInstFailed
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Freenet"
  RMDir /r $INSTDIR
  RMDir /r "$TEMP/Freenet"
FunctionEnd

;-----------------------------------------------------------------------------------------