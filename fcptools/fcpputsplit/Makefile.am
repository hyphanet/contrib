INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

bin_PROGRAMS = fcpputsplit

fcpputsplit_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpputsplit_LDADD    = -lezfcp -lfcpcommon
fcpputsplit_SOURCES  = fcpputsplit.c
