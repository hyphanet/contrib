@echo off
SET PATH=C:\Windows\Command;"C:\Programme\Microsoft Visual Studio\Common\Tools"
REM -- First make map file from Microsoft Visual C++ generated resource.h
echo // MAKEHELP.BAT generated Help Map file.  Used by Freenet.HPJ. >"hlp\Freenet.hm"
echo. >>"hlp\Freenet.hm"
echo // Commands (ID_* and IDM_*) >>"hlp\Freenet.hm"
makehm ID_,HID_,0x10000 IDM_,HIDM_,0x10000 resource.h >>"hlp\Freenet.hm"
echo. >>"hlp\Freenet.hm"
echo // Prompts (IDP_*) >>"hlp\Freenet.hm"
makehm IDP_,HIDP_,0x30000 resource.h >>"hlp\Freenet.hm"
echo. >>"hlp\Freenet.hm"
echo // Resources (IDR_*) >>"hlp\Freenet.hm"
makehm IDR_,HIDR_,0x20000 resource.h >>"hlp\Freenet.hm"
echo. >>"hlp\Freenet.hm"
echo // Dialogs (IDD_*) >>"hlp\Freenet.hm"
makehm IDD_,HIDD_,0x20000 resource.h >>"hlp\Freenet.hm"
echo. >>"hlp\Freenet.hm"
echo // Frame Controls (IDW_*) >>"hlp\Freenet.hm"
makehm IDW_,HIDW_,0x50000 resource.h >>"hlp\Freenet.hm"
REM -- Make help for Project Freenet


echo Building Win32 Help files
start /wait hcw /C /E /M "hlp\Freenet.hpj"
if errorlevel 1 goto :Error
if not exist "hlp\Freenet.hlp" goto :Error
if not exist "hlp\Freenet.cnt" goto :Error
echo.
if exist Debug\nul copy "hlp\Freenet.hlp" Debug
if exist Debug\nul copy "hlp\Freenet.cnt" Debug
if exist Release\nul copy "hlp\Freenet.hlp" Release
if exist Release\nul copy "hlp\Freenet.cnt" Release
echo.
goto :done

:Error
echo hlp\Freenet.hpj(1) : error: Problem encountered creating help file

:done
echo.
