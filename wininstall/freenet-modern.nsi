; Global notes:
; $3 is used to store a bitmap of the files installed.  A bit is CLEAR if the file was found in the
; current directory (i.e. the directory in which the installer was run) or SET if
; the file was NOT found.  This is then used later to determine which files were not found in the current
; directory and therefore which must be extracted from the archive or downloaded from the server as appropriate.
;
; The bits are:
; NodeConfig.exe      8
; freenet.exe         4
; freenet-ext.jar     2
; freenet.jar         1  - note this is downloaded as freenet-latest.jar but renamed locally

; Note that also seednodes.ref is extracted from current directory if present but that this is done completely silently
; and cannot be cancelled.  seednodes.ref does not affect $3 at all, nor does it affect the download phase, because
; seednodes.ref is not available for download.  Similarly some other files, e.g. README
;
; If you add more files to the snapshots folder, augment this install script accordingly
; e.g
; freenet.hlp         16
; etc
; You'll also need to be careful to update occurrences of 15 to 31, etc.  If you don't understand
; the code, let someone who does make the changes

; ---------------------------------------------------------------------

;!packhdr will further optimize your installer package if you have upx.exe in your directory
!packhdr temp.dat "upx.exe -9 temp.dat"

XPStyle on

!define MUI_PRODUCT "Freenet"
!define WEBINSTALL
!define BUILDDATE 20030218

!ifdef WEBINSTALL
  !define MUI_VERSION "Webinstall"
!else
  !define MUI_VERSION $BUILDDATE
!endif

!include "MUI.nsh"

;$9 is being used to store the Start Menu Folder.
;Do not use this variable in your script (or Push/Pop it)!

;To change this variable, use MUI_STARTMENU_VARIABLE.
;Have a look at the Readme for info about other options (default folder,
;registry).

!include "WinMessages.nsh"

;Remember the Start Menu Folder
!define MUI_STARTMENU_REGISTRY_ROOT "HKLM" 
!define MUI_STARTMENU_REGISTRY_KEY "Software\${MUI_PRODUCT}" 
!define MUI_STARTMENU_REGISTRY_VALUENAME "Start Menu Folder"

!define TEMP $R0

;--------------------------------
;Configuration

  ;General
  ;Installer name:
  # are we including Java?
  !ifdef embedJava
    OutFile "Freenet-Java-${MUI_VERSION}.exe"
  !else
    OutFile "Freenet-${MUI_VERSION}.exe"
  !endif

  ;Folder selection page
  InstallDir "$PROGRAMFILES\${MUI_PRODUCT}"

;--------------------------------
;Modern UI Configuration

  !ifdef WEBINSTALL
    !define MUI_ICON ".\Freenet-NET.ico"
    !define MUI_UNICON ".\Freenet-NET.ico"
  !else
    !define MUI_ICON ".\Freenet-CD.ico"
    !define MUI_UNICON ".\Freenet-CD.ico"
  !endif
  
  !define MUI_PROGRESSBAR smooth
    
  !define MUI_WELCOMEPAGE
  !define MUI_LICENSEPAGE
  !define MUI_COMPONENTSPAGE
  !define MUI_DIRECTORYPAGE
  !define MUI_STARTMENUPAGE
  !define MUI_FINISHPAGE
    !define MUI_FINISHPAGE_RUN "$INSTDIR\freenet.exe"  
  
  !define MUI_ABORTWARNING
  !define MUI_UNINSTALLER
  !define MUI_UNCONFIRMPAGE

  ;Modern UI System
  !insertmacro MUI_SYSTEM
  
;--------------------------------
;Languages
 
  !insertmacro MUI_LANGUAGE "English"
  
;--------------------------------
;Language Strings

  ;Description
  !ifdef WEBINSTALL
    LangString DESC_SecFreenetNode ${LANG_ENGLISH} "Downloads the latest Freenet Node software"
  !else
    LangString DESC_SecFreenetNode ${LANG_ENGLISH} "Install the Freenet Node software (required)"
  !endif
  LangString DESC_SecLocalLibInstall ${LANG_ENGLISH} "-" #hidden dummy, was "Checks the local directory for updated freenet software for manual installation (optional)"


;--------------------------------
;Data
  
  LicenseData ".\GNU.txt"

;--------------------------------
;Reserve Files

  ;Things that need to be extracted on first (keep these lines before any File command!)
  ;Only useful for BZIP2 compression
  !insertmacro MUI_RESERVEFILE_WELCOMEFINISHPAGE

