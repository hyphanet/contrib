#include <math.h>
#include <stdarg.h>
#include <pthread.h>
#include "anarcast.h"
#include "sha.c"
#include "aes.c"

#define GRAPHCOUNT 1024

struct node {
    unsigned int addr;
    char hash[HASHLEN];
    struct node *left, *right;
};

struct graph {
    unsigned int dbc; // data block count
    unsigned int cbc; // check block count
    unsigned char *graph; // array of bits
};

void load_graphs ();
struct graph graphs[GRAPHCOUNT];

struct node *tree;
char *inform_server;

inline void inform ();
inline void * run_thread (void *arg);
void alert (const char *s, ...);

inline void insert (int c);
void insert_data_blocks (char *data, int block_size, int datablock_count, char *hashes);
void insert_check_blocks (char *data, int block_size, int datablock_count, char *hashes);

inline void request (int c);

inline void addref (unsigned int addr);
inline void rmref (struct node *n);
inline int route (char hash[HASHLEN]);

inline struct node * tree_search (struct node *tree, char hash[HASHLEN]);
inline void tree_insert (struct node **tree, struct node *item);
inline void tree_copy (struct node *tree, struct node **new, struct node *fuck);

int
main (int argc, char **argv)
{
    int l, c;
    pthread_t t;
    
    if (argc != 2) {
	fprintf(stderr, "Usage: %s <inform server>\n", argv[0]);
        exit(2);
    }
    
    chdir_to_home();
    load_graphs();
    inform((inform_server = argv[1]));
    l = listening_socket(PROXY_SERVER_PORT);
    
    for (;;)
	if ((c = accept(l, NULL, 0)) != -1) {
	    int *i = malloc(4);
	    *i = c;
	    if (pthread_create(&t, NULL, run_thread, i) != 0)
		die("pthread_create() failed");
	    if (pthread_detach(t) != 0)
		die("pthread_detach() failed");
	}
}

inline void *
run_thread (void *arg)
{
    int c = *(int*)arg;
    char d;
    if (read(c, &d, 1) == 1) {
        if (d == 'r') request(c);
        if (d == 'i') insert(c);
    }
    if (close(c) == -1)
	die("close() failed");
    free(arg);
    pthread_exit(NULL);
}

void
alert (const char *s, ...)
{
    va_list args;
    va_start(args, s);
    printf("\n");
    vprintf(s, args);
    printf("\n\n");
    va_end(args);
}

void
load_graphs ()
{
    int i, n;
    char *data;
    struct stat s;
    
    if (stat("graphs", &s) == -1)
	die("Can't stat graphs file");
    
    if ((i = open("graphs", O_RDONLY)) == -1)
	die("Can't open graphs file");

    data = mmap(0, s.st_size, PROT_READ, MAP_SHARED, i, 0);
    if (data == MAP_FAILED)
	die("mmap() failed");

    if (close(i) == -1)
	die("close() failed");
    
    for (n = 0, i = 0 ; i < GRAPHCOUNT ; i++) {
	memcpy(&graphs[i].dbc, &data[n], 4);
	n += 4;
	memcpy(&graphs[i].cbc, &data[n], 4);
	n += 4;
	graphs[i].graph = &data[n];
	n += (graphs[i].dbc + graphs[i].cbc) / 8;
	if ((graphs[i].dbc + graphs[i].cbc) % 8)
	    n++;
    }
}

