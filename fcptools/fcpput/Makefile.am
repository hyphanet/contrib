
FCP_SOURCE="\
_fcpCompat.c \
_fcpGlobals.c \
_fcpLog.c \
_fcpReadBlk.c \
_fcpRecvResponse.c \
_fcpSock.c \
_fcpUtil.c \
fcpOpenKey.c \
fcpOpenKeyIndex.c \
fcpPutKeyFromFile.c \
fcpStartup.c \
fcpput.c"

INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include
bin_PROGRAMS = fcpput

fcpput_LDFLAGS  = -L$../fcpcommon
fcpput_LDADD    = -lfcpcommon
fcpput_SOURCES  = $(FCP_SOURCE)
