/* Written and copyright 1997 Anonymous Coders and Junkbusters Corporation.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY. http://www.junkbusters.com/ht/en/gpl.html
 */

#ifndef _FCPPROXY_H
#define _FCPPROXY_H 

#define GPC		0x0001
#define CON		0x0002
#define IO		0x0004
#define HDR		0x0008
#define LOG		0x0010

#define DEBUG(X)	(debug & X)
#define freez(X)	if(X) free(X); X = NULL

extern int debug;

extern FILE *logfp;

extern char *prog;

extern char *blockfile;
extern char *cookiefile;
extern char *trustfile;
extern char *forwardfile;
extern char *aclfile;

extern char *jarfile;
extern FILE *jar;

extern char *referrer;
extern char *uagent;
extern char *from;
extern char *range;

extern struct list       wafer_list[];
extern struct list        xtra_list[];
extern struct list       trust_info[];
extern struct url_spec * trust_list[];

extern int add_forwarded;

struct gateway {
	/* generic attributes */
        char *name;
        int (*conn)();
        int   type;

	/* domain specific attributes */
        char *gateway_host;
        int   gateway_port;

	char *forward_host;
	int   forward_port;
};

struct proxy_args {
	char *header;
	char *invocation;
	char *gateways;
	char *trailer;
};

struct iob {
	char *buf;
	char *cur;
	char *eod;
};

struct http_request {
	char *cmd;
	char *gpc;
	char *host;
	int   port;
	char *path;
	char *ver;
	char *hostport; /* "host[:port]" */
	int   ssl;
};

struct list {
	char *str;
	struct list *last;
	struct list *next;
};

int add_to_iob();

#define IOB_PEEK(CSP) ((CSP->iob->cur > CSP->iob->eod) ? (CSP->iob->eod - CSP->iob->cur) : 0)
#define IOB_RESET(CSP) if(CSP->iob->buf) free(CSP->iob->buf); memset(CSP->iob, '\0', sizeof(CSP->iob));

struct client_state {
	int  send_user_cookie;
	int  accept_server_cookie;
	int  cfd;
	int  sfd;
	char *ip_addr_str;
	long  ip_addr_long;
	char *referrer;

	struct gateway *gw;
	struct http_request http[1];

	struct iob iob[1];

	struct list headers[1];
	struct list cookie_list[1];
	char   *x_forwarded;

	int active;

	/* files associated with this client */
	struct file_list *alist;
	struct file_list *blist;
	struct file_list *clist;
	struct file_list *tlist;
	struct file_list *flist;

	struct client_state *next;
};

extern struct client_state clients[];

extern char *remote_ip_str;
extern long  remote_ip_long;

struct parsers {
	char *str;
	char  len;
	char *(*parser)();
};

struct interceptors {
	char *str;
	char  len;
	char *(*interceptor)();
};

/* this allows the proxy to permit/block access to any host and/or path */
struct url_spec {
	char  *spec;
	char  *domain;
	char  *dbuf;
	char **dvec;
	int    dcnt;
	int    toplevel;

	char *path;
	int   pathlen;
	int   port;
#ifdef REGEX
	regex_t *preg;
#endif
};

struct file_list {
	void *f; /* this is a pointer to the data structures
		  * associated with the file
		  */
	void (*unloader)();
	int active;
	char *proxy_args;
	struct file_list *next;
};

struct block_spec {
	struct url_spec url[1];
	int   reject;
	struct block_spec *next;
};

struct cookie_spec {
	struct url_spec url[1];
	int   send_user_cookie;
	int   accept_server_cookie;
	struct cookie_spec *next;
};

struct forward_spec {
	struct url_spec url[1];
	int   reject;
	struct gateway gw[1];
	struct forward_spec *next;
};

#define ACL_PERMIT	1	/* accept connection request */
#define ACL_DENY  	2	/* reject connection request */

struct access_control_addr {
	unsigned long	addr;
	unsigned long	mask;
	unsigned long   port;
};

struct access_control_list {
	struct access_control_addr src[1];
	struct access_control_addr dst[1];

	short	action;
	struct access_control_list *next;
};

extern struct file_list files[];
extern struct proxy_args proxy_args[];

extern int (*loaders[])();
extern int run_loader();
extern void add_loader(), unload_url(), destroy_list(), *zalloc();
extern int bind_port(), accept_connection(), atoip();
extern int strcmpic(), strncmpic();

#define NLOADERS 8
#define SZ(X)	(sizeof(X) / sizeof(*X))

extern char *url_code_map[];
extern char *html_code_map[];
extern char *cookie_code_map[];

extern void fperror(), enlist();
extern char *safe_strerror(), *strsav(), *get_header(), *sed();
extern void parse_http_request();
extern void  free_http_request();

extern int domaincmp(), ssplit();
extern struct url_spec dsplit();

extern int read_header(), connect_to(), main();
extern int read_socket(), write_socket(), getchar_socket(), flush_socket();
extern void close_socket();

extern int block_acl();

/* parsers */
extern char *crumble();
extern char *url_http(), *url_https();
extern char *client_referrer(), *client_range(), *client_uagent(), *client_ua();
extern char *client_x_forwarded(), *client_from(), *client_send_cookie();
extern char *server_set_cookie();

/* adders */
extern void client_cookie_adder(), client_xtra_adder();
extern void client_x_forwarded_adder();

/* interceptors */
extern char *show_proxy_args();
extern char *ij_blocked_url();
extern char *ij_untrusted_url();

extern int load_blockfile(), load_cookiefile(), load_trustfile(), load_forwardfile(), load_aclfile();
extern void init_proxy_args(), end_proxy_args(), savearg(), sweep();

extern char *url_encode(), *url_decode();

/* filters */
extern char *intercept_url();
extern char *block_url();
extern char *trust_url();
extern struct cookie_spec *cookie_url();
extern struct gateway     *forward_url();

extern struct gateway gateways[], *gw_default;
extern struct parsers url_patterns[];
extern struct parsers client_patterns[];
extern struct parsers server_patterns[];
extern struct interceptors intercept_patterns[];

extern void (*add_client_headers[])();
extern void (*add_server_headers[])();

extern char DEFAULT_USER_AGENT[];

extern int socks4_connect(), direct_connect();
#define SOCKS_4		40	/* original SOCKS 4 protocol */
#define SOCKS_4A	41	/* as modified for hosts w/o external DNS */

#define WHITEBG	"<body bgcolor=\"#ffffff\" link=\"#000078\" alink=\"#ff0022\" vlink=\"#787878\">\n"

#define BODY	"<body bgcolor=\"#f8f8f0\" link=\"#000078\" alink=\"#ff0022\" vlink=\"#787878\">\n"

#define BANNER "<strong>Internet J<small>UNK<i><font color=\"red\">BUSTER</font></i></small></strong>"

#endif
