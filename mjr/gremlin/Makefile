all: base64 gt gremlin gtc gtd

gremlin: base64.c gt.c gremlin.c Makefile
	gcc -g -Wall -o gremlin gremlin.c gt.o base64.o -lreadline -lcurses -lpthread #-lefence

gt: base64.c gt.c gt.h
	gcc -g -Wall -c gt.c

base64: base64.c
	gcc -g -Wall -c base64.c

gtc: gtc.c Makefile
	gcc -g -Wall -o gtc gtc.c base64.o -lpthread #-lefence

gtd: gt.c gtd.c Makefile
	gcc -g -Wall -o gtd gtd.c gt.o base64.o -lpthread #-lefence

clean:
	rm -f *.o gtc gtd gremlin core

