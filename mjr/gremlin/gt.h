#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

typedef struct {
    FILE *in;
    uint32_t depth; // how deep are we into the tree? root = 0
    uint32_t offsets[256]; // the offsets of higher nodes. root = 0, current = depth
    char *names[256]; // the names which correspond to each offset.
} gremlin_tree;

enum {GT_FILE, GT_DIR};

typedef struct {
    char *name;
    char *data; // if dir, then offset. if file, then CHK
    char type; // GT_FILE or GT_DIR
} gt_entity;

// initialize gt with the root node from /in/.
int gt_init (gremlin_tree *gt, FILE *in);

// return a null-name-terminated malloc'ed array of entities (files or directories)
gt_entity * gt_ls (gremlin_tree *gt);

// free /gte/ from ls
void gt_free (gt_entity *gte);

// cd to /name/ with offset (from ls) /data/
int gt_cd (gremlin_tree *gt, char *name, char *data);

// free the name list, close the in stream
int gt_close (gremlin_tree *gt);
