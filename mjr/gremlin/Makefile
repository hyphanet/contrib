all: gt gtc gp3

gremlin: gt.c gremlin.c Makefile
	gcc -g -Wall -o gremlin gremlin.c gt.o -lreadline -lcurses -pthread

gt: gt.c gt.h Makefile
	gcc -g -Wall -c gt.c

gtc: gtc.c Makefile
	gcc -g -Wall -o gtc gtc.c -lfcp -pthread

gtd: gt gtd.c Makefile
	gcc -g -Wall -o gtd gtd.c gt.o -lpthread

gp3: gp3.c gt Makefile
	gcc -g -Wall -o gp3 gp3.c gt.o -lfcp -lcurses -pthread

clean:
	rm -f *.o gtc gtd gremlin gp3 core

