INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

CFLAGS=-ansi -W -Wall -pedantic -g

# joliveri local settings
#CFLAGS=-ansi -W -Wall -pedantic -g -DDMALLOC
#fcpput_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon ../libdmalloc.a

bin_PROGRAMS = fcpput

fcpput_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpput_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon
fcpput_SOURCES  = fcpput.c libezfcp.a
