char *filters_rcs = "$Id: filters.c,v 1.2 2001/10/14 01:14:43 heretic108 Exp $";
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

#define URL(X) url_encode(url_code_map, (X))

char CBLOCK[] = "HTTP/1.0 202 Request for blocked URL\n"
		 "Pragma: no-cache\n"
		 "Last-Modified: Thu Jul 31, 1997 07:42:22 pm GMT\n"
		 "Expires:       Thu Jul 31, 1997 07:42:22 pm GMT\n"
		 "Content-Type: text/html\n\n"
		 "<html>\n"
		 "<head>\n"
		 "<title>Internet Junkbuster: Request for blocked URL</title>\n"
		 "</head>\n"
		 WHITEBG
		 "<center>"
		 "<a href=http://internet.junkbuster.com/ij-blocked-url?%s+%s+%s>"
		 BANNER
		 "</a>"
		 "</center>"
		 "</body>\n"
		 "</html>\n"
		 ;

char CTRUST[] = "HTTP/1.0 202 Request for untrusted URL\n"
		 "Pragma: no-cache\n"
		 "Last-Modified: Thu Jul 31, 1997 07:42:22 pm GMT\n"
		 "Expires:       Thu Jul 31, 1997 07:42:22 pm GMT\n"
		 "Content-Type: text/html\n\n"
		 "<html>\n"
		 "<head>\n"
		 "<title>Internet Junkbuster: Request for untrusted URL</title>\n"
		 "</head>\n"
		 WHITEBG
		 "<center>"
		 "<a href=http://internet.junkbuster.com/ij-untrusted-url?%s+%s+%s>"
		 BANNER
		 "</a>"
		 "</center>"
		 "</body>\n"
		 "</html>\n"
		 ;

char *
block_url(struct http_request *http, struct client_state *csp)
{
	struct file_list *fl;
	struct block_spec *b;
	struct url_spec url[1];
	char *p;
	char *hostport, *path, *spec;
	int n;

	if(((fl = csp->blist) == NULL) || ((b  = fl->f) == NULL)) {
		return(NULL);
	}

	*url = dsplit(http->host);

	/* if splitting the domain fails, punt */
	if(url->dbuf == NULL) return(NULL);

	for(b = b->next; b ; b = b->next) {
		if((b->url->port == 0) || (b->url->port == http->port)) {
			if((b->url->domain[0] == '\0') || (domaincmp(b->url, url) == 0)) {
				if((b->url->path == NULL) ||
#ifdef REGEX
				   (regexec(b->url->preg, http->path, 0, NULL, 0) == 0)
#else
				   (strncmp(b->url->path, http->path, b->url->pathlen) == 0)
#endif
				) {
					freez(url->dbuf);
					freez(url->dvec);

					if(b->reject == 0) return(NULL);

					hostport = URL(http->hostport);
					path     = URL(http->path);
					spec     = URL(b->url->spec);

					n  = strlen(CBLOCK);
					n += strlen(hostport);
					n += strlen(path);
					n += strlen(spec);

					p = safeMalloc(n);

					sprintf(p, CBLOCK,
						hostport, path, spec);

					freez(hostport);
					freez(path);
					freez(spec);

					return(p);
				}
			}
		}
	}
	freez(url->dbuf);
	freez(url->dvec);
	return(NULL);
}

