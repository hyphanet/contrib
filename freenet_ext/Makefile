# Makefile to build the freenet-ext.jar
#
# This Makefile requires that you have the ../fecimpl/onion and
# ../javax directories installed.
#
# Real Makefile hackers will undoubtably find this somewhat
# underwhelming. Constructive criticism is welcome.
#

# Stuff all external dependencies into one
# jar.  

#JAVAC=javac
#JAVAC_OPTS=-target 1.1 -classpath .

JAVAC=jikes
JAVAC_OPTS=-classpath ${JIKESPATH}:.


freenet-ext:	servlet onion
		rm -rf build_dir
		mkdir -p  build_dir/javax/servlet
		mkdir -p build_dir/javax/servlet/jsp
		mkdir -p build_dir/javax/servlet/jsp/tagext
		mkdir -p build_dir/javax/servlet/http
		cd ..; `find javax -name '*.class' -exec cp {} freenet_ext/build_dir/{} \;` 
		cd ..; `find javax -name '*.properties' -exec cp {} freenet_ext/build_dir/{} \;`
		cd build_dir; jar xvf ../../fecimpl/onion/alien/fec.jar
		cd build_dir; jar xvf ../../fecimpl/onion/alien/common.jar
		rm -rf build_dir/META_INF
		cp ../fecimpl/onion/alien/onion_LICENSE build_dir
		cp ../fecimpl/onion/*.class build_dir
		cd build_dir; jar -cf ../freenet-ext.jar `find -type f`
		rm -rf build_dir

# zip up all the sources that were used to build the jar.
freenet-ext-src:
		rm -rf build_dir
		cd ..; `find javax -name '*.class' -exec rm -f {} \;` 
		make -C ../fecimpl/onion clean
		rm -f freenet-ext.jar
		rm -f freenet-ext-src.zip
		cd ..; zip -r freenet-ext-src.zip javax/ fecimpl/ freenet_ext/; \
                   mv freenet-ext-src.zip freenet_ext/

# build the javax.servlet.* classes
servlet:
		cd ..; rm -f `find javax -name '*.class'`; ${JAVAC} ${JAVAC_OPTS} `find javax -name '*.java'`

# build the FECEncoder/Decoder plugins for fproxy.
onion:
		make -C ../fecimpl/onion clean
		make -C ../fecimpl/onion classes

clean:
		rm -f freenet-ext.jar
		rm -f freenet-ext-src.zip

.PHONY:		servlet onion freenet-ext freenet-ext-src clean


