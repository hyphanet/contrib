INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

CFLAGS=-ansi -W -Wall -pedantic -g

# joliveri local settings
#CFLAGS=-ansi -W -Wall -pedantic -g -DDMALLOC
#fcpget_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon ../libdmalloc.a

bin_PROGRAMS = fcpget

fcpget_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpget_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon
fcpget_SOURCES  = fcpget.c libezfcp.a

