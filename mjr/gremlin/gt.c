#include "gt.h"

int base64_encode(char *in, char *out, int length, int equalspad);
uint32_t read_int (FILE *in);
uint32_t read_array (char **array, FILE *in);

int
gt_init (gremlin_tree *gt, FILE *in)
{
    if (read_int(in) != 0) return 1; // unsupported version / bad data
    gt->in = in;
    gt->depth = 0;
    gt->offsets[0] = read_int(in); // the root node offset
    read_array(&gt->names[0], in); // the root name
    if (fseek(in, gt->offsets[0], SEEK_SET) != 0) return 1; // go there
    return 0;
}

char *
get_chk (FILE *in)
{
    int len;
    char *s, *out;
    len = read_array(&s, in);
    if (len != 37) return s; // better failure mode?
    out = malloc(54);
    len = base64_encode(s, out, 21, 0);
    out[28] = 'A'; out[29] = 'w'; out[30] = 'E'; out[31] = ',';
    len = base64_encode(&s[21], &out[32], 16, 0);
    free(s);
    return out;
}

gt_entity *
gt_ls (gremlin_tree *gt)
{
    int i;
    int child_count = read_int(gt->in);
    int entry_count = read_int(gt->in);
    gt_entity *entities = calloc(child_count + entry_count + 1, sizeof(gt_entity));
    for (i = 0 ; i < child_count ; i++) {
	read_array(&entities[i].name, gt->in);
	entities[i].data = malloc(4);
	fread(entities[i].data, sizeof(uint32_t), 1, gt->in); // the offset
	entities[i].type = GT_DIR;
    }
    for (i = child_count ; i < child_count + entry_count ; i++) {
	read_array(&entities[i].name, gt->in);
	entities[i].data = get_chk(gt->in); // the CHK
	entities[i].type = GT_FILE;
    }
    entities[child_count + entry_count].name = NULL; // null terminated
    return entities;
}

void
gt_free (gt_entity *gte)
{
    int i;
    for (i = 0 ; gte[i].name ; i++) {
	free(gte[i].name);
	free(gte[i].data);
    }
    free(gte);
}

int
gt_cd (gremlin_tree *gt, char *name, char *data)
{
    uint32_t offset;
    if (strcmp(name, ".") == 0) return 0;
    if (strcmp(name, "..") == 0) {
	free(gt->names[gt->depth--]);
	fseek(gt->in, gt->offsets[gt->depth], SEEK_SET);
	return 0;
    }
    memcpy(&offset, data, 4);
    fseek(gt->in, offset, SEEK_SET); printf("seeking to: %d\n", offset);
    gt->depth++;
    gt->names[gt->depth] = strdup(name);
    gt->offsets[gt->depth] = offset;
    return 0;
}

int
gt_close (gremlin_tree *gt)
{
    fclose(gt->in);
    while (gt->depth--)
	free(gt->names[gt->depth]);
    return 0;
}

uint32_t
read_int (FILE *in)
{
    uint32_t i;
    fread(&i, sizeof(uint32_t), 1, in);
    return i;
}

uint32_t
read_array (char **array, FILE *in)
{
    uint32_t len = read_int(in);
    *array = malloc(len+1);
    if (len) fread(*array, sizeof(char), len, in);
    (*array)[len] = '\0'; // convenience for strings
    return len;
}

