// gcc -g3 -Wall -o saturn saturn.c -lpthread

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <netdb.h>
#include <pthread.h>

#define MIN_SIZE 50000

pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
int htl, threads, max_threads, collisions, max_collisions;
char *nntpserver, *group;
uint32_t article_count, *articles;
FILE *sock, *log;

typedef struct {
    char id[512], name[512];
    FILE *data;
} article;

void nntp_connect ();
void nntp_xover ();
void nntp_noop ();
void * fcp_put_thread (void *args);
article * get_article (uint32_t msg_num);
FILE * read_stduu (FILE *in);
FILE * read_base64 (FILE *in);

void
nntp_connect ()
{
    struct in_addr addr;
    struct sockaddr_in address;
    struct hostent *hent;
    int p = htons(119);
    int connected_socket, connected;

    addr.s_addr = inet_addr(nntpserver);
    if (addr.s_addr == -1) {
        hent = gethostbyname(nntpserver);
        if (hent == NULL) {
	    fprintf(stderr, "Could not resolve host %s.\n", nntpserver);
	    exit(1);
	}
        addr.s_addr = ((struct in_addr *) *hent->h_addr_list)->s_addr;
    }

    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = (p);
    address.sin_addr.s_addr = addr.s_addr;

    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket, (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) {
	fprintf(stderr, "Connection to %s:119 failed.\n", nntpserver);
	exit(1);
    }

    sock = fdopen(connected_socket, "w+");
}

void
nntp_xover ()
{
    char line[512];
    int status;
    
    fgets(line, 512, sock);
    if (strncmp(line, "200", 3) != 0) goto badreply;
    fflush(sock);
    
    fprintf(sock, "listgroup %s\r\n", group);
    fflush(sock);
    
    fgets(line, 512, sock); status = 0;
    sscanf(line, "%d", &status);
    if (status != 211) goto badreply;
    fflush(sock);

    printf("Downloading article list... ");
    fflush(NULL);
    article_count = 0;
    articles = calloc(128, sizeof(uint32_t));
    while (fgets(line, 512, sock)) {
	if (strcmp(line, ".\r\n") == 0) break;
	status = sscanf(line, "%d\r\n", &articles[article_count++]);
	if (status != 1) goto badreply;
	if (article_count % 128 == 0)
	    articles = realloc(articles, sizeof(uint32_t) * (article_count + 128));
    }

    if (strcmp(line, ".\r\n") != 0) {
	fprintf(stderr, "listgroup from server terminated unexpectedly!\n");
	exit(1);
    }

    printf("%d articles.\n", article_count);
    return;

badreply:
    fprintf(stderr, "Unexpected reply: %s", line);
    exit(1);
}

void
nntp_noop ()
{
    char line[512];
    fprintf(sock, "date\r\n");
    fflush(sock);
    fgets(line, 512, sock);
}

FILE *
fcp_connect ()
{
    struct in_addr addr;
    struct sockaddr_in address;
    struct servent *serv;
    int connected_socket, connected;
    serv = getservbyname("fcp", "tcp");
    if (!serv) return NULL;
    addr.s_addr = inet_addr("127.0.0.1");
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = (serv->s_port);
    address.sin_addr.s_addr = addr.s_addr;
    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket, (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) return NULL;
    return fdopen(connected_socket, "w+");
}

