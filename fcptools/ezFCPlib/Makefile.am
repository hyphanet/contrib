
# Makefile.am for ezFCPlib

INCLUDES= -I$(srcdir)
CFLAGS = -g

noinst_LIBRARIES = libezfcp.a

libezfcp_a_SOURCES = fcpStartup.c fcpCreateHandle.c fcpInitHandle.c \
	fcpDestroyHandle.c fcpRawMode.c fcpSetHtl.c fcpSetParam.c fcpGetKeyToFile.c \
	fcpGetKeyToMem.c fcpPutKeyFromFile.c fcpPutKeyFromMem.c fcpMakChkFromMem.c \
	fcpMakeChkFromFile.c fcpMakeSvkKeypair.c fcpOpenKeyIndex.c \
	fcpReadKeyIndex.c fcpWriteKeyIndex.c fcpOpenKey.c fcpReadKey.c \
	fcpWriteKey.c fcpCloseKey.c metaParse.c _fcpSock.c _fcpReadBlk.c \
	_fcpRecvResponse.c _fcpGlobals.c _fcpUtil.c _fcpLog.c _fcpPutSplit.c

noinst_HEADERS = ezFCPlib.h
