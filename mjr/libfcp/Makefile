CFLAGS = -g -Wall -L. -I.

all: libfcp ic rc

libfcp:
	gcc $(CFLAGS) -shared -o libfcp.so fcp.c -pthread

ic:
	gcc $(CFLAGS) -o ic ic.c -lfcp -pthread

rc:
	gcc $(CFLAGS) -o rc rc.c -lfcp -pthread

install:
	cp libfcp.so /usr/local/include
	cp ic /usr/local/bin
	cp rc /usr/local/bin

.PHONY: libfcp ic rc
