@echo off
setlocal

rem Copyright (c) 1999, 2007 Tanuki Software Inc.
rem
rem Java Service Wrapper windows build script.  This script is designed to be
rem  called by the ant build.xml file.
rem

rem %1 Makefile name
rem %2 Visual Studio environment script
rem %3 script argument
rem %4 script argument
rem %5 script argument

echo Configuring the Visual Studio environment...
call %2 %3 %4 %5

echo Run the make file...
nmake /f %1 /c clean all
