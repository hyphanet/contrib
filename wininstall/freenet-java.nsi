#
# freenet-java.nsi
#
# NSIS installer generator script for Freenet with built-in java
#
# To avoid maintenance nightmare, this script just sets a flag and runs the
# freenet.nsi installer generator. With this flag set, the freenet.nsi
# script generates Freenet with built-in java/
#
# You still need to add the javaexecutable yourself
# Created April 2001 by David McNab and improved by various others :-)

!define embedJava
!define JAVAINSTALLER j2re-1_3_1_01-win.exe
!include freenet.nsi

