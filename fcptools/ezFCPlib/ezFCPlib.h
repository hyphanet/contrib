/*
  This code is part of FCPTools - an FCP-based client library for Freenet

  CopyLeft (c) 2001 by David McNab

  Developers:
  - David McNab <david@rebirthing.co.nz>
  - Jay Oliveri <ilnero@gmx.net>
  
  Currently maintained by Jay Oliveri <ilnero@gmx.net>
  
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

#ifndef _EZFCPLIB_H
#define _EZFCPLIB_H 

/* Generic <sys/> includes here so they are first. */
#include <sys/types.h>
#include <sys/stat.h>

/**************************************************************************
  MS-WIN32 specifics
**************************************************************************/
#ifdef WIN32

#include <malloc.h>
#include <process.h>
#include <winsock2.h>
#include <io.h>
#include <time.h>

typedef SOCKET FCPSOCKET;

#define strcasecmp strcmpi
#define strncasecmp strnicmp

/* VERSION is defined by automake for non-Win platforms. */
#define VERSION "0.4.9w"

/**************************************************************************
  UNIX specifics
**************************************************************************/
#else

typedef int FCPSOCKET;

/* UNIX includes that do not correspond on WIN32 go here */
/* Keep 'sys' files first in include order */

#include <sys/socket.h>
#include <sys/time.h>

#include <unistd.h>

#endif

/**************************************************************************
  GENERIC (place anything that must happen after the above decl's here)
**************************************************************************/

#include <stdio.h>

/*************************************************************************/

/*
  Threshold levels for the user-provided fcpLogCallback() function
  fcpLogCallback will be called with a verbosity argument, which will
  be one of these values. This allows the client program to screen log
  messages according to importance.
*/
#define FCP_LOG_SILENT        0
#define FCP_LOG_CRITICAL      1
#define FCP_LOG_NORMAL        2
#define FCP_LOG_VERBOSE       3
#define FCP_LOG_DEBUG         4
#define FCP_LOG_MESSAGE_SIZE  4096   /* Was 65K */

#define FCP_SOCKET_DISCONNECTED -99

/*
  Lengths of allocated strings/arrays.
*/
#define L_64                64
#define L_256               256
#define L_512               512

#define L_KEY               L_64
#define L_FILENAME          L_512
#define L_URI               L_512
#define L_RAW_METADATA      65536

/* rework */
#define L_FILE_BLOCKSIZE    8192
/* ~rework */

#define KEY_TYPE_SSK  1
#define KEY_TYPE_CHK  2
#define KEY_TYPE_KSK  3

/*
	0 is the "unset" value
*/
#define META_TYPE_REDIRECT  'r'
#define META_TYPE_DBR       'd'
#define META_TYPE_SPLITFILE 's'
#define META_TYPE_INFO      'i'
#define META_TYPE_EXTINFO   'e'

/*
	option flags
	these must be powers of 2; they're bitmasks (1,2,4,8,16,32,64,128,256,512,1024,...)
*/
#define FCP_MODE_O_READ        0x0001
#define FCP_MODE_O_WRITE       0x0002
#define FCP_MODE_RAW           0x0004
#define FCP_MODE_DELETE_LOCAL  0x0008
#define FCP_MODE_SKIP_LOCAL    0x0010
/**************************    0x0012 */
/**************************    0x0014 */
/**************************    0x0018 */

/*
	Reasonable defaults
*/
#define EZFCP_DEFAULT_HOST         "127.0.0.1"
#define EZFCP_DEFAULT_PORT         8481
#define EZFCP_DEFAULT_HTL          3
#define EZFCP_DEFAULT_VERBOSITY    FCP_LOG_NORMAL
#define EZFCP_DEFAULT_LOGSTREAM    stdout
#define EZFCP_DEFAULT_BLOCKSIZE    1024000  /* default split part size (1,000 * 1,024) */
#define EZFCP_DEFAULT_RETRY        5
#define EZFCP_DEFAULT_REGRESS      0
#define EZFCP_DEFAULT_DELETELOCAL  0
#define EZFCP_DEFAULT_SKIPLOCAL    0
#define EZFCP_DEFAULT_RAWMODE      0
#define EZFCP_DEFAULT_TIMEOUT      300000 /* 5 minutes in milliseconds */

/* error codes; just negative numbers; group together
*/
#define EZERR_GENERAL -1
#define EZERR_SOCKET_TIMEOUT -100

