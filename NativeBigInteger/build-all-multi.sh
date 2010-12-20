#!/bin/sh

# Modification of i2p's i2p-0.7.6/core/c/jbigi/mbuild-all.sh
#
# This build script will produce jbigi shared libraries that are statically
# linked with libgmp, in lib/net/i2p/util and named *jbigi-K-CPU_ABI.*, where
# K is the kernel (eg. linux), CPU is the processor type (eg. core2), and ABI
# is the instruction set (eg. 32 or 64).
#
# If you are on a 64-bit platform with gcc-multilib installed, you can compile
# 32-bit and 64-bit binaries for a bunch of architectures with:
#
# $ ABI=32 ./build-all-multi.sh
# $ ./build-all-multi.sh core2 athlon64 pentium4 atom
#
# Otherwise, just run
#
# $ ./build-all-multi.sh
#
# TO-DO: Test on Darwin, FreeBSD, mingw32,, mingw64
#
# FIXME: the binaries that this build script creates is not yet name-compatible
# with the jcpuid supplied. It also includes some newer processors that are not
# recognised by jcpuid. So don't deploy them with ext until this is fixed.
#
# FIXME: using GMP 5.0.1 currently gives
#   libtool: link: can not build a shared library
# when building libjbigi.so
#

WGET=""                                     # custom URL retrieval program
VER="4.3.1"                                 # version of GMP to retrieve

# Environment variables (NAME=default)
#FAIL_FAST=true                              # fail overall if a platform fails
#JAVA_HOME=(2 up from javac's realpath)      # java home directory

# Note: You will have to add the CPU ID for the platform in the CPU ID code
# for a new CPU. Just adding them here won't let I2P use the code!

# General x86 platforms that all kernels can build for
X86_PLATFORMS="pentium pentiummmx pentium2 pentium3 pentium4 pentiumm k6 k62 k63 athlon athlon64 core2 geode atom"

# Misc kernel-specific platforms
MISC_LINUX_PLATFORMS="hppa2.0 alphaev56 armv5tel mips64el itanium itanium2 ultrasparc2 ultrasparc2i alphaev6 powerpc970 powerpc7455 powerpc7447"
MISC_FREEBSD_PLATFORMS="alphaev56 ultrasparc2i"
MISC_DARWIN_PLATFORMS="powerpc970 powerpc7455 powerpc7447"
MISC_MINGW_PLATFORMS=""

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


###########################################################################
# The rest of this file should not need to be changed.
###########################################################################

# Error codes:
# 1: (at least) some tasks not completed
# 2: error retrieving something from a remote location
# 3: error extracting an archive
# 6: unsupported environment (eg. unavailable utility program)

MINGW_PLATFORMS="${X86_PLATFORMS} ${MISC_MINGW_PLATFORMS}"
LINUX_PLATFORMS="${X86_PLATFORMS} ${MISC_LINUX_PLATFORMS}"
FREEBSD_PLATFORMS="${X86_PLATFORMS} ${MISC_FREEBSD_PLATFORMS}"
DARWIN_PLATFORMS="${X86_PLATFORMS} ${MISC_DARWIN_PLATFORMS}"

if ! which realpath > /dev/null; then realpath() { readlink -f "$@"; }; fi

if [ -z "$FAIL_FAST" ]; then FAIL_FAST=true; fi
if [ -z "$JAVA_HOME" ]; then
	export JAVA_HOME=$(dirname $(dirname $(realpath $(which javac))))
	echo "!!! \$JAVA_HOME not set, automatically setting to $JAVA_HOME"
fi

# Platform-specifc variables. Default variables are below this section.
case `uname -s` in
MINGW*)
	PLATFORM_LIST="${MINGW_PLATFORMS}"
	NAME="jbigi"
	TYPE="dll"
	TARGET="-windows-"
	LINKFLAGS="-shared -Wl,--kill-at"
	PLAT_MSG="Building windows .dlls"
	#JAVA_HOME="c:/j2sdk1.4.2_05"
	JINCLUDES="-I\$(JAVA_HOME)/include/win32"
	;;
Linux*)
	PLATFORM_LIST="${LINUX_PLATFORMS}"
	NAME="libjbigi"
	TYPE="so"
	TARGET="-linux-"
	PLAT_MSG="Building linux .sos"
	JINCLUDES="-I\$(JAVA_HOME)/include/linux"
	;;
FreeBSD*)
	PLATFORM_LIST="${FREEBSD_PLATFORMS}"
	NAME="libjbigi"
	TYPE="so"
	TARGET="-freebsd-"
	PLAT_MSG="Building freebsd .sos"
	JINCLUDES="-I\$(JAVA_HOME)/include/linux -I/usr/local/include"
	;;
Darwin*)
	PLATFORM_LIST="${DARWIN_PLATFORMS}"
	NAME="libjbigi"
	TYPE="jnilib"
	TARGET="-darwin-"
	LINKFLAGS="-dynamiclib"
	PLAT_MSG="Building Darwin .jnilibs"
	#JAVA_HOME="/Library/Java/Home"
	;;
*)
	echo "Unsupported build environment"
	exit 6
	;;
esac

