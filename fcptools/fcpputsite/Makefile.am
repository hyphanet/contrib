INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

bin_PROGRAMS = fcpputsite

fcpputsite_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpputsite_LDADD    = -lezfcp -lfcpcommon
fcpputsite_SOURCES  = fcpputsite.c insertFreesite.c scanDir.c
