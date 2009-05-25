@echo off
echo Building C Targets
REM call VCVARSnn.BAT
call %1

NMAKE.EXE -f Wrapper32.mak CFG="Wrapper - Win32 Release" ALL
if not errorlevel 1 goto ok1
exit %ERRORLEVEL%

:ok1
NMAKE.EXE -f WrapperJNI32.mak CFG="WrapperJNI - Win32 Release" ALL
if not errorlevel 1 goto end
exit %ERRORLEVEL%

:end
