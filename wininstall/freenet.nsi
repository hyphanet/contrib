;uncomment on of the two next lines, depending on the type of installer you are building
!define VERSION "webinstall"
!define WEBINSTALL

!define NUM "0.5.1.pre1-1"
!define MAJOR "0.5"

#!define BUNDLEDMFC42
#!define LANGUAGE "Dutch"

# include language specific installer data
!ifdef LANGUAGE
  !include "${LANGUAGE}.inc"
!else
  !include "Def_lang.inc"
!endif
# are we including Java?
!ifdef embedJava
  OutFile "freenet-Java-${NUM}.exe"
!else ifdef WEBINSTALL
  OutFile "freenet-webinstall-${NUM}.exe"
!else
  OutFile "freenet-${NUM}.exe"
!endif

# webinstall specific stuff
!ifdef WEBINSTALL
  Name "Freenet webinstaller - packaged for freenet ${NUM}"
  !include "webinstall.inc"
!else
  Name "Freenet ${NUM}"
!endif

LicenseData GNU.txt

 InstType Minimal
 InstType Normal

;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "upx.exe -9 temp.dat"

InstallDir "$PROGRAMFILES\Freenet ${MAJOR}"
InstallDirRegKey HKEY_LOCAL_MACHINE "Software\Freenet" "instpath"
ShowInstDetails show
InstProgressFlags smooth
EnabledBitmap lucas-checked.bmp
DisabledBitmap lucas-unchecked.bmp
#CheckBitmap "checked.bmp"
#BGGradient
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

  LogSet on

  #make a 8.3 name
  GetFullPathName /SHORT $7 $INSTDIR

  # First of all see if we need to install the mfc42.dll
  # Each Win user should have it anyway
  IfFileExists "$SYSDIR\Mfc42.dll" MfcDLLExists
  !ifdef WEBINSTALL
    MessageBox MB_OK "You need to have Mfc42.dll in the directory $SYSDIR.$\r$\nIt is not included in this installer, so please download a regular snapshot first.$\r$\nAborting now..."
    Abort
  !else ifdef BUNDLEDMFC42
    DetailPrint "Installing Mfc42.dll"
    SetOutPath "$SYSDIR"
    File "Mfc42.dll"
    ClearErrors
  !else
    MessageBox MB_OK "You need to have Mfc42.dll in the directory $SYSDIR.$\r$\nIt is not included in this installer, so please download a snapshot containing it.$\r$\nAborting now..."
    Abort
  !endif
  MfcDLLExists:
  
  # Always save a copy of the old fserve.ini
  CopyFiles "$INSTDIR\flaunch.ini" "$INSTDIR\flaunch.old"

  # create the configuration file now
  # set the diskstoresize to 0 to tell NodeConfig, to propose a value later on
  IfFileExists "$INSTDIR\freenet.ini" iniFileExisted
  Goto doCopyFiles

  iniFileExisted:
    MessageBox MB_YESNO "There is already have a freenet.ini - Keep the settings?" IDNO doCopyFiles
    CopyFiles "$INSTDIR\freenet.ini" "$INSTDIR\freenet.ini.install"

  doCopyFiles:  
  # Copying the Freenet files to the install dir
  DetailPrint "Copying Freenet files"
  SetOutPath "$INSTDIR\docs"
  File "freenet\docs\*.*"
  SetOutPath "$INSTDIR"
  # copying the temporary install and config utilities now
  File freenet\tools\*.*
  # copying the real Freenet files now
  File freenet\*.*
  WriteINIStr "$INSTDIR\freenet.ini" "Freenet Node" "storeSize" "0"
  WriteUninstaller "Uninstall-Freenet.exe"

  IfFileExists "$INSTDIR\freenet.ini.install" 0 checkForFiles
    Delete "$INSTDIR\freenet.ini"
    Rename "$INSTDIR\freenet.ini.install" "$INSTDIR\freenet.ini"
  
  checkForFiles:
  #StrStr $EXEDIR "freenet-distribution" distribution not-distribution
  Push $EXEDIR
  Push "freenet-distribution"
  Call StrStr
  Pop $0
  MessageBox MB_OK "Got $0"
  StrCmp $0 "freenet-distribution" distribution not-distribution
  distribution:
    CopyFiles "$EXEDIR\freenet.jar" "$INSTDIR\freenet.jar"
    CopyFiles "$EXEDIR\freenet-ext.jar" "$INSTDIR\freenet-ext.jar"
    CopyFiles "$EXEDIR\seednodes.ref" "$INSTDIR\seednodes.ref"
    CopyFiles "$EXEDIR\*.exe" "$INSTDIR\"
  IfErrors checkedForFiles getFilesDone

  not-distribution:
  
  # look if there is a freenet.jar and an ext-freenet.jar in the same directory and use this
  IfFileExists "$EXEDIR\lib\freenet.jar" 0 checkedForFiles

  # copy the local files and use these instead
  CopyFiles "$EXEDIR\lib\freenet.jar" "$INSTDIR\freenet.jar"
  CopyFiles "$EXEDIR\lib\freenet-ext.jar" "$INSTDIR\freenet-ext.jar"
  IfErrors removeJars
  CopyFiles "$EXEDIR\seednodes.ref" "$INSTDIR\seednodes.ref"
  ClearErrors

  Goto getFilesDone

  removeJars:
    Delete "$INSTDIR\freenet.jar"
    Delete "$INSTDIR\freenet-ext.jar"
    ClearErrors

 checkedForFiles:
  !ifdef WEBINSTALL
    # download the necessary files
    AddSize 1500 ; add 1500kb for Freenet.jar
    AddSize 100 ; add 100kb for freenet-ext.jar
    SetOutPath "$TEMP\Freenet"
    File nsisdl.dll
    SetOutPath "$INSTDIR"
    StrCpy $1 "http://freenetproject.org/snapshots/freenet-latest.jar"
    StrCpy $2 "$INSTDIR\freenet.jar.new"
    Call DownloadFile
    StrCpy $1 "http://freenetproject.org/snapshots/freenet-ext.jar"
    StrCpy $2 "$INSTDIR\freenet-ext.jar.new"
    Call DownloadFile
    Delete "$INSTDIR\freenet.jar"
    Delete "$INSTDIR\freenet-ext.jar"
    ClearErrors
    Rename "$INSTDIR\freenet.jar.new" "$INSTDIR\freenet.jar"
    Rename "$INSTDIR\freenet-ext.jar.new" "$INSTDIR\freenet-ext.jar"
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
   CreateDirectory "$SMPROGRAMS\Freenet ${MAJOR}"
   CreateShortCut "$SMPROGRAMS\Freenet ${MAJOR}\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$INSTDIR\freenet.exe" 0
   WriteINIStr "$SMPROGRAMS\Freenet ${MAJOR}\FN Homepage.url" "InternetShortcut" "URL" "http://www.freenetproject.org"  
   ;WriteINIStr "$SMPROGRAMS\Freenet ${MAJOR}\FNGuide.url" "InternetShortcut" "URL" "http://www.freenetproject.org/quickguide" 
   CreateShortcut "$SMPROGRAMS\Freenet ${MAJOR}\Update Snapshot.lnk" "$INSTDIR\UpdateSnapshot.exe" "" "" 0
   CreateShortCut "$SMPROGRAMS\Freenet ${MAJOR}\Uninstall.lnk" "$INSTDIR\Uninstall-Freenet.exe" "" "$INSTDIR\Uninstall-Freenet.exe" 0
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
  # don't let cfgnode change the storeSize
  ReadINIStr $8 "$INSTDIR\freenet.ini" "Freenet Node" "storeSize"
  IfErrors noStoreSize

  runCfgNode:
  # $7 should be a shorter name for the file
  ExecWait '"$7\cfgnode.exe" freenet.ini --silent'
  IfErrors CfgnodeError
  WriteINIStr "$INSTDIR\freenet.ini" "Freenet Node" "storeSize" $8
  # now calling the GUI configurator
  ExecWait "$7\NodeConfig.exe"
  
 
  # Seeding the initial references
  # we need to check the existence of seed.ref here and fail if it does not exist.
  # do the seeding and export our own ref file
  #ClearErrors
  #ExecWait "$7\fserve --seed seed.ref"
  #IfErrors SeedError NoSeedError
  #SeedError:
  #MessageBox MB_OK "There was an error while seeding your node. This might mean that you can´t connect to other nodes lateron."
  #NoSeedError:
  Rename myOwn.ref myOld.ref
  ClearErrors
  DetailPrint "Exporting the node reference to MyOwn.ref"
  ExecWait "$7\fservew --export myOwn.ref"
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

 noStoreSize:
  ClearErrors
  StrCpy $8 "0"
  Goto runCfgNode
 
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
  Delete "$INSTDIR\flaunch.old"
  RMDir /r "$TEMP\Freenet"
  MessageBox MB_OK "After starting Freenet a little rabbit icon will appear in the lower right corner of your screen (system tray)$\r$\n$\r$\nFreenet is running in this case, you can use it by:$\r$\n  1) Using special applications (i.e. Frost)$\r$\n  2) Right-clicking on the icon and select from the menu$\r$\n  3) Double-click on the icon to open the web gateway (same as Open Gateway from the menu)"
