INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include
CFLAGS=-ansi -W -Wall -pedantic -g -DDMALLOC
#CFLAGS=-ansi -W -Wall -pedantic -g

bin_PROGRAMS = fcpput

fcpput_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
#fcpput_LDADD    = -lezfcp -lfcpcommon
fcpput_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon /usr/lib/libdmalloc.a
fcpput_SOURCES  = fcpput.c libezfcp.a
