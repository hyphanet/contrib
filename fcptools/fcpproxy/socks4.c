char *socks4_rcs = "$Id: socks4.c,v 1.1 2001/09/29 01:30:37 heretic108 Exp $";
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
#include <errno.h>

#ifdef _WIN32
#include <io.h>
#include <windows.h>
#else
#include <unistd.h>
#include <netinet/in.h>
#endif

#ifdef REGEX
#include <gnu_regex.h>
#endif

#include "fcpproxy.h"

#define SOCKS_REQUEST_GRANTED		90
#define SOCKS_REQUEST_REJECT		91
#define SOCKS_REQUEST_IDENT_FAILED	92
#define SOCKS_REQUEST_IDENT_CONFLICT	93

/* structure of a socks client operation */
struct socks_op {
	unsigned char vn;		/* socks version number */
	unsigned char cd;		/* command code		*/
	unsigned char dstport[2];	/* destination port	*/
	unsigned char dstip[4];		/* destination address	*/
	unsigned char userid;		/* first byte of userid	*/
	/* more bytes of the userid follow, terminated by a NULL	*/
};

/* structure of a socks server reply */
struct socks_reply {
	unsigned char vn;		/* socks version number */
	unsigned char cd;		/* command code		*/
	unsigned char dstport[2];	/* destination port	*/
	unsigned char dstip[4];		/* destination address	*/
};

static char socks_userid[] = "anonymous";

int
socks4_connect(struct gateway *gw, struct http_request *http, struct client_state *csp)
{
	unsigned char cbuf[BUFSIZ];
	unsigned char sbuf[BUFSIZ];
	struct socks_op    *c = (struct socks_op    *)cbuf;
	struct socks_reply *s = (struct socks_reply *)sbuf;
	int web_server_addr;
	int n, csiz, sfd, target_port;
	int err = 0;
	char *errstr, *target_host;

	if((gw->gateway_host == NULL) || (*gw->gateway_host == '\0')) {
		if(DEBUG(CON)) fprintf(logfp,
			" socks4_connect: NULL gateway host specified\n");
		err = 1;
	}

	if(gw->gateway_port <= 0) {
		if(DEBUG(CON)) fprintf(logfp,
			" socks4_connect: invalid gateway port specified\n");
		err = 1;
	}

	if(err) {
		errno = EINVAL;
		return(-1);
	}

	if(gw->forward_host) {
		target_host = gw->forward_host;
		target_port = gw->forward_port;
	} else {
		target_host = http->host;
		target_port = http->port;
	}

	/* build a socks request for connection to the web server */

	strcpy((char *)&(c->userid), socks_userid);

	csiz = sizeof(*c) + sizeof(socks_userid) - 1;

	switch(gw->type) {
	case SOCKS_4:
		web_server_addr = htonl(atoip(target_host));
		break;
	case SOCKS_4A:
		web_server_addr = 0x00000001;
		n = csiz + strlen(target_host) + 1;
		if(n > sizeof(cbuf)) {
			errno = EINVAL;
			return(-1);
		}
		strcpy(((char *)cbuf) + csiz, http->host);
		csiz = n;
		break;
	}

	c->vn         = 4;
	c->cd         = 1;
	c->dstport[0] = (target_port      >> 8) & 0xff;
	c->dstport[1] = (target_port          ) & 0xff;
	c->dstip[0]   = (web_server_addr >> 24) & 0xff;
	c->dstip[1]   = (web_server_addr >> 16) & 0xff;
	c->dstip[2]   = (web_server_addr >>  8) & 0xff;
	c->dstip[3]   = (web_server_addr      ) & 0xff;

	/* pass the request to the socks server */
	sfd = connect_to(gw->gateway_host, gw->gateway_port, csp);

	if(sfd < 0) {
		return(-1);
	}

	if((n = write_socket(sfd, c, csiz)) != csiz) {
		if(DEBUG(CON)) {
			fprintf(logfp, "SOCKS4 negotiation write failed...");
		}
		close_socket(sfd);
		return(-1);
	}

	if((n = read_socket(sfd, sbuf, sizeof(sbuf))) != sizeof(*s)) {
		if(DEBUG(CON)) {
			fprintf(logfp, "SOCKS4 negotiation read failed...");
		}
		close_socket(sfd);
		return(-1);
	}

	switch(s->cd) {
	case SOCKS_REQUEST_GRANTED:
		return(sfd);
		break;
	case SOCKS_REQUEST_REJECT:
		errstr = "SOCKS request rejected or failed";
		errno = EINVAL;
		break;
	case SOCKS_REQUEST_IDENT_FAILED:
		errstr = "SOCKS request rejected because "
			 "SOCKS server cannot connect to identd on the client";
		errno = EACCES;
		break;
	case SOCKS_REQUEST_IDENT_CONFLICT:
		errstr = "SOCKS request rejected because "
			 "the client program and identd report "
			 "different user-ids";
		errno = EACCES;
		break;
	default:
		errstr = (char *) cbuf;
		errno = ENOENT;
		sprintf(errstr,
			"SOCKS request rejected for reason code %d\n", s->cd);
	}

	if(DEBUG(CON)) {
		fprintf(logfp, " socks4_connect: %s ...", errstr);
	}

	close_socket(sfd);
	return(-1);
}