;--------------------------------
;Installer Sections
; 1.  Local Lib Install
;     Installs the files builtinto the webinstaller
;     Also installs files in the current directory (if such files exist)
;     (Prompting user if such files actually exist)
Section "-Local Lib Install" SecLocalLibInstall # hidden

  IfFileExists "$INSTDIR\*.*" 0 NotNewDirectory
  # Create a flag that says whether this installer created the directory or not ...
  # .. if it did then it should delete the directory again when installation fails
  SetDetailsPrint none
  SetOutPath "$INSTDIR"
  FileOpen $R0 "$INSTDIR\__newdir__" "w"
  FileClose $R0
  SetDetailsPrint both

  NotNewDirectory:
  SetOutPath "$INSTDIR"

  WriteRegStr HKLM "Software\${MUI_PRODUCT}" "instpath" "$INSTDIR"

  IntOp $3 0 + 0 # set to zero - is there no other way?

  GetFullPathName /SHORT $1 $INSTDIR # convert INSTDIR into short form and put into $1
  GetFullPathName /SHORT $2 $EXEDIR # same for EXEDIR into $2
  GetFullPathName /SHORT $R0 $TEMP # same for TEMP into $R0

  # make sure the files we're downloading don't already exist in the temp dir:
  Delete "$R0\freenet-install\*.*"

  Call DetectJava

  # First of all see if we need to install the mfc42.dll
  # Each Win user should have it anyway
  IfFileExists "$SYSDIR\Mfc42.dll" MfcDLLExists
  !ifdef WEBINSTALL
    MessageBox MB_OK "You need to have Mfc42.dll in the directory $SYSDIR.$\r$\nIt is not included in this installer, so please download a regular snapshot first.$\r$\nAborting now..."
    Call AbortCleanup
  !else ifdef BUNDLEDMFC42
    DetailPrint "Installing Mfc42.dll"
    SetOutPath "$SYSDIR"
    File "Mfc42.dll"
    ClearErrors
    SetOutPath "$INSTDIR"
  !else
    MessageBox MB_OK "You need to have Mfc42.dll in the directory $SYSDIR.$\r$\nIt is not included in this installer, so please download a snapshot containing it.$\r$\nAborting now..."
    Call AbortCleanup
  !endif
  MfcDLLExists:
  
  IfFileExists "$INSTDIR\flaunch.ini" dontInstallFlaunch InstallFlaunch
  InstallFlaunch:
  File ".\Freenet\FLaunch.ini"
  dontInstallFlaunch:

  !ifdef WEBINSTALL
  # copy self to main directory
  CopyFiles "$2\freenet-webinstall.exe" "$INSTDIR\freenet-webinstall.exe"
  !else
  # monolithic installer includes a copy of webinstaller
  File ".\Freenet\tools\freenet-webinstall.exe"
  !endif

  # Look in current folder to see if there's any files in here
  # We make a bitmap of the files we've installed from the current folder, so we know NOT
  # to download those files (for webinstaller) or install those files from the archive
  # (for monolithic installer)
  IntOp $R1 0 + 0 # a flag to see if any local files are to be installed
  # Check that the install dir and the exec dir are not the same -
  # if they are then don't do this check
  IfFileExists "$INSTDIR\__dir__" 0 testlock1
  SetDetailsPrint none
  Delete "$INSTDIR\__dir__"
  SetDetailsPrint both
  testlock1:
  IfFileExists "$EXEDIR\__dir__" 0 testlock2
  SetDetailsPrint none
  Delete "$EXEDIR\__dir__"
  SetDetailsPrint both
  testlock2:
  FileOpen $R2 "$INSTDIR\__dir__" "w"
  FileClose $R2
  IfFileExists "$EXEDIR\__dir__" SkipLibInstallTest
  SetDetailsPrint none
  Delete "$INSTDIR\__dir__"
  SetDetailsPrint both
  # potentially test to see if the name of the current dir is "freenet-distribution"
  # I think that's a bit too limiting so I'm not going to do that
  #ifFileExists "$2\freenet-distribution\*.*" 0 FinishedLibInstall
  IfFileExists "$2\seednodes.ref" 0 seednotfound
  IntOp $R1 1 + 0 # set flag
  seednotfound:
  IfFileExists "$2\README" 0 readmenotfound
  IntOp $R1 1 + 0 # set flag
  readmenotfound:
  IfFileExists "$2\NodeConfig.exe" libFound4 NoLibFound4
  libFound4:
  IntOp $R1 1 + 0 # set flag
  IntOp $3 $3 + 8
  NoLibFound4:
  IfFileExists "$2\freenet.exe" libFound3 NoLibFound3
  libFound3:
  IntOp $R1 1 + 0 # set flag
  IntOp $3 $3 + 4
  NoLibFound3:
  IfFileExists "$2\freenet-ext.jar" libFound2 NoLibFound2
  libFound2:
  IntOp $R1 1 + 0 # set flag
  IntOp $3 $3 + 2
  NoLibFound2:
  IfFileExists "$2\freenet.jar" libFound1 NoLibFound1
  libFound1:
  IntOp $R1 1 + 0 # set flag
  IntOp $3 $3 + 1
  NoLibFound1:
  
  IntCmp $R1 0 FinishedLibInstall # no local files to install

  MessageBox MB_YESNO "Updated files were found in $EXEDIR - would you like to install these?" IDNO DontLibInstall

  # note - seednodes.ref is installed completely silently, if present
  IfFileExists "$2\seednodes.ref" 0 seednotinstalled
  CopyFiles "$2\seednodes.ref" "$INSTDIR\seednodes.ref"
  seednotinstalled:
  # note - updated README is installed completely silently, if present
  IfFileExists "$2\README" 0 readmenotinstalled
  CopyFiles "$2\README" "$INSTDIR\README"
  readmenotinstalled:

  SetDetailsPrint none
  SetOutPath "$R0\freenet-install"
  SetDetailsPrint both
  Push $3
  IntCmp $3 8 libInst4 NoLibInst4 libInst4
  libInst4:
  CopyFiles "$2\NodeConfig.exe" "$R0\freenet-install\NodeConfig.exe"
  #Delete "$2\NodeConfig.exe"
  IntOp $3 $3 - 8
  NoLibInst4:
  IntCmp $3 4 libInst3 NoLibInst3 libInst3
  libInst3:
  CopyFiles "$2\freenet.exe" "$R0\freenet-install\freenet.exe"
  #Delete "$2\freenet.exe"
  IntOp $3 $3 - 4
  NoLibInst3:
  IntCmp $3 2 libInst2 NoLibInst2 libInst2
  libInst2:
  CopyFiles "$2\freenet-ext.jar" "$R0\freenet-install\freenet-ext.jar"
  #Delete "$2\freenet-ext.jar"
  IntOp $3 $3 - 2
  NoLibInst2:
  IntCmp $3 1 libInst1 NoLibInst1 libInst1
  libInst1:
  CopyFiles "$2\freenet.jar" "$R0\freenet-install\freenet.jar"
  #Delete "$2\freenet.jar"
  IntOp $3 $3 - 1
  NoLibInst1:
  Pop $3

  IntCmp $3 15 FinishedLibInstall # if all files have been obtained from local, don't need to ask user if they want to download missing files

  MessageBox MB_YESNO "The $EXEDIR directory did not contain all the files needed for a full installation$\r$\nWould you like to download the missing files from The Free Network Project's server?$\r$\n(You may want to say NO to this if you already have a working Freenet installation)" IDYES FinishedLibInstall
  IntOp $3 15 + 0 # make download section believe that all files have been extracted from local so that no files are downloaded
  goto FinishedLibInstall

  DontLibInstall:
  IntOp $3 0 + 0 # zero $3
  MessageBox MB_YESNO "Would you like to download updated files from The Free Network Project's server instead?" IDYES FinishedLibInstall
  MessageBox MB_OK "Nothing to do, installation is complete"
  Abort

  SkipLibInstallTest:
  # used to clear up the temp file handle that we created earlier (to see if the install dir and the exec
  # dir are one and the same)
  SetDetailsPrint none
  Delete "$INSTDIR\__dir__"
  SetDetailsPrint both
  goto FinishedLibInstall

  FinishedLibInstall:

