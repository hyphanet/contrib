
#
# makefile for fcpput - FCP-based Freenet command line insert client
#

CFLAGS=-c -g -Wall

default:
	@echo "Target must be one of 'linux', 'cygwin' or 'clean'"

linux: fcpputsplit.o mimetype.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make ezFCPlib.a
	gcc -o fcpputsplit $^ -pthread

cygwin: fcpputsplit.o mimetype.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make cygwin
	gcc -o fcpputsplit $^ -pthread

clean:
	rm -f fcpputsplit.o mimetype.o fcpputsite.exe fcpputsplit

fcpputsplit.o: fcpputsplit.c
	gcc $(CFLAGS) -I../ezFCPlib $<

mimetype.o : mimetype.c
	 gcc $(CFLAGS) -I../ezFCPlib $<