void *
fcp_put_thread (void *args)
{
    article *a = (article *) args;
    FILE *fcp = fcp_connect();
    char reply[512];
    int c;
    if (!fcp) {
	fprintf(stderr, "Error connecting to node!\n");
	goto exit;
    }
    fprintf(fcp, "ClientPut\nHopsToLive=%x\nURI=freenet:CHK@\nDataLength=%lx\nData\n", htl, ftell(a->data));
    fflush(fcp);
    rewind(a->data);
    while ((c = fgetc(a->data)) != EOF) fputc(c, fcp);
    fflush(fcp);
    fgets(reply, 512, fcp);
    if (strncmp(reply, "Success", 7) == 0) {
	fgets(reply, 512, fcp);
	if (strncmp(reply, "URI=", 4) != 0) goto free;
	fprintf(log, "%s=%s", a->name, &reply[4]);
    } else if (strncmp(reply, "KeyCollision", 12) == 0) {
	pthread_mutex_lock(&mutex);
	collisions++;
	pthread_mutex_unlock(&mutex);
    }

free:
    fclose(fcp);
    fclose(a->data);
    free(a);
exit:
    pthread_mutex_lock(&mutex);
    threads--;
    pthread_cond_broadcast(&cond);
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

article *
get_article (uint32_t msg_num)
{
    int status = 0, base64 = 0;
    char line[512], msg_id[512], name[512];
    FILE *decoded, *data = tmpfile();
    article *a;

    fprintf(sock, "xhdr subject %d\r\n", msg_num);
    fflush(sock);
    fgets(line, 512, sock);
    sscanf(line, "%d", &status);
    if (status != 221) goto badreply;
    fgets(line, 512, sock);
    if (!strstr(line, ".jp")) {
	fprintf(stderr, "Skipping article %d: not a JPEG file.\n", msg_num);
	fgets(line, 512, sock);
	return NULL;
    }
    fgets(line, 512, sock);
    if (strcmp(line, ".\r\n") != 0) goto badreply;
    
    fprintf(sock, "xhdr bytes %d\r\n", msg_num);
    fflush(sock);
    fgets(line, 512, sock);
    sscanf(line, "%d", &status);
    if (status != 221) goto badreply;
    fgets(line, 512, sock);
    sscanf(line, "%*d %d\r\n", &status);
    if (status < MIN_SIZE) {
	fprintf(stderr, "Skipping article %d: less than %d bytes.\n", msg_num, MIN_SIZE);
	fgets(line, 512, sock);
	return NULL;
    }
    fgets(line, 512, sock);
    if (strcmp(line, ".\r\n") != 0) goto badreply;
    
    fprintf(sock, "body %d\r\n", msg_num);
    fflush(sock);
    fgets(line, 512, sock);
    sscanf(line, "%d %*d %s\r\n", &status, msg_id);
    if (status != 222) goto badreply;
    printf("Downloading article %d... ", msg_num);
    fflush(NULL);
    while (fgets(line, 512, sock)) {
	if (strcmp(line, ".\r\n") == 0) break;
	fputs(line, data);
    }
    if (strcmp(line, ".\r\n") != 0) {
	fprintf(stderr, "terminated unexpectedly!\n");
	return NULL;
    }
    printf("done.\n");

    rewind(data); name[0] = '\0';
    while (fgets(line, 512, data)) {
       if (strncmp(line, "begin ", 6) == 0) {
	   sscanf(line, "begin %*o %s", name);
	   break;
       } else if (strncmp(line, "begin-base64 ", 13) == 0) {
	   sscanf(line, "begin-base64 %*o %s", name);
	   base64 = 1;
	   break;
       }
    }

    if (!strlen(name)) {
	fprintf(stderr, "Decoding error on article %d: no name found!\n", msg_num);
	return NULL;
    }
    
    if (base64) decoded = read_base64(data);
    else decoded = read_stduu(data);
    if (!decoded) {
	fprintf(stderr, "Decoding error on article %d: bad data!\n", msg_num);
	return NULL;
    }
    fclose(data);
    
    a = malloc(sizeof(article));
    a->data = decoded;
    strcpy(a->id, msg_id);
    strcpy(a->name, name);
    return a;

badreply:
    fprintf(stderr, "Unexpected reply: %s", line);
    return NULL;
}

int
main (int argc, char **argv)
{
    pthread_t thread;
    article *a;
    
    if (argc < 2) {
	fprintf(stderr, "Usage: %s news.group.name [htl] [threads] [max-collisions]\n"
			"If [htl] is not specified, 15 is assumed.\n"
		        "If [threads] is not specified, single-thread mode is assumed.\n"
			"If [max-collisions] is not specified, unlimited CHK collisions are allowed.\n",
			argv[0]);
	exit(2);
    }
    
    nntpserver = getenv("NNTPSERVER");
    if (!nntpserver) {
	fprintf(stderr, "The NNTPSERVER environment variable must be set to the hostname of your NNTP server.\n"
		        "Example: export NNTPSERVER=nntp.myisp.com\n");
	exit(2);
    }

    group = argv[1];

    htl = argc > 2 ? atoi(argv[2]) : 15;
    max_threads = argc > 3 ? atoi(argv[3]) : 1;
    max_collisions = argc > 4 ? atoi(argv[4]) : 0;
    collisions = 0;
    nntp_connect();
    nntp_xover();
    
    if (!article_count) exit(0);
    
    log = fopen(group, "a");
    if (!log) {
	fprintf(stderr, "Can't open log file to append to!\n");
	exit(1);
    }
    
    do {
	if (max_collisions && collisions > max_collisions) {
	    fprintf(stderr, "Max collisions exceeded. Aborting.\n");
	    pthread_exit(NULL);
	}
	if ((a = get_article(articles[--article_count]))) {
	    pthread_create(&thread, NULL, fcp_put_thread, a);
	    if (max_threads && ++threads >= max_threads)
		while (threads >= max_threads) pthread_cond_wait(&cond, &mutex);
	}
    } while (article_count);
    
    pthread_exit(NULL);
}

#define DEC(Char) (((Char) - ' ') & 077)

FILE *
read_stduu (FILE *in)
{
    FILE *out = tmpfile();
    char ch, *p, buf[2 * BUFSIZ];
    int n;

    while (1) {
        if (!fgets(buf, sizeof(buf), in)) return NULL;
        p = buf;
        n = DEC(*p);
        if (n <= 0) break;
        for (++p; n > 0; p += 4, n -= 3) {
            if (n >= 3) {
                ch = DEC(p[0]) << 2 | DEC (p[1]) >> 4;
                fputc(ch, out);
                ch = DEC(p[1]) << 4 | DEC (p[2]) >> 2;
                fputc(ch, out);
                ch = DEC(p[2]) << 6 | DEC (p[3]);
                fputc(ch, out);
            } else {
                if (n >= 1) {
                    ch = DEC(p[0]) << 2 | DEC (p[1]) >> 4;
                    fputc(ch, out);
                }
                if (n >= 2) {
                    ch = DEC(p[1]) << 4 | DEC (p[2]) >> 2;
                    fputc(ch, out);
                }
            }
        }
    }

    if (!fgets(buf, sizeof(buf), in)
	    || strcmp(buf, "end\n") != 0) return out; // be lenient
    return out;
}

FILE *
read_base64 (FILE *in)
{
    static const char b64_tab[256] = {
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*000-007*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*010-017*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*020-027*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*030-037*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*040-047*/
        '\177', '\177', '\177', '\76',  '\177', '\177', '\177', '\77',  /*050-057*/
        '\64',  '\65',  '\66',  '\67',  '\70',  '\71',  '\72',  '\73',  /*060-067*/
        '\74',  '\75',  '\177', '\177', '\177', '\100', '\177', '\177', /*070-077*/
        '\177', '\0',   '\1',   '\2',   '\3',   '\4',   '\5',   '\6',   /*100-107*/
        '\7',   '\10',  '\11',  '\12',  '\13',  '\14',  '\15',  '\16',  /*110-117*/
        '\17',  '\20',  '\21',  '\22',  '\23',  '\24',  '\25',  '\26',  /*120-127*/
        '\27',  '\30',  '\31',  '\177', '\177', '\177', '\177', '\177', /*130-137*/
        '\177', '\32',  '\33',  '\34',  '\35',  '\36',  '\37',  '\40',  /*140-147*/
        '\41',  '\42',  '\43',  '\44',  '\45',  '\46',  '\47',  '\50',  /*150-157*/
        '\51',  '\52',  '\53',  '\54',  '\55',  '\56',  '\57',  '\60',  /*160-167*/
        '\61',  '\62',  '\63',  '\177', '\177', '\177', '\177', '\177', /*170-177*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*200-207*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*210-217*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*220-227*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*230-237*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*240-247*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*250-257*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*260-267*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*270-277*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*300-307*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*310-317*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*320-327*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*330-337*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*340-347*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*350-357*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*360-367*/
        '\177', '\177', '\177', '\177', '\177', '\177', '\177', '\177', /*370-377*/
    };
    
    unsigned char *p, buf[2 * BUFSIZ];
    char c1, c2, c3;
    int last_data = 0;
    FILE *out = tmpfile();

    while (1) {
      if (!fgets(buf, sizeof(buf), in)) return out; // be lenient
      p = buf;
      if (memcmp(buf, "====", 4) == 0) break;
      if (last_data != 0) return NULL;
      while (*p != '\n') {
          while ((b64_tab[*p] & '\100') != 0)
              if (*p == '\n' || *p++ == '=') break;
          if (*p == '\n') continue;
          c1 = b64_tab[*p++];
          while ((b64_tab[*p] & '\100') != 0)
              if (*p == '\n' || *p++ == '=') return NULL;
          c2 = b64_tab[*p++];
          while (b64_tab[*p] == '\177')
              if (*p++ == '\n') return NULL;
	  if (*p == '=') {
              fputc(c1 << 2 | c2 >> 4, out);
              last_data = 1;
              break;
          }
          c3 = b64_tab[*p++];
          while (b64_tab[*p] == '\177')
              if (*p++ == '\n') return NULL;
          fputc(c1 << 2 | c2 >> 4, out);
          fputc(c2 << 4 | c3 >> 2, out);
          if (*p == '=') {
              last_data = 1;
              break;
          } else
              fputc(c3 << 6 | b64_tab[*p++], out);
        }
    }

    return out;
}
