char *acl_rcs = "$Id: acl.c,v 1.1 2001/09/29 01:30:53 heretic108 Exp $";
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
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#ifdef _WIN32
#include "windows.h"
#else
#include <netinet/in.h>
#endif

#ifdef REGEX
#include "gnu_regex.h"
#endif

#include "fcpproxy.h"

static struct file_list *current_aclfile;

int
block_acl(struct access_control_addr *src, struct access_control_addr *dst, struct client_state *csp)
{
	struct file_list *fl;
	struct access_control_list *a, *acl;
	struct access_control_addr s[1], d[1];

	/* if not using an access control list, then permit the connection */
	if(((fl = csp->alist) == NULL) || ((acl = fl->f) == NULL)) {
		return(0);
	}

	/* search the list */
	for(a = acl->next ; a ; a = a->next) {

		*s = *src;
		*d = *dst;

		s->addr &= a->src->mask;
		d->addr &= a->dst->mask;

		if((s->addr  == a->src->addr)
		&& (d->addr  == a->dst->addr)
		&& ((s->port == a->src->port)
		 || (s->port == 0)
		 || (a->src->port == 0))
		&& ((d->port == a->dst->port)
		 || (d->port == 0)
		 || (a->dst->port == 0))) {
			if(a->action == ACL_PERMIT) {
				return(0);
			} else {
				return(1);
			}
		}
	}

	return(1);
}

void
unload_aclfile(struct access_control_list *b)
{
	if(b == NULL) return;

	unload_aclfile(b->next);

	freez(b);
}

int
acl_addr(char *aspec, struct access_control_addr *aca)
{
	int i, masklength, port;
	char *p;

	masklength = 32;
	port       =  0;

	if((p = strchr(aspec, '/'))) {
		*p++ = '\0';

		if(isdigit(*p) == 0) {
			return(-1);
		}
		masklength = atoi(p);
	}

	if((masklength < 0)
	|| (masklength > 32)) {
		return(-1);
	}

	if((p = strchr(aspec, ':'))) {
		*p++ = '\0';

		if(isdigit(*p) == 0) {
			return(-1);
		}
		port = atoi(p);
	}

	aca->port = port;

	aca->addr = ntohl(atoip(aspec));

	if(aca->addr == -1) {
		fprintf(logfp,
			"%s: can't resolve address for %s\n",
				prog, aspec);
		return(-1);
	}

	/* build the netmask */
	aca->mask = 0;
	for(i=1; i <= masklength ; i++) {
		aca->mask |= (1 << (32 - i));
	}

	/* now mask off the host portion of the ip address
	 * (i.e. save on the network portion of the address).
	 */
	aca->addr = aca->addr & aca->mask;

	return(0);
}

int
load_aclfile(struct client_state *csp)
{
	FILE *fp;
	char buf[BUFSIZ], *v[3], *p;
	int i;
	struct access_control_list *a, *bl;
	struct file_list *fs;
	static struct stat prev[1], curr[1];

	if(stat(aclfile, curr) < 0) {
		goto load_aclfile_error;
	}

	if(current_aclfile && (prev->st_mtime == curr->st_mtime)) {
		csp->alist = current_aclfile;
		return(0);
	}

	fs = (struct file_list           *) zalloc(sizeof(*fs));
	bl = (struct access_control_list *) zalloc(sizeof(*bl));

	if((fs == NULL) || (bl == NULL)) {
		goto load_aclfile_error;
	}

	fs->f = bl;

	fs->next = files->next;
	files->next = fs;

	if(csp) {
		csp->alist = fs;
	}

	fp = fopen(aclfile, "r");

	if(fp == NULL) {
		fprintf(logfp, "%s: can't open access control list %s\n",
			prog, aclfile);
		fperror(logfp, "");
		goto load_aclfile_error;
	}

	while(fgets(buf, sizeof(buf), fp)) {

		if((p = strpbrk(buf, "#\r\n"))) *p = '\0';

		if(*buf == '\0') continue;

		i = ssplit(buf, " \t", v, SZ(v), 1, 1);

		/* allocate a new node */
		a = (struct access_control_list *) zalloc(sizeof(*a));

		if(a == NULL) {
			fclose(fp);
			goto load_aclfile_error;
		}

		/* add it to the list */
		a->next  = bl->next;
		bl->next = a;

		switch(i) {
		case 3:
			if(acl_addr(v[2], a->dst) < 0) {
				goto load_aclfile_error;
			}
			/* no break */
		case 2:
			if(acl_addr(v[1], a->src) < 0) {
				goto load_aclfile_error;
			}

			p = v[0];
			if(strcmpic(p, "permit") == 0) {
				a->action = ACL_PERMIT;
				break;
			}

			if(strcmpic(p, "deny"  ) == 0) {
				a->action = ACL_DENY;
				break;
			}
			/* no break */
		default:
			goto load_aclfile_error;	
		}
	}

	*prev = *curr;

	fclose(fp);

	if(current_aclfile) {
		current_aclfile->unloader = unload_aclfile;
	}

	current_aclfile = fs;

	return(0);

load_aclfile_error:
	fprintf(logfp,
		"%s: can't load access control list '%s': ",
			prog, aclfile);
	fperror(logfp, "");
	return(-1);
}
