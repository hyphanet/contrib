all: gmp nogmp

gmp:
	gcj -DUSE_GMP -o gmp --main=Test Test.java BigInteger.java MPN.java natMPN.cc -lgmp

nogmp:
	gcj -o nogmp --main=Test Test.java BigInteger.java MPN.java natMPN.cc
