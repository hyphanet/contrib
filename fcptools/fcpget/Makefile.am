INCLUDES = -I$(top_srcdir)/include @INCLUDES@
LDADD=@LDADD@
LIBS=@LIBS@

bin_PROGRAMS = fcpget

fcpget_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpget_LDADD    = -lezfcp -lfcpcommon
fcpget_SOURCES  = fcpget.c
