#define GRAPHCOUNT    512
#define CONCURRENCY   16
#define AVL_MAXHEIGHT 41

#include <math.h>
#include <stdarg.h>
#include <pthread.h>
#include "anarcast.h"
#include "sha.c"
#include "aes.c"

struct graph {
    unsigned short dbc; // data block count
    unsigned short cbc; // check block count
    unsigned char *graph; // array of bits
};

struct node {
    unsigned int addr;
    char hash[HASHLEN];
    struct node *left, *right;
    unsigned char heightdiff;
};

void load_graphs ();
struct graph graphs[GRAPHCOUNT];

struct node *tree;
char *inform_server;

void inform ();
void * run_thread (void *arg);
void alert (const char *s, ...);

void insert (int c);
void do_insert (char *blocks, int blockcount, int blocksize, char *hashes);

void request (int c);

void addref (unsigned int addr);
void rmref (unsigned int addr);
unsigned int route (char hash[HASHLEN]);

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

void *
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
    vprintf(s, args);
    printf("\n");
    fflush(stdout);
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
	memcpy(&graphs[i].dbc, &data[n], 2);
	n += 2;
	memcpy(&graphs[i].cbc, &data[n], 2);
	n += 2;
	graphs[i].graph = &data[n];
	n += (graphs[i].dbc * graphs[i].cbc) / 8;
	if ((graphs[i].dbc * graphs[i].cbc) % 8)
	    n++;
    }
}

int
is_set (struct graph *g, int db, int cb)
{
    int n = (db * g->cbc) + cb;
    return (g->graph[n / 8] << (n % 8)) & 128;
}

void
insert (int c)
{
    char *hashes, *blocks;
    unsigned int i, j;
    unsigned int blocksize, len, hlen, dlen, clen;
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
    if (i/blocksize > GRAPHCOUNT) {
	alert("I do not have a graph for %d data blocks!", i/blocksize);
	return;
    }
    g = graphs[i/blocksize];
    
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
    alert("Reading plaintext from client.");
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
    alert("Generating %d check blocks for %d data blocks.", g.cbc, g.dbc);
    for (i = 0 ; i < g.cbc ; i++) {
	char b[1024];
	sprintf(b, "Check block %2d:", i+1);
	for (j = 0 ; j < g.dbc ; j++)
	    if (is_set(&g, j, i)) {
		xor(&blocks[dlen+(i*blocksize)], // check block (modified)
		    &blocks[j*blocksize], // data block (const)
		    blocksize);
		sprintf(b, "%s %d", b, j+1);
	    }
	alert("%s.", b);
    }
    
    alert("Hashing blocks.");
    
    // generate data block hashes
    for (i = 0 ; i < g.dbc ; i++)
	sha_buffer(&blocks[i*blocksize], blocksize, &hashes[(i+1)*HASHLEN]);
    
    // generate check block hashes
    for (i = 0 ; i < g.cbc ; i++)
	sha_buffer(&blocks[dlen+(i*blocksize)], blocksize,
		   &hashes[(g.dbc+1)*HASHLEN+(i*HASHLEN)]);
    
    // send the URI to the client
    alert("Writing key to client.");
    if (writeall(c, &hlen, 4) != 4 || writeall(c, hashes, hlen) != hlen) {
	ioerror();
	if (munmap(blocks, len) == -1)
	    die("munmap() failed");
	free(hashes);
	return;
    }

    // actually insert the blocks
    do_insert(blocks, g.dbc + g.cbc, blocksize, &hashes[HASHLEN]);

    if (munmap(blocks, len) == -1)
	die("munmap() failed");
    free(hashes);
}

void
do_insert (char *blocks, int blockcount, int blocksize, char *hashes)
{
    int m;
    fd_set r, w;
    
    FD_ZERO(&r);
    FD_ZERO(&w);
    
    alert("Inserting %d blocks.", blockcount);
    
    m = 0;
    for (;;) {
	int i;
	fd_set s = r, x = w;

	i = select(m, &s, &x, NULL, NULL);
	if (i == -1) die("select() failed");
	if (!i) continue;

	
    }
}

void
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
	alert("%s: %s.", inform_server, hstrerror(h_errno));
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

void avl_insert (struct node *node, struct node **stack[], int stackmax);
void avl_remove (struct node **stack[], int stackmax);
int avl_findwithstack (struct node **tree, struct node ***stack, int *count, char hash[HASHLEN]);

