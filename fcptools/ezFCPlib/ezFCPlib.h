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

/* this keeps warnings on other systems from generating unnecessarily */
/*#define _GNU_SOURCE*/

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

/* (BINARY is commented out until I figure out *how* to
	 document this section */

#define _FCP_READFILE_FLAGS (_O_RDONLY /*| _O_BINARY*/)
#define _FCP_WRITEFILE_FLAGS (_O_CREAT | _O_WRONLY | _O_TRUNC /*| _O_BINARY*/)

#define _FCP_CREATEFILE_MODE (_S_IWRITE | _S_IREAD)
#define _FCP_READFILE_MODE 0

#define _FCP_DIR_SEP '\\'

/* VERSION is defined by automake for non-Win platforms. */
#define VERSION "0.9.0w"

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

#define _FCP_READFILE_FLAGS (O_RDONLY)
#define _FCP_WRITEFILE_FLAGS (O_CREAT | O_WRONLY | O_TRUNC)

#define _FCP_CREATEFILE_MODE (S_IWUSR | S_IRUSR)
#define _FCP_READFILE_MODE 0

#define _FCP_DIR_SEP '/'

#endif

#define _FCP_READ   0x0001
#define _FCP_WRITE  0x0002

/**************************************************************************
  GENERIC (place anything that must happen after the above decl's here)
**************************************************************************/

/* Needed for struct FILE */
#include <stdio.h>

/*************************************************************************/
/* Reasonable defaults */
/*************************************************************************/
#define FCPT_DEF_HOST         "127.0.0.1"
#define FCPT_DEF_PORT         8481
#define FCPT_DEF_HTL          3
#define FCPT_DEF_VERBOSITY    FCPT_LOG_NORMAL
#define FCPT_DEF_LOGSTREAM    stdout
#define FCPT_DEF_BLOCKSIZE    262144  /* default split part size (256kB) */
#define FCPT_DEF_RETRY        5
#define FCPT_DEF_REGRESS      0
#define FCPT_DEF_DELETELOCAL  0
#define FCPT_DEF_SKIPLOCAL    0
#define FCPT_DEF_RAWMODE      0
#define FCPT_DEF_TIMEOUT      420000 /* 7 minutes in milliseconds */
/*************************************************************************/

/* Log verbosity values */
#define FCPT_LOG_SILENT        0
#define FCPT_LOG_CRITICAL      1
#define FCPT_LOG_NORMAL        2
#define FCPT_LOG_VERBOSE       3
#define FCPT_LOG_DEBUG         4
#define FCPT_LOG_MESSAGE_SIZE  4096

/* Option flags; these must be powers of 2 */
#define FCPT_MODE_O_READ              0x0001 /* 0000 0001 */
#define FCPT_MODE_O_WRITE             0x0002 /* 0000 0010 */
#define FCPT_MODE_RAW                 0x0004 /* 0000 0100 */
#define FCPT_MODE_REMOVE_LOCAL        0x0008 /* 0000 1000 */
#define FCPT_MODE_DBR                 0x0020 /* 0001 0000 */

/* Tokens for response types */
#define FCPT_RESPONSE_SUCCESS         1
#define FCPT_RESPONSE_NODEHELLO       10
#define FCPT_RESPONSE_NODEINFO        11
#define FCPT_RESPONSE_DATAFOUND       20
#define FCPT_RESPONSE_DATACHUNK       21
#define FCPT_RESPONSE_DATANOTFOUND    22
#define FCPT_RESPONSE_ROUTENOTFOUND   30
#define FCPT_RESPONSE_URIERROR        40
#define FCPT_RESPONSE_RESTARTED       50
#define FCPT_RESPONSE_KEYCOLLISION    60
#define FCPT_RESPONSE_PENDING         70
#define FCPT_RESPONSE_FAILED          80
#define FCPT_RESPONSE_FORMATERROR     90
#define FCPT_RESPONSE_SEGMENTHEADER   100
#define FCPT_RESPONSE_BLOCKMAP        110
#define FCPT_RESPONSE_BLOCKSENCODED   111
#define FCPT_RESPONSE_BLOCKSDECODED   112
#define FCPT_RESPONSE_MADEMETADATA    120

