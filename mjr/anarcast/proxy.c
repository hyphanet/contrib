#include <math.h>
#include <stdarg.h>
#include <pthread.h>
#include "anarcast.h"
#include "sha.c"
#include "aes.c"

struct node {
    unsigned int addr;
    char hash[HASH_LEN];
    struct node *left, *right;
};

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
inline int route (char hash[HASH_LEN]);

inline struct node * tree_search (struct node *tree, char hash[HASH_LEN]);
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
    va_start (args, s);
    printf("\n");
    vprintf(s, args);
    printf("\n\n");
    va_end (args);
}

inline void
insert (int c)
{
    char *hashes, *data;
    unsigned int i, len;
    int block_size, datablock_count;
    keyInstance key;
    cipherInstance cipher;
    
    if (readall(c, &i, 4) != 4) {
	ioerror();
	return;
    }

    block_size = 64 * sqrt(i);
    datablock_count = i / block_size;
    
    if (!(hashes = malloc((datablock_count+1) * HASH_LEN)))
	die("malloc() failed");
    
    while (datablock_count * block_size < i)
	block_size++;
    
    len = datablock_count * block_size;
    data = mbuf(datablock_count * block_size);
    memset(&data[i], 0, len-i);
    if (readall(c, data, i) != i) {
	ioerror();
	if (munmap(data, len) == -1)
	    die("munmap() failed");
	free(hashes);
	return;
    }

    sha_buffer(data, len, hashes);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	die("cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, hashes) != TRUE)
	die("makeKey() failed");
    if (blockEncrypt(&cipher, &key, data, len, data) <= 0)
	die("blockEncrypt() failed");
    
    insert_data_blocks(data, block_size, datablock_count, &hashes[HASH_LEN]);

    insert_check_blocks(data, block_size, datablock_count, hashes);
    
    i = (datablock_count+1) * HASH_LEN;
    if (writeall(c, &i, 4) != 4 || writeall(c, hashes, i) != i) {
	ioerror();
	if (munmap(data, len) == -1)
	    die("munmap() failed");
	free(hashes);
	return;
    }

    if (munmap(data, len) == -1)
	die("munmap() failed");
    free(hashes);
    
    alert("Insertion complete.");
}

void
insert_data_blocks (char *data, int block_size, int datablock_count, char *hashes)
{
    int i;

    for (i = 0 ; i < datablock_count ; i++) {
	int c;
	sha_buffer(&data[i*block_size], block_size, &hashes[i*HASH_LEN]);
restart:
	c = route(&hashes[i*HASH_LEN]);
	
	if (writeall(c, "i", 1) != 1
		|| writeall(c, &block_size, 4) != 4
		|| writeall(c, &data[i*block_size], block_size) != block_size) {
	    if (close(c) == -1)
		die("close() failed");
	    goto restart;
	}

	if (close(c) == -1)
	    die("close() failed");
    }
}

void
insert_check_blocks (char *data, int block_size, int datablock_count, char *hashes)
{
    //int i;
    
    //for (i = 0 ; i < datablock_count ; i++) {
	
    //}
}

inline void
request (int c)
{
    int i;

    if (readall(c, &i, 4) != 4) {
	ioerror();
	return;
    }

    if (i % HASH_LEN) {
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
    	if (j != 4) die("inform server hung up unexpectedly");
	addref(i);
    }

    alert("%d Anarcast servers loaded.", n);

    if (!n) {
	puts("No servers, exiting.");
	exit(0);
    }
/*
    {
	int i;
	for (i = 0 ; i < 10 ; i++)
	    addref(i);
    }

    {
	int i = 39;
	char hash[20];
	sha_buffer((char *) &i, 4, hash);
	c = route(hash);
	printf("c = %d\n", c);
    }
*/
}

inline void
addref (unsigned int addr)
{
    struct in_addr x;
    char hex[HASH_LEN*2+1];
    struct node *item;
    
    if (!(item = malloc(sizeof(struct node))))
	die("malloc() failed");
    
    item->left = item->right = NULL;
    item->addr = addr;
    sha_buffer((char *) &addr, 4, item->hash);
    
    tree_insert(&tree, item);
    
    bytestohex(hex, item->hash, HASH_LEN);
    x.s_addr = addr;
    printf("+ %15s %s\n", inet_ntoa(x), hex);
}

inline void
rmref (struct node *n)
{
    struct node *new = NULL;
    char hex[HASH_LEN*2+1];
    struct in_addr x;
    
    x.s_addr = n->addr;
    bytestohex(hex, n->hash, HASH_LEN);
    
    tree_copy(tree, &new, n);
    tree = new;
    
    printf("- %15s %s\n", inet_ntoa(x), hex);
}

inline int
route (char hash[HASH_LEN])
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
	    char hex[HASH_LEN*2+1];
	    bytestohex(hex, n->hash, HASH_LEN);
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
tree_search (struct node *tree, char hash[HASH_LEN])
{
    if (!tree)
	return tree;
    else if (memcmp(hash, tree->hash, HASH_LEN) < 0) {
	if (tree->left)
	    return tree_search(tree->left, hash);
        else
	    return tree;
    } else if (memcmp(hash, tree->hash, HASH_LEN) > 0) {
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
    else if (memcmp(item->hash, (*tree)->hash, HASH_LEN) < 0)
	tree_insert(&(*tree)->left, item);
    else if (memcmp(item->hash, (*tree)->hash, HASH_LEN) > 0) 
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

