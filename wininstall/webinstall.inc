;---------------------------------------------------------------------------------------

Function DownloadFile
# Use $1 and $2 to hand the URL and the Filename to this function
# Result is returned in $0, "cancel" if cancelled, "success" if success,
# otherwise, an error string describing the error

  ; make the call to download
  Push $1
  Push $2
  CallInstDLL $TEMP\Freenet\nsisdl.dll download ; for a quiet install, use download_quiet

  ; check if download succeeded
  StrCmp $0 "success" successful
  StrCmp $0 "cancel" cancelled

  ; we failed
    DetailPrint "Download failed: $0"
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
