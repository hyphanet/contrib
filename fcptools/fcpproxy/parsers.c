char *parsers_rcs = "$Id: parsers.c,v 1.1 2001/09/29 01:30:38 heretic108 Exp $";
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
#include <sys/types.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>

#ifndef _WIN32
#include <unistd.h>
#endif

#ifdef REGEX
#include <gnu_regex.h>
#endif

#include "fcpproxy.h"

struct parsers client_patterns[] = {
	{ "referer:", /* sic */	 8,	client_referrer		},
	{ "user-agent:",	11,	client_uagent		},
	{ "ua-",		 3,	client_ua		},
	{ "from:",		 5,	client_from		},
	{ "cookie:",		 7,	client_send_cookie	},
	{ "range:",		 19,	client_range	},
	{ "x-forwarded-for:",	16,	client_x_forwarded	},
	{ "proxy-connection:",	17,	crumble			},
/*	{ "if-modified-since:", 18,	crumble			}, */
	{ NULL,			 0,	NULL			}
};

struct interceptors intercept_patterns[] = {
	{ "show-proxy-args",	14,	show_proxy_args		},
	{ "ij-blocked-url",	14,	ij_blocked_url		},
	{ "ij-untrusted-url",	14,	ij_untrusted_url	},
	{ NULL,			 0,	NULL			}
};

struct parsers server_patterns[] = {
	{ "set-cookie:",	11,	server_set_cookie	},
	{ "connection:",	11,	crumble			},
	{ NULL,			 0,	NULL			}
};

void (*add_client_headers[])() = {
	client_cookie_adder,
	client_x_forwarded_adder,
	client_xtra_adder,
	NULL
};

void (*add_server_headers[])() = {
	NULL
};

struct parsers *
match(char *buf, struct parsers *pats)
{
	struct parsers *v;

	if(buf == NULL) {
		/* hit me */
		fprintf(logfp, "bingo!\n");
		return(NULL);
	}

	for(v = pats; v->str ; v++) {
		if(strncmpic(buf, v->str, v->len) == 0) {
			return(v);
		}
	}
	return(NULL);
}

int
flush_socket(int fd, struct client_state *csp)
{
	struct iob *iob = csp->iob;
	int n = iob->eod - iob->cur;

	if(n <= 0) return(0);

	n = write_socket(fd, iob->cur, n);

	iob->eod = iob->cur = iob->buf;

	return(n);
}

int
add_to_iob(struct client_state *csp, char *buf, int n)
{
	struct iob *iob = csp->iob;
	int have, need;
	char *p;

	have = iob->eod - iob->cur;

	if(n <= 0) return(have);

	need = have + n;

	if((p = malloc(need + 1)) == NULL) {
			fprintf(logfp, "%s: malloc() iob failed\n", prog);
			fperror(logfp, "");
			return(-1);
	}

	if(have) {
		/* there is something in the buffer - save it */
		memcpy(p, iob->cur, have);

		/* replace the buffer with the new space */
		freez(iob->buf);
		iob->buf = p;

		/* point to the end of the data */
		p += have;
	} else {
		/* the buffer is empty, free it and reinitialize */
		freez(iob->buf);
		iob->buf = p;
	}

	/* copy the new data into the iob buffer */
	memcpy(p, buf, n);

	/* point to the end of the data */
	 p +=   n ;

	/* null terminate == cheap insurance */
	*p  = '\0';

	/* set the pointers to the new values */
	iob->cur = iob->buf;
	iob->eod = p;

	return(need);
}

/* this (odd) routine will parse the csp->iob and return one of the following:
 *
 * 1) a pointer to a dynamically allocated string that contains a header line
 * 2) NULL  indicating that the end of the header was reached
 * 3) ""    indicating that the end of the iob was reached before finding
 *          a complete header line.
 */