/* Handled key types */
#define FN_KEY_SSK  1
#define FN_KEY_CHK  2
#define FN_KEY_KSK  3

/* Supported types of metadata */
#define FN_META_REDIRECT    'r'
#define FN_META_DBR         'd'
#define FN_META_SPLITFILE   's'
#define FN_META_INFO        'i'
#define FN_META_EXTINFO     'e'

/* Error codes; just negative numbers */
#define FCPT_ERR_GENERAL           -1
#define FCPT_ERR_SOCKET_TIMEOUT    -100

/*
#define FCPT_ERR_
#define FCPT_ERR_
#define FCPT_ERR_
#define FCPT_ERR_
*/

/* Value to set hfcp->socket when not connected */
#define FCPT_SOCKET_DISCONNECTED   -99

/* Lengths of strings/arrays */
#define L_KEY               128
#define L_FILENAME          1024
#define L_URI               1024
#define L_RAW_METADATA      65536
#define L_FILE_BLOCKSIZE    8192


/***********************************************************************
	Connection handling structgures and definitions.
*/
typedef struct {
	char *uri;

	char  pub[L_KEY+1];

	char  publickey[L_KEY+1];
	char  privatekey[L_KEY+1];
	char  cryptokey[L_KEY+1];

	unsigned long length;
} FCPRESP_SUCCESS;

typedef struct {
	char  *architecture;
	char  *operatingsystem;
	char  *operatingsystemversion;
	
	unsigned long  nodeport;
	char          *nodeaddress;

	char  *javavendor;
	char  *javaname;
	char  *javaversion;

	unsigned long  maximummemory;
	unsigned long  maxfilesize;
	unsigned long  allocatedmemory;
	unsigned long  freememory;
	unsigned long  processors;
	unsigned long  availablethreads;
	unsigned long  activejobs;

	unsigned long  datastoremax;
	unsigned long  datastorefree;
	unsigned long  datastoreused;
	unsigned long  estimatedload;
	unsigned long  estimateratelimitingload;

	unsigned long  mostrecenttimestamp;
	unsigned long  leastrecenttimestamp;
	unsigned long  routingtime;
} FCPRESP_NODEINFO;

typedef struct {
	char  *description;
  char  *protocol;

	unsigned short  highest_build;
	unsigned long   max_filesize;
} FCPRESP_NODEHELLO;

typedef struct {
  unsigned long  datalength;
  unsigned long  metadatalength;
	unsigned short timeout;
} FCPRESP_DATAFOUND;

typedef struct {
  unsigned long  length;

  char          *data;
} FCPRESP_DATACHUNK;

typedef struct {
	char *uri;

	char  publickey[L_KEY+1];
	char  privatekey[L_KEY+1];
} FCPRESP_KEYCOLLISION;

typedef struct {
	char *uri;
	unsigned short   timeout;

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

	unsigned short   unreachable;
	unsigned short   rejected;
	unsigned short   restarted;
	unsigned short   backedoff;

} FCPRESP_ROUTENOTFOUND;

typedef struct {
  unsigned short  timeout;
  char           *reason;
} FCPRESP_RESTARTED;

typedef struct {
  char *reason;
} FCPRESP_FORMATERROR;

typedef struct {
	char  fec_algorithm[L_KEY+1];
 
	unsigned long   filelength;
	unsigned long   offset;
	unsigned long   block_size;
	unsigned long   datablock_offset;
	unsigned long   checkblock_size;
	unsigned long   checkblock_offset;
	unsigned long   segment_num;
	unsigned long   blocks_required;

	unsigned long   block_count;
	unsigned long   checkblock_count;
	unsigned long   segments;
} FCPRESP_SEGMENTHEADER;

typedef struct {
	unsigned short  block_count;
	unsigned short  checkblock_count;

	char  **blocks;
	char  **checkblocks;
} FCPRESP_BLOCKMAP;

typedef struct {
	unsigned short  block_count;
	unsigned long   block_size;
} FCPRESP_BLOCKSENCODED;

