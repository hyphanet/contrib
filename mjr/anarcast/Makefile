PROGRAMS = server inform insert request
DEPS = anarcast.h Makefile

all: $(PROGRAMS) proxy

%: %.c $(DEPS)
	gcc -g -Wall -o $@ $<

proxy: proxy.c $(DEPS)
	gcc -g -Wall -o proxy proxy.c -pthread

clean:
	rm -f $(PROGRAMS) proxy
