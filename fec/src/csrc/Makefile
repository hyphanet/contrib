#
# makefile for vdm.
#
# gcc does something strange, so check the various opt. levels for
# best performance (or write addmul1 in assembly code).
#
# Standard compilation with -O9 works well for PentiumPro and Pentium2
# machines.
#

uname_S := $(shell sh -c 'uname -s 2>/dev/null || echo not')

ifeq ($(uname_S),Linux)
	CC?=gcc
	JINCLUDE=$(JAVA_HOME)/include/linux
	LDFLAGS?= --add-stdcall-alias #-m32 #for 32-bit cross-compile
else ifeq ($(uname_S),FreeBSD)
	CC?=cc
	JINCLUDE=$(JAVA_HOME)/include/freebsd
	#LDFLAGS?= --add-stdcall-alias #-m32 #for 32-bit cross-compile
else
	$(error unrecognized os $(uname_S))
endif

# COPT= -O9 -funroll-loops
COPT= -O1 -funroll-loops -fno-strict-aliasing
CFLAGS?=$(COPT) -Wall -fPIC -I$(JAVA_HOME)/include #-m32 #for 32-bit cross-compile
SRCS= fec.c Makefile test.c
DOCS= README fec.3
ALLSRCS= $(SRCS) $(DOCS) fec.h

fec: libfec8.so libfec16.so test.c
	$(CC) $(CFLAGS) -DGF_BITS=8 -o fec fec8.o test.c

libfec8.so: fec8.o fec8-jinterf.o
	$(CC) $(LDFLAGS) -shared fec8-jinterf.o fec8.o -o libfec8.so

fec8-jinterf.o: fec-jinterf.c
	$(CC) $(CFLAGS) -DGF_BITS=8 -c \
		-I$(JINCLUDE) fec-jinterf.c \
		-o fec8-jinterf.o

fec8.o: fec.h fec8.S
	$(CC) $(CFLAGS) -DGF_BITS=8 -c -o fec8.o fec8.S

fec8.S: fec.c Makefile
	$(CC) $(CFLAGS) -DGF_BITS=8 -S -o fec8.S fec.c

libfec16.so: fec16.o fec16-jinterf.o
	$(CC) $(LDFLAGS) -shared fec16-jinterf.o fec16.o -o libfec16.so

fec16-jinterf.o: fec-jinterf.c
	$(CC) $(CFLAGS) -DGF_BITS=16 -c \
		-I$(JINCLUDE) fec-jinterf.c \
		-o fec16-jinterf.o

fec16.o: fec.h fec16.S
	$(CC) $(CFLAGS) -DGF_BITS=16 -c -o fec16.o fec16.S

fec16.S: fec.c Makefile
	$(CC) $(CFLAGS) -DGF_BITS=16 -S -o fec16.S fec.c

clean:
	- rm -f *.o *.S fec *.so

tgz: $(ALLSRCS)
	tar cvzf vdm`date +%y%m%d`.tgz $(ALLSRCS)
