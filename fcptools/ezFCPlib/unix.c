
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <unistd.h>

#include <errno.h>
#include <unistd.h>
#include <fcntl.h>

#include <sys/time.h>
#include <netdb.h>
#include <sys/param.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <setjmp.h>
#include <signal.h>



main()
{
    struct in_addr addr;
    struct sockaddr_in address;
    int connected_socket, connected;
    FILE *fp_sock;

    OpenClientSocket("127.0.0.1", 8082);

}


int OpenClientSocket(char* host, int port)
{
    int s;
    int retval;
    struct sockaddr_in server;
    struct hostent* hp;

    server.sin_family=AF_INET;
    server.sin_port=htons((unsigned short)port);

    hp=gethostbyname(host);

    if(!hp)
    {
        unsigned long int addr=inet_addr(host);
        if(addr!=-1)
            hp=gethostbyaddr((char*)addr,sizeof(addr),AF_INET);

        if(!hp)
        {
            if(errno!=ETIMEDOUT)
                errno=-1; /* use h_errno */
            printf("Unknown host '%s' for server [%!s].",host);
            return(-1);
        }
    }

    memcpy((char*)&server.sin_addr,(char*)hp->h_addr,sizeof(server.sin_addr));

    s=socket(PF_INET,SOCK_STREAM,0);
    if(s==-1)
    {
        printf("Cannot create client socket [%!s].");
        return(-1);
    }


    retval=connect(s,(struct sockaddr *)&server,sizeof(server));

    if(retval<0)
    {
        close(s);
        s=-1;

        printf("Connect fail [%!s].");
    }

    printf("connect successful\n");
    return(s);

}

/* force cvs update */
