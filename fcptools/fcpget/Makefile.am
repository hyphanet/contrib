INCLUDES = `pkg-config --cflags glib` -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

bin_PROGRAMS = fcpget

fcpget_LDFLAGS  = `pkg-config --libs glib` -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpget_LDADD    = -lezfcp -lfcpcommon
fcpget_SOURCES  = fcpget.c
