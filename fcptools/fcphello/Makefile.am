INCLUDES = `pkg-config --cflags glib` -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

bin_PROGRAMS = fcphello

fcphello_LDFLAGS  = `pkg-config --libs glib` -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcphello_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon
fcphello_SOURCES  = fcphello.c

