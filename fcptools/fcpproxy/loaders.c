char *loaders_rcs = "$Id: loaders.c,v 1.3 2001/12/02 20:12:32 joliveri Exp $";
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
#include <stdlib.h>
#include <sys/types.h>
#include <string.h>
#include <malloc.h>
#include <errno.h>
#include <sys/stat.h>
#include <ctype.h>

#ifndef _WIN32
#include <unistd.h>
#endif

#ifdef REGEX
#include <gnu_regex.h>
#endif

#include "fcpproxy.h"


/* sweep() is basically a mark and sweep garbage collector.
 * it is run (by the parent thread) every once in a while to reclaim memory.
 *
 * it uses a mark and sweep strategy:
 *   1) mark all files as inactive
 *
 *   2) check with each client:
 *       if it is active,   mark its files as active
 *       if it is inactive, free its resources
 *
 *   3) free the resources of all of the files that
 *      are still marked as inactive (and are obsolete).
 *
 *   N.B. files that are not obsolete don't have an unloader defined.
 */

void
sweep()
{
	struct file_list *fl, *nfl;
	struct client_state *csp, *ncsp;

	/* clear all of the file's active flags */
	for(fl = files->next; fl ; fl = fl->next) {
		fl->active = 0;
	}

	for(csp = clients; csp && (ncsp = csp->next) ; csp = csp->next) {
		if(ncsp->active) {
			/* mark this client's files as active */
			if(ncsp->alist) ncsp->alist->active = 1;
			if(ncsp->blist) ncsp->blist->active = 1;
			if(ncsp->clist) ncsp->clist->active = 1;
			if(ncsp->tlist) ncsp->tlist->active = 1;
		} else {
			/* this client one is not active,
			 * release its resources
			 */
			csp->next = ncsp->next;

			freez(ncsp->ip_addr_str);
			freez(ncsp->referrer);
			freez(ncsp->x_forwarded);
			freez(ncsp->ip_addr_str);
			freez(ncsp->iob->buf);

			free_http_request(ncsp->http);

			destroy_list(ncsp->headers);
			destroy_list(ncsp->cookie_list);

			freez(ncsp);
		}
	}

	for(fl = files; fl && (nfl = fl->next) ; fl = fl->next) {
		if(nfl->active == 0) {
			if(nfl->unloader) {
				fl->next = nfl->next;

				(nfl->unloader)(nfl->f);

				freez(nfl->proxy_args);

				freez(nfl);
			}
		}
	}
}

void
unload_url(struct url_spec *url)
{
	if(url == NULL) return;

	freez(url->spec);
	freez(url->domain);
	freez(url->dbuf);
	freez(url->dvec);
	freez(url->path);
#ifdef REGEX
	if(url->preg) {
		regfree(url->preg);
		freez(url->preg);
	}
#endif
}

void
unload_blockfile(struct block_spec *b)
{
	if(b == NULL) return;

	unload_blockfile(b->next);

	unload_url(b->url);

	freez(b);
}

void
unload_cookiefile(struct cookie_spec *b)
{
	if(b == NULL) return;

	unload_cookiefile(b->next);

	unload_url(b->url);

	freez(b);
}

void
unload_trustfile(struct block_spec *b)
{
	if(b == NULL) return;

	unload_trustfile(b->next);

	unload_url(b->url);

	freez(b);
}

void
unload_forwardfile(struct forward_spec *b)
{
	if(b == NULL) return;

	unload_forwardfile(b->next);

	unload_url(b->url);

	freez(b->gw->gateway_host);
	freez(b->gw->forward_host);

	freez(b);
}

static struct file_list *current_blockfile;
static struct file_list *current_cookiefile;
static struct file_list *current_trustfile;
static struct file_list *current_forwardfile;

