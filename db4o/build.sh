#!/bin/sh
# Eventually this is going to be an ant script

REPO="https://source.db4o.com/db4o/trunk"
SVN_REV="14653"
BND_VER="0.0.384"

# pull in core projects
for i in db4o.cs db4o.cs.optional db4oj db4oj.optional decaf; do svn checkout -r $SVN_REV "$REPO/$i"; done

# pull, cull, and patch db4obuild
svn checkout -r $SVN_REV --depth=files "$REPO/db4obuild"
cp machine.properties db4obuild/
patch -p1 < build.diff
cd db4obuild
sed -i -e "s/bnd-0.0.337.jar/bnd-$BND_VER.jar/g" -e "s/@svnRevision@/$SVN_REV/g" common.xml
mkdir lib
for i in config scripts src; do svn checkout -r $SVN_REV "$REPO/db4obuild/$i"; done
rm -rf src/com/db4o/devtools/ant/SvnSync.java
rm -rf src/com/db4o/devtools/ant/SvnRevision.java
rm -rf src/com/db4o/osgi/
cd ..

# remove pre-built binaries
find . -name '*.jar' | xargs rm -rf

# build decaf.annotations
cd decaf/decaf.annotations
ant || exit 1
cd ../..
ln -s ../../decaf/decaf/lib/decaf-annotations.jar db4oj/lib/decaf-annotations.jar

# retrieve bnd
wget "http://www.aqute.biz/repo/biz/aQute/bnd/$BND_VER/bnd-$BND_VER.jar" && mv "bnd-$BND_VER.jar" db4obuild/lib/

# do the build!
cd db4obuild
CLASSPATH=/usr/share/java/ant-contrib.jar ant -f build-db4obuild.xml || exit 1
CLASSPATH=/usr/share/java/ant-contrib.jar ant -f build-java.xml clean build.db4ojdk1.5 || exit 1
cd ..


# below are some notes on the convoluted build process, including hints on how to incorporate tests in

#for i in bloat db4o.cs db4o.cs.optional db4o.instrumentation db4o.net db4obuild db4obuild.tests \
#  db4oj db4oj.optional db4oj.tests db4onqopt db4otaj db4otaj.tests.integration \
#  db4otools db4ounit db4ounit.extensions decaf; do
#	svn checkout -r 14653 "https://source.db4o.com/db4o/trunk/$i"
#done
#CLASSPATH=/usr/share/java/ant-contrib.jar ant -f build-java.xml clean build.nodep1.5

#
# misc
# - db4obuild/lib/svnkit/svnkit.jar -- needed to detect svn revision, replace token
#
# instrumentation
# - db4o.instrumentation/lib/bloat-1.0.jar
#
# tests
# + db4ounit.extensions/lib/easymock/easymock.jar -- needed for tests
# + db4oj.tests/src/com/db4o/test/legacy/soda/engines/db4o/db4o.jar
# + db4otaj.tests.integration/lib/db4o-7.9.93.13038-all-java1.2.jar
# + db4otaj.tests.integration/lib/db4o-7.9.93.13038-db4ounit-java1.2.jar
#
# other
# - db4otools/lib/ant-launcher.jar
# - db4o.instrumentation/lib/ant-launcher.jar
# - db4o.instrumentation/lib/ant.jar
# - db4obuild/maven/lib/maven-ant-tasks-2.0.10.jar
# - db4obuild/config/osgi/org.eclipse.osgi_3.2.2.R32x_v20070118.jar
# - db4obuild/lib/macker/jdom.jar
# - db4obuild/lib/macker/commons-lang.jar
# - db4obuild/lib/macker/bcel.jar
# - db4obuild/lib/macker/macker.jar
# - db4obuild/lib/macker/innig-util.jar
# - db4obuild/lib/macker/jakarta-regexp.jar
# - db4obuild/lib/tidy.jar
# - db4obuild/lib/junit.jar
# - db4obuild/lib/org.eclipse.osgi_3.3.jar
# - db4obuild/lib/ant.jar
#
