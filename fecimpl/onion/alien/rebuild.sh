#!/bin/sh

# Quick and dirty script to rebuild fixed fec.jar

rm -rf fec_rebuild
mkdir fec_rebuild
mkdir fec_rebuild/lib
cp fec_src/lib/fec.properties fec_rebuild/lib

#javac -classpath .:./common.jar -d fec_rebuild -g -target 1.1 `find fec_src -name '*.java'` 
javac -classpath .:./common.jar -d fec_rebuild -target 1.1 `find fec_src -name '*.java'` 
#jikes -classpath .:./common.jar:$JIKESPATH -d fec_rebuild  `find fec_src -name '*.java'` 
cd fec_rebuild
jar cvf minimal_fec.jar `find -name '*.class'; find -name '*.properties'` 
mv minimal_fec.jar ../fec.jar
cd ..

rm -rf fec_rebuild