# Modify to point to your Freenet tree or jar.

FREENET_CP=../../../freenet/build
ONION_CP=./alien/onion-common.jar:./alien/onion-fec.jar


#FREENET_CP = ../../..
#ONION_CP = ./alien/onion-common.jar:./alien/onion-fec.jar

DISTRO_TAG=onionfec-0.2

LOCAL_CP=.:${ONION_CP}:${FREENET_CP}

JAVAC=javac
JAVAC_OPTS=-target 1.1 -classpath ${LOCAL_CP}

#JAVAC=jikes
#JAVAC_OPTS=-classpath ${LOCAL_CP}:${JIKESPATH} 


classes:	FECUtils.class \
		OnionFECEncoder.class \
		OnionFECDecoder.class

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
		cp ./alien/*.jar distro
		cp ./alien/onion_LICENSE distro
		cp *.jar distro
		cp *.zip distro
		cp README distro
		cd distro; zip ../${DISTRO_TAG}.zip *
		rm -f *.class
		rm -f *.jar
		rm -rf distro
		rm ${DISTRO_TAG}_src.zip 

%.class:	%.java
	${JAVAC} ${JAVAC_OPTS} $<

.PHONY:		clean jar distro





