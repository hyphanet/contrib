INCLUDES = -I$(top_srcdir)/include @INCLUDES@
LDADD=@LDADD@
LIBS=@LIBS@

bin_PROGRAMS = fcpput

fcpput_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpput_LDADD    = -lezfcp -lfcpcommon
fcpput_SOURCES  = fcpput.c
