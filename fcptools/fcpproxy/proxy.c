char *jcc_rcs = "$Id: proxy.c,v 1.1 2001/09/29 01:30:38 heretic108 Exp $";
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
#include <signal.h>
#include <fcntl.h>
#include <errno.h>

#ifdef _WIN32

#include <sys/timeb.h>
#include <windows.h>
#include <io.h>
#include <process.h>

#else

#include <unistd.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <sys/stat.h>

#ifdef __BEOS__
#include <socket.h>	/* BeOS has select() for sockets only. */
#include <OS.h>		/* declarations for threads and stuff. */
#endif

#ifndef FD_ZERO
#include <select.h>
#endif

#endif

#ifdef REGEX
#include <gnu_regex.h>
#endif

#include "ezFCPlib.h"

#include "fcpproxy.h"

//
// IMPORTED DECLARATIONS
//

extern int fcpreq(char *host, int port, char *docpath, char *range, int sock);

extern char progdir[];


//
// GLOBAL DECLARATIONS
//

char *prog;

#define BODY	"<body bgcolor=\"#f8f8f0\" link=\"#000078\" alink=\"#ff0022\" vlink=\"#787878\">\n"

char CFAIL[]   = "HTTP/1.0 503 Connect failed\n"
		 "Content-Type: text/html\n\n"
		 "<html>\n"
		 "<head>\n"
		 "<title>Internet Junkbuster: Connect failed</title>\n"
		 "</head>\n"
		 BODY
		 "<h1><center>"
		 BANNER
		 "</center></h1>"
		 "TCP connection to '%s' failed: %s.\n<br>"
		 "</body>\n"
		 "</html>\n"
		 ;

char CNXDOM[]  = "HTTP/1.0 404 Non-existent domain\n"
		 "Content-Type: text/html\n\n"
		 "<html>\n"
		 "<head>\n"
		 "<title>Internet Junkbuster: Non-existent domain</title>\n"
		 "</head>\n"
		 BODY
		 "<h1><center>"
		 BANNER
		 "</center></h1>"
		 "No such domain: %s\n"
		 "</body>\n"
		 "</html>\n"
		 ;
char CSUCCEED[] = "HTTP/1.0 200 Connection established\n"
		  "Proxy-Agent: IJ/" VERSION "\n\n"
		  ;

char CHEADER[] = "HTTP/1.0 400 Invalid header received from browser\n\n";

char SHEADER[] = "HTTP/1.0 502 Invalid header received from server\n\n";

char VANILLA_WAFER[] =
	"NOTICE=TO_WHOM_IT_MAY_CONCERN_"
	"Do_not_send_me_any_copyrighted_information_other_than_the_"
	"document_that_I_am_requesting_or_any_of_its_necessary_components._"
	"In_particular_do_not_send_me_any_cookies_that_"
	"are_subject_to_a_claim_of_copyright_by_anybody._"
	"Take_notice_that_I_refuse_to_be_bound_by_any_license_condition_"
	"(copyright_or_otherwise)_applying_to_any_cookie._";

char DEFAULT_USER_AGENT[] ="User-Agent: Mozilla/3.01Gold (Macintosh; I; 68K)";

int debug           = 0;
int multi_threaded  = 1;
int hideConsole     = 0;

#ifdef _WIN32
#define sleep(N)	Sleep(((N) * 1000))
#endif

char *logfile = NULL;
FILE *logfp;

char *blockfile    = NULL;
char *cookiefile   = NULL;
char *trustfile    = NULL;
char *forwardfile  = NULL;
char *aclfile      = NULL;

char *jarfile = NULL;
FILE *jar;

char *referrer   = NULL;
char *uagent     = NULL;
char *from       = NULL;
char *range      = NULL;

int suppress_vanilla_wafer = 0;
int add_forwarded      = 0;

struct client_state clients[1];
struct file_list    files[1];

struct list wafer_list[1];
struct list xtra_list[1];
struct list trust_info[1];

