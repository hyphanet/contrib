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
inline void insert (int c);
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
	    pthread_create(&t, NULL, run_thread, i);
	    pthread_detach(t);
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
    close(c);
    free(arg);
    pthread_exit(NULL);
}

inline void
insert (int c)
{
    char hash[HASH_LEN], *p;
    unsigned int len;
    keyInstance key;
    cipherInstance cipher;
    
    if (readall(c, &len, 4) != 4) {
	ioerror();
	return;
    }

    p = mbuf(len);
    if (readall(c, p, len) != len) {
	ioerror();
	munmap(p, len);
	return;
    }
    
    sha_buffer(p, len, hash);
    if (cipherInit(&cipher, MODE_CFB1, NULL) != TRUE)
	err(1, "cipherInit() failed");
    if (makeKey(&key, DIR_ENCRYPT, 128, hash) != TRUE)
	err(1, "makeKey() failed");
    if (blockEncrypt(&cipher, &key, p, len, p) <= 0)
	err(1, "blockEncrypt() failed");
    
    munmap(p, len);
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
	err(1, "socket() failed");
    
    if (connect(c, &a, sizeof(a)) == -1)
	err(1, "connect() to %s failed", inform_server);
    
    tree = NULL;
    
    for (n = 0 ; ; n++) {
	unsigned int i;
	int j = readall(c, &i, 4);
	if (!j) break;
    	if (j != 4) err(1, "inform server hung up unexpectedly");
	addref(i);
    }

    printf("\n%d Anarcast servers loaded.\n\n", n);

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
	err(1, "malloc() failed");
    
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
	    err(1, "socket() failed");

	if (connect(c, &a, sizeof(a)) != -1) {
	    char hex[HASH_LEN*2+1];
	    bytestohex(hex, n->hash, HASH_LEN);
	    printf("* %15s %s\n", inet_ntoa(a.sin_addr), hex);
	    return c;
	}

	rmref(n);
	close(c);
    }
    
    puts("\nServer list exhausted. Contacting inform server.\n");
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

