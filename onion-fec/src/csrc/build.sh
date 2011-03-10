#!/bin/sh
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which javac)")")")"
DIST_DIR=../../bin/lib/"$(uname -s | tr A-Z a-z)"-"$(uname -m)"

set -x

JAVA_HOME="$JAVA_HOME" make
cp libfec*.so "$DIST_DIR"