char *
get_header(struct client_state *csp)
{
	struct iob *iob = csp->iob;
	char *p, *q, *ret;

	if((iob->cur == NULL)
	|| ((p = strchr(iob->cur, '\n')) == NULL)) {
		return("");	/* couldn't find a complete header */
	}

	*p = '\0';

	ret = strdup(iob->cur);

	iob->cur = p+1;

	if((q = strchr(ret, '\r'))) *q = '\0';

	/* is this a blank linke (i.e. the end of the header) ? */
	if(*ret == '\0') {
		freez(ret);
		return(NULL);
	}

	return(ret);
}

/* h = pointer to list 'dummy' header
 * s = string to add to the list
 */

void
enlist(struct list *h, char *s)
{
	struct list *n = (struct list *)malloc(sizeof(*n));
	struct list *l;

	if(n) {
		n->str  = strdup(s);
		n->next = NULL;

		if((l = h->last)) {
			l->next = n;
		} else {
			h->next = n;
		}

		h->last = n;
	}
}

void
destroy_list(struct list *h)
{
	struct list *p, *n;

	for(p = h->next; p ; p = n) {

		n = p->next;

		freez(p->str);

		freez(p);
	}

	memset(h, '\0', sizeof(*h));
}

char *
list_to_text(struct list *h)
{
	struct list *p;
	char *ret = NULL;
	char *s;
	int size;

	size = 0;

	for(p = h->next; p ; p = p->next) {
		if(p->str) {
			size += strlen(p->str) + 2;
		}
	}

	if((ret = malloc(size + 1)) == NULL) {
		return(NULL);
	}

	ret[size] = '\0';

	s = ret;

	for(p = h->next; p ; p = p->next) {
		if(p->str) {
			strcpy(s, p->str);
			s += strlen(s);
			*s++ = '\r'; *s++ = '\n';
		}
	}

	return(ret);
}

/* sed(): add, delete or modify lines in the HTTP header streams.
 * on entry, it receives a linked list of headers space that was
 * allocated dynamically (both the list nodes and the header contents).
 *
 * it returns a single pointer to a fully formed header.
 *
 * as a side effect it frees the space used by the original header lines.
 *
 */

char *
sed(pats, more_headers, csp)
struct parsers pats[];
int (*more_headers[])();
struct client_state *csp;
{
	struct list *p;
	struct parsers *v;
	char *hdr;
	int (**f)();

	for(p = csp->headers->next; p ; p = p->next) {

		if(DEBUG(HDR)) fprintf(logfp, "scan: %s", p->str);

		if((v = match(p->str, pats))) {
			hdr = v->parser(v, p->str, csp);
			freez(p->str);
			p->str = hdr;
		}

		if(DEBUG(HDR)) fprintf(logfp, "\n");
	}

	/* place any additional headers on the csp->headers list */
	for(f = more_headers; *f ; f++) {
		(*f)(csp);
	}

	/* add the blank line at the end of the header */
	enlist(csp->headers, "");

	hdr = list_to_text(csp->headers);

	return(hdr);
}

void
free_http_request(struct http_request *http)
{
	freez(http->cmd);
	freez(http->gpc);
	freez(http->host);
	freez(http->hostport);
	freez(http->path);
	freez(http->ver);
}

/* parse out the host and port from the URL */