SectionEnd


; 2.  Freenet Node Install
;     For webinstall, downloads the files from freenetproject.org
;     (does NOT download any files it happened to find in the current directory if user asked to install those)
;     For monolithic install, extracts the files from the installer
;     (does NOT extract any files it happened to find in the current directory if user asked to install those)
Section "Freenet Node" SecFreenetNode

  GetFullPathName /SHORT $1 $INSTDIR # convert INSTDIR into short form and put into $1
  GetFullPathName /SHORT $2 $EXEDIR # same for EXEDIR into $2
  GetFullPathName /SHORT $R0 $TEMP # same for TEMP into $R0

  SetDetailsPrint none
  SetOutPath "$R0\freenet-install"
  SetDetailsPrint both

  IfFileExists "$INSTDIR\seednodes.ref" NoDownloadSeednodes
  IfFileExists "$R0\freenet-install\seednodes.ref" NoDownloadSeednodes
  # if "Don't Prompt Me" is selected the following message box will not appear and seed download will be automatic
  # ###TODO
  MessageBox MB_YESNO "To connect to the Freenet network, your Freenet node needs to know about at least one other Freenet node.$\r$\nThis is called a 'Node Reference' or 'seednodes.ref' file.$\r$\nDo you want to download 'seednodes.ref' from the Free Net Project's servers?$\r$\nYou may want to say NO if you have been given a .ref file by a friend,$\r$\nor if you have installed Freenet before and still have the file named seednodes.ref" IDNO NoDownloadSeedNodes
  Push "http://freenetproject.org/snapshots/seednodes.ref"
  Push "$R0\freenet-install"
  Push "seednodes.ref"
  Call RetryableDownload
  StrCmp $0 "success" seedsuccess
  MessageBox MB_YESNO "Couldn't download seednodes.ref - Without this file Freenet will not work.$\r$\nDo you want to continue installation anyway?  (You will still need to download seednodes.ref yourself)" IDYES NoDownloadSeedNodes
  Call AbortCleanup

  seedsuccess:
  ClearErrors

  NoDownloadSeedNodes:

  IntOp $4 $3 + 0 # save $3 in $4 (although it isn't used beyond this point ... yet...)
  IntCmp $3 8 NoInstall4 Install4 NoInstall4
  Install4:
  !ifdef WEBINSTALL
    Push "http://freenetproject.org/snapshots/NodeConfig.exe"
    Push "$R0\freenet-install"
    Push "NodeConfig.exe"
    Call CheckedDownload
  !else
    ClearErrors
    File ".\Freenet\tools\NodeConfig.exe" /ofile="$R0\freenet-install\NodeConfig.exe"
    IfErrors TestPreExistingFailedExtract4 Success4
    TestPreExistingFailedExtract4:
    IfFileExists "$INSTDIR\NodeConfig.exe" PreExistingExtract4 DiskWriteError
    PreExistingExtract4:
    MessageBox MB_YESNO "Couldn't extract NodeConfig.exe - Continue with installation?$\r$\n(This will use your pre-existing NodeConfig.exe)" IDNO DiskWriteError
  !endif
  goto Test3
  NoInstall4:
  IntOp $3 $3 - 8
  Test3:
  IntCmp $3 4 NoInstall3 Install3 NoInstall3
  Install3:
  !ifdef WEBINSTALL
    Push "http://freenetproject.org/snapshots/freenet.exe"
    Push "$R0\freenet-install"
    Push "freenet.exe"
    Call CheckedDownload
  !else
    ClearErrors
	File ".\Freenet\tools\freenet.exe" /ofile="$R0\freenet-install\freenet.exe"
    IfErrors TestPreExistingFailedExtract3 Success3
	TestPreExistingFailedExtract3:
	IfFileExists "$INSTDIR\freenet.exe" PreExistingExtract3 DiskWriteError
	PreExistingExtract3:
	MessageBox MB_YESNO "Couldn't extract freenet.exe - Continue with installation?$\r$\n(This will use your pre-existing freenet.exe)" IDNO DiskWriteError
  !endif
  goto Test2
  NoInstall3:
  IntOp $3 $3 - 4
  Test2:
  IntCmp $3 2 NoInstall2 Install2 NoInstall2
  Install2:
  !ifdef WEBINSTALL
    Push "http://freenetproject.org/snapshots/freenet-ext.jar"
    Push "$R0\freenet-install"
    Push "freenet-ext.jar"
    Call CheckedDownload
  !else
    ClearErrors
	File ".\Freenet\jar\freenet-ext.jar" /ofile="$R0\freenet-install\freenet-ext.jar"
    IfErrors TestPreExistingFailedExtract2 Success2
	TestPreExistingFailedExtract2:
	IfFileExists "$INSTDIR\freenet-ext.jar" PreExistingExtract2 DiskWriteError
	PreExistingExtract2:
	MessageBox MB_YESNO "Couldn't extract freenet-ext.jar - Continue with installation?$\r$\n(This will use your pre-existing freenet-ext.jar)" IDNO DiskWriteError
  !endif
  goto Test1
  NoInstall2:
  IntOp $3 $3 - 2
  Test1:
  IntCmp $3 1 NoInstall1 Install1 NoInstall1
  Install1:
  !ifdef WEBINSTALL
    Push "http://freenetproject.org/snapshots/freenet-latest.jar"
    Push "$R0\freenet-install"
    Push "freenet.jar"
    Call CheckedDownload
  !else
    ClearErrors
	File ".\Freenet\jar\freenet.jar" /ofile="$R0\freenet-install\freenet.jar"
    IfErrors TestPreExistingFailedExtract1 Success1
	TestPreExistingFailedExtract1:
	IfFileExists "$INSTDIR\freenet.jar" PreExistingExtract1 DiskWriteError
	PreExistingExtract1:
	MessageBox MB_YESNO "Couldn't extract freenet.jar - Continue with installation?$\r$\n(This will use your pre-existing freenet.jar)" IDNO DiskWriteError
  !endif
  goto EndTests
  NoInstall1:
  IntOp $3 $3 - 1
  EndTests:
  IntOp $3 $4 + 0 # restore $3 from $4

  # when we get here, the stack contains a list of files that have been successfully downloaded
  # or extracted and that must be copied over the existing freenet files.
  # Step 1- stop freenet
  CheckRunning:
  FindWindow $0 "TrayIconFreenetClass"
  IsWindow $0 StillRunning NotRunning 
  StillRunning:
  MessageBox MB_OKCANCEL "Installer now needs to stop Freenet to complete the installation.$\r$\nYou are still running Freenet.  Please shut it down then click OK" IDCANCEL Cancel
  goto CheckRunning
  NotRunning:
  # Step 2- copy the files
  SetOutPath "$INSTDIR"
  ClearErrors
  CopyFiles "$R0\freenet-install\*.*" "$INSTDIR"
  IfErrors DiskWriteError
  # Step 3- Merge ini files
  # Step 3a - create a default .ini file
  IfFileExists "$INSTDIR\default.ini" 0 NoFreenetIniDefaults
  Delete "$INSTDIR\default.ini" # prevents freenet node from trying to 'update' an existing config (experience shows it will cock it up horribly)
  NoFreenetIniDefaults:
  ExecWait "$1\freenet.exe -createconfig $1\default.ini"
  # Step 3b - Merge the existing .ini with the defaults, or use the defaults if there is no existing .ini
  IfFileExists "$INSTDIR\freenet.ini" MergeIniStuff
  CopyFiles "$INSTDIR\default.ini" "$INSTDIR\freenet.ini"
  goto DoneMergeIniStuff
  MergeIniStuff:
  ExecWait "$1\freenet.exe -mergeconfig $1\freenet.ini $1\default.ini"
  DoneMergeIniStuff:

  !insertmacro MUI_STARTMENU_WRITE_BEGIN
    
    ;Create shortcuts
    SetOutPath "$INSTDIR"
    CreateDirectory "$SMPROGRAMS\${MUI_STARTMENU_VARIABLE}"
    CreateShortCut "$SMPROGRAMS\${MUI_STARTMENU_VARIABLE}\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$1\freenet.exe" 0
    CreateShortCut "$SMPROGRAMS\${MUI_STARTMENU_VARIABLE}\Uninstall.lnk" "$INSTDIR\Uninstall.exe" "" "$1\Uninstall.exe" 0
    WriteINIStr "$SMPROGRAMS\${MUI_STARTMENU_VARIABLE}\Freenet Homepage.url" "InternetShortcut" "URL" "http://www.freenetproject.org"
    CreateShortcut "$SMPROGRAMS\${MUI_STARTMENU_VARIABLE}\Update Snapshot.lnk" "$INSTDIR\freenet-webinstall.exe" "" "$1\freenet-webinstall.exe" 0

    ; launch freenet on Startup - this will be setup automatically if user asks for Start Menu options
    CreateShortCut "$SMSTARTUP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$1\freenet.exe" 0
    ; Desktop icon - this will be setup automatically if user asks for Start Menu options
    CreateShortCut "$DESKTOP\Freenet.lnk" "$INSTDIR\freenet.exe" "" "$1\freenet.exe" 0

    ;Write shortcut location to the registry (for Uninstaller)
    WriteRegStr HKLM "Software\${MUI_PRODUCT}" "Start Menu Folder" "${MUI_STARTMENU_VARIABLE}"

  !insertmacro MUI_STARTMENU_WRITE_END
  
  ;Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  goto InstComplete

  DiskWriteError:
  Call funDiskWriteError
  
  Cancel:
  MessageBox MB_OK "Installation Cancelled"
  Call AbortCleanup

  InstComplete:
  Delete "$R0\freenet-install\*.*"
  RMDir "$R0\freenet-install"

  ; Also tidy up 'old' files from target folder
  Delete "$INSTDIR\UpdateSnapshot.exe"
  Delete "$INSTDIR\FindJava.exe"

  ; Associated with .ref files:
  Push $R0
  ReadRegStr $R0 HKEY_CLASSES_ROOT ".ref" ""
  StrCmp $R0 "" DoRefs
  StrCmp $R0 "Freenet_node_ref" DoRefs
  StrCmp $R0 "Freenet node reference" DoRefs
  goto DontDoRefs ; already associated with something else so don't clobber
  DoRefs:
  WriteRegStr HKEY_CLASSES_ROOT ".ref" "" "Freenet node reference"
  WriteRegStr HKEY_CLASSES_ROOT "Freenet node reference\shell\open\command" "" '"$INSTDIR\freenet.exe" -import "%1"'
  WriteRegStr HKEY_CLASSES_ROOT "Freenet node reference\DefaultIcon" "" "$INSTDIR\freenet.exe,6"
  DeleteRegKey HKEY_CLASSES_ROOT "Freenet_node_ref"
  DontDoRefs:
  Pop $R0

  ; finally remove the 'flag' file if we created it:
  SetDetailsPrint none
  Delete "$INSTDIR\__dir__"
  Delete "$INSTDIR\__newdir__"
  ClearErrors
  SetDetailsPrint both

