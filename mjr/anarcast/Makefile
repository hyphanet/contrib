CFLAGS = -O -Wall
PROGRAMS = inform insert lc le proxy register request server sync trim
DEPS = anarcast.h graphs.h crypt.c Makefile

all: $(PROGRAMS)

%: %.c $(DEPS)
	gcc $(CFLAGS) -o $@ $<

proxy: proxy.c $(DEPS)
	gcc $(CFLAGS) -o proxy proxy.c -pthread -lm

clean:
	rm -f $(PROGRAMS)

install: all
	cp -f lc le ri /usr/local/bin/
	@echo "The client tools (lc, le, ri) have been installed. Other daemons and utilities"
	@echo "must be user-installed. See the INSTALL file for instructions. It's easy."