struct url_spec * trust_list[64];


int (*loaders[NLOADERS])();

struct gateway gateways[] = {
/* type         function        gw type/host/port,    fw host/port*/
{ "direct",	direct_connect, 0,        NULL, 0,     NULL, 0    },
{ ".",		direct_connect, 0,        NULL, 0,     NULL, 0    },
{ "socks",	socks4_connect, SOCKS_4,  NULL, 1080,  NULL, 0    },
{ "socks4",	socks4_connect, SOCKS_4,  NULL, 1080,  NULL, 0    },
{ "socks4a",	socks4_connect, SOCKS_4A, NULL, 1080,  NULL, 0    },
{ NULL,		NULL,           0,        NULL, 0,     NULL, 0    }
};

struct gateway *gw_default = gateways;

// char *haddr = "127.0.0.1";	/* default binding to localhost */
char *haddr = NULL;

int   hport = 8888;

struct proxy_args proxy_args[1];

int
write_socket(int fd, char *buf, int n)
{
	if(n <= 0) return(0);

	if(DEBUG(LOG)) fwrite(buf, n, 1, logfp);

#if defined(_WIN32) || defined(__BEOS__)
	return send(fd, buf, n, 0);
#else
	return write(fd, buf, n);
#endif
}

int
read_socket(int fd, char *buf, int n)
{
	if(n <= 0) return(0);
#if defined(_WIN32) || defined(__BEOS__)
	return recv(fd, buf, n, 0);
#else
	return read(fd, buf, n);
#endif
}

void
close_socket(int fd)
{
#if defined(_WIN32) || defined(__BEOS__)
	closesocket(fd);
#else
	close(fd);
#endif
}