char *
trust_url(struct http_request *http, struct client_state *csp)
{
	struct file_list *fl;
	struct block_spec *b;
	struct url_spec url[1], **tl, *t;
	char *p, *h;
	char *hostport, *path, *referrer;
	struct http_request rhttp[1];
	int n;

	if(((fl = csp->tlist) == NULL) || ((b  = fl->f) == NULL)) {
		return(NULL);
	}

	*url = dsplit(http->host);

	/* if splitting the domain fails, punt */
	if(url->dbuf == NULL) return(NULL);

	memset(rhttp, '\0', sizeof(*rhttp));

	for(b = b->next; b ; b = b->next) {

		if((b->url->port == 0) || (b->url->port == http->port)) {
			if((b->url->domain[0] == '\0') || (domaincmp(b->url, url) == 0)) {
				if((b->url->path == NULL) ||
#ifdef REGEX
				   (regexec(b->url->preg, http->path, 0, NULL, 0) == 0)
#else
				   (strncmp(b->url->path, http->path, b->url->pathlen) == 0)
#endif
				) {
					freez(url->dbuf);
					freez(url->dvec);

					if(b->reject == 0) return(NULL);

					hostport = URL(http->hostport);
					path     = URL(http->path);

					if(csp->referrer) {
						referrer = URL(csp->referrer);
					} else {
						referrer = URL("undefined");
					}

					n  = strlen(CTRUST);
					n += strlen(hostport);
					n += strlen(path);
					n += strlen(referrer);

					p = safeMalloc(n);

					sprintf(p, CTRUST,
						hostport, path, referrer);

					freez(hostport);
					freez(path);
					freez(referrer);

					return(p);
				}
			}
		}
	}

	freez(url->dbuf);
	freez(url->dvec);

	if((csp->referrer == NULL)|| (strlen(csp->referrer) <= 9)) {
		/* no referrer was supplied */
		goto trust_url_not_trusted;
	}

	/* forge a URL from the referrer so we can use
	 * convert_url() to parse it into its components.
	 */

	p = NULL;
	p = strsav(p, "GET ");
	p = strsav(p, csp->referrer + 9);	/* skip over "Referer: " */
	p = strsav(p, " HTTP/1.0");

	parse_http_request(p, rhttp, csp);

	if(rhttp->cmd == NULL) {
		freez(p);
		goto trust_url_not_trusted;
	}

	freez(p);

	*url = dsplit(rhttp->host);

	/* if splitting the domain fails, punt */
	if(url->dbuf == NULL) goto trust_url_not_trusted;

	for(tl = trust_list; (t = *tl) ; tl++) {
		if((t->port == 0) || (t->port == rhttp->port)) {
			if((t->domain[0] == '\0') || domaincmp(t, url) == 0) {
				if((t->path == NULL) ||
#ifdef REGEX
				   (regexec(t->preg, rhttp->path, 0, NULL, 0) == 0)
#else
				   (strncmp(t->path, rhttp->path, t->pathlen) == 0)
#endif
				) {
					/* if the URL's referrer is from a trusted referrer, then
					 * add the target spec to the trustfile as an unblocked
					 * domain and return NULL (which means it's OK).
					 */

					FILE *fp;
					
					freez(url->dbuf);
					freez(url->dvec);

					if((fp = fopen(trustfile, "a"))) {
						h = NULL;

						h = strsav(h, "~");
						h = strsav(h, http->hostport);

						p = http->path;
						if((*p++ == '/')
						&& (*p++ == '~')) {
						/* since this path points into a user's home space
						 * be sure to include this spec in the trustfile.
						 */
							if((p = strchr(p, '/'))) {
								*p = '\0';
								h = strsav(h, http->path);
								h = strsav(h, "/");
							}
						}

						free_http_request(rhttp);

						fprintf(fp, "%s\n", h);
						freez(h);
						fclose(fp);
					}
					return(NULL);
				}
			}
		}
	}

trust_url_not_trusted:
	free_http_request(rhttp);

	hostport = URL(http->hostport);
	path     = URL(http->path);

	if(csp->referrer) {
		referrer = URL(csp->referrer);
	} else {
		referrer = URL("undefined");
	}

	n  = strlen(CTRUST);
	n += strlen(hostport);
	n += strlen(path);
	n += strlen(referrer);

	p = safeMalloc(n);
	sprintf(p, CTRUST, hostport, path, referrer);

	freez(hostport);
	freez(path);
	freez(referrer);

	return(p);
}

/* intercept_url() checks the URL `basename' against a list of URLs
 * to snarf.  If it matches, it calls the associated function which
 * returns an HTML page to send back to the client.
 */

char *
intercept_url(struct http_request *http, struct client_state *csp)
{
	char *basename;
	struct interceptors *v;
	
	basename = strrchr(http->path, '/');

	if(basename == NULL) return(NULL);

	basename++; /* first char past the last slash */

	if(*basename) {
		for(v = intercept_patterns; v->str; v++) {
			if(strncmp(basename, v->str, v->len) == 0) {

				return((v->interceptor)(http, csp));
			}
		}
	}

	return(NULL);
}

struct cookie_spec *
cookie_url(struct http_request *http, struct client_state *csp)
{
	struct file_list *fl;
	struct cookie_spec *b;
	struct url_spec url[1];

	if(((fl = csp->clist) == NULL) || ((b  = fl->f) == NULL)) {
		return(NULL);
	}

	*url = dsplit(http->host);

	/* if splitting the domain fails, punt */
	if(url->dbuf == NULL) return(NULL);

	for(b = b->next; b ; b = b->next) {
		if((b->url->port == 0) || (b->url->port == http->port)) {
			if((b->url->domain[0] == '\0') || (domaincmp(b->url, url) == 0)) {
				if((b->url->path == NULL) ||
#ifdef REGEX
				   (regexec(b->url->preg, http->path, 0, NULL, 0) == 0)
#else
				   (strncmp(b->url->path, http->path, b->url->pathlen) == 0)
#endif
				) {
					freez(url->dbuf);
					freez(url->dvec);
					return(b);
				}
			}
		}
	}
	freez(url->dbuf);
	freez(url->dvec);
	return(NULL);
}

