# Makefile.am for ezFCPlib

# Keep these flags since this directory may be used in other projects.
# KDE for example requires strict ansi compliance (these params actually).
CFLAGS=-ansi -W -Wall -pedantic -g

lib_LIBRARIES = libezfcp.a

lib_LTLIBRARIES = libezfcp.la
libezfcp_la_LDFLAGS = -version-info 0:0:0

include_HEADERS = ezFCPlib.h

FCP_SOURCE = "\
_fcpLog.c \
_fcpGlobals.c \
_fcpSock.c \
_fcpRecvResponse.c \
_fcpUtil.c \
fcpOpenKey.c \
ezFCPlib.h \
fcpCloseKey.c \
fcpStartup.c \
_fcpCompat.c \
fcpReadKey.c \
fcpWriteKey.c \
fcpCreation.c \
mimetype.c \
"

# The following files are currently ignored for now
# _fcpPutSplit.c \
# fcpGetKeyToFile.c \
# fcpGetKeyToMem.c \
# fcpMakChkFromMem.c \
# fcpMakeChkFromFile.c \
# fcpMakeSvkKeypair.c \
# fcpOpenKeyIndex.c \
# fcpPutKeyFromFile.c \
# fcpPutKeyFromMem.c \
# fcpReadKeyIndex.c \
# fcpWriteKeyIndex.c \

libezfcp_a_SOURCES = $(FCP_SOURCE)
libezfcp_la_SOURCES = $(FCP_SOURCE)