void
chat(struct client_state *csp)
{
	char buf[BUFSIZ], *hdr, *p, *req;
	char *err = NULL;
	char *eno;
	fd_set rfds;
	int n, maxfd, server_body;
	struct cookie_spec *cs;
	struct gateway *gw;
	struct http_request *http;

	http = csp->http;

	/* read the client's request.
	 * note that since we're not using select()
	 * we could get blocked here if a client
	 * connected, then didn't say anything!
	 */

	for(;;) {
		n = read_socket(csp->cfd, buf, sizeof(buf));

		if(n <= 0) break;		/* error! */

		add_to_iob(csp, buf, n);

		req = get_header(csp);

		if(req == NULL) break;		/* no HTTP request! */

		if(*req == '\0') continue;	/* more to come! */

		parse_http_request(req, http, csp);
		freez(req);
		break;
	}

	if(http->cmd == NULL) {
		strcpy(buf, CHEADER);
		write_socket(csp->cfd, buf, strlen(buf));
		return;
	}

	/* decide how to route the HTTP request */

	if((gw = forward_url(http, csp)) == NULL) {

		fprintf(logfp,
			"%s: gateway spec is NULL!?!?  This can't happen!\n", prog);
		abort();
	}

	/* build the http request to send to the server
	 * we have to do one of the following:
	 *
	 * create = use the original HTTP request to create a new
	 *          HTTP request that has only the path component
	 *          without the http://domainspec
	 * pass   = pass the original HTTP request unchanged
	 *
	 * drop   = drop the HTTP request
	 *
	 * here's the matrix:
	 *                        SSL
	 *                    0        1
         *                +--------+--------+
         *                |        |        |
         *             0  | create | drop   |
         *                |        |        |
         *  Forwarding    +--------+--------+
         *                |        |        |
         *             1  | pass   | pass   |
         *                |        |        |
         *                +--------+--------+
         *
         */

	if(gw->forward_host) {
			/* if forwarding, just pass the request as is */
			enlist(csp->headers, http->cmd);
	} else {
		if(http->ssl == 0) {
			/* otherwise elide the host information from the url */
			p = NULL;
			p = strsav(p, http->gpc);
			p = strsav(p, " ");
			p = strsav(p, http->path);
			p = strsav(p, " ");
			p = strsav(p, http->ver);
			enlist(csp->headers, p);
			freez(p);
		}
	}

	/* decide what we're to do with cookies */

	if((cs = cookie_url(http, csp))) {
		csp->accept_server_cookie  = cs->accept_server_cookie;
		csp->send_user_cookie      = cs->send_user_cookie;
	} else {
		csp->accept_server_cookie  = 0;
		csp->send_user_cookie      = 0;
	}

	/* grab the rest of the client's headers */

	for(;;) {
		if(( p = get_header(csp))
		&& (*p == '\0')) {
			n = read_socket(csp->cfd, buf, sizeof(buf));
			if(n <= 0) {
				fprintf(logfp,
					"%s: read from client failed: ", prog);
				fperror(logfp, "");
				return;
			}
			add_to_iob(csp, buf, n);
			continue;
		}

		if(p == NULL) break;

		enlist(csp->headers, p);
		freez(p);
	}

	/* filter it as required */

	hdr = sed(client_patterns, add_client_headers, csp);

	destroy_list(csp->headers);

	// pass request to freenet interface
	if (fcpreq(http->host,
				http->port,
				http->path[0] == '/' ? http->path+1 : http->path,
				hdr,
				csp->cfd) != 0)
	{
		// request was handled by freenet interface - bail out
		freez(hdr);
		return;
	}

	// test for URLs to be intercepted or blocked
	if((p = intercept_url(http, csp))
	|| (p =     block_url(http, csp))
	|| (p =     trust_url(http, csp))) {
		if(DEBUG(GPC)) {
			fprintf(logfp, "%s: GPC\t%s%s crunch!\n",
				prog, http->hostport, http->path);
		}

		write_socket(csp->cfd, p, strlen(p));

		if(DEBUG(LOG)) fwrite(p, strlen(p), 1, logfp);

		freez(p);
		freez(hdr);
		return;
	}

	if(DEBUG(GPC)) {
		fprintf(logfp, "%s: GPC\t%s%s\n",
			prog, http->hostport, http->path);
	}

	if(DEBUG(CON)) {
		if(gw->forward_host) {
			fprintf(logfp,
				"%s: connect via %s:%d to: %s ... ",
					prog,
					gw->forward_host,
					gw->forward_port,
					http->hostport);
		} else {
			fprintf(logfp,
				"%s: connect to: %s ... ",
					prog, http->hostport);
		}
	}

	/* here we connect to the server, gateway, or the forwarder */

	csp->sfd = (gw->conn)(gw, http, csp);

	if(csp->sfd < 0) {
		if(DEBUG(CON)) {
			fprintf(logfp, "%s: connect to: %s failed: ",
					prog, http->hostport);
			fperror(logfp, "");
		}

		if(errno == EINVAL) {
			err = zalloc(strlen(CNXDOM) + strlen(http->host));
			sprintf(err, CNXDOM, http->host);
		} else {
			eno = safe_strerror(errno);
			err = zalloc(strlen(CFAIL) + strlen(http->hostport) + strlen(eno));
			sprintf(err, CFAIL, http->hostport, eno);
		}

		write_socket(csp->cfd, err, strlen(err));

		if(DEBUG(LOG)) fwrite(err, strlen(err), 1, logfp);

		freez(err);
		freez(hdr);
		return;
	}

	if(DEBUG(CON)) {
		fprintf(logfp, "OK\n");
	}

	if(gw->forward_host || (http->ssl == 0)) {
		/* write the client's (modified) header to the server
		 * (along with anything else that may be in the buffer)
		 */

		n = strlen(hdr);

		if((write_socket(csp->sfd, hdr, n) != n)
		|| (flush_socket(csp->sfd, csp   ) <  0)) {
			if(DEBUG(CON)) {
				fprintf(logfp, "%s: write header to: %s failed: ",
					prog, http->hostport);
				fperror(logfp, "");
			}

			eno = safe_strerror(errno);
			err = zalloc(strlen(CFAIL) + strlen(http->hostport) + strlen(eno));
			sprintf(err, CFAIL, http->hostport, eno);
			write_socket(csp->cfd, err, strlen(err));

			freez(err);
			freez(hdr);
			return;
		}
	} else {
		/* we're running an SSL tunnel and we're not
		 * forwarding, so just send the "connect succeeded"
		 * message to the client, flush the rest, and
		 * get out of the way.
		 */

		if(write_socket(csp->cfd, CSUCCEED, sizeof(CSUCCEED)-1) < 0) {
			freez(hdr);
			return;
		}
		IOB_RESET(csp);
	}

	/* we're finished with the client's header */
	freez(hdr);

	maxfd = ( csp->cfd > csp->sfd ) ? csp->cfd : csp->sfd;

	/* pass data between the client and server
	 * until one or the other shuts down the connection.
	 */

	server_body = 0;

	for(;;) {
		FD_ZERO(&rfds);

		FD_SET(csp->cfd, &rfds);
		FD_SET(csp->sfd, &rfds);

		n = select(maxfd+1, &rfds, NULL, NULL, NULL);

		if(n < 0) {
			fprintf(logfp, "%s: select() failed!: ", prog);
			fperror(logfp, "");
			return;
		}

		/* this is the body of the browser's request
		 * just read it and write it.
		 */

		if(FD_ISSET(csp->cfd, &rfds)) {

			n = read_socket(csp->cfd, buf, sizeof(buf));

			if(n <= 0) break; /* "game over, man" */

			if(write_socket(csp->sfd, buf, n) != n) {
				fprintf(logfp, "%s: write to: %s failed: ",
						prog, http->host);
				fperror(logfp, "");
				return;
			}
			continue;
		}

		/* the server wants to talk.
		 * it could be the header or the body.
		 * if `hdr' is null, then it's the header
		 * otherwise it's the body
		 */

		if(FD_ISSET(csp->sfd, &rfds)) {

			n = read_socket(csp->sfd, buf, sizeof(buf));

			if(n < 0) {
				fprintf(logfp, "%s: read from: %s failed: ",
						prog, http->host);
				fperror(logfp, "");

				eno = safe_strerror(errno);
				sprintf(buf, CFAIL, http->hostport, eno);
				freez(eno);
				write_socket(csp->cfd, buf, strlen(buf));
				return;
			}

			if(n == 0) break; /* "game over, man" */

			/* if this is an SSL connection or we're in the body
			 * of the server document, just write it to the client.
			 */

			if(server_body || http->ssl) {
				/* just write */
				if(write_socket(csp->cfd, buf, n) != n) {
					fprintf(logfp, "%s: write to client failed: ",
							prog);
					fperror(logfp, "");
					return;
				}
				continue;
			} else {
				/* we're still looking for the end of the
				 * server's header ... (does that make header
				 * parsing an "out of body experience" ?
				 */

				/* buffer up the data we just read */
				add_to_iob(csp, buf, n);

				/* get header lines from the iob */
				while((p = get_header(csp))) {
					if(*p == '\0') {
						/* see following note */
						break;
					}
					enlist(csp->headers, p);
					freez(p);
				}

				/* NOTE: there are no "empty" headers so
				 * if the pointer `p' is not NULL we must
				 * assume that we reached the end of the
				 * buffer before we hit the end of the header.
				 *
				 * Since we have to wait for more from the
				 * server before we can parse the headers
				 * we just continue here.
				 */

				if(p) continue;

				/* we have now received the entire header.
				 * filter it and send the result to the client
				 */

				hdr = sed(
					server_patterns,
					add_server_headers,
					csp);

				n   = strlen(hdr);

				/* write the server's (modified) header to
				 * the client (along with anything else that
				 * may be in the buffer)
				 */

				if((write_socket(csp->cfd, hdr, n) != n)
				|| (flush_socket(csp->cfd, csp   ) <  0)) {
					if(DEBUG(CON)) {
						fprintf(logfp,
							"%s: write header to client failed: ",
								prog);
						fperror(logfp, "");
					}
					/* the write failed, so don't bother
					 * mentioning it to the client...
					 * it probably can't hear us anyway.
					 */
					freez(hdr);
					return;
				}

				/* we're finished with the server's header */

				freez(hdr);
				server_body = 1;
			}
			continue;
		}

		return; /* huh? we should never get here */
	}
}