void
parse_http_request(char *req, struct http_request *http, struct client_state *csp)
{
	char *buf, *v[10], *url, *p;
	int n;

	memset(http, '\0', sizeof(*http));

	http->cmd = strdup(req);

	buf = strdup(req);

	n = ssplit(buf, " \r\n", v, SZ(v), 1, 1);

	if(n == 3) {

		/* this could be a CONNECT request */
		if(strcmpic(v[0], "connect") == 0) {
			http->ssl      = 1;
			http->gpc      = strdup(v[0]);
			http->hostport = strdup(v[1]);
			http->ver      = strdup(v[2]);
		}

		/* or it could be a GET or a POST */
		if((strcmpic(v[0], "get")  == 0)
		|| (strcmpic(v[0], "head") == 0)
		|| (strcmpic(v[0], "post") == 0)) {

			http->ssl      = 0;
			http->gpc      = strdup(v[0]);
			url            =        v[1] ;
			http->ver      = strdup(v[2]);

			if(strncmpic(url, "http://",  7) == 0) {
				url += 7;
			} else if(strncmpic(url, "https://", 8) == 0) {
				url += 8;
			} else {
				//url = NULL;
			}

			if(url && (p = strchr(url, '/'))) {
				http->path = strdup(p);

				*p = '\0';

				http->hostport = strdup(url);
			}
		}
	}

	freez(buf);


	if(http->hostport == NULL) {
		free_http_request(http);
		return;
	}

	buf = strdup(http->hostport);

	n = ssplit(buf, ":", v, SZ(v), 1, 1);

	if(n == 1) {
		http->host = strdup(v[0]);
		http->port = 80;
	}

	if(n == 2) {
		http->host = strdup(v[0]);
		http->port = atoi(v[1]);
	}

	freez(buf);

	if(http->host == NULL) {
		if (http->path == NULL)
			free_http_request(http);
//		else
//			http->host = "freenet";
	}

	if(http->path == NULL) {
		http->path = strdup("");
	}
}

/* here begins the family of parser functions that reformat header lines */

char *crumble(struct parsers *v, char *s, struct client_state *csp)
{
	if(DEBUG(HDR)) fprintf(logfp, " crunch!");
	return(NULL);
}

char *client_referrer(struct parsers *v, char *s, struct client_state *csp)
{
	csp->referrer = strdup(s);

	if(referrer == NULL) {
		if(DEBUG(HDR)) fprintf(logfp, " crunch!");
		return(NULL);
	}

	if(*referrer == '.') {
		return(strdup(s));
	}

	if(*referrer == '@') {
		if(csp->send_user_cookie) {
			return(strdup(s));
		} else {
			if(DEBUG(HDR)) fprintf(logfp, " crunch!");
			return(NULL);
		}
	}

	if(DEBUG(HDR)) fprintf(logfp, " modified");

	s = strsav(NULL, "Referer: ");
	s = strsav(s,     referrer  );
	return(s);
}

char *client_range(struct parsers *v, char *s, struct client_state *csp)
{
	if(range == NULL) {
		if(DEBUG(HDR)) fprintf(logfp, " default");
		return(NULL);
	}

	if(*range == '.') {
		return(strdup(s));
	}

	if(*range == '@') {
		if(csp->send_user_cookie) {
			return(strdup(s));
		} else {
			if(DEBUG(HDR)) fprintf(logfp, " default");
			return(strdup(NULL));
		}
	}

	if(DEBUG(HDR)) fprintf(logfp, " modified");

	s = strsav(NULL, "Range: ");
	s = strsav(s,     range   );
	return(s);
}


char *client_uagent(struct parsers *v, char *s, struct client_state *csp)
{
	if(uagent == NULL) {
		if(DEBUG(HDR)) fprintf(logfp, " default");
		return(strdup(DEFAULT_USER_AGENT));
	}

	if(*uagent == '.') {
		return(strdup(s));
	}

	if(*uagent == '@') {
		if(csp->send_user_cookie) {
			return(strdup(s));
		} else {
			if(DEBUG(HDR)) fprintf(logfp, " default");
			return(strdup(DEFAULT_USER_AGENT));
		}
	}

	if(DEBUG(HDR)) fprintf(logfp, " modified");

	s = strsav(NULL, "User-Agent: ");
	s = strsav(s,     uagent   );
	return(s);
}

char *client_ua(struct parsers *v, char *s, struct client_state *csp)
{
	if(uagent == NULL) {
		if(DEBUG(HDR)) fprintf(logfp, " crunch!");
		return(NULL);
	}

	if(*uagent == '.') {
		return(strdup(s));
	}

	if(*uagent == '@') {
		if(csp->send_user_cookie) {
			return(strdup(s));
		} else {
			if(DEBUG(HDR)) fprintf(logfp, " crunch!");
			return(NULL);
		}
	}

	if(DEBUG(HDR)) fprintf(logfp, " crunch!");
	return(NULL);
}

