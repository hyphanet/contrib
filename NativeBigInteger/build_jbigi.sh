#!/bin/sh
# When executed in Mingw: Produces an jbigi.dll
# When executed in Linux: Produces an libjbigi.so
# When executed in OSX: Produces an libjbigi.jnilib

CC="gcc"

case `uname -sr` in
MINGW*)
	JAVA_HOME="c:/j2sdk1.4.2_05"
	COMPILEFLAGS="-Wall"
	INCLUDES="-I. -I../../jbigi/include -I$JAVA_HOME/include/win32/ -I$JAVA_HOME/include/"
	LINKFLAGS="-shared -Wl,--kill-at"
	LIBFILE="jbigi.dll";;
Darwin*)
	COMPILEFLAGS="-Wall"
	INCLUDES="-I. -I../../jbigi/include"
	LINKFLAGS="-dynamiclib"
	LIBFILE="libjbigi.jnilib";;
FreeBSD*)
	COMPILEFLAGS="-fPIC -Wall"
	INCLUDES="-I. -I../../jbigi/include -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -I/usr/local/include"
	LINKFLAGS="-shared -Wl,-soname,libjbigi.so -L/usr/local/lib"
	LIBFILE="libjbigi.so";;
*)
	COMPILEFLAGS="-fPIC -Wall"
	INCLUDES="-I. -I../../jbigi/include -I$JAVA_HOME/include -I$JAVA_HOME/include/linux"
	LINKFLAGS="-shared -Wl,-soname,libjbigi.so"
	LIBFILE="libjbigi.so";;
esac

#To link dynamically to GMP (use libgmp.so or gmp.lib), uncomment the first line below
#To link statically to GMP, uncomment the second line below
if [ "$1" = "dynamic" ]
then
	echo "Building jbigi lib that is dynamically linked to GMP" 
	LIBPATH="-L.libs"
	INCLUDELIBS="-lgmp"
else
	echo "Building jbigi lib that is statically linked to GMP"
	STATICLIBS=".libs/libgmp.a"
fi

# cleanup
rm -f jbigi.o $LIBFILE

# building
echo "Compiling ../../jbigi/src/jbigi.c -> jbigi.o ..."
$CC -c $COMPILEFLAGS $INCLUDES ../../jbigi/src/jbigi.c

echo "Linking jbigi.o + $STATICLIBS -> $LIBFILE ..."
$CC $LINKFLAGS $INCLUDELIBS -o $LIBFILE jbigi.o $STATICLIBS

if [ -f $LIBFILE ]
then
	echo "$LIBFILE is done"
else
	echo "Error building $LIBFILE"
fi

#echo ""
#echo "Doing an ant build..."
#ANT="ant"
#JAVA="java"
#(cd ../java/ ; $ANT build)
#
#echo ""
#echo "Built, now testing... This will take a while."
#LD_LIBRARY_PATH=. $JAVA -cp ../java/build/i2p.jar -DloggerConfigLocation=../../installer/java/src/logger.config.template net.i2p.util.NativeBigInteger
#
#
#echo ""
#echo ""
#echo "Test complete. Please review the lines 'native run time:', 'java run time:', and 'native = '"

