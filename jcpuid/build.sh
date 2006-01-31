#!/bin/sh
#
# build.sh -- build an OS/architecture-specific libjcpuid
#
# assumes that we're using the GNU C++ compiler, and that our Java includes
# are relative to the user-defined JAVA_HOME variable (which must be set in
# the user's environment prior to running this script
#

if [ -z "${JAVA_HOME}" ]
then
	{	echo
		echo "Error: JAVA_HOME undefined"
		echo
		echo "Type 'export JAVA_HOME=<path to your local Java installation>'"
		echo "(for example, 'export JAVA_HOME=/usr/local/jdk1.5.0')"
		echo "and run this script again"
		echo
	} >&2
	exit 1
fi

# platform-independent variables
SRC="src/jcpuid.cpp"
OBJDIR="lib/freenet/support/CPUInformation"
CXXFLAGS="-shared  -static -static -libgcc"

# determine the operating system and machine type
OS="$(uname -s|tr "[A-Z]" "[a-z]")" 	# convert any uppercase to lowercase
ARCH=$(uname -m)

# amd64 machine type requires an additional compiler flag to build 
# successfully it won't works as -shared and -static are curently 
# incompatible
[ "${ARCH}" = "x86_64" ] && CXXFLAGS="-shared -static -static-libgcc -fPIC"

# OK, now we can generate our object file's name and the proper include paths
OBJ="libjcpuid-x86-${OS}.so"
INCLUDES="-Iinclude -I${JAVA_HOME}/include -I${JAVA_HOME}/include/${OS}"

# Finally, do the actual compile
echo "Building jcpuid library"
if g++ ${CXXFLAGS} ${INCLUDES} ${SRC} -o ${OBJDIR}/${OBJ}
then
	echo "Build successful!"
	ls -l ${OBJDIR}/${OBJ};
	exit 0
else
	echo "Build failed!" >&2
	exit 1
fi
