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

  SetOutPath "$INSTDIR"
  WriteRegStr HKLM "Software\${MUI_PRODUCT}" "instpath" "$INSTDIR"

  Call DetectJava

  IntOp $3 0 + 0 # set to zero - is there no other way?

  GetFullPathName /SHORT $1 $INSTDIR # convert INSTDIR into short form and put into $1
  GetFullPathName /SHORT $2 $EXEDIR # same for EXEDIR into $2
  GetFullPathName /SHORT $R0 $TEMP # same for TEMP into $R0

  # make sure the files we're downloading don't already exist in the temp dir:
  Delete "$R0\freenet-install\*.*"

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
  Delete "$INSTDIR\__dir__"
  testlock1:
  IfFileExists "$EXEDIR\__dir__" 0 testlock2
  Delete "$EXEDIR\__dir__"
  testlock2:
  FileOpen $R2 "$INSTDIR\__dir__" "w"
  IfFileExists "$EXEDIR\__dir__" SkipLibInstallTest
  FileClose $R2
  Delete "$INSTDIR\__dir__"
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
  FileClose $R2
  Delete "$INSTDIR\__dir__"
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

  SetOutPath "$R0\freenet-install"

  !ifdef WEBINSTALL
    Push $3 # so we can retrieve it, as here we will modify $3
	# Note, must call in DESCENDING order
    IntCmp $3 8 setSize4 NoSetSize4 setSize4
    setSize4:
    AddSize 32 ; add 32kb for NodeConfig.exe
    IntOp $3 $3 - 8
    NoSetSize4:
    IntCmp $3 8 setSize3 NoSetSize3 setSize3
    setSize3:
	AddSize 32 ; add 32kb for freenet.exe
    IntOp $3 $3 - 4
    NoSetSize3:
    IntCmp $3 8 setSize2 NoSetSize2 setSize2
    setSize2:
    AddSize 100 ; add 100kb for freenet-ext.jar
    IntOp $3 $3 - 2
    NoSetSize2:
    IntCmp $3 8 setSize1 NoSetSize1 setSize1
    setSize1:
    AddSize 1500 ; add 1500kb for freenet.jar
    IntOp $3 $3 - 1
    NoSetSize1:
    Pop $3 # retrieve previous value of $3 to carry out actual installation
  !endif

  IntOp $4 $3 + 0 # save $3 in $4 (although it isn't used beyond this point ... yet...)
  IntCmp $3 8 NoInstall4 Install4 NoInstall4
  Install4:
  !ifdef WEBINSTALL
    NSISdl::download "http://freenetproject.org/snapshots/NodeConfig.exe" "$R0\freenet-install\NodeConfig.exe"
	StrCmp $0 "success" Success4
	IfFileExists "$INSTDIR\NodeConfig.exe" PreExistingDownload4 FailedDownload4
	FailedDownload4:
    Delete "$R0\freenet-install\NodeConfig.exe"
	MessageBox MB_OK "Download of NodeConfig.exe failed.  Installation aborted at this time"
	Call AbortCleanup
	PreExistingDownload4:
	MessageBox MB_YESNO "Download of NodeConfig.exe failed - Carry on installation?$\r$\n(This will use your pre-existing NodeConfig.exe)" IDYES NoFailed4
	goto FailedDownload4
	Success4:
  !else
    ClearErrors
	File ".\Freenet\tools\NodeConfig.exe" /ofile="$R0\freenet-install\NodeConfig.exe"
    IfErrors TestPreExistingFailedExtract4 Success4
	TestPreExistingFailedExtract4:
	IfFileExists "$INSTDIR\NodeConfig.exe" PreExistingExtract4 DiskWriteError
	PreExistingExtract4:
	MessageBox MB_YESNO "Couldn't extract NodeConfig.exe - Carry on installation?$\r$\n(This will use your pre-existing NodeConfig.exe)" IDYES NoFailed4
	goto DiskWriteError
  !endif
  NoFailed4:
  goto +2
  NoInstall4:
  IntOp $3 $3 - 8
  IntCmp $3 4 NoInstall3 Install3 NoInstall3
  Install3:
  !ifdef WEBINSTALL
    NSISdl::download "http://freenetproject.org/snapshots/freenet.exe" "$R0\freenet-install\freenet.exe"
	StrCmp $0 "success" Success3
	IfFileExists "$INSTDIR\freenet.exe" PreExisting3 Failed3
	Failed3:
    Delete "$R0\freenet-install\freenet.exe"
	MessageBox MB_OK "Download of freenet.exe failed.  Installation aborted at this time"
	Call AbortCleanup
	PreExisting3:
	MessageBox MB_YESNO "Download of freenet.exe failed - Carry on installation?$\r$\n(This will use your pre-existing freenet.exe)" IDYES NoFailed3
	goto Failed3
	Success3:
  !else
    ClearErrors
	File ".\Freenet\tools\freenet.exe" /ofile="$R0\freenet-install\freenet.exe"
    IfErrors TestPreExistingFailedExtract3 Success3
	TestPreExistingFailedExtract3:
	IfFileExists "$INSTDIR\freenet.exe" PreExistingExtract3 DiskWriteError
	PreExistingExtract3:
	MessageBox MB_YESNO "Couldn't extract freenet.exe - Carry on installation?$\r$\n(This will use your pre-existing freenet.exe)" IDYES NoFailed3
	goto DiskWriteError
  !endif
  NoFailed3:
  goto +2
  NoInstall3:
  IntOp $3 $3 - 4
  IntCmp $3 2 NoInstall2 Install2 NoInstall2
  Install2:
  !ifdef WEBINSTALL
    NSISdl::download "http://freenetproject.org/snapshots/freenet-ext.jar" "$R0\freenet-install\freenet-ext.jar"
	StrCmp $0 "success" Success2
	IfFileExists "$INSTDIR\freenet-ext.jar" PreExisting2 Failed2
	Failed2:
    Delete "$R0\freenet-install\freenet-ext.jar"
	MessageBox MB_OK "Download of freenet-ext.jar failed.  Installation aborted at this time"
	Call AbortCleanup
	PreExisting2:
	MessageBox MB_YESNO "Download of freenet-ext.jar failed - Carry on installation?$\r$\n(This will use your pre-existing freenet-ext.jar)" IDYES NoFailed2
	goto Failed2
	Success2:
  !else
    ClearErrors
	File ".\Freenet\jar\freenet-ext.jar" /ofile="$R0\freenet-install\freenet-ext.jar"
    IfErrors TestPreExistingFailedExtract2 Success2
	TestPreExistingFailedExtract2:
	IfFileExists "$INSTDIR\freenet-ext.jar" PreExistingExtract2 DiskWriteError
	PreExistingExtract2:
	MessageBox MB_YESNO "Couldn't extract freenet-ext.jar - Carry on installation?$\r$\n(This will use your pre-existing freenet-ext.jar)" IDYES NoFailed2
	goto DiskWriteError
  !endif
  NoFailed2:
  goto +2
  NoInstall2:
  IntOp $3 $3 - 2
  IntCmp $3 1 NoInstall1 Install1 NoInstall1
  Install1:
  !ifdef WEBINSTALL
    NSISdl::download "http://freenetproject.org/snapshots/freenet-latest.jar" "$R0\freenet-install\freenet.jar"
	StrCmp $0 "success" Success1
	IfFileExists "$INSTDIR\freenet.jar" PreExisting1 Failed1
	Failed1:
    Delete "$R0\freenet-install\freenet.jar"
	MessageBox MB_OK "Download of freenet-latest.jar failed.  Installation aborted at this time"
	Call AbortCleanup
	PreExisting1:
	MessageBox MB_YESNO "Download of freenet-latest.jar failed - Carry on installation?$\r$\n(This will use your pre-existing freenet.jar)" IDYES NoFailed1
	goto Failed1
	Success1:
  !else
    ClearErrors
	File ".\Freenet\jar\freenet.jar" /ofile="$R0\freenet-install\freenet.jar"
    IfErrors TestPreExistingFailedExtract1 Success1
	TestPreExistingFailedExtract1:
	IfFileExists "$INSTDIR\freenet.jar" PreExistingExtract1 DiskWriteError
	PreExistingExtract1:
	MessageBox MB_YESNO "Couldn't extract freenet.jar - Carry on installation?$\r$\n(This will use your pre-existing freenet.jar)" IDYES NoFailed1
	goto DiskWriteError
  !endif
  NoFailed1:
  goto +2
  NoInstall1:
  IntOp $3 $3 - 1
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
  Call AbortCleanup
!endif

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

    DetailPrint "Aborted installation - Cleaning up any downloaded files..."
    GetFullPathName /SHORT $R0 $TEMP # get short version of TEMP into $R0
    Delete "$R0\freenet-install\*.*"
    RMDir "$R0\freenet-install"
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