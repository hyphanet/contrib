#!/bin/bash

# Modification of i2p's i2p-0.7.6/core/c/jbigi/mbuild-all.sh

# FIXME: make the thing name files differently when compiled on 32/64 platforms
# currently target "none" will build either a 32-bit or 64-bit binary depending
# on your compiler.

WGET=""                                     # custom URL retrieval program
VER="4.3.1"                                 # version of GMP to retrieve

# TO-DO: Darwin.

# Note: You will have to add the CPU ID for the platform in the CPU ID code
# for a new CPU. Just adding them here won't let I2P use the code!

#
# If you know of other platforms i2p on linux works on,
# please add them here.
# Do NOT add any X86 platforms, do that below in the x86 platform list.
#
MISC_LINUX_PLATFORMS="hppa2.0 alphaev56 armv5tel mips64el itanium itanium2 ultrasparc2 ultrasparc2i alphaev6 powerpc970 powerpc7455 powerpc7447"

#
# If you know of other platforms i2p on FREEBSD works on,
# please add them here.
# Do NOT add any X86 platforms, do that below in the x86 platform list.
#
MISC_FREEBSD_PLATFORMS="alphaev56 ultrasparc2i"

#
# MINGW/Windows??
#
MISC_MINGW_PLATFORMS=""

#
# Are there any other X86 platforms that work on i2p? Add them here.
#
# Oddly athlon64 builds.... I wonder what others can :-)
#
X86_PLATFORMS="pentium pentiummmx pentium2 pentium3 pentium4 k6 k62 k63 athlon pentiumm core2 athlon64 geode atom"

#
# Platforms which need to link against PIC code (-fPIC)
#
PLAT_PIC="core2 athlon64 atom"
# We need -fPIC on x86_64
if [ `uname -m` = "x86_64" -o `uname -m` = "mips" ]; then PLAT_PIC="none $PLAT_PIC"; fi

#
# You should not need to edit anything below this comment.
#

MINGW_PLATFORMS="${X86_PLATFORMS} ${MISC_MINGW_PLATFORMS}"
LINUX_PLATFORMS="${X86_PLATFORMS} ${MISC_LINUX_PLATFORMS}"
FREEBSD_PLATFORMS="${X86_PLATFORMS} ${MISC_FREEBSD_PLATFORMS}"

case `uname -sr` in
MINGW*)
	PLATFORM_LIST="${MINGW_PLATFORMS}"
	NAME="jbigi"
	TYPE="dll"
	TARGET="-windows-"
	LIBFILE="jbigi.dll"
	LINKFLAGS="-shared -Wl,--kill-at"
	PLAT_MSG="Building windows .dlls";;
Linux*)
	PLATFORM_LIST="${LINUX_PLATFORMS}"
	NAME="libjbigi"
	TYPE="so"
	TARGET="-linux-"
	LIBFILE="libjbigi.so"
	LINKFLAGS="-shared -Wl,-soname,$LIBFILE"
	PLAT_MSG="Building linux .sos";;
FreeBSD*)
	PLATFORM_LIST="${FREEBSD_PLATFORMS}"
	NAME="libjbigi"
	TYPE="so"
	TARGET="-freebsd-"
	LIBFILE="libjbigi.so"
	LINKFLAGS="-shared -Wl,-soname,$LIBFILE"
	PLAT_MSG="Building freebsd .sos";;
*)
	echo "Unsupported build environment"
	exit;;
esac

if [ -n "$WGET" ]; then
	get_latest() { if ! $WGET "$@"; then echo "could not download $@; abort"; exit 2; fi }
elif which wget > /dev/null; then
	get_latest() { if ! wget -N "$@"; then echo "could not download $@; abort"; exit 2; fi }
elif which curl > /dev/null; then
	get_latest() { if ! curl -O "$@"; then echo "could not download $@; abort"; exit 2; fi }
else
	echo "could not find a suitable URL-retrieval program. try setting the WGET variable "
	echo "near the top of this file."
	exit 6
fi

function make_static {
	echo "Attempting .${4} creation for ${3}${5}${2}"
	make $LIBFILE || return 1
	cp ${3}.${4} ../../lib/net/i2p/util/${3}${5}${2}.${4}
	return 0
}

function is_pic {
	for i in $PLAT_PIC; do [ $i = $1 ] && return 0; done;
	return 1;
}

function make_file {
	# Nonfatal bail out on Failed build.
	echo "Attempting build for ${3}${5}${2}"
	make && return 0
	cd ..
	rm -R "$2"
	echo -e "\n\nFAILED! ${3}${5}${2} not made.\a"
	sleep 1
	return 1
}

function configure_file {
	echo -e "Attempting configure for ${3}${5}${2}"
	sleep 1
	if is_pic ${2}; then FLAGS_PIC=--with-pic; fi
	# Nonfatal bail out on unsupported platform
	../../gmp-${1}/configure $FLAGS_PIC --host=${2} && return 0;
	cd ..
	rm -R "$2"
	echo -e "\n\nSorry, ${3}${5}${2} is not supported on your build environment.\a"
	sleep 1
	return 1
}

function build_file {
	echo -e "\n\n== Building for ${3}${5}${2} ==\n"
	configure_file "$1" "$2" "$3" "$4" "$5"  && make_file "$1" "$2" "$3" "$4" "$5" && make_static "$1" "$2" "$3" "$4" "$5" && return 0
	echo -e "\nError building ${3}${5}${2}!\n\a"
	sleep 1
	return 1
}

get_latest ftp://ftp.gnu.org/gnu/gmp/gmp-${VER}.tar.bz2

echo "Extracting GMP Version $VER ..."
tar -xf gmp-$VER.tar.bz2 || ( echo "Error in tarball file!" ; exit 1 )
cp jbigi/include/jbigi.h gmp-$VER
cp jbigi/src/jbigi.c gmp-$VER
echo "Attaching jbigi to GMP's Makefile.in"
cat >> gmp-$VER/Makefile.in <<EOF

# This section added by the build script for jbigi

jbigi.o: jbigi.c
	\$(LTCOMPILE) -c \$(srcdir)/jbigi.c

$LIBFILE: jbigi.o .libs/libgmp.a
	\$(LINK) -rpath \$(libdir) $LINKFLAGS jbigi.o .libs/libgmp.a
EOF

if [ ! -d bin ]; then
	mkdir bin
fi
if [ ! -d lib/net/i2p/util ]; then
	mkdir -p lib/net/i2p/util
fi

# Don't touch this one.
NO_PLATFORM=none

if [ -z "$1" ]; then PLATFORMS="$NO_PLATFORM $PLATFORM_LIST";
else PLATFORMS="$@"; fi

echo "$PLAT_MSG for target platforms $PLATFORMS"

FAILED=
for x in $PLATFORMS
do
	if ! (
		if [ ! -d bin/$x ]; then
			mkdir bin/$x
			cd bin/$x
		else
			cd bin/$x
			rm -Rf *
		fi

		build_file "$VER" "$x" "$NAME" "$TYPE" "$TARGET"
		exit $?

	); then FAILED="$x $FAILED"; fi
done

if [ -z "$FAILED" ]; then echo -e "\nAll targets built successfully: $PLATFORMS";
else echo -e "\nBuild complete; failed targets: $FAILED"; fi
exit 0
