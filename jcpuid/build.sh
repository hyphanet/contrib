#!/bin/sh

g++ -shared -static -static-libgcc -Iinclude -I$JAVA_HOME/include -I$JAVA_HOME/include/linux src/*.cpp -o lib/freenet/support/CPUInformation/libjcpuid-x86-linux.so
