#!/bin/sh
#
# build.sh -- main script for building jbigi libraries
#
# depends on the accompanying script build_jbigi.sh (which should *not*
# be run directly)
#

GMP_VERSION="4.2.1"

OS=$(uname -s)
WGET="wget -c"
MAKE="make"

case ${OS} in
MINGW*)
	echo "Building Windows .dll files"
	;;
Linux*)
	echo "Building Linux .so files"
	;;
FreeBSD*)
	echo "Building FreeBSD .so files"
	WGET="fetch -m"
	MAKE="gmake"
	;;
Darwin*)
	echo "Building osx .jnilib's"
	WGET="curl -C - -O"
	;;
*)
	echo "Sorry, unsupported OS/build environment, exiting"
	exit
	;;
esac

# We need -fPIC on x86_64
if [ `uname -m` = "x86_64" -o `uname -m` = "mips" ]
then
	export CFLAGS="-fPIC"
fi


# Don't extract gmp if it's already been done
$WGET ftp://ftp.gnu.org/gnu/gmp/gmp-${GMP_VERSION}.tar.gz

if [ ! -d gmp-${GMP_VERSION} ]
then
	echo "Extracting sources for GNU MP library version ${GMP_VERSION}..."
	tar -xzf gmp-${GMP_VERSION}.tar.gz
fi

# (Re)create directories for jbigi build output
#
# Use "mkdir -p" for all directory creates, to avoid error message
# if directory already exists.  Also allows for multiple levels of
# directories to be created all at once.

echo "(Re)creating directories for jbigi build output"
mkdir -pv bin
mkdir -pv lib/net/i2p/util

# Break out build into Darwin and everything else.
if test ! `uname` = "Darwin"
then

# Build a library version for each of the enumerated (x86) CPU types
#
# "none" = a generic build with no specific CPU type indicated to the 
# compiler

if [ -n "$1" ]
then
	TARGT="$1"
else
	TARGT="none pentium pentiummmx pentium2 pentium3 pentium4 k6 k62 k63 athlon x86_64"
fi

for CPU in $TARGT
do
	mkdir -p bin/${CPU}
	cd bin/${CPU}

	# Build a CPU-specific version of gmp first

	echo "Building GNU MP library for ${CPU}..."

	../../gmp-${GMP_VERSION}/configure --build=${CPU}
	$MAKE

	# Now build a CPU-specific jbigi library
	# linked with the CPU-specific gmp we just built

	echo "Building statically linked jbigi library for ${CPU}..."

	sh ../../build_jbigi.sh static

	# Copy library to its final location with CPU-specific name
	
	case ${OS} in
	MINGW*)
		cp jbigi.dll ../../lib/net/i2p/util/jbigi-windows-${CPU}.dll
		;;
	Linux*)
		cp libjbigi.so ../../lib/net/i2p/util/libjbigi-linux-${CPU}.so
		;;
	FreeBSD*)
		cp libjbigi.so ../../lib/net/i2p/util/libjbigi-freebsd-${CPU}.so
		;;
	esac

	echo "Done!"
	
	# return to original directory for next build
	# (or return user to original directory upon exit)
	cd ../..
done


# Otherwise we are building for Darwin
else
# Darwin build script
# --with-pic is required for static linking
# Only build static library since it would be rare for OSX user to have gmp installed.
mkdir -p bin/none
cd bin/none
../../gmp-${GMP_VERSION}/configure --with-pic
$MAKE
sh ../../build_jbigi.sh static
cp libjbigi.jnilib ../../lib/net/i2p/util/libjbigi-osx-$(uname -m).jnilib
cd ../..
fi


#mkdir -p bin/dynamic
#cd bin/dynamic
#../../gmp-${GMP_VERSION}/configure
#make
#../../build_jbigi.sh dynamic
#case ${OS} in
#MINGW*)
#	cp jbigi.dll ../../lib/jbigi-windows-dynamic.dll;;
#Linux*)
#	cp libjbigi.so ../../lib/libjbigi-linux-dynamic.so;;
#FreeBSD*)
#	cp libjbigi.so ../../lib/libjbigi-freebsd-dynamic.so;;
#esac

# return to original directory for next build
# (or return user to original directory upon exit)
#cd ../..
