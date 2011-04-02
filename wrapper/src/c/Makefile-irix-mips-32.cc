# Copyright (c) 1999, 2008 Tanuki Software, Inc.
# http://www.tanukisoftware.com
# All rights reserved.
#
# This software is the proprietary information of Tanuki Software.
# You shall use it only in accordance with the terms of the
# license agreement you entered into with Tanuki Software.
# http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html

# Makefile for SGI IRIX 6.5 (may work on other versions as well but not tested)
# MIPSpro Compilers: Version 7.3.1.3m
COMPILE = cc -DIRIX -KPIC

INCLUDE=$(JAVA_HOME)/include

DEFS = -I$(INCLUDE) -I$(INCLUDE)/irix 

wrapper_OBJECTS = wrapper.o wrapperinfo.o wrappereventloop.o wrapper_unix.o property.o logger.o

libwrapper_so_OBJECTS = wrapperjni_unix.o wrapperinfo.o wrapperjni.o

BIN = ../../bin
LIB = ../../lib

all: init wrapper libwrapper.so

clean:
	rm -f *.o

cleanall: clean
	rm -rf *~ .deps
	rm -f $(BIN)/wrapper $(LIB)/libwrapper.so

init:
	if test ! -d .deps; then mkdir .deps; fi

wrapper: $(wrapper_OBJECTS)
	$(COMPILE) $(wrapper_OBJECTS) -o $(BIN)/wrapper -lm

libwrapper.so: $(libwrapper_so_OBJECTS)
	${COMPILE} -shared -no_unresolved -n32 -all $(libwrapper_so_OBJECTS) -o $(LIB)/libwrapper.so

%.o: %.c
	@echo '$(COMPILE) -c $<'; \
	$(COMPILE) $(DEFS) -c $<