void
serve(struct client_state *csp)
{
	chat(csp);
	close_socket(csp->cfd);

	if(csp->sfd >= 0) {
		close_socket(csp->sfd);
	}

	csp->active = 0;
}

#ifdef __BEOS__
int32
server_thread(void *data)
{
	serve((struct client_state *) data);
	return 0;
}
#endif

void proxy(int port, int extproxyenabled, char *extproxyaddr, int extproxyport)
{
	char buf[BUFSIZ];
	int cfd, bfd;
	char *p, *q;
	extern char *optarg;
	extern int optind;
	struct client_state *csp;

	char *default_configfile = NULL;
	FILE *configfp = NULL;

	char configfile[256];
	char *homedir;
	int err = 0;

	prog = "fcpproxy";

	logfp = stdout;

	/*init_proxy_args(argc, argv);*/

	hport = port;

	cfd  = -1;

#ifdef WINDOWS
	sprintf(configfile, "%s\\.fcpproxyrc", progdir);
#else
	homedir = getenv("HOME");
	sprintf(configfile, "%s/.fcpproxyrc", homedir);
#endif

	_fcpLog(FCP_LOG_VERBOSE, "Sourcing config from file '%s'", configfile);

	if((configfp = fopen(configfile, "r")) == NULL)
	{
		if(configfile != default_configfile)
		{
			fprintf(logfp,
					"%s: can't open configuration file '%s': ",
						prog, configfile);
			fperror(logfp, "");
#ifdef WINDOWS
			fprintf(logfp, "To fix this, copy .fcpproxyrc to the fcpproxy directory\n");
#else
			fprintf(logfp, "To fix this, copy .fcpproxyrc to your home directory\n");
#endif
			exit(1);
		}
	}

	if(configfp) {
		int line_num = 0;
		while(fgets(buf, sizeof(buf), configfp)) {
			char cmd[BUFSIZ];
			char arg[BUFSIZ];
			char tmp[BUFSIZ];

			line_num++;

			strcpy(tmp, buf);

			if((p = strpbrk(tmp, "#\r\n"))) *p = '\0';

			p = tmp;

			/* leading skip whitespace */
			while(*p && ((*p == ' ') || (*p == '\t'))) p++;

			q = cmd;

			while(*p && (*p != ' ') && (*p != '\t')) *q++ = *p++;

			*q = '\0';

			while(*p && ((*p == ' ') || (*p == '\t'))) p++;

			strcpy(arg, p);

			p = arg + strlen(arg) - 1;

			/* ignore trailing whitespace */
			while(*p && ((*p == ' ') || (*p == '\t'))) *p-- = '\0';

			if(*cmd == '\0') continue;

			/* insure the command field is lower case */
			for(p=cmd; *p; p++) if(isupper(*p)) *p = tolower(*p);

			savearg(cmd, arg);

			if(strcmp(cmd, "trustfile") == 0) {
				trustfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "trust_info_url") == 0) {
				enlist(trust_info, arg);
				continue;
			}

			if(strcmp(cmd, "debug") == 0) {
				debug |= atoi(arg);
				continue;
			}

			if(strcmp(cmd, "add-forwarded-header") == 0) {
				add_forwarded = 1;
				continue;
			}

			if(strcmp(cmd, "single-threaded") == 0) {
				multi_threaded = 0;
				continue;
			}

			if(strcmp(cmd, "suppress-vanilla-wafer") == 0) {
				suppress_vanilla_wafer = 1;
				continue;
			}

			if(strcmp(cmd, "wafer") == 0) {
				enlist(wafer_list, arg);
				continue;
			}

			if(strcmp(cmd, "add-header") == 0) {
				enlist(xtra_list,  arg);
				continue;
			}

			if(strcmp(cmd, "cookiefile") == 0) {
				cookiefile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "logfile") == 0) {
				logfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "blockfile") == 0) {
				blockfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "jarfile") == 0) {
				jarfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "listen-address") == 0) {
				haddr = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "forwardfile") == 0) {
				forwardfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "aclfile") == 0) {
				aclfile = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "user-agent") == 0) {
				uagent = strdup(arg);
				continue;
			}

			if((strcmp(cmd, "referrer") == 0)
			|| (strcmp(cmd, "referer" ) == 0)) {
				referrer = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "from") == 0) {
				from = strdup(arg);
				continue;
			}

			if(strcmp(cmd, "hide-console") == 0) {
				hideConsole = 1;
				continue;
			}

			fprintf(logfp,
				"%s: unrecognized directive "
				"in configuration file "
				"at line number %d:\n%s",
				prog, line_num, buf);
			err = 1;
		}
		fclose(configfp);
	}

	if(err) exit(1);