SectionEnd

;--------------------------------
; Section end
;Display the Finish header
;Insert this macro after the sections if you are not using a finish page
#!insertmacro MUI_SECTIONS_FINISHHEADER

;--------------------------------
;Descriptions

!insertmacro MUI_FUNCTIONS_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecLocalLibInstall} $(DESC_SecLocalLibInstall)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecFreenetNode} $(DESC_SecFreenetNode)
!insertmacro MUI_FUNCTIONS_DESCRIPTION_END
 
;--------------------------------
;Uninstaller Section

Function un.onInit
  ReadRegStr $0 HKLM "Software\${MUI_PRODUCT}" "instpath"
  StrCmp $0 "" NoInstpath
  StrCpy $INSTDIR $0
  NoInstPath:
FunctionEnd

Section "Uninstall"

  GetFullPathName /SHORT $1 $INSTDIR # convert INSTDIR into short form and put into $1
  GetFullPathName /SHORT $2 $EXEDIR # same for EXEDIR into $2

  # Step 1- stop freenet
  CheckRunning:
  FindWindow $R0 "TrayIconFreenetClass"
  IsWindow $R0 StillRunning NotRunning 
  StillRunning:
  MessageBox MB_OKCANCEL "Freenet is still running.  You will need to stop it to uninstall Freenet.$\r$\nPlease shut it down then click OK" IDCANCEL Cancel
  goto CheckRunning
  NotRunning:

  Delete "$INSTDIR\freenet.exe"
  Delete "$INSTDIR\NodeConfig.exe"
  Delete "$INSTDIR\freenet-ext.jar"
  Delete "$INSTDIR\freenet.jar"
  Delete "$INSTDIR\freenet-webinstall.exe"
  Delete "$INSTDIR\README"
  Delete "$INSTDIR\default.ini"
  Delete "$INSTDIR\freenet.log"
  Delete "$INSTDIR\node-temp"
  Delete "$INSTDIR\install.log"
  Delete "$INSTDIR\fserve.exe"
  Delete "$INSTDIR\fservew.exe"
  Delete "$INSTDIR\fclient.exe"
  Delete "$INSTDIR\cfgclient.exe"
  Delete "$INSTDIR\COPYING.TXT"
  Delete "$INSTDIR\docs\freenet.hlp"
  Delete "$INSTDIR\docs\freenet.gid"
  Delete "$INSTDIR\docs\freenet.cnt"
  Delete "$INSTDIR\docs\readme.txt"
  RMDir /r "$INSTDIR\stats"
  RMDir /r "$INSTDIR\distrib"
  RMDir "$INSTDIR\docs"
  
  ;Remove shortcut
  ReadRegStr ${TEMP} HKLM "Software\${MUI_PRODUCT}" "Start Menu Folder"
  StrCpy ${TEMP} "$SMPROGRAMS\${TEMP}"
  GetFullPathName /SHORT ${TEMP} ${TEMP} # convert to short filename

  ; Disassociated from .ref files:
  Push $R0
  ReadRegStr $R0 HKEY_CLASSES_ROOT ".ref" ""
  StrCmp $R0 "" DontDisassociateRefs
  StrCmp $R0 "Freenet_node_ref" DisassociateRefs
  StrCmp $R0 "Freenet node reference" DisassociateRefs
  goto DontDisassociateRefs
  DisassociateRefs:
  WriteRegStr HKEY_CLASSES_ROOT ".ref" "" "Freenet node reference"
  WriteRegStr HKEY_CLASSES_ROOT "Freenet node reference\shell\open\command" "" '"$INSTDIR\freenet.exe" -import "%1"'
  WriteRegStr HKEY_CLASSES_ROOT "Freenet node reference\DefaultIcon" "" "$INSTDIR\freenet.exe,7"
  DeleteRegKey HKEY_CLASSES_ROOT "Freenet_node_ref"
  DontDisassociateRefs:
  Pop $R0
  
  StrCmp ${TEMP} "" noshortcuts
  
    Delete "${TEMP}\Freenet.lnk"
    Delete "${TEMP}\Uninstall.lnk"
    Delete "${TEMP}\Freenet Homepage.url"
    Delete "${TEMP}\Update Snapshot.lnk"
    Delete "$SMSTARTUP\Freenet.lnk"
    Delete "$DESKTOP\Freenet.lnk"

    RMDir "${TEMP}" ;Only if empty, so it won't delete other shortcuts
    
  noshortcuts:

  MessageBox MB_YESNO "Would you like to uninstall your node configuration and datastore?$\r$\nWARNING- you may want to say NO if you intend to reinstall Freenet in the future$\r$\nThis operation is irreversible and will delete your node's entire datastore and configuration" IDNO DontKillEverything

  # NOTE this does NOT just kill *.*.  It selective deletes what's installed.  This avoids the luser-deleting-
  # all-the-mpegs-he-put-in-his-datastore-folder problem...
  Delete "$INSTDIR\flaunch.ini"
  Delete "$INSTDIR\freenet.ini"
  Delete "$INSTDIR\seednodes.ref"
  Delete "$INSTDIR\prng.seed"
  RMDir /r "$INSTDIR\store"
  Delete "$INSTDIR\lsnodes*"
  Delete "$INSTDIR\rtnodes*"
  Delete "$INSTDIR\rtprops*"
  DeleteRegValue HKLM "Software\${MUI_PRODUCT}" "Start Menu Folder"
  DeleteRegValue HKLM "Software\${MUI_PRODUCT}" "instpath"
  
  DontKillEverything:

  Delete "$INSTDIR\Uninstall.exe"

  RMDir "$INSTDIR"  ; only if empty, so it won't delete the Freenet folder if it's got user stuff in it
  
  DeleteRegKey /ifempty HKLM "Software\${MUI_PRODUCT}"

  ;Display the Finish header
  !insertmacro MUI_UNFINISHHEADER

  Cancel:

