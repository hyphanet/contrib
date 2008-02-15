CC = gcc
INC = /path/to/java/include
CFLAGS = -Wall -O3
LDFLAGS = -shared -Wl,-soname,libnative.so -I$(INC) -I$(INC)/linux
LIBS=-static -lc
FREEENETDIR=/home/nextgens/repo/freenet/

all: clean libNativeThread.so

NativeThread.java:
	mkdir -p freenet/support/io
	ln -s $(FREEENETDIR)/src/freenet/support/io/NativeThread.java freenet/support/io

NativeThread.class: NativeThread.java
	javac freenet/support/io/NativeThread.java

libNativeThread.so: freenet_support_io_NativeThread.c NativeThread.h
	$(CC) $(CFLAGS) -o libNativeThread.so $(LDFLAGS) freenet_support_io_NativeThread.c $(LIBS)

NativeThread.h: NativeThread.class
	javah  freenet.support.io.NativeThread

clean:
	-rm -rf freenet *.class *.h libNativeThread*.so NativeThread.java
