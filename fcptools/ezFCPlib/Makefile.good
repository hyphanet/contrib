#
# unix makefile for ezFCPlib
#
# C Client library for Freenet using FCP
#

default:
	@echo "Target must be one of 'linux', 'cygwin' or 'clean'"

linux: ezFCPlib.a

cygwin: ezFCPlib.a

clean:
	rm -f *.o *.a meta_parse.c meta_parse.h

CC=gcc
CFLAGS=-O2 -c -g -Wall -U_WIN32
COMPILE=$(CC) $(CFLAGS)


ezFCPlib.a: fcpStartup.o \
		fcpCreateHandle.o \
		fcpInitHandle.o \
		fcpDestroyHandle.o \
		fcpRawMode.o \
		fcpSetHtl.o \
		fcpSetHost.o \
		fcpGetKeyToFile.o \
		fcpGetKeyToMem.o \
		fcpPutKeyFromFile.o \
		fcpPutKeyFromMem.o \
		fcpMakChkFromMem.o \
		fcpMakeChkFromFile.o \
		fcpMakeSvkKeypair.o \
		fcpOpenKeyIndex.o \
		fcpReadKeyIndex.o \
		fcpWriteKeyIndex.o \
		fcpOpenKey.o \
		fcpReadKey.o \
		fcpWriteKey.o \
		fcpCloseKey.o \
		metaParse.o \
		_fcpSock.o _fcpReadBlk.o _fcpRecvResponse.o _fcpGlobals.o \
		_fcpUtil.o \
		_fcpLog.o
	ar -rc $@ $^

%.o:%.c
	gcc $(CFLAGS) $<

