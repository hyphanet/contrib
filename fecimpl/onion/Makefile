# Modify to point to your Freenet tree or jar.
FREENET_CP = ../../..

ONION_CP = ./alien/common.jar:./alien/fec.jar
DISTRO_TAG = onionfec-0.1

classes:	FECUtils.class \
		OnionFECEncoder.class \
		OnionFECDecoder.class \
		NativeOnionFECEncoder.class \
		NativeOnionFECDecoder.class

jar:		classes
		jar -cvf ${DISTRO_TAG}.jar ./*.class

snap:		clean
		rm -f *~
		zip  ${DISTRO_TAG}_src.zip *.java Makefile README alien/*

clean:
		rm -f *.class
		rm -f *.jar
		rm -f *.zip
		rm -rf distro

distro:         snap jar
		rm -rf distro
		mkdir distro/
		cp ./alien/* distro
		cp *.jar distro
		cp *.zip distro
		cp README distro
		cd distro; zip ../${DISTRO_TAG}.zip *
		rm -f *.class
		rm -f *.jar
		rm -rf distro
		rm ${DISTRO_TAG}_src.zip 

%.class:	%.java
	CLASSPATH=.:${ONION_CP}:${FREENET_CP}:${CLASSPATH} javac  -target 1.1 $<

.PHONY:		clean jar distro