#ifdef _WIN32
	InitWin32();
#endif

	if(logfile) {
		FILE *tlog = fopen(logfile, "a");
		if(tlog == NULL) {
			fprintf(logfp, "%s: can't open logfile '%s': ",
				prog, logfile);
			fperror(logfp, "");
			err = 1;
		}
		logfp = tlog;
	}

	setbuf(logfp, NULL);

	if(cookiefile)   add_loader(load_cookiefile);

	if(blockfile)    add_loader(load_blockfile);

	if(trustfile)    add_loader(load_trustfile);

	if(forwardfile)  add_loader(load_forwardfile);

	if(aclfile)      add_loader(load_aclfile);

	if(jarfile) {
		jar = fopen(jarfile, "a");
		if(jar == NULL) {
			fprintf(logfp, "%s: can't open jarfile '%s': ",
				prog, jarfile);
			fperror(logfp, "");
			err = 1;
		}
		setbuf(jar, NULL);
	}

	if(haddr) {
		if((p = strchr(haddr, ':'))) {
			*p++ = '\0';
			if(*p) hport = atoi(p);
		}

		if(hport <= 0) {
			*--p = ':' ;
			fprintf(logfp, "%s: invalid bind port spec %s",
				prog, haddr);
			err = 1;
		}
		if(*haddr == '\0') haddr = NULL;
	}

	if(run_loader(NULL)) err = 1;

	if(err) exit(1);

	/* if we're logging cookies in a cookie jar,
	 * and the user has not supplied any wafers,
	 * and the user has not told us to suppress the vanilla wafer,
	 * then send the vanilla wafer.
	 */
	if((jarfile != NULL)
	&& (wafer_list->next == NULL)
	&& (suppress_vanilla_wafer == 0)) {
		enlist(wafer_list, VANILLA_WAFER);
	}

	if(DEBUG(CON)) {
		fprintf(logfp, "%s: bind (%s, %d)\n",
			prog, haddr ? haddr : "INADDR_ANY", hport);
	}

	if (port > 0)
		hport = port;
	bfd = bind_port(haddr, hport);

	if(bfd < 0) {
		fprintf(logfp, "%s: can't bind %s:%d: ",
			prog, haddr ? haddr : "INADDR_ANY", hport);
		fperror(logfp, "");
		fprintf(logfp,
			"There may be another junkbuster or some other "
			"proxy running on port %d\n", hport);
		err = 1;
	}

	if(err) exit(1);

	end_proxy_args();

