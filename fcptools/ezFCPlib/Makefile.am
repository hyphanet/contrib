
# Makefile.am for ezFCPlib

INCLUDES= -I$(srcdir)
CFLAGS = -g

noinst_LIBRARIES = libezfcp.a

libezfcp_a_SOURCES = fcpStartup.c fcpCreateHandle.c fcpInitHandle.c \
	fcpDestroyHandle.c fcpRawMode.c fcpSetParam.c fcpGetKeyToFile.c \
	fcpGetKeyToMem.c fcpPutKeyFromFile.c fcpPutKeyFromMem.c fcpMakChkFromMem.c \
	fcpMakeChkFromFile.c fcpMakeSvkKeypair.c fcpOpenKeyIndex.c \
	fcpReadKeyIndex.c fcpWriteKeyIndex.c fcpOpenKey.c fcpReadKey.c \
	fcpWriteKey.c fcpCloseKey.c metaParse.c _fcpSock.c _fcpReadBlk.c \
	_fcpRecvResponse.c _fcpGlobals.c _fcpUtil.c _fcpLog.c _fcpPutSplit.c

noinst_HEADERS = ezFCPlib.h

ezFCPlib.a: fcpStartup.o fcpCreateHandle.o fcpInitHandle.o \
							fcpDestroyHandle.o fcpRawMode.o fcpSetHtl.o fcpSetHost.o \
							fcpGetKeyToFile.o fcpGetKeyToMem.o fcpPutKeyFromFile.o \
							fcpPutKeyFromMem.o fcpMakChkFromMem.o \
							fcpMakeChkFromFile.o fcpMakeSvkKeypair.o \
							fcpOpenKeyIndex.o fcpReadKeyIndex.o fcpWriteKeyIndex.o \
							fcpOpenKey.o fcpReadKey.o fcpWriteKey.o fcpCloseKey.o \
							metaParse.o _fcpSock.o _fcpReadBlk.o _fcpRecvResponse.o \
							_fcpGlobals.o _fcpUtil.o _fcpLog.o

	ar -rc ezFCPlib.a fcpStartup.o fcpCreateHandle.o fcpInitHandle.o \
							fcpDestroyHandle.o fcpRawMode.o fcpSetHtl.o fcpSetHost.o \
							fcpGetKeyToFile.o fcpGetKeyToMem.o fcpPutKeyFromFile.o \
							fcpPutKeyFromMem.o fcpMakChkFromMem.o \
							fcpMakeChkFromFile.o fcpMakeSvkKeypair.o \
							fcpOpenKeyIndex.o fcpReadKeyIndex.o fcpWriteKeyIndex.o \
							fcpOpenKey.o fcpReadKey.o fcpWriteKey.o fcpCloseKey.o \
							metaParse.o _fcpSock.o _fcpReadBlk.o _fcpRecvResponse.o \
							_fcpGlobals.o _fcpUtil.o _fcpLog.o

%.o:%.c
	$(CC) $(CFLAGS) -c $<

clean:
	test -z "ezFCPlib.a" || rm -f ezFCPlib.a
