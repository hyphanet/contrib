#!/bin/sh

g++ -shared -Iinclude -I$JAVA_HOME/include -I$JAVA_HOME/include/linux src/*.cpp -o lib/freenet/support/cpuinformation/jcpuid.so
