# Makefile.am for ezFCPlib

# Keep these flags since this directory may be used in other projects.
# KDE for example requires strict ansi compliance (these params actually).
#CFLAGS=-ansi -W -Wall -pedantic -g
CFLAGS=-W -Wall -pedantic -g

# joliveri local settings.. related to dmalloc mem-leak util
#CFLAGS=-W -Wall -pedantic -g -DDMALLOC
#LDADD=/usr/lib/libdmalloc.a

#lib_LIBRARIES = libezfcp.a
noinst_LIBRARIES = libezfcp.a

#lib_LTLIBRARIES = libezfcp.la
#libezfcp_la_LDFLAGS = -version-info 0:0:0

include_HEADERS = ezFCPlib.h

FCP_SOURCE = "\
_fcpLog.c \
_fcpRecvResponse.c \
_fcpSock.c \
_fcpUtil.c \
_fcpFile.c \
_fcpMetadata.c \
_fcpMimetype.c \
_fcpGet.c \
_fcpPut.c \
ezFCPlib.h \
fcpCreation.c \
fcpInfo.c \
fcpOpenKey.c \
fcpCloseKey.c \
fcpPutKeyFromFile.c \
fcpGetKeyToFile.c \
fcpReadKey.c \
fcpMakeSvkKeypair.c \
fcpStartup.c \
fcpWriteKey.c \
"

# The following files are currently ignored for now
# fcpMakeChkFromFile.c \

libezfcp_a_SOURCES = $(FCP_SOURCE)
#libezfcp_la_SOURCES = $(FCP_SOURCE)

