INCLUDES = `pkg-config --cflags glib` -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

bin_PROGRAMS = fcpx

fcpx_LDFLAGS  = `pkg-config --libs glib` -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpx_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon ../libdmalloc.a
fcpx_SOURCES  = fcpx.c