inline void
insert (int c)
{
    char *hashes, *blocks;
    unsigned int i, len, hlen, dlen, clen;
    int blocksize;
    struct graph g;
    keyInstance key;
    cipherInstance cipher;
    
    // read data length in bytes
    if (readall(c, &i, 4) != 4) {
	ioerror();
	return;
    }
    
    // find the graph for this datablock count
    blocksize = 64 * sqrt(i);
    g = graphs[i/blocksize];
    if (!g.dbc) {
	alert("I do not have a graph for %d data blocks!", i/blocksize);
	return;
    }
    
    // allocate space for plaintext hash and data- and check-block hashes
    hlen = (1 + g.dbc + g.cbc) * HASHLEN;
    if (!(hashes = malloc(hlen)))
	die("malloc() failed");
    
    // padding
    while (g.dbc * blocksize < i)
	blocksize++;
    
    dlen = g.dbc * blocksize;
    clen = g.cbc * blocksize;
    len  = dlen + clen;
    
    // read data from client
    blocks = mbuf(len);
    memset(&blocks[i], 0, dlen - i);
    if (readall(c, blocks, i) != i) {
	ioerror();
	if (munmap(blocks, len) == -1)
	    die("munmap() failed");
	free(hashes);
	return;
    }
    
    // encrypt data with its hash
    alert("Hashing and encrypting data.");
    sha_buffer(blocks, dlen, hashes);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	die("cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, hashes) != TRUE)
	die("makeKey() failed");
    if (blockEncrypt(&cipher, &key, blocks, dlen, blocks) <= 0)
	die("blockEncrypt() failed");
    
    // generate check blocks
    alert("Generating check blocks.");
    // ....
    
    alert("Hashing blocks.");
    
    // generate data block hashes
    for (i = 0 ; i < g.dbc ; i++)
	sha_buffer(&blocks[(i+1)*blocksize], blocksize,
		   &hashes[(i+1)*HASHLEN]);
    
    // generate check block hashes
    for (i = 0 ; i < g.cbc ; i++)
	sha_buffer(&blocks[HASHLEN+dlen+(i*blocksize)], blocksize,
		   &hashes[(g.dbc+1)*HASHLEN+(i*hlen)]);
    
    // send the URI to the client
    if (writeall(c, &hlen, 4) != 4 || writeall(c, hashes, hlen) != hlen) {
	ioerror();
	if (munmap(blocks, len) == -1)
	    die("munmap() failed");
	free(hashes);
	return;
    }

    // (insert everything now)

    if (munmap(blocks, len) == -1)
	die("munmap() failed");
    free(hashes);
    
    alert("Insertion complete.");
}

inline void
request (int c)
{
    int i;

    if (readall(c, &i, 4) != 4) {
	ioerror();
	return;
    }

    if (i % HASHLEN) {
	puts("Bad key length.");
	return;
    }
}

void
inform ()
{
    int c, n;
    struct sockaddr_in a;
    struct hostent *h;
    extern int h_errno;
    
    if (!(h = gethostbyname(inform_server))) {
	printf("%s: %s.\n", inform_server, hstrerror(h_errno));
	exit(1);
    }
    
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(INFORM_SERVER_PORT);
    a.sin_addr.s_addr = ((struct in_addr *)h->h_addr)->s_addr;
    
    if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	die("socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	die("connect() failed");
    
    tree = NULL;
    
    for (n = 0 ; ; n++) {
	unsigned int i;
	int j = readall(c, &i, 4);
	if (!j) break;
    	if (j != 4) die("Inform server hung up unexpectedly");
	addref(i);
    }

    alert("%d Anarcast servers loaded.", n);

    if (!n) {
	puts("No servers, exiting.");
	exit(0);
    }
}

//=== routing crap ==========================================================

inline void
addref (unsigned int addr)
{
    struct in_addr x;
    char hex[HASHLEN*2+1];
    struct node *item;
    
    if (!(item = malloc(sizeof(struct node))))
	die("malloc() failed");
    
    item->left = item->right = NULL;
    item->addr = addr;
    sha_buffer((char *) &addr, 4, item->hash);
    
    tree_insert(&tree, item);
    
    bytestohex(hex, item->hash, HASHLEN);
    x.s_addr = addr;
    printf("+ %15s %s\n", inet_ntoa(x), hex);
}

inline void
rmref (struct node *n)
{
    struct node *new = NULL;
    char hex[HASHLEN*2+1];
    struct in_addr x;
    
    x.s_addr = n->addr;
    bytestohex(hex, n->hash, HASHLEN);
    
    tree_copy(tree, &new, n);
    tree = new;
    
    printf("- %15s %s\n", inet_ntoa(x), hex);
}

inline int
route (char hash[HASHLEN])
{
    for (;;) {
        struct node *n;
	struct sockaddr_in a;
	int c;

	if (!(n = tree_search(tree, hash)))
	    break;
	
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_port = htons(ANARCAST_SERVER_PORT);
	a.sin_addr.s_addr = n->addr;
	
	if ((c = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	    die("socket() failed");

	if (connect(c, &a, sizeof(a)) != -1) {
	    char hex[HASHLEN*2+1];
	    bytestohex(hex, n->hash, HASHLEN);
	    printf("* %15s %s\n", inet_ntoa(a.sin_addr), hex);
	    return c;
	}

	rmref(n);
	if (close(c) == -1)
	    die("close() failed");
    }
    
    alert("Server list exhausted. Contacting inform server.");
    inform();
    return route(hash);
}

inline struct node *
tree_search (struct node *tree, char hash[HASHLEN])
{
    if (!tree)
	return tree;
    else if (memcmp(hash, tree->hash, HASHLEN) < 0) {
	if (tree->left)
	    return tree_search(tree->left, hash);
        else
	    return tree;
    } else if (memcmp(hash, tree->hash, HASHLEN) > 0) {
	if (tree->right)
	    return tree_search(tree->right, hash);
	else
	    return tree;
    } else
	return tree;
}

inline void
tree_insert (struct node **tree, struct node *item)
{
    if (!*tree)
	*tree = item;
    else if (memcmp(item->hash, (*tree)->hash, HASHLEN) < 0)
	tree_insert(&(*tree)->left, item);
    else if (memcmp(item->hash, (*tree)->hash, HASHLEN) > 0) 
	tree_insert(&(*tree)->right, item);
}

inline void
tree_copy (struct node *tree, struct node **new, struct node *fuck)
{
    if (tree->left) tree_copy(tree->left, new, fuck);
    if (tree->right) tree_copy(tree->right, new, fuck);
    if (tree == fuck) free(tree);
    else {
	tree->left = tree->right = NULL;
	tree_insert(new, tree);
    }
}

