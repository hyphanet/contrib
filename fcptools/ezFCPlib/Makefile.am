
# Makefile.am for ezFCPlib

INCLUDES= -I$(srcdir)

noinst_LIBRARIES = libezfcp.a

libezfcp_a_SOURCES = \
	_fcpGlobals.c _fcpLog.c _fcpPutSplit.c _fcpReadBlk.c _fcpRecvResponse.c \
	_fcpSock.c _fcpUtil.c fcpCloseKey.c fcpCreateHandle.c fcpDestroyHandle.c \
	fcpGetKeyToFile.c fcpGetKeyToMem.c fcpInitHandle.c fcpMakChkFromMem.c \
	fcpMakeChkFromFile.c fcpMakeSvkKeypair.c fcpOpenKey.c fcpOpenKeyIndex.c \
	fcpPutKeyFromFile.c fcpPutKeyFromMem.c fcpRawMode.c fcpReadKey.c \
	fcpReadKeyIndex.c fcpSetParam.c fcpStartup.c fcpWriteKey.c \
	fcpWriteKeyIndex.c metaParse.c mimetype.c template.c \
	test.c \
	unix.c

noinst_HEADERS = ezFCPlib.h
