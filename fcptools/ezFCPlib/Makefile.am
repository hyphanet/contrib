# Makefile.am for ezFCPlib

# Keep these flags since this directory may be used in other projects.
# KDE for example requires strict ansi compliance (these params actually).
CFLAGS=-ansi -W -Wall -pedantic -g

#lib_LIBRARIES = libezfcp.a
noinst_LIBRARIES = libezfcp.a

#lib_LTLIBRARIES = libezfcp.la
#libezfcp_la_LDFLAGS = -version-info 0:0:0

include_HEADERS = ezFCPlib.h

FCP_SOURCE = "\
_fcpCompat.c \
_fcpGlobals.c \
_fcpLog.c \
_fcpRecvResponse.c \
_fcpSock.c \
_fcpUtil.c \
_fcpPut.c \
ezFCPlib.h \
fcpCloseKey.c \
fcpCreation.c \
fcpOpenKey.c \
fcpPutKeyFromFile.c \
fcpReadKey.c \
fcpStartup.c \
fcpWriteKey.c \
mimetype.c \
"

# The following files are currently ignored for now
# fcpGetKeyToFile.c \
# fcpMakeChkFromFile.c \
# fcpMakeSvkKeypair.c \
# fcpOpenKeyIndex.c \
# fcpReadKeyIndex.c \
# fcpWriteKeyIndex.c \

libezfcp_a_SOURCES = $(FCP_SOURCE)
#libezfcp_la_SOURCES = $(FCP_SOURCE)

