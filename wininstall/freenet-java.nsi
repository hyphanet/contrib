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

# Bob H, Nov 2005 : Resurrected, now uses freenet-modern.nsi instead of
# freenet.nsi. Thus inherits zipped seednode support I added.

!define embedJava
!define JAVAINSTALLER jre-1_5_0_05-windows-i586-p.exe
!include freenet-modern.nsi

