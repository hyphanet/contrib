# Makefile.am for ezFCPlib

# Keep these flags since this directory may be used in other projects.
# KDE for example requires strict ansi compliance (these params actually).
CFLAGS=-ansi -W -Wall -pedantic -g

lib_LIBRARIES = libezfcp.a

lib_LTLIBRARIES = libezfcp.la
libezfcp_la_LDFLAGS = -version-info 0:0:0

include_HEADERS = ezFCPlib.h compat.h

FCP_SOURCE = "\
_fcpLog.c \
_fcpGlobals.c \
_fcpPutSplit.c \
_fcpReadBlk.c \
_fcpSock.c \
_fcpRecvResponse.c \
_fcpUtil.c \
fcpOpenKey.c \
ezFCPlib.h \
fcpCloseKey.c \
fcpDestroyHandle.c \
fcpGetKeyToFile.c \
fcpGetKeyToMem.c \
fcpMakChkFromMem.c \
fcpMakeChkFromFile.c \
fcpMakeSvkKeypair.c \
fcpStartup.c \
fcpOpenKeyIndex.c \
fcpPutKeyFromFile.c \
fcpPutKeyFromMem.c \
_fcpCompat.c \
fcpReadKey.c \
fcpReadKeyIndex.c \
fcpWriteKey.c \
fcpCreation.c \
fcpWriteKeyIndex.c \
mimetype.c \
"

libezfcp_a_SOURCES = $(FCP_SOURCE)
libezfcp_la_SOURCES = $(FCP_SOURCE)