struct gateway *
forward_url(struct http_request *http, struct client_state *csp)
{
	struct file_list *fl;
	struct forward_spec *b;
	struct url_spec url[1];

	if(((fl = csp->flist) == NULL) || ((b  = fl->f) == NULL)) {
		return(gw_default);
	}

	*url = dsplit(http->host);

	/* if splitting the domain fails, punt */
	if(url->dbuf == NULL) return(gw_default);

	for(b = b->next; b ; b = b->next) {
		if((b->url->port == 0) || (b->url->port == http->port)) {
			if((b->url->domain[0] == '\0') || (domaincmp(b->url, url) == 0)) {
				if((b->url->path == NULL) ||
#ifdef REGEX
				   (regexec(b->url->preg, http->path, 0, NULL, 0) == 0)
#else
				   (strncmp(b->url->path, http->path, b->url->pathlen) == 0)
#endif
				) {
					freez(url->dbuf);
					freez(url->dvec);
					return(b->gw);
				}
			}
		}
	}
	freez(url->dbuf);
	freez(url->dvec);
	return(gw_default);
}

/* dsplit() takes a domain and returns a pointer to a url_spec
 * structure populated with dbuf, dcnt and dvec.  the other fields
 * in the structure that is returned are zero.
 *
 */

struct url_spec
dsplit(char *domain)
{
	struct url_spec ret[1];
	char *v[BUFSIZ];
	int size;
	char *p;

	memset(ret, '\0', sizeof(*ret));

	if((p = strrchr(domain, '.'))) {
		if(*(++p) == '\0') {
			ret->toplevel = 1;
		}
	}

	ret->dbuf = strdup(domain);

	/* map to lower case */
	for(p = ret->dbuf; *p ; p++) *p = tolower(*p);

	/* split the domain name into components */
	ret->dcnt = ssplit(ret->dbuf, ".", v, SZ(v), 1, 1);

	if(ret->dcnt <= 0) {
		memset(ret, '\0', sizeof(ret));
		return(*ret);
	}

	/* save a copy of the pointers in dvec */
	size = ret->dcnt * sizeof(*ret->dvec);
		
	if((ret->dvec = safeMalloc(size))) {
		memcpy(ret->dvec, v, size);
	}

	return(*ret);
}

/* the "pattern" is a domain that may contain a '*' as a wildcard.
 * the "fqdn" is the domain name against which the patterns are compared.
 *
 * domaincmp("a.b.c" , "a.b.c")	=> 0 (MATCH)
 * domaincmp("a*.b.c", "a.b.c")	=> 0 (MATCH)
 * domaincmp("b.c"   , "a.b.c")	=> 0 (MATCH)
 * domaincmp(""      , "a.b.c")	=> 0 (MATCH)
 */

int
domaincmp(struct url_spec *pattern, struct url_spec *fqdn)
{
	char **pv, **fv;	/* vectors  */
	int    pn,   fn;	/* counters */
	char  *p,   *f;		/* chars    */
	
	pv = pattern->dvec;
	pn = pattern->dcnt;

	fv = fqdn->dvec;
	fn = fqdn->dcnt;

	while((pn > 0) && (fn > 0)) {
		p = pv[--pn];
		f = fv[--fn];

		while(*p && *f && (*p == tolower(*f))) {
			p++, f++;
		}

		if((*p != tolower(*f)) && (*p != '*')) return(1);
	}

	if(pn > 0) return(1);

	return(0);
}

/* intercept functions */

char *
show_proxy_args(struct http_request *http, struct client_state *csp)
{
	char *s = NULL;

	s = strsav(s, proxy_args->header);
	s = strsav(s, proxy_args->invocation);
	s = strsav(s, proxy_args->gateways);

	if(csp->blist) {
		s = strsav(s, csp->blist->proxy_args);
	}

	if(csp->clist) {
		s = strsav(s, csp->clist->proxy_args);
	}

	if(csp->tlist) {
		s = strsav(s, csp->tlist->proxy_args);
	}

	if(csp->flist) {
		s = strsav(s, csp->flist->proxy_args);
	}
	s = strsav(s, proxy_args->trailer);

	return(s);
}