#ifndef _WIN32
	signal(SIGPIPE, SIG_IGN);
	signal(SIGCHLD, SIG_IGN);
#endif

#ifdef _WIN32
{
	/* print a verbose messages about FAQ's and such */
	extern char *win32_blurb;
	if(logfp == stdout) fprintf(logfp, win32_blurb);
}
#endif

	for(;;) {

#if !defined(_WIN32) && !defined(__BEOS__)
		while(waitpid(-1, NULL, WNOHANG) > 0) {
			/* zombie children */
		}
#endif
		sweep();

		if(DEBUG(CON)) {
			fprintf(logfp, "%s: accept connection ... ", prog);
		}

		cfd = accept_connection(bfd);

		if(cfd < 0) {
			if(DEBUG(CON)) {
				fprintf(logfp, "%s: accept failed: ", prog);
				fperror(logfp, "");
			}
			continue;
		} else {
			if(DEBUG(CON)) {
				fprintf(logfp, "OK\n");
			}
		}

		csp = (struct client_state *) malloc(sizeof(*csp));

		if(csp == NULL) {
			fprintf(logfp, "%s: malloc(%d) for csp failed: ", prog, sizeof(*csp));
			fperror(logfp, "");
			close_socket(cfd);
			continue;
		}

		memset(csp, '\0', sizeof(*csp));

		csp->active = 1;
		csp->cfd    = cfd;
		csp->sfd    =  -1;
		csp->ip_addr_str  = remote_ip_str;
		csp->ip_addr_long = remote_ip_long;

		/* add it to the list of clients */
		csp->next = clients->next;
		clients->next = csp;

		if(run_loader(csp)) {
			fprintf(logfp, "%s: a loader failed - must exit\n", prog);
			exit(1);
		}

		if(multi_threaded) {
			int child_id;

/* this is a switch() statment in the C preprocessor - ugh */
#undef SELECTED_ONE_OPTION

#if defined(_WIN32) && !defined(SELECTED_ONE_OPTION)
#define SELECTED_ONE_OPTION
			child_id = _beginthread(
					(void*)serve,
					64 * 1024,
					csp);
#endif

#if defined(__BEOS__) && !defined(SELECTED_ONE_OPTION)
#define SELECTED_ONE_OPTION
			{
				thread_id tid = spawn_thread
					(server_thread, "server", B_NORMAL_PRIORITY, csp);

				if ((tid >= 0) && (resume_thread(tid) == B_OK)) {
					child_id = (int) tid;
				} else {
					child_id = -1;
				}
			}
#endif

#if !defined(SELECTED_ONE_OPTION)
			child_id = fork();
#endif

#undef SELECTED_ONE_OPTION
/* end of cpp switch() */

			if(child_id < 0) {	/* failed */
				fprintf(logfp, "%s: can't fork: ", prog);
				fperror(logfp, "");

				sprintf(buf , "%s: can't fork: errno = %d",
					prog, errno);

				write_socket(csp->cfd, buf, strlen(buf));
				close_socket(csp->cfd);
				csp->active = 0;
				sleep(5);
				continue;
			}
#if !defined(_WIN32) && !defined(__BEOS__)
			/* This block is only needed when using fork().
			 * When using threads, the server thread was
			 * created and run by the call to _beginthread().
			 */
			if(child_id == 0) {	/* child */

				serve(csp);
				_exit(0);

			} else {		/* parent */

				/* in a fork()'d environment, the parent's
				 * copy of the client socket and the CSP
				 * are not used.
				 */

				close_socket(csp->cfd);
				csp->active = 0;
			}
#endif
		} else {
			serve(csp);
		}
	}
	/* NOTREACHED */
}


char *
safe_strerror(int err)
{
	char buf[BUFSIZ];
	char *s = NULL;

#ifndef   NOSTRERROR
	s = strerror(err);
#endif /* NOSTRERROR */

	if(s == NULL) {
		sprintf(buf, "(errno = %d)", err);
		s = buf;
	}

	return(strdup(s));
}

void
fperror(FILE *fp, char *str)
{
	char *eno = safe_strerror(errno);

	if(str && *str) {
		fprintf(fp, "%s: %s\n", str, eno);
	} else {
		fprintf(fp, "%s\n", eno);
	}
	freez(eno);
}
