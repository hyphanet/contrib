#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcp.h>

FILE *in, *out;

void
usage (char *me)
{
    fprintf(stderr, "Usage: %s [options] infile outfile\n\n"
	            "  -t --threads       Concurrency for insert.\n"
		    "  -h --htl           Hops to live for insert.\n\n"
	            "If outfile is a Freenet URI, the compiled data will be inserted respecting\n"
		    "the above parameters.\n",
		    me);
    exit(1);
}

// find the depth of the deepest node, from 0.
int
depth (FILE *in)
{
    char line[256];
    int depth = 0, lnum, len, i;
    for (lnum = 1 ; fgets(line, 256, in) ; lnum++) {
	if (strchr(line, '=')) {
	    if (line[0] == ' ') {
		printf("Spaces before directory name detected on line %d. Aborting.\n", lnum);
		return -1;
	    } else continue;
	}
	len = strlen(line);
	for (i = 0 ; i < len ; i++) {
	    if (line[i] != '\t') {
		if (line[i] == ' ') {
		    printf("Spaces before name detected on line %d. Aborting.\n", lnum);
		    return -1;
		}
		break;
	    }
	}
	if (i > depth) depth = i;
    }
    rewind(in);
    return depth;
}

// find the depth of this line -- how many tabs, from 0, does it begin with?
int
ld (char *line)
{
    int i, len = strlen(line);
    for (i = 0 ; i < len ; i++)
	if (line[i] != '\t')
	    return i;
    return 0;
}

// split string into name and key around '='
void
split (char *string, char *name, char *key)
{
    int i = strlen(string);
    while (i--) {
	if (string[i] == '=') {
	    strcpy(key, &string[i+1]);
	    key[strlen(key)-1] = '\0';
	    strncpy(name, string, i+1);
	    name[i+1] = '\0';
	    return;
	}
    }
}

// strip leading tabs and trailing newline
char *
strip (char *string)
{
    int i = 0;
    while (string[i++] == '\t');
    string[strlen(string)-1] = '\0';
    return &string[i-1];
}

void
write_int (int i)
{
    uint32_t j = (uint32_t) i;
    fwrite(&j, sizeof(uint32_t), 1, out);
}

void
write_array (char *array, int len)
{
    write_int(len);
    if (len) fwrite(array, sizeof(char), len, out);
}

int
main (int argc, char **argv)
{
    char line[256], name[256], key[256], *s;
    int d, e, child_count, entry_count, root_entry_count = 0;
    long l;
    uint32_t *this, *last, **t, i, j;
    
    if (argc != 3) usage(argv[0]);
    
    in = fopen(argv[1], "r");
    if (!in) {
	fprintf(stderr, "Can't open file %s for reading!\n", argv[1]);
	return 1;
    }
    out = tmpfile();

    d = depth(in);
    if (d == -1) return 1;

    // write out the header
    write_int(0); // version 0
    write_int(0); // the offset of the root node -- we'll fix this later
    write_array("The Root Node", 13); // the name of the root node, effectively the name of the archive.
    write_array("", 0); // SSK, no updating for now
    write_int(0); // update interval, ditto
    write_int(0); // patch interval, ditto

    // write the nodes out from deepest on up, because parents need the offsets of their children
    // store the file offsets in /this/, and use /last/ to set the offsets of any children
    this = calloc(65536, sizeof(uint32_t));
    last = calloc(65536, sizeof(uint32_t));
    do {
	i = 0; j = 0; // reset the indexes of this and last, respectively
	while (fgets(line, 256, in)) {
	    if (ld(line) != d) continue; // not in this level ||
	    if (strchr(line, '=')) { // not a node
		if (d == 0) root_entry_count++;
		continue;
	    }
	    l = ftell(in); // first we find out the child_count and entry_count, then write the children, then the entries
	    child_count = 0;
	    entry_count = 0;
	    while (fgets(line, 256, in)) {
		e = ld(line);
		if (e == d) break; // end of this node
		if (e > d+1) continue; // child's data, ignore
	        if (strchr(line, '=')) entry_count++;
		else child_count++;
	    }
	    this[i++] = ftell(out); // we're writing a node, we need to set its offset for future reference
	    write_int(child_count);
	    write_int(entry_count);
	    fseek(in, l, SEEK_SET);
	    while (fgets(line, 256, in)) { // write the children
		e = ld(line);
		if (e == d) break; // end of this node
		if (e > d+1) continue; // child's data, ignore
		if (strchr(line, '=')) continue; // entry, not child
		s = strip(line);
		write_array(s, strlen(s));
		write_int(last[j++]); // its offset is saved in /last/ from the previous run
	    }
	    fseek(in, l, SEEK_SET);
	    while (fgets(line, 256, in)) { // write the entries
		e = ld(line);
		if (e == d) break; // end of this node
		if (e > d+1) continue; // child's data, ignore
		if (!strchr(line, '=')) continue; // child, not entry
		split(line, name, key);
		s = strip(name);
		write_array(s, strlen(s));
		write_array(key, strlen(key));
	    }
	    fseek(in, 0 - strlen(line), SEEK_CUR); // back up, we read one line too many
	    t = &last; last = this; this = *t; // swap the offset arrays
	}
	rewind(in);
    } while (d--);

    // write the root node
    l = ftell(out); // save root location for below
    write_int(i); // child count (we got this from the last iteration)
    write_int(root_entry_count);
    j = 0;
    while (fgets(line, 256, in)) {
	if (ld(line) != 0) continue;
	if (strchr(line, '=')) continue; // entry, not child
	s = strip(line);
	write_array(s, strlen(s));
	write_int(last[j++]); // from last iteration
    }
    rewind(in);
    while (fgets(line, 256, in)) {
	if (ld(line) != 0) continue;
	if (!strchr(line, '=')) continue; // child, not entry
	split(line, name, key);
	s = strip(name);
	write_array(s, strlen(s));
	write_array(key, strlen(key));
    }

    j = ftell(out);
    
    // update the header to point to the root node
    fseek(out, 4, SEEK_SET);
    write_int(l);
    
    rewind(out);
    if (strncmp(argv[2], "freenet:", 8) == 0) {
	printf("Inserting %s .... ", argv[2]);
	if (j <= FCP_SPLIT_THRESHOLD) {
	    strcpy(line, argv[2]);
	    l = fcp_insert_raw(out, NULL, line, j, FCP_DATA, 1);
	    if (l != FCP_SUCCESS && l != FCP_KEY_COLLISION) {
		printf("%s!\n", fcp_status_to_string(l));
		return 1;
	    }
	    printf("%s inserted.\n", line);
	} else {
	    //blah
	}
    } else {
	FILE *f = fopen(argv[2], "w");
	if (!f) {
	    printf("Can't open file %s!\n", argv[2]);
	    return 1;
	}
	while (j--)
	    fputc(fgetc(out), f);
    }
    
    return 0;
}

