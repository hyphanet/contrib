INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include
CFLAGS=-ansi -W -Wall -pedantic -g -DDMALLOC

bin_PROGRAMS = fcpget

fcpget_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
#fcpget_LDADD    = -lezfcp -lfcpcommon
fcpget_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon /usr/lib/libdmalloc.a
fcpget_SOURCES  = fcpget.c libezfcp.a

