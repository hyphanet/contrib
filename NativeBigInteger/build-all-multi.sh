#!/bin/sh

# Modification of i2p's i2p-0.7.6/core/c/jbigi/mbuild-all.sh

# This build script will produce shared libraries in lib/net/i2p/util of the
# form *jbigi-K-CPU_ABI.*, where K is the kernal (eg. linux), CPU is the
# processor type (eg. core2), and ABI is the instruction set (eg. 32 or 64).

# If you are on a 64-bit platform with gcc-mulitlib installed, you can compile
# 32-bit and 64-bit binaries for a bunch of architectures with:

# ABI=32 ./build-all-multi.sh
# ./build-all-multi.sh core2 athlon64 pentium4 atom

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
# Platforms which need to link against PIC code (-fPIC). These are generally
# CPUs that use the x86_64 instruction set
#
# ABI=32 doesn't require -fPIC
if [ "$ABI" = 32 ]; then PLAT_PIC="";
# if no ABI is set, ./configure defaults to ABI=64, which require -fPIC
else PLAT_PIC="core2 athlon64 pentium4 atom"; fi
# If our own platform is x86_64 we need to set it too
if [ `uname -m` = "x86_64" -o `uname -m` = "mips" ]; then PLAT_PIC="none $PLAT_PIC"; fi

#echo "PLAT_PIC = $PLAT_PIC"

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
elif which fetch > /dev/null; then
	get_latest() { if ! fetch -m "$@"; then echo "could not download $@; abort"; exit 2; fi }
else
	echo "could not find a suitable URL-retrieval program. try setting the WGET variable "
	echo "near the top of this file."
	exit 6
fi

if which tailf > /dev/null; then tailf="tailf";
else tailf="tail -f"; fi

make_static() {
	echo "Attempting make for ${3}${5}${2}.${4}"
	make $LIBFILE && eval $(grep "ABI=" config.log) && cp ${3}.${4} ../../lib/net/i2p/util/${3}${5}${2}_${ABI}.${4} && return 0;
	echo "Failed to make ${3}${5}${2}.${4}"
	sleep 1 && return 1
}

is_pic() {
	for i in $PLAT_PIC; do [ $i = $1 ] && return 0; done;
	return 1;
}

make_file() {
	# Nonfatal bail out on Failed build.
	echo "Attempting make for ${3}${5}${2}"
	make && return 0
	cd .. && rm -R "$2"
	echo && echo "Failed to make ${3}${5}${2}."
	sleep 1 && return 1
}

configure_file() {
	echo "Attempting configure for ${3}${5}${2}"
	if is_pic ${2}; then FLAGS_PIC=--with-pic; fi
	# Nonfatal bail out on unsupported platform
	../../gmp-${1}/configure $FLAGS_PIC --host=${2} && return 0;
	cd .. && rm -rf "$2"
	echo && echo "Failed to configure for ${3}${5}${2}; maybe it isn't supported on your build environment.\a"
	sleep 1 && return 1
}

build_file() {
	echo && echo && echo "== Building for ${3}${5}${2} ==" && echo
	configure_file "$1" "$2" "$3" "$4" "$5"  && make_file "$1" "$2" "$3" "$4" "$5" && make_static "$1" "$2" "$3" "$4" "$5" && return 0
	echo && echo "Error building ${3}${5}${2}!"
	sleep 1 && return 1
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

if [ -z "$FAILED" ]; then echo && echo "All targets built successfully: $PLATFORMS";
else echo && echo "Build complete; failed targets: $FAILED"; fi
exit 0