/*
	Tokens for response types.
*/
#define FCPRESP_TYPE_SUCCESS        1
#define FCPRESP_TYPE_NODEHELLO      10
#define FCPRESP_TYPE_NODEINFO       11
#define FCPRESP_TYPE_DATAFOUND      20
#define FCPRESP_TYPE_DATACHUNK      21
#define FCPRESP_TYPE_DATANOTFOUND   22
#define FCPRESP_TYPE_ROUTENOTFOUND  30
#define FCPRESP_TYPE_URIERROR       40
#define FCPRESP_TYPE_RESTARTED      50
#define FCPRESP_TYPE_KEYCOLLISION   60
#define FCPRESP_TYPE_PENDING        70
#define FCPRESP_TYPE_FAILED         80
#define FCPRESP_TYPE_FORMATERROR    90
#define FCPRESP_TYPE_SEGMENTHEADER  100
#define FCPRESP_TYPE_BLOCKMAP       110
#define FCPRESP_TYPE_BLOCKSENCODED  111
#define FCPRESP_TYPE_BLOCKSDECODED  112
#define FCPRESP_TYPE_MADEMETADATA   120

/* Tokens for receive states
*/
#define RECV_STATE_WAITING      0
#define RECV_STATE_GOTHEADER    1


/***********************************************************************
	Connection handling structgures and definitions.
*/
typedef struct {
	char *uri;

	char  publickey[L_KEY+1];
	char  public[L_KEY+1];

	char  privatekey[L_KEY+1];
	int   length;
} FCPRESP_SUCCESS;

typedef struct {
	char architecture[L_64+1];
} FCPRESP_NODEINFO;

typedef struct {
  int datalength;
  int metadatalength;
	int timeout;
} FCPRESP_DATAFOUND;

typedef struct {
  int    length;
  char  *data;
} FCPRESP_DATACHUNK;

typedef struct {
	char *uri;

	char  publickey[L_KEY+1];
	char  privatekey[L_KEY+1];
} FCPRESP_KEYCOLLISION;

typedef struct {
	char *uri;
	int   timeout;

	char  publickey[L_KEY+1];
	char  privatekey[L_KEY+1];
} FCPRESP_PENDING;

typedef struct {
  char *reason;
} FCPRESP_FAILED;

typedef struct {
  char *reason;
} FCPRESP_URIERROR;

typedef struct {
  char *reason;

	int   unreachable;
	int   rejected;
	int   restarted;
} FCPRESP_ROUTENOTFOUND;

typedef struct {
  int  timeout;
} FCPRESP_RESTARTED;

typedef struct {
  char *reason;
} FCPRESP_FORMATERROR;

typedef struct {
	char  fec_algorithm[L_64+1];
 
	int   filelength;
	long  offset;
	int   block_count;
	int   block_size;
	int   datablock_offset;
	int   checkblock_count;
	int   checkblock_size;
	int   checkblock_offset;
	int   segments;
	int   segment_num;
	int   blocks_required;
} FCPRESP_SEGMENTHEADER;

typedef struct {
	int     block_count;
	char  **blocks;
	int     checkblock_count;
	char  **checkblocks;
} FCPRESP_BLOCKMAP;

typedef struct {
	int   block_count;
	int   block_size;
} FCPRESP_BLOCKSENCODED;

typedef struct {
	int   block_count;
	int   block_size;
	int   data_length;
} FCPRESP_BLOCKSDECODED;

typedef struct {
  int datalength;
} FCPRESP_MADEMETADATA;


/**********************************************************************
  Now bundle all these together.
*/
typedef struct {
  int type;

	FCPRESP_SUCCESS         success;
	FCPRESP_NODEINFO        nodeinfo;
	FCPRESP_DATAFOUND       datafound;
	FCPRESP_DATACHUNK       datachunk;
	FCPRESP_KEYCOLLISION    keycollision;
	FCPRESP_PENDING         pending;
	FCPRESP_RESTARTED       restarted;
	FCPRESP_ROUTENOTFOUND   routenotfound;
	FCPRESP_FAILED          failed;
	FCPRESP_FORMATERROR     formaterror;
	FCPRESP_URIERROR        urierror;

	/* FEC specific responses */
	FCPRESP_SEGMENTHEADER   segmentheader;
	/*FCPRESP_BLOCKMAP        blockmap;*/
	FCPRESP_BLOCKSENCODED   blocksencoded;
	FCPRESP_BLOCKSDECODED   blocksdecoded;
	FCPRESP_MADEMETADATA    mademetadata;

} FCPRESP;


