CFLAGS = -g -Wall -L. -I.

all: libfcp ic rc install

libfcp: fcp.c
	gcc $(CFLAGS) -shared -o libfcp.so fcp.c

ic: libfcp ic.c
	gcc $(CFLAGS) -o ic ic.c -lfcp -lpthread

rc: libfcp rc.c
	gcc $(CFLAGS) -o rc rc.c -lfcp -lpthread

install:
