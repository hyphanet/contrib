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