/**********************************************************************
  Freenet Client Protocol Handle Definition Section
*/
typedef struct {
	int   verbosity;
	int   splitblock;
	int   retry;
	int   regress;
	int   delete_local;
	int   skip_local;
	int   timeout;
	int   rawmode;

	FILE *logstream;
	
	char *tempdir;
	char *homedir;

} hOptions;

typedef struct {
  int   type;        /* CHK@, KSK@, SSK@ */

	char  *uri_str;    /* the unparsed uri */
  char  *keyid;      /* the pub/priv/ch key */

	/* SSK's */
	char  *docname;    /* the /name// piece */
	char  *metastring; /* the //images/activelink.gif piece */

} hURI;

typedef struct {
	char   filename[L_FILENAME+1];  /* null terminated filename */
	int    fd;         /* corresponding file descriptor */

	int    fn_status;   /* status relative to Freenet */
	int    size;        /* size of this chunk */
	int    binary_mode; /* 0 for text , 1 for binary */

	hURI  *uri;         /* this block's CHK */

} hBlock;


typedef struct {
	char  *header_str;

	int    filelength;
	long   offset;

	int    block_count;
	int    block_size;
	int    datablock_offset;

	int    checkblock_count;
	int    checkblock_size;
	int    checkblock_offset;

	int    segments;
	int    segment_num;
	int    blocks_required;

	int      db_count;
	hBlock **data_blocks;

	int      cb_count;
	hBlock **check_blocks;

} hSegment;


typedef struct {
	int    type;
	char  *name;

	int    field_count;
	char  *data[128];  /* max == 64 */
	
} hDocument;


typedef struct {
	int  size;
	
	int  revision;
	int  encoding;

	hBlock     *tmpblock;

	int         cdoc_count;
	hDocument  *cdocs[128];

	char       *raw_metadata;

	int  _start;  /* intended for internal use */
} hMetadata;


typedef struct {
	int        type;

	hURI      *uri;        /* always just a CHK@ */
	hURI      *target_uri; /* holds the final key uri */

	char       public_key[L_KEY+1];
	char       private_key[L_KEY+1];

	int        openmode;
	char      *mimetype;
	int        size;

	hBlock    *tmpblock;

	hMetadata *metadata;

	int        segment_count;
	hSegment **segments;

} hKey;


typedef struct {
	char    *host;

	unsigned short port;
	int            htl;

	hOptions *options;

	char *description;
  char *protocol;
	int   highest_build;
	int   max_filesize;  /* returned from fcphello */

  FCPSOCKET socket;
	hKey *key;
		
  FCPRESP response;
} hFCP;


/**********************************************************************/

/* Function prototypes */
#ifdef __cplusplus
extern "C" {
#endif

	/* Any function prototype listed here should be considered
		 "documented", and matched with an explanation in ezFCPlib's
		 API documentation.
	*/

	/* Startup and shutdown functions */
	int    fcpStartup(FILE *logstream, int verbosity);
	void   fcpTerminate(void);

	/* HFCP handle management functions */
	hFCP  *fcpCreateHFCP(char *host, int port, int htl, int optmask);
	hFCP  *fcpInheritHFCP(hFCP *hfcp);
	void   fcpDestroyHFCP(hFCP *hfcp);

	/* hURI handle functions */
	hURI  *fcpCreateHURI(void);
	void   fcpDestroyHURI(hURI *uri);

	int    fcpParseHURI(hURI *uri, char *key);

	/* Client functions for operations between files on disk and freenet */
	int    fcpPutKeyFromFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename);
	int    fcpGetKeyToFile(hFCP *hfcp, char *key_uri, char *key_filename, char *meta_filename);
	
	/* Generate Key/Value pair (entropy not currently used */
	int    fcpMakeSvkKeypair(hFCP *hfcp, char *pub_key, char *priv_key, char *entropy);

	int    fcpClientHello(hFCP *hfcp);
	int    fcpClientInfo(hFCP *hfcp);
	int    fcpInvertPrivateKey(hFCP *hfcp);

	/* Freenet functions for operations between memory and freenet */
	int    fcpOpenKey(hFCP *hfcp, char *key, int mode);
	int    fcpWriteKey(hFCP *hfcp, char *buf, int len);
	int    fcpWriteMetadata(hFCP *hfcp, char *buf, int len);

	int    fcpReadKey(hFCP *hfcp, char *buf, int len);
	int    fcpReadMetadata(hFCP *hfcp, char *buf, int len);

	int    fcpCloseKey(hFCP *hfcp);

#ifdef __cplusplus
}
#endif

#endif /* EZFCPLIB_H */