SectionEnd
;------------------------------------------------------------------------------------------

# Uninstall part begins here:
Section Uninstall
  MessageBox MB_YESNO|MB_DEFBUTTON2 "Are you sure you want to remove freenet" IDNO DoNotDelete

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
  RMDir /r "$SMPROGRAMS\Freenet ${MAJOR}"

  #delete "$SMPROGRAMS\Start FProxy.lnk"
  #Delete "$QUICKLAUNCH\Start FProxy.lnk"

  DoNotDelete:
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
  MessageBox MB_YESNO|MB_DEFBUTTON2 "Do you want to remove the failed installation" IDNO DoNotDelete

  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Microsoft\Windows\CurrentVersion\Uninstall\Freenet"
  DeleteRegKey HKEY_LOCAL_MACHINE "Software\Freenet"
  RMDir /r $INSTDIR

 DoNotDelete:
 #if we failed, copy old fserve.log over (since cfgnode probably wasn't run -- nonworking install)
  IfFileExists "$INSTDIR\flaunch.old" 0 DeleteRemaining
  Delete "$INSTDIR\flaunch.ini"
  Rename "$INSTDIR\flaunch.old" "$INSTDIR\flaunch.ini"

 DeleteRemaining:
  Delete $INSTDIR\freenet.jar.new
  Delete $INSTDIR\freenet-ext.jar.new
  RMDir /r "$TEMP\Freenet"
FunctionEnd

;-----------------------------------------------------------------------------------------

;------------------------------------------------------------------------------
; StrStr
; input, top of stack = string to search for
;        top of stack-1 = string to search in
; output, top of stack (replaces with the portion of the string remaining)
; modifies no other variables.
;
; Usage:
;   Push "this is a long ass string"
;   Push "ass"
;   Call StrStr
;   Pop $0
;  ($0 at this point is "ass string")

Function StrStr
  Exch $1 ; st=haystack,old$1, $1=needle
  Exch    ; st=old$1,haystack
  Exch $2 ; st=old$1,old$2, $2=haystack
  Push $3
  Push $4
  Push $5
  StrLen $3 $1
  StrCpy $4 0
  ; $1=needle
  ; $2=haystack
  ; $3=len(needle)
  ; $4=cnt
  ; $5=tmp
  loop:
    StrCpy $5 $2 $3 $4
    StrCmp $5 $1 done
    StrCmp $5 "" done
    IntOp $4 $4 + 1
    Goto loop
  done:
  StrCpy $1 $2 "" $4
  Pop $5
  Pop $4
  Pop $3
  Pop $2
  Exch $1
FunctionEnd
