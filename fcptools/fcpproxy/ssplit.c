char *ssplit_rcs = "$Id: ssplit.c,v 1.1 2001/09/29 01:30:37 heretic108 Exp $";
/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

/* ssplit() - split a string (in-place) into fields
 *      s = string to split
 *      c = list of characters to be used as field separators
 *          (if NULL, use default separators of space, tab, and newline)
 *
 *      v = vector into which field pointers are placed
 *      n = number of fields in vector
 *
 *      m = flag indicating whether to treat strings of field
 *          separators as indicating multiple fields
 *
 *      l = flag indicating whether to ignore leading field separators
 */

#include <string.h>

int ssplit(char *s, char *c, char *v[], int n, int m, int l)
{
    char t[256];
    char **x = NULL;
    int xsize = 0;
    unsigned char *p, b;
    int xi = 0;
    int vi = 0;
    int i;
    int last_was_null;

    if (!s)
	return (-1);

    memset(t, '\0', sizeof(t));

    p = (unsigned char *) c;

    if (!p)
	p = (unsigned char *) " \t";	/* default field separators */

    while (*p)
	t[*p++] = 1;	/* separator  */

    t['\0'] = 2;	/* terminator */
    t['\n'] = 2;	/* terminator */

    p = (unsigned char *) s;

    if(l) {	/* are we to skip leading separators ? */
    	while((b = t[*p]) != 2) {
		if(b != 1) break;
		p++;
	}
    }

    xsize = 256;

    x = (char **) zalloc((xsize) * sizeof(char *));

    x[xi++] = (char *) p;	/* first pointer is the beginning of string */

    /* first pass:  save pointers to the field separators */
    while((b = t[*p]) != 2) {
    	if(b == 1) {		/* if the char is a separator ... */
		*p++    = '\0';	/* null terminate the substring */

		if(xi == xsize) {
			/* get another chunk */
			int new_xsize = xsize + 256;
			char **new_x = (char **)
				zalloc((new_xsize) * sizeof(char *));

			for(i=0; i < xsize; i++) new_x[i] = x[i];

			free(x);
			xsize = new_xsize;
			x     = new_x;
		}
		x[xi++] = (char *) p;	/* save pointer to beginning of next string */
	} else {
		p++;
	}
    }
    *p = '\0';		/* null terminate the substring */

#ifdef DEBUG
print(x, xi); /* debugging */
#endif

    /* second pass: copy the relevant pointers to the output vector */
    last_was_null = 0;
    for(i=0 ; i < xi; i++) {
	if(m) {
		/* there are NO null fields */
		if(*x[i] == 0) continue;
	}
	if(vi < n) {
		v[vi++] = x[i];
	} else {
		free(x);
		return(-1);	/* overflow */
	}
    }
    free(x);

#ifdef DEBUG
print(v, vi); /* debugging  */
#endif
    return (vi);
}

#ifdef DEBUG
print(char **v, int n)
{
	int i;
	printf("dump %d strings\n", n);
	for(i=0; i < n; i++) {
		printf("%d '%s'\n", i, v[i]);
	}
}
#endif
