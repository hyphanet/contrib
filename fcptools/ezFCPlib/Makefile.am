# Makefile.am for ezFCPlib

# Keep these flags since this directory may be used in other projects.
# KDE for example requires strict ansi compliance (these params actually).
CFLAGS=-ansi -W -Wall -pedantic -g

lib_LIBRARIES = libezfcp.a

lib_LTLIBRARIES = libezfcp.la
libezfcp_la_LDFLAGS = -version-info 0:0:0

include_HEADERS = ezFCPlib.h compat.h

FCP_SOURCE = \
	_fcpGlobals.c _fcpLog.c _fcpPutSplit.c _fcpReadBlk.c \
	_fcpRecvResponse.c _fcpSock.c _fcpUtil.c fcpCloseKey.c \
	fcpCreateHandle.c fcpDestroyHandle.c fcpGetKeyToFile.c \
	fcpGetKeyToMem.c fcpInitHandle.c fcpMakChkFromMem.c \
	fcpMakeChkFromFile.c fcpMakeSvkKeypair.c fcpOpenKey.c \
	fcpOpenKeyIndex.c fcpPutKeyFromFile.c fcpPutKeyFromMem.c \
	fcpRawMode.c fcpReadKey.c fcpReadKeyIndex.c fcpSetParam.c \
	fcpStartup.c fcpWriteKey.c fcpWriteKeyIndex.c \
	metaParse.c mimetype.c template.c safeMalloc.c

libezfcp_a_SOURCES = $(FCP_SOURCE)
libezfcp_la_SOURCES = $(FCP_SOURCE)