SectionEnd



Function DetectJava
# this function detects Sun Java from registry and calls the JavaFind utility otherwise

  # Save $0, $2, $5 and $6
  Push $0
  Push $2
  Push $5
  Push $6
  
  # This is the first time we run the JavaFind
  StrCpy $5 "No"

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
  SetOutPath "$INSTDIR"
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

  GetFullPathName /SHORT $R0 $TEMP # get short version of TEMP into $R0
  SetDetailsPrint none
  SetOutPath "$R0\freenet-install"
  SetDetailsPrint both
  !ifdef embedJava
    # Install Java runtime only if not found
    DetailPrint "Lauching Sun's Java Runtime Environment installation..."
    File ${JAVAINSTALLER}
    ExecWait "$TEMP\${JAVAINSTALLER}"
    Delete "$TEMP\${JAVAINSTALLER}"
    Goto StartCheck
  !else
    !ifdef WEBINSTALL
      DetailPrint "Downloading Java Runtime Environment ..."
      Push "http://freenetproject.org/snapshots/jre-win32-latest.exe"
      Push "$R0\freenet-install"
      Push "jre-win32-latest.exe"
      Call RetryableDownload
      StrCmp $0 "success" jredownloadsuccess
      MessageBox MB_OK "I could not find a Java Runtime Environment installed and I couldn't download one from$\r$\nThe Free Net Project's servers.  Installation has failed.  You could try downloading$\r$\nJava Runtime Environment ('JRE') from http://java.sun.com/getjava/ or you could just try running$\r$\nthis installer again later.  Click OK to cancel this installation"
      call AbortCleanup
      jredownloadsuccess:
      DetailPrint "Installing JAVA ..."
      ExecWait "$R0\freenet-install\jre-win32-latest.exe"
      Delete "$R0\freenet-install\jre-win32-latest.exe"
    !endif
  !endif
      
  goto RunJavaFind

  AbortJava:
  MessageBox MB_OK|MB_ICONSTOP "I still can't find any Java interpreter. Did you really installed the JRE?$\r$\nInstallation will now stop."
  Call AbortCleanup
  
