INCLUDES = -I$(top_srcdir)/ezFCPlib -I$(top_srcdir)/include
CFLAGS=-ansi -W -Wall -pedantic -g

bin_PROGRAMS = fcpputsite

fcpputsite_LDFLAGS  = -L$(top_srcdir)/ezFCPlib -L$(top_srcdir)/fcpcommon
fcpputsite_LDADD    = ../ezFCPlib/libezfcp.a -lfcpcommon
fcpputsite_SOURCES  = fcpputsite.c
