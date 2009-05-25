# Copyright (c) 1999, 2009 Tanuki Software, Ltd.
# http://www.tanukisoftware.com
# All rights reserved.
#
# This software is the proprietary information of Tanuki Software.
# You shall use it only in accordance with the terms of the
# license agreement you entered into with Tanuki Software.
# http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html

COMPILE = gcc -O3 -fPIC --pedantic -DAIX -D_FILE_OFFSET_BITS=64

INCLUDE=$(JAVA_HOME)/include

DEFS = -I$(INCLUDE) -I$(INCLUDE)/aix

wrapper_SOURCE = wrapper.c wrapperinfo.c wrappereventloop.c wrapper_unix.c property.c logger.c

libwrapper_a_SOURCE = wrapperjni_unix.c wrapperinfo.c wrapperjni.c

BIN = ../../bin
LIB = ../../lib

all: init wrapper libwrapper.a

clean:
	rm -f *.o

cleanall: clean
	rm -rf *~ .deps
	rm -f $(BIN)/wrapper $(LIB)/libwrapper.a

init:
	if test ! -d .deps; then mkdir .deps; fi

wrapper: $(wrapper_SOURCE)
	$(COMPILE) -lpthread -lnsl -lm $(wrapper_SOURCE) -o $(BIN)/wrapper

libwrapper.a: $(libwrapper_a_SOURCE)
	${COMPILE} $(DEFS) -shared -lpthread $(libwrapper_a_SOURCE) -o $(LIB)/libwrapper.a

%.o: %.c
	@echo '$(COMPILE) -c $<'; \
	$(COMPILE) $(DEFS) -Wp,-MD,.deps/$(*F).pp -c $<
	@-cp .deps/$(*F).pp .deps/$(*F).P; \
	tr ' ' '\012' < .deps/$(*F).pp \
	| sed -e 's/^\\$$//' -e '/^$$/ d' -e '/:$$/ d' -e 's/$$/ :/' \
	>> .deps/$(*F).P; \
	rm .deps/$(*F).pp
