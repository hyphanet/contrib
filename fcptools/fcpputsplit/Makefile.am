INCLUDES = -I$(top_srcdir)/include @INCLUDES@
LDADD=@LDADD@
LIBS=@LIBS@

bin_PROGRAMS = fcpputsplit

fcpputsplit_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpputsplit_LDADD    = -lezfcp -lfcpcommon
fcpputsplit_SOURCES  = fcpputsplit.c
