CC = gcc
INC = /path/to/java/include
CFLAGS = -Wall -O3 -fPIC
LDFLAGS = -shared -Wl,-soname,libnative.so -I$(INC) -I$(INC)/linux
LIBS= -lc

all: clean libNativeThread.so

NativeThread.class:
	javac java/NativeThread.java
	mv java/NativeThread.class .

libNativeThread.so: NativeThread.c NativeThread.h
	$(CC) $(CFLAGS) -o libNativeThread.so $(LDFLAGS) NativeThread.c $(LIBS)

NativeThread.h: NativeThread.class
	javah NativeThread

clean:
	-rm -f *.class NativeThread.h libNativeThread*.so
