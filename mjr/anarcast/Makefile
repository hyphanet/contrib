CFLAGS = -g -Wall
PROGRAMS = server inform insert request
DEPS = anarcast.h Makefile

all: $(PROGRAMS) proxy

%: %.c $(DEPS)
	gcc $(CFLAGS) -o $@ $<

inform: inform.c $(DEPS)
	gcc $(CFLAGS) -o inform inform.c -pthread

proxy: proxy.c $(DEPS)
	gcc $(CFLAGS) -o proxy proxy.c -pthread -lm

clean:
	rm -f $(PROGRAMS) proxy