# Default variables
JINCLUDES="-I\$(JAVA_HOME)/include $JINCLUDES"
if [ -z "$LIBFILE" ]; then LIBFILE="$NAME.$TYPE"; fi
if [ -z "$LINKFLAGS" ]; then LINKFLAGS="-shared -Wl,-soname,$LIBFILE"; fi

get_latest() { if ! $WGET "$@"; then echo "could not download $@; abort"; exit 2; fi }

if [ -n "$WGET" ]; then true;
elif which wget > /dev/null; then WGET="wget -N";
elif which curl > /dev/null; then WGET="curl -O";
elif which fetch > /dev/null; then WGET="fetch -m";
else
	echo "could not find a suitable URL-retrieval program. try setting the WGET variable "
	echo "near the top of this file."
	exit 6
fi

is_pic() {
	for i in $PLAT_PIC; do [ $i = $1 ] && return 0; done;
	return 1;
}

make_jbigi_static() {
	echo "Attempting make for ${3}${5}${2}.${4}"
	make $LIBFILE && eval $(grep "^ABI=" config.log) && cp "${3}.${4}" "../../lib/net/i2p/util/${3}${5}${2}_$ABI.${4}" && return 0;
	echo "Failed to make ${3}${5}${2}.${4}"
	sleep 1 && return 1
}

test_gmp() {
	eval $(grep "^ABI=" config.log)
	eval $(grep "^build=" config.log)
	echo "Testing ${3}${5}${2} by running it on the current CPU ($build)."
	{ status=$( { { make check 2>&1; echo $? >&3; } | tee make_check.log >&4; } 3>&1 ); } 4>&1;
	# in bash, one would just do "set -o pipefail; { make check 2>&1 | tee make_check.log; } && return 0"
	case $status in
	0) return 0;;
	esac
	cat <<- EOF
	================================================================================
	Tests failed. However, note that if the current CPU does not support the entire
	instruction set of ${2}_$ABI, then these test results are invalid and you need
	to re-run it on a machine that *is* compatible with ${2}_$ABI.
	================================================================================
	EOF
	sleep 1 && return 1
}

make_gmp() {
	# Nonfatal bail out on Failed build.
	echo "Attempting make for ${3}${5}${2}"
	#make && make check >make_check.log 2>&1 && return 0
	make && return 0
	echo && echo "Failed to make ${3}${5}${2}."
	sleep 1 && return 1
}

configure_gmp() {
	echo "Attempting configure for ${3}${5}${2}"
	if is_pic ${2}; then FLAGS_PIC=--with-pic; fi
	# Nonfatal bail out on unsupported platform
	../../gmp-${1}/configure $FLAGS_PIC --host=${2} && return 0;
	echo && echo "Failed to configure for ${3}${5}${2}; maybe it isn't supported on your build environment."
	sleep 1 && return 1
}

build_jbigi() {
	# Error codes:
	#
	# 0: successful build
	# 1: failed build
	# 2: failed test

	echo && echo && echo "== Building for ${3}${5}${2} ==" && echo
	TEST_EXIT=0
	while true; do
		configure_gmp "$@" || break
		make_gmp "$@" || break
		test_gmp "$@" || TEST_EXIT=2
		make_jbigi_static "$@" || break
		return $TEST_EXIT
	done
	echo && echo "Error building ${3}${5}${2}!"
	sleep 1 && return 1
}

get_latest ftp://ftp.gnu.org/gnu/gmp/gmp-${VER}.tar.bz2

echo "Extracting GMP Version $VER ..."
tar -xf gmp-$VER.tar.bz2 || ( echo "Error in tarball file!" ; exit 3 )
cp jbigi/include/jbigi.h gmp-$VER
cp jbigi/src/jbigi.c gmp-$VER
echo "Attaching jbigi to GMP's Makefile.in"
cat >> gmp-$VER/Makefile.in <<EOF

# This section added by the build script for jbigi

jbigi.o: jbigi.c
	\$(LTCOMPILE) $JINCLUDES -c \$(srcdir)/jbigi.c

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
TEST_FAILED=
for x in $PLATFORMS
do
	(
		if [ -d "bin/$x" ]; then rm -rf bin/$x; fi
		mkdir -p bin/$x
		cd bin/$x

		build_jbigi "$VER" "$x" "$NAME" "$TYPE" "$TARGET"
		exit $?
	);

	case $? in
	0) ;;
	1) FAILED="$x $FAILED"; if $FAIL_FAST; then exit 1; fi;;
	2) TEST_FAILED="$x $TEST_FAILED";;
	*) "bug in build script?"; exit 1;;
	esac

done

echo
EXIT=0
echo "Build complete.";
if [ -n "$FAILED" ]; then echo "Attempted targets: $PLATFORMS"; echo "Failed targets: $FAILED"; EXIT=1;
else echo "All targets built successfully: $PLATFORMS"; fi

if [ -n "$TEST_FAILED" ]; then
	cat <<- EOF
	Failed test targets: $TEST_FAILED
	However, note that if the current CPU does not support the entire instruction
	set of a given target, then the test results for that target are invalid, and
	you need to re-run it on a machine that *is* compatible with that target.
	EOF
	EXIT=1
fi

exit $EXIT
