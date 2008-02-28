CC = gcc
INC = $(JAVA_HOME)/include
CFLAGS = -Wall -O3 -fPIC
LDFLAGS = -shared -Wl,-soname,libnative.so -I$(INC) -I$(INC)/linux
LIBS = -lc

all: clean libNativeThread.so

NativeThread.class:
	javac java/NativeThread.java
	mv java/NativeThread.class freenet/support/io/

libNativeThread.so: NativeThread.c NativeThread.h
	$(CC) $(CFLAGS) -o libNativeThread.so $(LDFLAGS) NativeThread.c $(LIBS)

NativeThread.h: NativeThread.class
	javah -o NativeThread.h -classpath . freenet.support.io.NativeThread

clean:
	-rm -f freenet/support/io/NativeThread.class *.class NativeThread.h libNativeThread*.so
