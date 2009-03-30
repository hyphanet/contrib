#
# makefile for vdm.
#
# fec.S.980624a is an optimized version for use on 486 and old pentium
# machines. It is only for GF_BITS=8 and generally does not work
# fast on systems with multiple instruction pipelines (PentiumPro,
# Pentium2)
# same for fec.S16.980624a , for use with GF_BITS=16
#
# gcc does something strange, so check the various opt. levels for
# best performance (or write addmul1 in assembly code).
#
# Standard compilation with -O9 works well for PentiumPro and Pentium2
# machines.
#

CC=gcc
# COPT= -O9 -funroll-loops
COPT= -O1 
CFLAGS=$(COPT) -Wall # -DTEST
SRCS= fec.c Makefile test.c fec.s.980621e \
	fec.S.980624a \
	fec.S16.980624a
DOCS= README fec.3
ALLSRCS= $(SRCS) $(DOCS) fec.h

fec: libfec8.so libfec16.so test.o
	$(CC) $(CFLAGS) -o fec fec8.o test.o

libfec8.so: fec8.o
	$(CC) $(CFLAGS) -DGF_BITS=8 -c -I$(JAVA_HOME)/include/ \
		-I$(JAVA_HOME)/include/linux fec8-jinterf.c \
		-o fec8-jinterf.o ; \
	mkdir -p ../../lib/fec-linux-x86/lib/linux/x86 ; \
        ld -shared fec8-jinterf.o fec8.o -o \
		../../lib/fec-linux-x86/lib/linux/x86/libfec8.so

fec8.o: fec.h fec8.S
	$(CC) $(CFLAGS) -DGF_BITS=8 -c -o fec8.o fec8.S

fec8.S: fec.c Makefile
	$(CC) $(CFLAGS) -DGF_BITS=8 -S -o fec8.S fec.c

libfec16.so: fec16.o
	$(CC) $(CFLAGS) -DGF_BITS=16 -c -I$(JAVA_HOME)/include/ \
		-I$(JAVA_HOME)/include/linux fec16-jinterf.c \
		-o fec16-jinterf.o ; \
	mkdir -p ../../lib/fec-linux-x86/lib/linux/x86 ; \
        ld -shared fec16-jinterf.o fec16.o -o \
		../../lib/fec-linux-x86/lib/linux/x86/libfec16.so

fec16.o: fec.h fec16.S
	$(CC) $(CFLAGS) -DGF_BITS=16 -c -o fec16.o fec16.S

fec16.S: fec.c Makefile
	$(CC) $(CFLAGS) -DGF_BITS=16 -S -o fec16.S fec.c

clean:
	- rm *.o *.S fec

tgz: $(ALLSRCS)
	tar cvzf vdm`date +%y%m%d`.tgz $(ALLSRCS)
