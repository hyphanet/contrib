
#
# makefile for fcpget - FCP-based Freenet command line request client
#


default:
	@echo "Target must be one of 'linux', 'cygwin' or 'clean'

linux: fcpget.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make ezFCPlib.a
	gcc -o fcpget $^

cygwin: fcpget.o ../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make cygwin
	gcc -o fcpget $^

clean:
	rm -f fcpget.o fcpget.exe fcpget

fcpget.o: fcpget.c
	gcc -c -g -Wall -I../ezFCPlib $<
