#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "gt.h"

FILE *in, *out;

void
usage (char *me)
{
    fprintf(stderr, "Usage: %s infile outfile\nGremlin Tree Decompiler\n", me);
    exit(1);
}

void
list (gremlin_tree *gt, int depth)
{
    int i, n;
    gt_entity *ls = gt_ls(gt);
    for (i = 0 ; ls[i].name ; i++) {
	for (n = 0 ; n < depth ; n++) fprintf(out, "\t");
	fprintf(out, "%s", ls[i].name);
	if (ls[i].type == GT_DIR) {
	    fprintf(out, "\n");
	    printf("Changing to %s\n", ls[i].name);
	    gt_cd(gt, ls[i].name, ls[i].data);
	    list(gt, depth + 1);
	    gt_cd(gt, "..", NULL);
	} else {
	    fprintf(out, "=%s\n", ls[i].data);
	}
    }
    gt_free(ls);
}

int
main (int argc, char **argv)
{
    gremlin_tree gt;
    int status;

    if (argc < 3) usage(argv[0]);
    
    in = fopen(argv[1], "r");
    if (!in) {
	fprintf(stderr, "Can't open %s for reading! Aborting.\n", argv[1]);
	return 1;
    }

    out = fopen(argv[2], "w");
    if (!out) {
	fprintf(stderr, "Can't open %s for writing! Aborting.\n", argv[2]);
	return 1;
    }
    
    status = gt_init(&gt, in);
    if (status != 0) {
	fprintf(stderr, "Invalid tree. Aborting.\n");
	return 1;
    }
    
    list(&gt, 0);
    return 0;
}

