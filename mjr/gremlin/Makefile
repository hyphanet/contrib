all: gt gtc gp3

gremlin: base64.c gt.c gremlin.c Makefile
	gcc -g -Wall -o gremlin gremlin.c gt.o base64.o -lreadline -lcurses -lpthread #-lefence

gt: gt.c gt.h Makefile
	gcc -g -Wall -c gt.c

gtc: gtc.c ../libfcp/fcp.c Makefile
	gcc  -I../libfcp -g -Wall -o gtc gtc.c ../libfcp/fcp.c -lpthread #-lefence

gtd: gt gtd.c Makefile
	gcc -g -Wall -o gtd gtd.c gt.o -lpthread #-lefence

gp3: gp3.c ../libfcp/fcp.c gt Makefile
	gcc -I../libfcp -g -Wall -o gp3 gp3.c gt.o ../libfcp/fcp.c -lpthread -lcurses #-lefence

clean:
	rm -f *.o gtc gtd gremlin gp3 core

