# Copyright (c) 1999, 2008 Tanuki Software, Inc.
# http://www.tanukisoftware.com
# All rights reserved.
#
# This software is the proprietary information of Tanuki Software.
# You shall use it only in accordance with the terms of the
# license agreement you entered into with Tanuki Software.
# http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html

COMPILE = cc -DHPUX -D_XOPEN_SOURCE_EXTENDED -Ae +Z

INCLUDE=$(JAVA_HOME)/include

DEFS = -I$(INCLUDE) -I$(INCLUDE)/hp-ux

wrapper_SOURCE = wrapper.c wrapperinfo.c wrappereventloop.c wrapper_unix.c property.c logger.c

libwrapper_sl_SOURCE = wrapperjni_unix.c wrapperinfo.c wrapperjni.c

BIN = ../../bin
LIB = ../../lib

all: init wrapper libwrapper.sl

clean:
	rm -f *.o

cleanall: clean
	rm -rf *~ .deps
	rm -f $(BIN)/wrapper $(LIB)/libwrapper.sl

init:
	if test ! -d .deps; then mkdir .deps; fi

wrapper: $(wrapper_SOURCE)
	$(COMPILE) $(wrapper_SOURCE) -lm -lpthread -o $(BIN)/wrapper

libwrapper.sl: $(libwrapper_sl_SOURCE)
	${COMPILE} ${DEFS} $(libwrapper_sl_SOURCE) -b -lm -lpthread -o $(LIB)/libwrapper.sl

%.o: %.c
	${COMPILE} -c ${DEFS} $<