char *
ij_blocked_url(struct http_request *http, struct client_state *csp)
{
	int n;
	char *hostport, *path, *pattern, *p, *v[9];

	char *template =
		"HTTP/1.0 200 OK\r\n"
		"Pragma: no-cache\n"
		"Last-Modified: Thu Jul 31, 1997 07:42:22 pm GMT\n"
		"Expires:       Thu Jul 31, 1997 07:42:22 pm GMT\n"
		"Content-Type: text/html\n\n"
		"<html>\n"
		"<head>\n"
		"<title>Internet Junkbuster: Request for blocked URL</title>\n"
		"</head>\n"
		BODY
		"<center><h1>"
		BANNER
		"</h1></center>"
		"The " BANNER " Proxy "
		"<A href=\"http://internet.junkbuster.com\">"
		"(http://internet.junkbuster.com) </A>"
		"intercepted the request for %s%s\n"
		"because the URL matches the following pattern "
		"in the blockfile: %s\n"
		"</body>\n"
		"</html>\n"
		;

	if((n = ssplit(http->path, "?+", v, SZ(v), 0, 0)) == 4) {
		hostport = url_decode(v[1]);
		path     = url_decode(v[2]);
		pattern  = url_decode(v[3]);
	} else {
		hostport = strdup("undefined_host");
		path     = strdup("/undefined_path");
		pattern  = strdup("undefined_pattern");
	}

	n  = strlen(template);
	n += strlen(hostport);
	n += strlen(path    );
	n += strlen(pattern );

	if((p = safeMalloc(n))) {
		sprintf(p, template, hostport, path, pattern);
	}

	freez(hostport);
	freez(path    );
	freez(pattern );

	return(p);
}

char *
ij_untrusted_url(struct http_request *http, struct client_state *csp)
{
	int n;
	char *hostport, *path, *p, *v[9];
	char buf[BUFSIZ];
	struct url_spec **tl, *t;


	char *template =
		"HTTP/1.0 200 OK\r\n"
		"Pragma: no-cache\n"
		"Last-Modified: Thu Jul 31, 1997 07:42:22 pm GMT\n"
		"Expires:       Thu Jul 31, 1997 07:42:22 pm GMT\n"
		"Content-Type: text/html\n\n"
		"<html>\n"
		"<head>\n"
		"<title>Internet Junkbuster: Request for untrusted URL</title>\n"
		"</head>\n"
		BODY
		"<center><h1>"
		BANNER
		"</h1></center>"
		"The " BANNER " Proxy "
		"<A href=\"http://internet.junkbuster.com\">"
		"(http://internet.junkbuster.com) </A>"
		"intercepted the request for %s%s\n"
		"because the URL is not trusted.\n"
		"<br><br>\n"
		;

	if((n = ssplit(http->path, "?+", v, SZ(v), 0, 0)) == 4) {
		hostport = url_decode(v[1]);
		path     = url_decode(v[2]);
		referrer = url_decode(v[3]);
	} else {
		hostport = strdup("undefined_host");
		path     = strdup("/undefined_path");
		referrer = strdup("undefined");
	}

	n  = strlen(template);
	n += strlen(hostport);
	n += strlen(path    );

	if((p = safeMalloc(n))) {
		sprintf(p, template, hostport, path);
	}

	freez(hostport);
	freez(path    );

	strsav(p, "The referrer in this request was <strong>");
	strsav(p, referrer);
	strsav(p, "</strong><br>\n");

	p = strsav(p, "<h3>The following referrers are trusted</h3>\n");

	for(tl = trust_list; (t = *tl) ; tl++) {
		sprintf(buf, "%s<br>\n", t->spec);
		p = strsav(p, buf);
	}

	if(trust_info->next) {
		struct list *l;

		strcpy(buf,
			"<p>"
			"You can learn more about what this means "
			"and what you may be able to do about it by "
			"reading the following documents:<br>\n"
			"<ol>\n"
		);

		p = strsav(p, buf);
		
		for(l = trust_info->next; l ; l = l->next) {
			sprintf(buf,
				"<li> <a href=%s>%s</a><br>\n",
					l->str, l->str);
			p = strsav(p, buf);
		}

		p = strsav(p, "</ol>\n");
	}

	p = strsav(p, "</body>\n" "</html>\n");

	return(p);
}
