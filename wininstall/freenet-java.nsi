#
# freenet-java.nsi
#
# NSIS installer generator script for Freenet with built-in java
#
# To avoid maintenance nightmare, this script just sets a flag and runs the
# freenet.nsi installer generator. With this flag set, the freenet.nsi
# script generates Freenet with built-in java/
#
# Created April 2001 by David McNab

!define embedJava
!include freenet.nsi

