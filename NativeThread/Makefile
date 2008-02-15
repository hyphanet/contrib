CC = gcc
INC = /path/to/java/include
CFLAGS = -Wall -O3
LDFLAGS = -shared -Wl,-soname,libnative.so -I$(INC) -I$(INC)/linux
LIBS=-static -lc
FREEENETDIR=/home/nextgens/repo/freenet/

all: clean libNativeThread.so

NativeThread.java:
	ln -s $(FREEENETDIR)/src/freenet/support/io/NativeThread.java

NativeThread.class: NativeThread.java
	javac NativeThread.java

libNativeThread.so: NativeThread.c NativeThread.h
	$(CC) $(CFLAGS) -o libNativeThread.so $(LDFLAGS) NativeThread.c $(LIBS)

NativeThread.h: NativeThread.class
	javah NativeThread

clean:
	-rm -f *.class NativeThread.h libNativeThread*.so NativeThread.java
