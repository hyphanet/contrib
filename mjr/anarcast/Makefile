CFLAGS = -g -Wall
PROGRAMS = server insert request inform proxy
DEPS = anarcast.h graphs.h crypt.c Makefile

all: $(PROGRAMS)

%: %.c $(DEPS)
	gcc $(CFLAGS) -o $@ $<

inform: inform.c $(DEPS)
	gcc $(CFLAGS) -o inform inform.c -pthread

proxy: proxy.c $(DEPS)
	gcc $(CFLAGS) -o proxy proxy.c -pthread -lm

clean:
	rm -f $(PROGRAMS)
