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
