;---------------------------------------------------------------------------------------

Function DownloadFile
# Use $1 and $2 to hand the URL and the Filename to this function
# Result is returned in $0, "cancel" if cancelled, "success" if success,
# otherwise, an error string describing the error

  ; make the call to download
  Push $1
  Push $2
;  CallInstDLL $TEMP\freenet\nsisdl.dll download ; for a quiet install, use download_quiet
  NSISdl::download "$1" "$2"
  
  Pop $R9
  ; check if download succeeded
  StrCmp $R9 "success" successful
  StrCmp $R9 "cancel" cancelled

  ; we failed
    DetailPrint "Download failed: $R9"
    Messagebox MB_OK "Download of$\r$\n$1$\r$\nfailed. Aborting installation..."
    Abort

  cancelled:
    DetailPrint "Download cancelled"
    Messagebox MB_OK "User canceled download of$\r$\n$1.$\r$\nAborting installation..."
    Abort

  successful:
    DetailPrint "Download of $2 successful"

FunctionEnd
;-----------------------------------------------------------------------------------

# Bob H - Retryable download, moved from freenet-modern.nsi so it can be shared

Function RetryableDownload
 POP $R2 # local filename
 POP $R3 # local dir
 POP $R4 # remote dir+filename

 StrCpy $R5 "$R3\$R2"

 DetailPrint "Downloading $R2 from $R4 ..."

 DoDownload:
 NSISdl::download /TIMEOUT=150000 "$R4" "$R5"
 Pop $R9
 StrCmp $R9 "success" Success
 DetailPrint "Download of $R2 failed: $R9"
 SetDetailsPrint none
 Delete "$R5"
 SetDetailsPrint both
 MessageBox MB_YESNO "Download of $R2 failed: '$R9'. Retry?" IDYES Retry
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
