PROGRAMS = server inform insert request

all: $(PROGRAMS)

%: %.c anarcast.h Makefile
	gcc -g -Wall -o $@ $<

proxy:
	gcc -g -Wall -o proxy proxy.c -pthread

clean:
	rm -f $(PROGRAMS) proxy
