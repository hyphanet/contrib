all:
	gcj -O9 -DUSE_GMP -o gmp --main=Test Test.java BigInteger.java MPN.java natMPN.cc -lgmp
	gcj -O9 -o nogmp --main=Test Test.java BigInteger.java MPN.java natMPN.cc
	./gmp > gmp_output
	./nogmp > nogmp_output
	sha1sum gmp_output nogmp_output

.PHONY: all

