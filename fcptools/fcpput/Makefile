
#
# makefile for fcpput - FCP-based Freenet command line insert client
#

CFLAGS=-c -g -Wall

default:
	@echo "Target must be one of 'linux', 'cygwin' or 'clean'

linux: fcpput.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make ezFCPlib.a
	gcc -o fcpput $^ /usr/lib/libpthread.a

cygwin: fcpput.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make cygwin
	gcc -o fcpput $^

clean:
	rm -f fcpput.o fcpput.exe fcpput

fcpput.o: fcpput.c
	gcc $(CFLAGS) -I../ezFCPlib $<

