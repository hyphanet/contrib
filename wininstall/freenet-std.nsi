#
# freenet-std.nsi
#
# NSIS installer generator script for Freenet Standard (java built in)
#
# To avoid maintenance nightmare, this script just sets a flag and runs the
# freenet lite installer generator. With this flag set, the Freenet Lite
# script generates Freenet Standard.
#
# Created April 2001 by David McNab

!define embedJava
!include freenet-lite.nsi