int
load_blockfile(struct client_state *csp)
{
	FILE *fp;

	struct block_spec *b, *bl;
	char  buf[BUFSIZ], *p, *q;
	int port, reject;
	struct file_list *fs;
	static struct stat prev[1], curr[1];
	struct url_spec url[1];

	if(stat(blockfile, curr) < 0) {
		goto load_blockfile_error;
	}

	if(current_blockfile && (prev->st_mtime == curr->st_mtime)) {
		csp->blist = current_blockfile;
		return(0);
	}

	fs = (struct file_list  *) zalloc(sizeof(*fs));
	bl = (struct block_spec *) zalloc(sizeof(*bl));

	if((fs == NULL) || (bl == NULL)) {
		goto load_blockfile_error;
	}

	fs->f = bl;

	fs->next    = files->next;
	files->next = fs;

	if(csp) {
		csp->blist = fs;
	}

	*prev = *curr;

	if((fp = fopen(blockfile, "r")) == NULL) {
		goto load_blockfile_error;
	}
	
	p = url_encode(html_code_map, blockfile);

	sprintf(buf, "<h2>The file `%s' contains the following patterns</h2>\n", p);
	freez(p);

	fs->proxy_args = strsav(fs->proxy_args, buf);

	fs->proxy_args = strsav(fs->proxy_args, "<pre>");

	while(fgets(buf, sizeof(buf), fp)) {

		if((p = url_encode(html_code_map, buf))) {
			fs->proxy_args = strsav(fs->proxy_args, p);
		}
		freez(p);
		fs->proxy_args = strsav(fs->proxy_args, "<br>");

		if((p = strpbrk(buf, "\r\n")) != NULL) {
			*p = '\0';
		}

		/* comments */
		if((p = strchr(buf, '#'))) *p = '\0';

		/* elide white-space */
		for(p = q = buf; *q ; q++) {
			if(!isspace(*q)) *p++ = *q;
		}

		*p = '\0';

		reject = 1;

		if(*buf == '~') {
			reject = 0;
			p = buf;
			q = p+1;
			while ((*p++ = *q++)) {
				/* nop */
			}
		}

		/* skip blank lines */
		if(*buf == '\0') continue;

		/* allocate a new node */
		if(((b            = zalloc(sizeof(*b)))            == NULL)
#ifdef REGEX
		|| ((b->url->preg = zalloc(sizeof(*b->url->preg))) == NULL)
#endif
		) {
			fclose(fp);
			goto load_blockfile_error;
		}

		/* add it to the list */
		b->next  = bl->next;
		bl->next = b;

		/* save a copy of the orignal specification */
		if((b->url->spec = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_blockfile_error;
		}

		b->reject = reject;

		if((p = strchr(buf, '/'))) {
			b->url->path    = strdup(p);
			b->url->pathlen = strlen(b->url->path);
			*p = '\0';
		} else {
			b->url->path    = NULL;
			b->url->pathlen = 0;
		}
#ifdef REGEX
		if(b->url->path) {
			int errcode;
			char rebuf[BUFSIZ];

			sprintf(rebuf, "^(%s)", b->url->path);

			errcode = regcomp(b->url->preg, rebuf,
					(REG_EXTENDED|REG_NOSUB|REG_ICASE));

			if(errcode) {
				size_t errlen =
					regerror(errcode,
						b->url->preg, buf, sizeof(buf));

				buf[errlen] = '\0';

				fprintf(logfp,
					"%s: error compiling %s: %s\n",
						prog, b->url->spec, buf);
				fclose(fp);
				goto load_blockfile_error;
			}
		} else {
			freez(b->url->preg);
		}
#endif
		if((p = strchr(buf, ':')) == NULL) {
			port = 0;
		} else {
			*p++ = '\0';
			port = atoi(p);
		}

		b->url->port = port;

		if((b->url->domain = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_blockfile_error;
		}

		/* split domain into components */
		*url = dsplit(b->url->domain);
		b->url->dbuf = url->dbuf;
		b->url->dcnt = url->dcnt;
		b->url->dvec = url->dvec;
	}
	fs->proxy_args = strsav(fs->proxy_args, "</pre>");

	fclose(fp);

	/* the old one is now obsolete */
	if(current_blockfile) {
		current_blockfile->unloader = unload_blockfile;
	}

	current_blockfile = fs;

	return(0);

load_blockfile_error:
	fprintf(logfp, "%s: can't load blockfile '%s': ",
		prog, blockfile);
	fperror(logfp, "");
	return(-1);
}

int
load_cookiefile(struct client_state *csp)
{
	FILE *fp;

	struct cookie_spec *b, *bl;
	char  buf[BUFSIZ], *p, *q;
	char *tmp_vec[BUFSIZ];
	int port, user_cookie, server_cookie;
	static struct stat prev[1], curr[1];
	static struct file_list *fs;
	struct url_spec url[1];

	if(stat(cookiefile, curr) < 0) {
		goto load_cookie_error;
	}

	if(current_cookiefile && (prev->st_mtime == curr->st_mtime)) {
		csp->clist = current_cookiefile;
		return(0);
	}

	fs = (struct file_list   *) zalloc(sizeof(*fs));
        bl = (struct cookie_spec *) zalloc(sizeof(*bl));

	if((fs == NULL) || (bl == NULL)) {
		goto load_cookie_error;
	}

	fs->f = bl;

	fs->next    = files->next;
	files->next = fs;

	if(csp) {
		csp->clist = fs;
	}

	*prev = *curr;
	
	if((fp = fopen(cookiefile, "r")) == NULL) {
		goto load_cookie_error;
	}

	p = url_encode(html_code_map, cookiefile);

	sprintf(buf, "<h2>The file `%s' contains the following patterns</h2>\n", p);

	freez(p);

	fs->proxy_args = strsav(fs->proxy_args, buf);

	fs->proxy_args = strsav(fs->proxy_args, "<pre>");

	while(fgets(buf, sizeof(buf), fp)) {

		if((p = url_encode(html_code_map, buf))) {
			fs->proxy_args = strsav(fs->proxy_args, p);
		}
		freez(p);
		fs->proxy_args = strsav(fs->proxy_args, "<br>");

		if((p = strpbrk(buf, "\r\n")) != NULL) {
			*p = '\0';
		}

		/* comments */
		if((p = strchr(buf, '#'))) *p = '\0';

		/* elide white-space */
		for(p = q = buf; *q ; q++) {
			if(!isspace(*q)) *p++ = *q;
		}

		*p = '\0';

		p = buf;

		switch((int)*p) {
		case '>':
			server_cookie = 0;
			user_cookie   = 1;
			p++;
			break;
		case '<':
			server_cookie = 1;
			user_cookie   = 0;
			p++;
			break;
		case '~':
			server_cookie = 0;
			user_cookie   = 0;
			p++;
			break;
		default:
			server_cookie = 1;
			user_cookie   = 1;
			break;
		}

		/* elide any of the "special" chars from the
		 * front of the pattern
		 */
		q = buf;
		if(p > q) while ((*q++ = *p++)) {
			/* nop */
		}

		/* skip blank lines */
		if(*buf == '\0') continue;

		/* allocate a new node */
		if(((b            = zalloc(sizeof(*b)))            == NULL)
#ifdef REGEX
		|| ((b->url->preg = zalloc(sizeof(*b->url->preg))) == NULL)
#endif
		) {
			fclose(fp);
			goto load_cookie_error;
		}

		/* add it to the list */
		b->next  = bl->next;
		bl->next = b;

		/* save a copy of the orignal specification */
		if((b->url->spec = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_cookie_error;
		}

		b->send_user_cookie     = user_cookie;
		b->accept_server_cookie = server_cookie;

		if((p = strchr(buf, '/'))) {
			b->url->path    = strdup(p);
			b->url->pathlen = strlen(b->url->path);
			*p = '\0';
		} else {
			b->url->path    = NULL;
			b->url->pathlen = 0;
		}
#ifdef REGEX
		if(b->url->path) {
			int errcode;
			char rebuf[BUFSIZ];

			sprintf(rebuf, "^(%s)", b->url->path);

			errcode = regcomp(b->url->preg, rebuf,
					(REG_EXTENDED|REG_NOSUB|REG_ICASE));
			if(errcode) {
				size_t errlen =
					regerror(errcode,
						b->url->preg, buf, sizeof(buf));

				buf[errlen] = '\0';

				fprintf(logfp,
					"%s: error compiling %s: %s\n",
						prog, b->url->spec, buf);
				fclose(fp);
				goto load_cookie_error;
			}
		} else {
			freez(b->url->preg);
		}
#endif
		if((p = strchr(buf, ':')) == NULL) {
			port = 0;
		} else {
			*p++ = '\0';
			port = atoi(p);
		}

		b->url->port = port;

		if((b->url->domain = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_cookie_error;
		}

		/* split domain into components */
		*url = dsplit(b->url->domain, tmp_vec);
		b->url->dbuf = url->dbuf;
		b->url->dcnt = url->dcnt;
		b->url->dvec = url->dvec;
	}

	fs->proxy_args = strsav(fs->proxy_args, "</pre>");

	fclose(fp);

	/* the old one is now obsolete */
	if(current_cookiefile) {
		current_cookiefile->unloader = unload_cookiefile;
	}

	current_cookiefile = fs;

	return(0);

load_cookie_error:
	fprintf(logfp, "%s: can't load cookiefile '%s': ",
		prog, cookiefile);
	fperror(logfp, "");
	return(-1);
}

int
load_trustfile(struct client_state *csp)
{
	FILE *fp;

	struct block_spec *b, *bl;
	struct url_spec **tl;

	char  buf[BUFSIZ], *p, *q;
	int port, reject, trusted;
	struct file_list *fs;
	static struct stat prev[1], curr[1];
	struct url_spec url[1];

	if(stat(trustfile, curr) < 0) {
		goto load_trustfile_error;
	}

	if(current_trustfile && (prev->st_mtime == curr->st_mtime)) {
		csp->tlist = current_trustfile;
		return(0);
	}

	fs = (struct file_list  *) zalloc(sizeof(*fs));
	bl = (struct block_spec *) zalloc(sizeof(*bl));

	if((fs == NULL) || (bl == NULL)) {
		goto load_trustfile_error;
	}

	fs->f = bl;

	fs->next    = files->next;
	files->next = fs;

	if(csp) {
		csp->tlist = fs;
	}

	*prev = *curr;

	if((fp = fopen(trustfile, "r")) == NULL) {
		goto load_trustfile_error;
	}
	
	p = url_encode(html_code_map, trustfile);

	sprintf(buf, "<h2>The file `%s' contains the following patterns</h2>\n", p);

	freez(p);

	fs->proxy_args = strsav(fs->proxy_args, buf);

	fs->proxy_args = strsav(fs->proxy_args, "<pre>");

	tl = trust_list;

	while(fgets(buf, sizeof(buf), fp)) {

		if((p = url_encode(html_code_map, buf))) {
			fs->proxy_args = strsav(fs->proxy_args, p);
		}
		freez(p);
		fs->proxy_args = strsav(fs->proxy_args, "<br>");

		if((p = strpbrk(buf, "\r\n")) != NULL) {
			*p = '\0';
		}

		/* comments */
		if((p = strchr(buf, '#'))) *p = '\0';

		/* elide white-space */
		for(p = q = buf; *q ; q++) {
			if(!isspace(*q)) *p++ = *q;
		}

		*p = '\0';

		trusted = 0;
		reject  = 1;

		if(*buf == '+') {
			trusted = 1;
			*buf = '~';
		}

		if(*buf == '~') {
			reject = 0;
			p = buf;
			q = p+1;
			while ((*p++ = *q++)) {
				/* nop */
			}
		}

		/* skip blank lines */
		if(*buf == '\0') continue;

		/* allocate a new node */
		if(((b            = zalloc(sizeof(*b)))            == NULL)
#ifdef REGEX
		|| ((b->url->preg = zalloc(sizeof(*b->url->preg))) == NULL)
#endif
		) {
			fclose(fp);
			goto load_trustfile_error;
		}

		/* add it to the list */
		b->next  = bl->next;
		bl->next = b;

		/* save a copy of the orignal specification */
		if((b->url->spec = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_trustfile_error;
		}

		b->reject = reject;

		if((p = strchr(buf, '/'))) {
			b->url->path    = strdup(p);
			b->url->pathlen = strlen(b->url->path);
			*p = '\0';
		} else {
			b->url->path    = NULL;
			b->url->pathlen = 0;
		}
#ifdef REGEX
		if(b->url->path) {
			int errcode;
			char rebuf[BUFSIZ];

			sprintf(rebuf, "^(%s)", b->url->path);

			errcode = regcomp(b->url->preg, rebuf,
					(REG_EXTENDED|REG_NOSUB|REG_ICASE));

			if(errcode) {
				size_t errlen =
					regerror(errcode,
						b->url->preg, buf, sizeof(buf));

				buf[errlen] = '\0';

				fprintf(logfp,
					"%s: error compiling %s: %s\n",
						prog, b->url->spec, buf);
				fclose(fp);
				goto load_trustfile_error;
			}
		} else {
			freez(b->url->preg);
		}
#endif
		if((p = strchr(buf, ':')) == NULL) {
			port = 0;
		} else {
			*p++ = '\0';
			port = atoi(p);
		}

		b->url->port = port;

		if((b->url->domain = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_trustfile_error;
		}

		/* split domain into components */
		*url = dsplit(b->url->domain);
		b->url->dbuf = url->dbuf;
		b->url->dcnt = url->dcnt;
		b->url->dvec = url->dvec;

		/* save a pointer to URL's spec
		 * in the list of trusted URL's, too
		 */
		if(trusted) *tl++ = b->url;
	}

	fs->proxy_args = strsav(fs->proxy_args, "</pre>");

	*tl = NULL;

	fclose(fp);

	/* the old one is now obsolete */
	if(current_trustfile) {
		current_trustfile->unloader = unload_trustfile;
	}

	current_trustfile = fs;

	return(0);

load_trustfile_error:
	fprintf(logfp, "%s: can't load trustfile '%s': ",
		prog, trustfile);
	fperror(logfp, "");
	return(-1);
}

int
load_forwardfile(struct client_state *csp)
{
	FILE *fp;

	struct forward_spec *b, *bl;
	char  buf[BUFSIZ], *p, *q, *tmp;
	char  *vec[4];
	int port, n, reject;
	struct file_list *fs;
	struct gateway *gw;
	static struct stat prev[1], curr[1];
	struct url_spec url[1];

	if(stat(forwardfile, curr) < 0) {
		goto load_forwardfile_error;
	}

	if(current_forwardfile && (prev->st_mtime == curr->st_mtime)) {
		csp->flist = current_forwardfile;
		return(0);
	}

	fs = (struct file_list    *) zalloc(sizeof(*fs));
	bl = (struct forward_spec *) zalloc(sizeof(*bl));

	if((fs == NULL) || (bl == NULL)) {
		goto load_forwardfile_error;
	}

	memset(fs, '\0', sizeof(*fs));
	memset(bl, '\0', sizeof(*bl));

	fs->f = bl;

	fs->next    = files->next;
	files->next = fs;

	if(csp) {
		csp->flist = fs;
	}

	*prev = *curr;

	if((fp = fopen(forwardfile, "r")) == NULL) {
		goto load_forwardfile_error;
	}
	
	p = url_encode(html_code_map, forwardfile);

	sprintf(buf, "<h2>The file `%s' contains the following patterns</h2>\n", p);

	freez(p);

	fs->proxy_args = strsav(fs->proxy_args, buf);

	tmp = NULL;

	fs->proxy_args = strsav(fs->proxy_args, "<pre>");

	while(fgets(buf, sizeof(buf), fp)) {

		freez(tmp);

		if((p = url_encode(html_code_map, buf))) {
			fs->proxy_args = strsav(fs->proxy_args, p);
		}
		freez(p);
		fs->proxy_args = strsav(fs->proxy_args, "<br>");

		if((p = strpbrk(buf, "\r\n")) != NULL) {
			*p = '\0';
		}

		/* comments */
		if((p = strchr(buf, '#'))) *p = '\0';

		/* skip blank lines */
		if(*buf == '\0') continue;

		tmp = strdup(buf);

		n = ssplit(tmp, " \t", vec, SZ(vec), 1, 1);

		if(n != 4) {
			fprintf(stderr, "error in forwardfile: %s\n", buf);
			continue;
		}

		strcpy(buf, vec[0]);

		reject = 1;

		if(*buf == '~') {
			reject = 0;
			p = buf;
			q = p+1;
			while ((*p++ = *q++)) {
				/* nop */
			}
		}

		/* skip blank lines */
		if(*buf == '\0') continue;

		/* allocate a new node */
		if(((b            = zalloc(sizeof(*b)))            == NULL)
#ifdef REGEX
		|| ((b->url->preg = zalloc(sizeof(*b->url->preg))) == NULL)
#endif
		) {
			fclose(fp);
			goto load_forwardfile_error;
		}

		/* add it to the list */
		b->next  = bl->next;
		bl->next = b;

		/* save a copy of the orignal specification */
		if((b->url->spec = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_forwardfile_error;
		}

		b->reject = reject;

		if((p = strchr(buf, '/'))) {
			b->url->path    = strdup(p);
			b->url->pathlen = strlen(b->url->path);
			*p = '\0';
		} else {
			b->url->path    = NULL;
			b->url->pathlen = 0;
		}
#ifdef REGEX
		if(b->url->path) {
			int errcode;
			char rebuf[BUFSIZ];

			sprintf(rebuf, "^(%s)", b->url->path);

			errcode = regcomp(b->url->preg, rebuf,
					(REG_EXTENDED|REG_NOSUB|REG_ICASE));

			if(errcode) {
				size_t errlen =
					regerror(errcode,
						b->url->preg, buf, sizeof(buf));

				buf[errlen] = '\0';

				fprintf(logfp,
					"%s: error compiling %s: %s\n",
						prog, b->url->spec, buf);
				fclose(fp);
				goto load_forwardfile_error;
			}
		} else {
			freez(b->url->preg);
		}
#endif
		if((p = strchr(buf, ':')) == NULL) {
			port = 0;
		} else {
			*p++ = '\0';
			port = atoi(p);
		}

		b->url->port = port;

		if((b->url->domain = strdup(buf)) == NULL) {
			fclose(fp);
			goto load_forwardfile_error;
		}

		/* split domain into components */
		*url = dsplit(b->url->domain);
		b->url->dbuf = url->dbuf;
		b->url->dcnt = url->dcnt;
		b->url->dvec = url->dvec;

		/* now parse the gateway specs */

		p = vec[2];

		for(gw = gateways; gw->name; gw++) {
			if(strcmp(gw->name, p) == 0) {
				break;
			}
		}

		if(gw->name == NULL) {
			goto load_forwardfile_error;
		}

		/* save this as the gateway type */
		*b->gw = *gw;

		/* now parse the gateway host[:port] spec */
		p = vec[3];

		if(strcmp(p, ".") != 0) {
			b->gw->gateway_host = strdup(p);

			if((p = strchr(b->gw->gateway_host, ':'))) {
				*p++ = '\0';
				b->gw->gateway_port = atoi(p);
			}

			if(b->gw->gateway_port <= 0) {
				goto load_forwardfile_error;
			}
		}

		/* now parse the forwarding spec */
		p = vec[1];

		if(strcmp(p, ".") != 0) {
			b->gw->forward_host = strdup(p);

			if((p = strchr(b->gw->forward_host, ':'))) {
				*p++ = '\0';
				b->gw->forward_port = atoi(p);
			}

			if(b->gw->forward_port <= 0) {
				b->gw->forward_port = 8000;
			}
		}
	}

	fs->proxy_args = strsav(fs->proxy_args, "</pre>");

	freez(tmp);

	fclose(fp);

	/* the old one is now obsolete */
	if(current_forwardfile) {
		current_forwardfile->unloader = unload_forwardfile;
	}

	current_forwardfile = fs;

	return(0);

load_forwardfile_error:
	fprintf(logfp, "%s: can't load forwardfile '%s': ",
		prog, forwardfile);
	fperror(logfp, "");
	return(-1);
}

#define JUNKBUSTERS "http://www.junkbusters.com"
#define OPT "href=\"" JUNKBUSTERS "/ht/en/ijb" VERSION "man.html#"

/* strsav() takes a pointer to a string stored in a dynamically allocated
 * buffer and a pointer to a string and returns a pointer to a new dynamically
 * allocated space that contains the concatenation of the two input strings
 * the previous space is free()'d by realloc().
 */
char *
strsav(char *old, char *text_to_append)
{
	int old_len, new_len;
	char *p;

	if(( text_to_append == NULL)
	|| (*text_to_append == '\0')) {
		return(old);
	}

	if(old) {
		old_len = strlen(old);
	} else {
		old_len = 0;
	}

	new_len = old_len + strlen(text_to_append) + 1;

	if(old) {
		if((p = realloc(old, new_len)) == NULL) {
			fprintf(logfp, "%s: realloc(%d) bytes for proxy_args failed!\n", prog, new_len);
			exit(1);
		}
	} else {
		if((p = malloc(new_len)) == NULL) {
			fprintf(logfp, "%s: malloc(%d) bytes for proxy_args failed!\n", prog, new_len);
			exit(1);
		}
	}

	strcpy(p + old_len, text_to_append);
	return(p);
}

void
savearg(char *c, char *o)
{
	char buf[BUFSIZ];
	static int one_shot = 1;

	if(one_shot) {
		one_shot = 0;
		proxy_args->invocation = strsav(proxy_args->invocation,
			"<br>\n"
			"and the following options were set "
			"in the configuration file"
			"<br><br>\n"
		);
	}

	*buf = '\0';

	if(c && *c) {
		if((c = url_encode(html_code_map, c))) {
			sprintf(buf, "<a " OPT "%s\">%s</a> ", c, c);
		}
		freez(c);
	}
	if(o && *o) { 
		if((o = url_encode(html_code_map, o))) {
			if(strncmpic(o, "http://", 7) == 0) {
				strcat(buf, "<a href=\"");
				strcat(buf, o);
				strcat(buf, "\">");
				strcat(buf, o);
				strcat(buf, "</a>");
			} else {
				strcat(buf, o);
			}
		}
		freez(o);
	}

	strcat(buf, "<br>\n");

	proxy_args->invocation = strsav(proxy_args->invocation, buf);
}


void
init_proxy_args(int argc, char *argv[])
{
	struct gateway *g;
	int i;

	proxy_args->header = strsav(proxy_args->header,
		"HTTP/1.0 200 OK\n"
		"Server: IJ/" VERSION "\n"
		"Content-type: text/html\n\n"

		"<html>"
		"<head>"
		"<title>Internet Junkbuster Proxy Status</title>"
		"</head>\n"
		"<body bgcolor=\"#f8f8f0\" link=\"#000078\" alink=\"#ff0022\" vlink=\"#787878\">\n"
		"<center>\n"
		"<h1>" BANNER "\n"
		"<a href=\"" JUNKBUSTERS "/ht/en/ijb" VERSION "faq.html#show\">Proxy Status</a>\n"
		"</h1></center>\n"
		"<h2>You are using the " BANNER " <sup><small><small>TM</small></small></sup></h2>\n"
		"Version: IJ/" VERSION "\n"
		"<p>\n"
	);

	proxy_args->header = strsav(proxy_args->header,
		"<h2>The program was invoked as follows</h2>\n");

	for(i=0; i < argc; i++) {
		proxy_args->header = strsav(proxy_args->header, argv[i]);
		proxy_args->header = strsav(proxy_args->header, " ");
	}
	proxy_args->header = strsav(proxy_args->header, "<br>\n");

	proxy_args->gateways = strsav(proxy_args->gateways,
		"<h2>It supports the following gateway protocols:</h2>\n");

	for(g = gateways; g->name; g++) {
		proxy_args->gateways = strsav(proxy_args->gateways, g->name);
		proxy_args->gateways = strsav(proxy_args->gateways, " ");
	}
	proxy_args->gateways = strsav(proxy_args->gateways, "<br>\n");
}

void
end_proxy_args()
{
	char buf[BUFSIZ];
	char *b = NULL;

	extern char	*acl_rcs, *bind_rcs, *conn_rcs, *encode_rcs,
			*jcc_rcs, *loaders_rcs, *parsers_rcs, *filters_rcs,
			*socks4_rcs, *ssplit_rcs, *gnu_regex_rcs, *win32_rcs;

	b = strsav(b, "<h2>Source versions:</h2>\n");
	b = strsav(b, "<pre>");
	sprintf(buf, "%s\n", jcc_rcs       );	b = strsav(b, buf);
	sprintf(buf, "%s\n", parsers_rcs   );	b = strsav(b, buf);
	sprintf(buf, "%s\n", filters_rcs   );	b = strsav(b, buf);
	sprintf(buf, "%s\n", loaders_rcs   );	b = strsav(b, buf);
	sprintf(buf, "%s\n", conn_rcs      );	b = strsav(b, buf);
	sprintf(buf, "%s\n", bind_rcs      );	b = strsav(b, buf);
	sprintf(buf, "%s\n", encode_rcs    );	b = strsav(b, buf);
	sprintf(buf, "%s\n", socks4_rcs    );	b = strsav(b, buf);
	sprintf(buf, "%s\n", ssplit_rcs    );	b = strsav(b, buf);
	sprintf(buf, "%s\n", acl_rcs       );	b = strsav(b, buf);
	sprintf(buf, "%s\n", gnu_regex_rcs );	b = strsav(b, buf);
	//sprintf(buf, "%s\n", win32_rcs     );	b = strsav(b, buf);
	b = strsav(b, "</pre>");

#ifdef REGEX
	b = strsav(b, "<p>This " BANNER " supports POSIX regular expressions in the path specs.\n");
#endif


	b = strsav(b,
		"<small><small><p>\n"
		"Code and documentation of the " BANNER " Proxy"
		"<sup><small>TM</small></sup>\n"
		"<a href=\""  JUNKBUSTERS "/ht/en/legal.html#copy\">\n" "Copyright</a>&#169; 1997 Junkbusters Corporation\n"
		"<a href=\"" JUNKBUSTERS "/ht/en/legal.html#marks\"><sup><small>TM</small></sup></a><br>\n"
		"Copying and distribution permitted under the"
		"<a href=\""  JUNKBUSTERS "/ht/en/gpl.html\">\n"
		"<small>GNU</small></a> "
		"General Public License.\n"
		"</small>"
		"<address><kbd>webmaster@junkbusters.com</kbd></address>"
		"</small>"
		"</body></html>\n"
	);

	proxy_args->trailer = b;
}

void
add_loader(int (*loader)())
{
	int i;

	for(i=0; i < NLOADERS; i++) {
		if(loaders[i] == NULL) {
			loaders[i] = loader;
			break;
		}
	}
}

int
run_loader(struct client_state *csp)
{
	int ret = 0;
	int i;

	for(i=0; i < NLOADERS; i++) {
		if(loaders[i] == NULL) break;
		ret |= (loaders[i])(csp);
	}
	return(ret);
}

/* the way calloc() ought to be -acjc */
void *
zalloc(int size)
{
	void *ret;

	if((ret = malloc(size))) {
		memset(ret, '\0', size);
	}
	return(ret);
}

