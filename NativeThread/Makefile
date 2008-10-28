CC = gcc
INC = $(JAVA_HOME)/include
CFLAGS = -Wall -O3 -fPIC
LDFLAGS = -shared -Wl,-soname,libnative.so -I$(INC) -I$(INC)/linux
LIBS = -lc

all: clean libNativeThread.so

libNativeThread.so: NativeThread.c NativeThread.h
	$(CC) $(CFLAGS) -o libNativeThread.so $(LDFLAGS) NativeThread.c $(LIBS)

NativeThread.h:
	javah -o NativeThread.h -classpath $(CLASSPATH) freenet.support.io.NativeThread

clean:
	-rm -f *.class NativeThread.h libNativeThread*.so