End:
  # Restore $0, $2, $5 and $6
  Pop $6
  Pop $5
  Pop $2
  Pop $0
FunctionEnd


Function AbortCleanup

    SetDetailsPrint both
    DetailPrint "Aborted installation - Cleaning up any downloaded files..."
    GetFullPathName /SHORT $R0 $TEMP # get short version of TEMP into $R0
    Delete "$R0\freenet-install\*.*"
    RMDir "$R0\freenet-install"

    # also check instdir:  we created a flag at the start of the installation if this was a new folder:
    IfFileExists "$INSTDIR\__newdir__" 0 NotNewDir
    # flag exists, meaning we created the $INSTDIR folder, so we delete the $INSTDIR folder
    SetDetailsPrint none
    Delete "$INSTDIR\__newdir__"
    SetDetailsPrint both
    Delete "$INSTDIR\*.*"
    ClearErrors
    SetDetailsPrint none
    RMDir "$INSTDIR" # Not using /r because we don't want to remove INSTDIR if it contained subdirs.
    SetDetailsPrint both
    IfErrors NotNewDir # this is because RMDir will say "Removed $dir" even if it DIDN'T.  Don't want to worry user!
    DetailPrint "Removed $INSTDIR"
    NotNewDir:

    DetailPrint "Done cleaning up"

    # Change "cancel" button caption to "close"
    # ... left as an exercise :-)
   
    Abort
