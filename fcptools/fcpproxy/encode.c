char *encode_rcs = "$Id: encode.c,v 1.3 2004/03/24 01:09:09 joliveri Exp $";
/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

/* This is fcpproxy, an http interface client for Freenet.
 * Adapted by David McNab for Freenet from the JunkBusters proxy engine
 * The latest version of this program is at the FreeWeb website,
 * http://freeweb.sourceforge.net, and can be downloaded as a tarball, or from
 * cvs, or as precompiled binaries
 */

#include <stdio.h>
#include <string.h>
#include <ctype.h>

#ifdef REGEX
#include "gnu_regex.h"
#endif

#include "fcpproxy.h"

char *url_code_map[256];
char *html_code_map[256];
char *cookie_code_map[256];

char *
url_encode(char **code_map, unsigned char *s)
{
	char *buf;
	unsigned char c, *p;
	char *m;

	static int one_shot = 1;

	if(one_shot) {
		char tmp[BUFSIZ];

		/* initialize the code maps */

		int i;

		one_shot = 0;

		/* for cookies, we turn white-space into '+'
		 * hex encode comma and semi-colons
		 * and leave everything else alone.
		 */

		cookie_code_map[' '] = "+";

		sprintf(tmp, "%%%02X", ',');
		cookie_code_map[','] = strdup(tmp);

		sprintf(tmp, "%%%02X", ';');
		cookie_code_map[';'] = strdup(tmp);

		/* for url's, we do full URL encoding.		*/
		/* non-alphanumerics get turned into hex ...	*/
		for(i=0; i < 256; i++) {
			if(isalnum(i) == 0) {
				sprintf(tmp, "%%%02X", i);
				url_code_map[i] = strdup(tmp);
			}
		}

		/* ... with the following 6 exceptions:		*/
		/* white-space gets turned into '+' ...		*/

		url_code_map[' '] = "+";

		/* ... and these punctuation chars map to themselves */
		url_code_map['-'] = "-";
		url_code_map['_'] = "_";
		url_code_map['.'] = ".";
		url_code_map['*'] = "*";
		url_code_map['@'] = "@";

		/* for html, we encode the four "special" characters */
		html_code_map['"'] = "&quot;" ;
		html_code_map['&'] = "&amp;"  ;
		html_code_map['>'] = "&gt;"   ;
		html_code_map['<'] = "&lt;"   ;
	}

	/* each input char can expand to at most 6 chars */
	buf = zalloc((strlen((char *) s) + 1) * 6);

	for(p = (unsigned char *) buf; (c = *s); s++) {
		if((m = code_map[c])) {
			strcpy((char *) p, m);
			p += strlen(m);
		} else {
			*p++ = c;
		}
	}

	*p = '\0';

	return(buf);
}

/* these decode a URL */

int
xdtoi(char d)
{
	if((d >= '0') && (d <= '9')) return(d - '0'     );
	if((d >= 'a') && (d <= 'f')) return(d - 'a' + 10);
	if((d >= 'A') && (d <= 'F')) return(d - 'A' + 10);
	return(0);
}

char *
url_decode(char *str)
{
	char *ret = strdup(str);
	char *p, *q;

	p = str;
	q = ret;

	while(*p) {
		switch(*p) {
		case '+':
			p++;
			*q++ = ' ';
			break;
		case '%':
			if((*q = xtol(p+1))) {
				p += 3;
				q++;
			} else {
				/* malformed, just use it */
				*q++ = *p++;
			}
			break;
		default:
			*q++ = *p++;
			break;
		}
	}
	*q = '\0';
	return(ret);
}
