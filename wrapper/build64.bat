@echo off
setlocal

echo --------------------
echo Wrapper Build System
echo --------------------

call %ANT_HOME%\bin\ant.bat -Dbits=64 %1 %2 %3 %4 %5 %6 %7 %8