FunctionEnd


Function funDiskWriteError

  MessageBox MB_OK "Unable to write a file to disk.  Check that the disk is not full, or write-protected,$\r$\nand that you have the rights to install files.  The installer will now exit."
  Call AbortCleanup

FunctionEnd

; -------------------------------
; callbacks
; Set up install path (by defaults installs to same location as existing installation if present)
Function .onInit
  ReadRegStr ${TEMP} HKLM "Software\${MUI_PRODUCT}" "instpath"
  StrCmp ${TEMP} "" NoInstpath
  StrCpy $INSTDIR ${TEMP}
  goto onInitEnd
  NoInstPath:
  ReadRegStr ${TEMP} HKCU "Software\${MUI_PRODUCT}" "instpath"
  StrCmp ${TEMP} "" onInitEnd
  StrCpy $INSTDIR ${TEMP}
  onInitEnd:
FunctionEnd


Function CheckedDownload

    POP $R2 # local filename
    POP $R3 # local dir
    POP $R4 # remote dir+filename

    PUSH $R4
    PUSH $R3
    PUSH $R2
    Call CheckedDownload
    StrCmp $0 "aborted" aborted
    goto end

    Aborted:
    Call AbortCleanup

    end:

FunctionEnd


Function RetryableDownload

    POP $R2 # local filename
    POP $R3 # local dir
    POP $R4 # remote dir+filename

    StrCpy $R5 "$R3\$R2"

    DetailPrint "Downloading $R2 from $R4 ..."
    DoDownload:
    NSISdl::download /TIMEOUT=150000 "$R4" "$R5"
    StrCmp $0 "success" Success
    DetailPrint "Download of $R2 failed: $0"
    SetDetailsPrint none
    Delete "$R5"
    SetDetailsPrint both
    MessageBox MB_YESNO "Download of $R2 failed: '$0'. Retry?" IDYES Retry
    IfFileExists "$INSTDIR\$R2" PreExistingDownload
    goto Aborted
    PreExistingDownload:
    MessageBox MB_YESNO "Download of $R2 failed - Continue with installation?$\r$\n(This will use your pre-existing $R2 file)" IDYES NoFailed
    goto Aborted

    Retry:
    DetailPrint "Retrying download of $R2 ..."
    goto DoDownload

    NoFailed:
    DetailPrint "Using preexisting $R2"
    StrCpy $0 "preexisting"
    goto end

    Success:
    DetailPrint "Downloaded $R2 successfully"
    StrCpy $0 "success"
    goto end

    Aborted:
    DetailPrint "Download of $R2 aborted"
    StrCpy $0 "aborted"
    goto end

    end:

FunctionEnd