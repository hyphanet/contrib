
#
# makefile for fcpputsite - FCP-based Freenet command line site insert client
#

CFLAGS=-c -g -Wall

default:
	@echo "Available targets:"
	@echo "  linux:          build linux version"
	@echo "  cygwin:         build for cygwin"
	@echo "  clean:          clean out binaries"

linux:  fcpputsite.o \
		insertFreesite.o \
		scanDir.o \
		mimeType.o \
		../ezFCPlib/ezFCPlib.a
	cd ../ezFCPlib; make linux
	gcc -o fcpputsite $^ -pthread


cygwin:  fcpputsite.o \
		insertFreesite.o \
		scanDir.o \
		mimeType.o \
		../ezFCPlib/ezFCPlib.a
	gcc -o fcpputsite $^

# ----------------------------------------------

clean:
	rm -f *.o fcpputsite.exe fcpputsite

fcpputsite.o: fcpputsite.c
	gcc $(CFLAGS) -I../ezFCPlib $<

insertFreesite.o: insertFreesite.c
	gcc $(CFLAGS) -I../ezFCPlib $<

scanDir.o: scanDir.c
	gcc $(CFLAGS) -I../ezFCPlib $<

mimeType.o: mimeType.c
	gcc $(CFLAGS) -I../ezFCPlib $<