char *client_from(struct parsers *v, char *s, struct client_state *csp)
{
	/* if not set, zap it */
	if(from == NULL) {
		if(DEBUG(HDR)) fprintf(logfp, " crunch!");
		return(NULL);
	}

	if(*from == '.') {
		return(strdup(s));
	}

	if(DEBUG(HDR)) fprintf(logfp, " modified");

	s = strsav(NULL, "From: ");
	s = strsav(s,     from   );
	return(s);
}

char *client_send_cookie(struct parsers *v, char *s, struct client_state *csp)
{
	if(csp->send_user_cookie) {
		enlist(csp->cookie_list, s + v->len + 1);
	} else {
		if(DEBUG(HDR)) fprintf(logfp, " crunch!");
	}

	/* always return NULL here.  the cookie header will be sent
	 * at the end of the header.
	 */
	return(NULL);
}

char *client_x_forwarded(struct parsers *v, char *s, struct client_state *csp)
{
	if(add_forwarded) {
		csp->x_forwarded = strdup(s);
	}

	/* always return NULL, since this information
	 * will be sent at the end of the header.
	 */

	return(NULL);
}

/* the following functions add headers directly to the header list */
void client_cookie_adder(struct client_state *csp)
{
	struct list *l;
	char *tmp = NULL;
	char *e;

	for(l = csp->cookie_list->next; l ; l = l->next) {
		if(tmp) {
			tmp = strsav(tmp, "; ");
		}
		tmp = strsav(tmp, l->str);
	}

	for(l = wafer_list->next;  l ; l = l->next) {
		if(tmp) {
			tmp = strsav(tmp, "; ");
		}

		if((e = url_encode(cookie_code_map, l->str))) {
			tmp = strsav(tmp, e);
			freez(e);
		}
	}

	if(tmp) {
		char *ret;

		ret = strdup("Cookie: ");
		ret = strsav(ret, tmp);
		if(DEBUG(HDR)) fprintf(logfp, "addh: %s\r\n", ret);
		enlist(csp->headers, ret);
		freez(tmp);
		freez(ret);
	}
}

void client_xtra_adder(struct client_state *csp)
{
	struct list *l;

	for(l = xtra_list->next; l ; l = l->next) {
		if(DEBUG(HDR)) fprintf(logfp, "addh: %s\r\n", l->str);
		enlist(csp->headers, l->str);
	}
}

void client_x_forwarded_adder(struct client_state *csp)
{
	char *p = NULL;

	if(add_forwarded == 0) return;

	if(csp->x_forwarded) {
		p = strsav(p, csp->x_forwarded);
		p = strsav(p, ", ");
		p = strsav(p, csp->ip_addr_str);
	} else {
		p = strsav(p, "X-Forwarded-For: ");
		p = strsav(p, csp->ip_addr_str);
	}
	if(DEBUG(HDR)) fprintf(logfp, "addh: %s\r\n", p);
	enlist(csp->headers, p);
}

char *server_set_cookie(struct parsers *v, char *s, struct client_state *csp)
{
	if(jar) fprintf(jar, "%s\t%s\n", csp->http->host, (s + v->len + 1));

	if(csp->accept_server_cookie == 0) return(crumble(v, s, csp));

	return(strdup(s));
}

/* case insensitive string comparison */
int strcmpic(char *s1, char *s2)
{
	while(*s1 && *s2) {
		if((        *s1  !=         *s2)
		&& (tolower(*s1) != tolower(*s2))) {
			break;
		}
		s1++, s2++;
	}
	return(tolower(*s1) - tolower(*s2));
}

int strncmpic(char *s1, char *s2, size_t n)
{
	if(n <= 0) return(0);

	while(*s1 && *s2) {


		if((        *s1  !=         *s2)
		&& (tolower(*s1) != tolower(*s2))) {
			break;
		}

		if(--n <= 0) break;

		s1++, s2++;
	}
	return(tolower(*s1) - tolower(*s2));
}
