#/bin/sh

case `uname -sr` in
MINGW*)
	echo "Building windows .dll's";;
Linux*)
	echo "Building linux .so's";;
*)
	echo "Unsupported build environment"
	exit;;
esac

echo "Extracting GMP..."
tar -xzf gmp-4.1.3.tar.gz
echo "Building..."
mkdir bin
mkdir lib
mkdir lib/net
mkdir lib/net/i2p
mkdir lib/net/i2p/util
for x in none pentium2 pentium3 pentium4 athlon
do
	mkdir bin/$x
	cd bin/$x
	../../gmp-4.1.3/configure --build=$x
	make
	../../build_jbigi.sh
	case `uname -sr` in
	MINGW*)
		cp jbigi.dll ../../lib/net/i2p/util/jbigi-windows-$x.dll;;
	Linux*)
		cp libjbigi.so ../../lib/net/i2p/util/libjbigi-linux-$x.so;;
	esac
	cd ..
	cd ..
done
