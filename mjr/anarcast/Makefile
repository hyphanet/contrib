PROGRAMS = server inform insert request
DEPS = anarcast.h Makefile

all: $(PROGRAMS)

%: %.c $(DEPS)
	gcc -g -Wall -o $@ $<

proxy: proxy.c $(DEPS)
	gcc -g -Wall -o proxy proxy.c -pthread

clean:
	rm -f $(PROGRAMS) proxy
