#
# makefile for vdm.
#
# gcc does something strange, so check the various opt. levels for
# best performance (or write addmul1 in assembly code).
#
# Standard compilation with -O9 works well for PentiumPro and Pentium2
# machines.
#

CC ?= gcc
# COPT= -O9 -funroll-loops
COPT = -O1 -funroll-loops -fno-strict-aliasing
CFLAGS ?= $(COPT) -Wall -fPIC -I$(JAVA_HOME)/include #-m32 #for 32-bit cross-compile
LDFLAGS ?= #-m32 #for 32-bit cross-compile
CLASSPATH ?= ../../classes
SRCS = fec.c fec.h test.c fec-jinterf.c Makefile
DOCS = README fec.3
ALLSRCS = $(SRCS) $(DOCS) fec.h

.PHONY: clean clean-all all

all: libfec8.so libfec16.so

all-test: fec8test fec16test

libfec%.so: fec%.o fec%-jinterf.o
	$(CC) $^ -o $@ $(LDFLAGS) -shared

fec%-jinterf.o: fec-jinterf.c com_onionnetworks_fec_Native%Code.h
	$(CC) $< -o $@ -c $(CFLAGS) -DGF_BITS=$* -I$(JAVA_HOME)/include/linux

com_onionnetworks_fec_Native%Code.h: $(CLASSPATH)/com/onionnetworks/fec/Native%Code.class
	javah -o $@ -classpath $(CLASSPATH) com.onionnetworks.fec.Native$*Code

fec%test: fec%.o test.c
	$(CC) $^ -o $@ $(CFLAGS) -DGF_BITS=$*

fec%.o: fec%.S fec.h
	$(CC) $< -o $@ -c $(CFLAGS) -DGF_BITS=$*

fec%.S: fec.c Makefile
	$(CC) $< -o $@ -S $(CFLAGS) -DGF_BITS=$*

clean:
	- rm -f *.o *.S *.so fec*test

clean-all: clean
	- rm -f com_*.h

tgz: $(ALLSRCS)
	tar -cvz -f vdm`date +%y%m%d`.tgz $(ALLSRCS)