typedef struct {
	unsigned short  block_count;
	unsigned long   block_size;
	unsigned long   data_length;
} FCPRESP_BLOCKSDECODED;

typedef struct {
  unsigned long  datalength;
} FCPRESP_MADEMETADATA;


/**********************************************************************
  Now bundle all these together.
*/
typedef struct {
  int type;

	FCPRESP_SUCCESS         success;
	FCPRESP_NODEINFO        nodeinfo;
	FCPRESP_NODEHELLO       nodehello;
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

/* *** ALERT!! *** Anything added in this struct must be also handled in
	 fcpInheritHFCP() in file fcpCreation.c */
typedef struct {
	unsigned long splitblock;

	int   verbosity;
	int   retry;
	int   regress;
	int   remove_local;
	int   timeout;
	int   min_timeout;
	int   noredirect;
	int   meta_redirect;
	int   dbr;
	int   future;

	FILE *logstream;
	
	char *tempdir;
	char *homedir;

} hOptions;


typedef struct {
  int    type;       /* CHK@, KSK@, SSK@ */
	char  *uri_str;    /* the unparsed uri */

  char  *routingkey;      /* the pub/priv key */
	char  *cryptokey;

	char  *filename;   /* filename hint */
	char  *metastring;

} hURI;


typedef struct {
	char   filename[L_FILENAME+1];  /* null terminated filename */
	int    fd;         /* corresponding file descriptor */

	int    fn_status;   /* status relative to Freenet */
	int    size;        /* size of this chunk */

	int    binary_mode; /* 0 for text , 1 for binary */
	int    m_delete;      /* 0 will not delete the file when requested,
												 !0 does */

	hURI  *uri;         /* this block's CHK */

} hBlock;


typedef struct {
	char  *header_str;

	unsigned long  filelength;
	unsigned long  offset;

	unsigned long  block_count;
	unsigned long  block_size;
	unsigned long  datablock_offset;

	unsigned long  checkblock_count;
	unsigned long  checkblock_size;
	unsigned long  checkblock_offset;

	unsigned long  segments;
	unsigned long  segment_num;
	unsigned long  blocks_required;

	unsigned long  db_count;
	unsigned long  cb_count;

	hBlock  **data_blocks;
	hBlock  **check_blocks;

} hSegment;


typedef struct {
	int    type;
	char  *name;

	char  *format;
	char  *description;

	int    field_count;
	char  **data;
	
} hDocument;


typedef struct {
	unsigned long  size;

	int   revision;
	char *encoding;

	hBlock     *tmpblock;

	int         cdoc_count;
	hDocument  **cdocs;

	char       *raw;   /* raw freenet metadata (no "rest" parts) */
	char       *rest;  /* the "rest" part/no freenet cdocs */

	int  _start;  /* intended for internal use in _fcpMetadata.c */
} hMetadata;


typedef struct {
	int        type;

	int        db_redirect;
	int        static_redirect;
	int        splitfile;

	hURI      *uri;        /* always just a CHK@ */
	hURI      *target_uri; /* holds the final key uri */

	char       public_key[L_KEY+1];
	char       private_key[L_KEY+1];

	int        openmode;
	char      *mimetype;

	unsigned long   size;
	unsigned long   segment_count;

	hBlock    *tmpblock;

	hMetadata *metadata;

	hSegment **segments;

} hKey;


typedef struct {
	char    *host;

	unsigned short  port;
	unsigned short  htl;

	char  *description;
	char  *protocol;

	unsigned long  highest_build;
	unsigned long  max_filesize;

	hOptions *options;

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
	int    fcpSetMimetype(hKey *key, char *mimetype); /* NEW; not doc'ed */
	int    fcpWriteKey(hFCP *hfcp, char *buf, int len);
	int    fcpWriteMetadata(hFCP *hfcp, char *buf, int len);

	int    fcpReadKey(hFCP *hfcp, char *buf, int len);
	int    fcpReadMetadata(hFCP *hfcp, char *buf, int len);

	int    fcpCloseKey(hFCP *hfcp);

#ifdef __cplusplus
}
#endif

#endif /* EZFCPLIB_H */