void
addref (unsigned int addr)
{
    struct in_addr x;
    int count;
    char hex[HASHLEN*2+1];
    struct node *n;
    struct node **stack[AVL_MAXHEIGHT];
    
    if (!(n = malloc(sizeof(struct node))))
	die("malloc() failed");
    
    n->addr = addr;
    sha_buffer((char *) &addr, 4, n->hash);
    bytestohex(hex, n->hash, HASHLEN);
    x.s_addr = addr;
    alert("+ %15s %s", inet_ntoa(x), hex);
    
    if (avl_findwithstack(&tree, stack, &count, n->hash))
	die("tried to addref() a duplicate reference");
    
    avl_insert(n, stack, count);
}

void
rmref (unsigned int addr)
{
    char hash[HASHLEN];
    char hex[HASHLEN*2+1];
    struct in_addr x;
    int count;
    struct node **stack[AVL_MAXHEIGHT];
    
    sha_buffer((char *) &addr, 4, hash);
    bytestohex(hex, hash, HASHLEN);
    x.s_addr = addr;
    alert("- %15s %s", inet_ntoa(x), hex);
    
    if (!avl_findwithstack(&tree, stack, &count, hash))
	die("tried to rmref() nonexistant reference");
    
    avl_remove(stack, count);
}

unsigned int
route (char hash[HASHLEN])
{
    int count;
    struct in_addr x;
    char hex[HASHLEN*2+1];
    struct node **stack[AVL_MAXHEIGHT];
    
    avl_findwithstack(&tree, stack, &count, hash);
    
    if (count < 2)
	die("the tree is empty");
    
    bytestohex(hex, (*stack[count-2])->hash, HASHLEN);
    x.s_addr = (*stack[count-2])->addr;
    alert("* %15s %s", inet_ntoa(x), hex);
    
    return (*stack[count-2])->addr;
}

int
avl_findwithstack (struct node **tree, struct node ***stack, int *count, char hash[HASHLEN])
{
    struct node *n = *tree;
    int found = 0;
    
    *stack++ = tree;
    *count = 1;
    while (n) {
	int compval = memcmp(n->hash, hash, HASHLEN);
	if (compval < 0) {
	    (*count)++;
	    *stack++ = &n->left;
	    n = n->left;
	} else if (compval > 0) {
	    (*count)++;
	    *stack++ = &n->right;
	    n = n->right;
	} else {
	    found = 1;
	    break;
	}
    }
    
    return found;
}
    
enum {TREE_BALANCED, TREE_LEFT, TREE_RIGHT};

static inline int
otherChild (int child)
{
    return child == TREE_LEFT ? TREE_RIGHT : TREE_LEFT;
}

static inline void
rotateWithChild (struct node **ptrnode, int child)
{
    struct node *node = *ptrnode;
    struct node *childnode;

    if (child == TREE_LEFT) {
        childnode = node->left;
        node->left = childnode->right;
        childnode->right = node;
    } else {
        childnode = node->right;
        node->right = childnode->left;
        childnode->left = node;
    }
    *ptrnode = childnode;

    if (childnode->heightdiff != TREE_BALANCED) {
        node->heightdiff = TREE_BALANCED;
        childnode->heightdiff = TREE_BALANCED;
    } else
        childnode->heightdiff = otherChild(child);
}

static inline void
rotateWithGrandChild (struct node **ptrnode, int child)
{
    struct node *node = *ptrnode;
    struct node *childnode;
    struct node *grandchildnode;
    int other = otherChild(child);

    if (child == TREE_LEFT) {
        childnode = node->left;
        grandchildnode = childnode->right;
        node->left = grandchildnode->right;
        childnode->right = grandchildnode->left;
        grandchildnode->left = childnode;
        grandchildnode->right = node;
    } else {
        childnode = node->right;
        grandchildnode = childnode->left;
        node->right = grandchildnode->left;
        childnode->left = grandchildnode->right;
        grandchildnode->right = childnode;
        grandchildnode->left = node;
    }
    *ptrnode = grandchildnode;

    if (grandchildnode->heightdiff == child) {
        node->heightdiff = other;
        childnode->heightdiff = TREE_BALANCED;
    } else if (grandchildnode->heightdiff == other) {
        node->heightdiff = TREE_BALANCED;
        childnode->heightdiff = child;
    } else {
        node->heightdiff = TREE_BALANCED;
        childnode->heightdiff = TREE_BALANCED;
    }
    grandchildnode->heightdiff = TREE_BALANCED;
}

