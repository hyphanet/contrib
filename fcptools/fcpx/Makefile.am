INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include

#CFLAGS=-ansi -W -Wall -pedantic -g
CFLAGS=-W -Wall -pedantic -g

bin_PROGRAMS = fcpx

# joliveri local settings
#CFLAGS=-ansi -W -Wall -pedantic -g -DDMALLOC
#fcpx_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon ../libdmalloc.a

fcpx_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpx_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon
fcpx_SOURCES  = fcpx.c
