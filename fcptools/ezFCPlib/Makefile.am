
# Makefile.am for ezFCPlib
#
# It builds both a static .a and shared libtool .la version.

INCLUDES = -I$(srcdir)
CFLAGS = -pedantic

FCP_SOURCE = \
	_fcpGlobals.c _fcpLog.c _fcpPutSplit.c _fcpReadBlk.c _fcpRecvResponse.c \
	_fcpSock.c _fcpUtil.c fcpCloseKey.c fcpCreateHandle.c fcpDestroyHandle.c \
	fcpGetKeyToFile.c fcpGetKeyToMem.c fcpInitHandle.c fcpMakChkFromMem.c \
	fcpMakeChkFromFile.c fcpMakeSvkKeypair.c fcpOpenKey.c fcpOpenKeyIndex.c \
	fcpPutKeyFromFile.c fcpPutKeyFromMem.c fcpRawMode.c fcpReadKey.c \
	fcpReadKeyIndex.c fcpSetParam.c fcpStartup.c fcpWriteKey.c \
	fcpWriteKeyIndex.c metaParse.c mimetype.c template.c safeMalloc.c

#noinst_HEADERS = ezFCPlib.h

lib_LTLIBRARIES = libezfcp.la
libezfcp_la_SOURCES = $(FCP_SOURCE)

noinst_LIBRARIES = libezfcp.a
libezfcp_a_SOURCES = $(FCP_SOURCE)

noinst_PROGRAMS = test unix

#test_SOURCES = \
#	test.c

#unix_SOURCES = \
#	unix.c