void
avl_insert (struct node *node, struct node **stack[], int stackcount)
{
    int oldheightdiff = TREE_BALANCED;

    node->left = node->right = NULL;
    node->heightdiff = 0;
    *stack[--stackcount] = node;

    while (stackcount) {
        int nodediff, insertside;
        struct node *parent = *stack[--stackcount];

        if (parent->left == node)
            insertside = TREE_LEFT;
        else 
            insertside = TREE_RIGHT;

        node = parent;
        nodediff = node->heightdiff;

        if (nodediff == TREE_BALANCED) {
            node->heightdiff = insertside;
            oldheightdiff = insertside;
        } else if (nodediff != insertside) {
            node->heightdiff = TREE_BALANCED;
            return;
        } else {
            if (oldheightdiff == nodediff)
                rotateWithChild(stack[stackcount], insertside);
            else
                rotateWithGrandChild(stack[stackcount], insertside);
            return;
        }
    }
}

void
avl_remove (struct node **stack[], int stackcount)
{
    struct node *node = *stack[--stackcount];
    struct node *nextgreatest = node->left;
    struct node *relinknode;
    struct node **removenodeptr;

    if (nextgreatest) {
        int newmax = stackcount+1;
        struct node *next, tmp;

        while ((next = nextgreatest->right)) {
            newmax++;
            stack[newmax] = &nextgreatest->right;
            nextgreatest = next;
        }

        tmp.left = node->left;
	tmp.right = node->right;
	tmp.heightdiff = node->heightdiff;
	
	node->left = nextgreatest->left;
	node->right = nextgreatest->left;
	node->heightdiff = nextgreatest->heightdiff;
	
        nextgreatest->left = tmp.left;
	nextgreatest->right = tmp.right;
	nextgreatest->heightdiff = tmp.heightdiff;

        *stack[stackcount] = nextgreatest;
        stack[stackcount+1] = &nextgreatest->left;
        *stack[newmax] = node;
        stackcount = newmax;

        relinknode = node->left;
    } else
        relinknode = node->right;

    removenodeptr = stack[stackcount];

    while (stackcount) {
        int nodediff, removeside;
        struct node *parent = *stack[--stackcount];

        if (parent->left == node)
            removeside = TREE_LEFT;
        else 
            removeside = TREE_RIGHT;

        node = parent;
        nodediff = node->heightdiff;

        if (nodediff == TREE_BALANCED) {
            node->heightdiff = otherChild(removeside);
            break;
        } else if (nodediff == removeside) {
            node->heightdiff = TREE_BALANCED;
        } else {
            int childdiff;
            if (nodediff == TREE_LEFT)
                childdiff = node->left->heightdiff;
            else
                childdiff = node->right->heightdiff;

            if (childdiff == otherChild(nodediff))
                rotateWithGrandChild(stack[stackcount], nodediff);
            else {
                rotateWithChild(stack[stackcount], nodediff);
                if (childdiff == TREE_BALANCED)
                    break;
            }

            node = *stack[stackcount];
        }
    }

    *removenodeptr = relinknode;
}

int
printk_avl(struct node *tree, int depth)
{
	int leftheight=0, rightheight=0, err=0;

	printf("tree: %p\n", tree);

	if (tree) {
		struct node *next;
		int diff;

		printf("(");
		next = tree->left;
		if (next) {
			leftheight = printk_avl(next, depth+1);
			printf(" < ");
		}

		diff = tree->heightdiff;
		printf("*%d/%d*: ", depth, diff);
		{
		    char hex[HASHLEN*2+1]; bytestohex(hex, tree->hash, HASHLEN); puts(hex);
		}
		next = tree->right;
		if (next) {
			printf(" > ");
			rightheight = printk_avl(next, depth+1);
		}

		if (abs(leftheight - rightheight) > 1) {
			printf(" ERROR imbalanced %d|%d.", leftheight, rightheight);
			err = 1;
		} else if ((leftheight > rightheight && diff != TREE_LEFT)
			   || (leftheight < rightheight && diff != TREE_RIGHT)
			   || (leftheight == rightheight
			       && diff != TREE_BALANCED)) {
			printf(" ERROR incorrect height.");
			err = 1;
		}

		printf(")");
	}

	return (leftheight > rightheight ? leftheight : rightheight) + 1;
}
