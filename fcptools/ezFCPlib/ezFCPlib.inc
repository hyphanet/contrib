; ezFCPlib.lib PROTOTYPES and STRUCTS
; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

; ==================================================================================
; Most of this file was converted from the header file using h2inc
; ==================================================================================

; Begin of file ezFCPlib.h
;
;   This code is part of FreeWeb - an FCP-based client for Freenet
;
;   Designed and implemented by David McNab, david@rebirthing.co.nz
;   CopyLeft (c) 2001 by David McNab
;
;   The FreeWeb website is at http://freeweb.sourceforge.net
;   The website for Freenet is at http://freenet.sourceforge.net
;
;   This code is distributed under the GNU Public Licence (GPL) version 2.
;   See http://www.gnu.org/ for further details of the GPL.
;

;         This is taken from stdio.h for the windows platform
_P_tmpdir		TEXTEQU		<"\\">
L_tmpnam		TEXTEQU		<sizeof(_P_tmpdir)+12>


VERSION		        TEXTEQU		<"0.4.6">       ;  VERSION is defined by automake for non-Win platforms.
write		        TEXTEQU		<_write>
open		        TEXTEQU		<_open>
read		        TEXTEQU		<_read>
close		        TEXTEQU		<_close>
mkstemp		        TEXTEQU		<_mkstemp>
snprintf		TEXTEQU		<_snprintf>
vsnprintf		TEXTEQU		<_vsnprintf>
strcasecmp		TEXTEQU		<strcmpi>
strncasecmp		TEXTEQU		<strnicmp>
S_IREAD		        TEXTEQU		<_S_IREAD>
S_IWRITE		TEXTEQU		<_S_IWRITE>
OPEN_MODE_READ		TEXTEQU		<(_O_RDONLY | _O_BINARY)>
OPEN_MODE_WRITE		EQU		0t

;
;   Threshold levels for the user-provided fcpLogCallback() function
;   fcpLogCallback will be called with a verbosity argument, which will
;   be one of these values. This allows the client program to screen log
;   messages according to importance
;
FCP_LOG_CRITICAL		EQU		0t
FCP_LOG_NORMAL		        EQU		1t
FCP_LOG_VERBOSE		        EQU		2t
FCP_LOG_DEBUG		        EQU		3t


FCP_LOG_MESSAGE_SIZE		EQU		4096t   ;  Was 65K

;
;   Lengths of allocated strings/arrays.
;
L_HOST		                EQU		128t
L_PROTOCOL		        EQU		10t
L_NODE_DESCRIPTION		EQU		128t
L_MIMETYPE		        EQU		80t
L_FILENAME		        EQU		128t
L_SSK_PATH		        EQU		128t
L_URI		                EQU		256t
L_KEY		                EQU		128t
L_KEYINDEX		        EQU		128t
L_KSK		                EQU		32768t
L_MESSAGE		        EQU		256t
L_SOCKET_REQUEST		EQU		2048t

;
;   Splitfiles handling definitions
;
;   For keeping track of splitfile insert threads - used by fcpputsplit
;   will discontinue when splitfiles mgr is working
;
@t_0		TYPEDEF		PTR SBYTE
@t_1		TYPEDEF		PTR SBYTE
@t_2		TYPEDEF		PTR PTR SBYTE

@tag_0		STRUCT 4t
buffer		        @t_0		?
threadSlot		@t_1		?
blocksize		SDWORD		?
key		        @t_2		?               ;  key URI, if inserting metadata
@tag_0		ENDS

fcpPutJob		TYPEDEF		@tag_0


SPLIT_BLOCK_SIZE		EQU		262144t         ;  default split part size (256*1024)
FCP_MAX_SPLIT_THREADS		EQU		8t

SPLIT_INSSTAT_IDLE		EQU		0t      ;  no splitfile insert requested

SPLIT_INSSTAT_WAITING		EQU		1t      ;  waiting for mgr thread to pick up

SPLIT_INSSTAT_INPROG		EQU		2t      ;  in progress

SPLIT_INSSTAT_BADNEWS		EQU		3t      ;  failure - awaiting cleanup

SPLIT_INSSTAT_MANIFEST		EQU		4t      ;  inserting splitfile manifest

SPLIT_INSSTAT_SUCCESS		EQU		5t      ;  full insert completed successfully

SPLIT_INSSTAT_FAILED		EQU		6t      ;  insert failed somewhere

;
;   Splitfile Insert Control Blocks
;

@t_3		TYPEDEF		PTR SBYTE

_splitChunk     STRUCT 4t
key		SBYTE		256t DUP (?)    ;  CHK key of inserted chunk
status		SDWORD		?               ;  insert status of this chunk
index		SDWORD		?               ;  index num of this chunk
chunk		@t_3		?               ;  byte-image of chunk to insert - malloc()'ed
size		SDWORD		?               ;  SIZE of this chunk
_splitChunk     ENDS

splitChunkIns		TYPEDEF		_splitChunk


@t_4		TYPEDEF		PTR SBYTE
@t_5		TYPEDEF		PTR SBYTE
@t_6		TYPEDEF		PTR splitChunkIns
@t_7		TYPEDEF		PTR _splitJob


_splitJob		STRUCT 4t
key		        SBYTE		256t DUP (?)
status		        SBYTE		?               ;  status as advised by splitmgr thread
fd		        SDWORD		?               ;  fd of file we are inserting from, if applicable
numChunks		SDWORD		?               ;  total number of chunks to insert
doneChunks		SDWORD		?               ;  number of chunks successfully inserted
totalSize		SDWORD		?               ;  total number of bytes to insert
fileName		@t_4		?               ;  path of file being inserted
mimeType		@t_5		?
chunk		        @t_6		?               ;  malloc'ed array of split chunk control blocks
next		        @t_7		?               ;  next on linked list
_splitJob		ENDS

splitJobIns		TYPEDEF		_splitJob


;
;   General FCP definitions
;
FCP_ID_REQUIRED		        TEXTEQU		<>
RECV_BUFSIZE		        EQU		2048t
EZFCP_DEFAULT_HOST		TEXTEQU		<"localhost">
EZFCP_DEFAULT_PORT		EQU		8481t
EZFCP_DEFAULT_HTL		EQU		3t
EZFCP_DEFAULT_REGRESS		EQU		3t
EZFCP_DEFAULT_RAWMODE		EQU		0t


;
;   flags for fcpOpenKey()
;
_FCP_O_READ		        EQU		000000100h
_FCP_O_WRITE		        EQU		000000200h
_FCP_O_RAW		        EQU		000000400h      ;  disable automatic metadata handling


;  METADATA related definitions
KEY_TYPE_MSK		        EQU		00000004dh
KEY_TYPE_SSK		        EQU		000000053h
KEY_TYPE_KSK		        EQU		00000004bh
KEY_TYPE_CHK		        EQU		000000043h
META_TYPE_REDIRECT		EQU		000000052h
META_TYPE_DBR		        EQU		000000044h
META_TYPE_CONTENT		EQU		000000043h
META_TYPE_MAPFILE		EQU		00000004dh
META_TYPE_04		        EQU		000000034h


;
;   cdoc type fields
;
META_TYPE_04_NONE		EQU		00000006eh
META_TYPE_04_REDIR		EQU		000000072h
META_TYPE_04_DBR		EQU		000000064h
META_TYPE_04_SPLIT		EQU		000000073h


;
;   Define structures for incoming data from node
;
;   Tokens for response types
;
FCPRESP_TYPE_HELLO		EQU		1t
FCPRESP_TYPE_SUCCESS		EQU		2t
FCPRESP_TYPE_DATAFOUND		EQU		3t
FCPRESP_TYPE_DATACHUNK		EQU		4t
FCPRESP_TYPE_FORMATERROR        EQU		5t
FCPRESP_TYPE_URIERROR		EQU		6t
FCPRESP_TYPE_DATANOTFOUND       EQU		7t
FCPRESP_TYPE_ROUTENOTFOUND      EQU		8t
FCPRESP_TYPE_KEYCOLLISION       EQU		9t
FCPRESP_TYPE_SIZEERROR		EQU		10t
FCPRESP_TYPE_FAILED		EQU		11t


;
;   Tokens for receive states
;
RECV_STATE_WAITING		EQU		0t
RECV_STATE_GOTHEADER		EQU		1t


;
;   first - basic node hello response
;

@t_8		TYPEDEF		PTR SBYTE

@tag_1		STRUCT 4t
protocol	SDWORD		?       ;  protocol ID
node		@t_8		?       ;  malloc - node ID
@tag_1		ENDS

FCPRESP_HELLO		TYPEDEF		@tag_1


;
;   Failed response
;
@t_9		TYPEDEF		PTR SBYTE

@tag_2		STRUCT 4t
reason		@t_9		?       ;  node explanation of failure
@tag_2		ENDS

FCPRESP_FAILED		TYPEDEF		@tag_2


;
;   SVK keypair response
;
@t_10		TYPEDEF		PTR SBYTE
@t_11		TYPEDEF		PTR SBYTE
@t_12		TYPEDEF		PTR SBYTE

@tag_3		STRUCT 4t
pubkey		@t_10		?       ;  SSK public key
privkey		@t_11		?       ;  SSK private key
uristr		@t_12		?       ;  generated URI
@tag_3		ENDS

FCPRESP_SVKKEYPAIR		TYPEDEF		@tag_3


;
;   Received data header response
;
; char *metaData; // DEPRACATE
; char *metaPtr;  // DEPRACATE

@tag_4		STRUCT 4t
dataLength      SDWORD		?       ;  count of: (metadata + data)
metaLength      SDWORD		?       ;  number of bytes of metadata
@tag_4		ENDS

FCPRESP_DATAFOUND		TYPEDEF		@tag_4


;
;   Received data chunk response
;

@t_13		TYPEDEF		PTR SBYTE
@t_14		TYPEDEF		PTR SBYTE
@t_15		TYPEDEF		PTR SBYTE

@tag_5		STRUCT 4t
length		SDWORD		?       ;  Length=<number: number of bytes in trailing field>
data		@t_13		?       ;  MetadataLength=<number: default=0: number of bytes of	metadata>
dataptr		@t_14		?       ;  points into data buf for partial reads
dataend		@t_15		?       ;  points just after last byte of data
@tag_5		ENDS

FCPRESP_DATACHUNK		TYPEDEF		@tag_5

@t_16		TYPEDEF		PTR SBYTE
@tag_6		STRUCT 4t
text		@t_16		?
@tag_6		ENDS

FCPRESP_FORMATERROR		TYPEDEF		@tag_6

@t_17		TYPEDEF		PTR SBYTE
@tag_7		STRUCT 4t
text		@t_17		?
@tag_7		ENDS

FCPRESP_URIERROR		TYPEDEF		@tag_7

@t_18		TYPEDEF		PTR SBYTE
@tag_8		STRUCT 4t
text		@t_18		?
@tag_8		ENDS

FCPRESP_KEYCOLLISION		TYPEDEF		@tag_8

;
;   Now bundle all these together
;
@tag_10		STRUCT 4t
hello		FCPRESP_HELLO		<>
failed		FCPRESP_FAILED		<>
keypair		FCPRESP_SVKKEYPAIR		<>
datafound	FCPRESP_DATAFOUND		<>
datachunk	FCPRESP_DATACHUNK		<>
fmterror        FCPRESP_FORMATERROR		<>
urierror        FCPRESP_URIERROR		<>
keycollision    FCPRESP_KEYCOLLISION		<>
@tag_10		ENDS

@tag_9		STRUCT 4t
type		SDWORD		?
body		@tag_10		<>
@tag_9		ENDS

FCPRESP		TYPEDEF		@tag_9


;
;   Universal tokens for metadata parsing
;
;   Freenet URI split up into its parts
; 
@t_19		TYPEDEF		PTR SBYTE
@t_20		TYPEDEF		PTR SBYTE
@t_21		TYPEDEF		PTR SBYTE

; char  **docname;           /* DEPRECATE */
@tag_11		STRUCT 4t
type		SBYTE		?
keyid		@t_19		?       ;  malloc
path		@t_20		?       ;  malloc - only used with SSKs
uri_str		@t_21		?       ;  malloc - raw uri string
numdocs		SDWORD		?       ;  number of documents
@tag_11		ENDS

FCP_URI		TYPEDEF		@tag_11


;
;   Container for a splitfile chunk
;
@t_22		TYPEDEF		PTR _04CHUNK

_04CHUNK	STRUCT 4t
uri		SBYTE		256t DUP (?)    ;  URI of this chunk
_next		@t_22		?               ;  parser internal use only
_04CHUNK	ENDS

META04SPLITCHUNK		TYPEDEF		_04CHUNK


;
;   Container for splitfile check piece
;

@t_23		TYPEDEF		PTR SDWORD
@t_24		TYPEDEF		PTR _04PIECE

_04PIECE	STRUCT 4t
uri		SBYTE		256t DUP (?)    ;  uri of check piece
graphLen	SDWORD		?               ;  size of graph elementa array
graph		@t_23		?               ;  array of graph elements
_next		@t_24		?               ;  parser internal use only
_04PIECE	ENDS

META04SPLITPIECE		TYPEDEF		_04PIECE


;
;   Container for a splitfile check level
;

@t_25		TYPEDEF		PTR META04SPLITPIECE
@t_26		TYPEDEF		PTR _04LEVEL

_04LEVEL		STRUCT 4t
numPieces		SDWORD		?       ;  number of check pieces
piece		        @t_25		?       ;  array of check piece specs
_next		        @t_26		?       ;  parser internal use only
_04LEVEL		ENDS

META04SPLITLEVEL		TYPEDEF		_04LEVEL


;  METADATA-parsing data structures

@tag_12		STRUCT 4t
name		SBYTE		128t DUP (?)    ;  key name
value		SBYTE		128t DUP (?)    ;  value if any, or NULL
@tag_12		ENDS

KEYVALPAIR		TYPEDEF		@tag_12



@t_27		TYPEDEF		PTR KEYVALPAIR

@tag_13		STRUCT 4t
count		SDWORD		?               ;  number of fields in this cdoc
type		SDWORD		?
keys		@t_27		64t DUP (?)     ;  array of key/value pairs
@tag_13		ENDS

FLDSET		TYPEDEF		@tag_13


;
;   Main 0.4 metadata structure
;
@t_28		TYPEDEF		PTR PTR FLDSET
;  new - array of cdocs, must be allocated.  We _WILL_ have more then 64 bits of metainfo
@tag_14		STRUCT 4t
vers		SBYTE		16t DUP (?)
count		SDWORD		?
cdoc		@t_28		?
@tag_14		ENDS

META04		TYPEDEF		@tag_14


;
;   Definitions for key index access
;


@tag_15		STRUCT 4t
name		SBYTE		128t DUP (?)    ;  name of key index
next_keynum	SDWORD		?               ;  the next key we are retrieving
basedate        SBYTE		9t DUP (?)      ;  basedate of key, if using basedates
@tag_15		ENDS

FCP_KEYINDEX		TYPEDEF		@tag_15


;
;   Splitfile control structure
;
CHUNK_STATUS_WAITING		EQU		0t
CHUNK_STATUS_INPROG		EQU		1t
CHUNK_STATUS_DONE		EQU		2t


@t_29		TYPEDEF		PTR SBYTE
@t_30		TYPEDEF		PTR SBYTE

@tag_16		STRUCT 4t
chunkSize		SDWORD		?
chunkTotal		SDWORD		?
chunksInserted		SDWORD		?
isfile		        SBYTE		?       ;  TRUE if splitting a file, FALSE if memory
keyfd		        SDWORD		?       ;  fd of open file being inserted, if a file
keymem		        @t_29		?       ;  ptr to block of mem being inserted, if mem
chunkStatus		@t_30		?       ;  array of status bytes for chunks
@tag_16		ENDS

FCP_SPLIT		TYPEDEF		@tag_16


;
;   Basic control block for FCP connections
;
_FCPCONN	TEXTEQU		<struct _fcpconn>
_fcpconn        STRUCT 4t
socket		SDWORD		?
Status		SDWORD		?
response	FCPRESP		<>
_fcpconn	ENDS

FCPCONN		TYPEDEF		_fcpconn


;
;   Now put these together into the main HFCP structure
;


@t_31		TYPEDEF		PTR SBYTE
@t_32		TYPEDEF		PTR META04
@t_33		TYPEDEF		PTR FLDSET
@t_34		TYPEDEF		PTR FCP_URI

@tag_18		STRUCT 4t
uri		@t_34		?               ;  uri of key being inserted
fd_data		SDWORD		?               ;  fd for writing key data to temp file
num_data_wr	SDWORD		?               ;  num bytes of normal data written
data_temp_file	SBYTE		14t DUP (?)     ;  temporary file full path
fd_meta		SDWORD		?               ;  fd for writing key metadata to temp file
num_meta_wr	SDWORD		?               ;  num bytes of metadata written
meta_temp_file	SBYTE		14t DUP (?)     ;  temporary file full path
@tag_18		ENDS


@tag_17		STRUCT 4t
malloced	SDWORD		?               ;  set if this block was created via malloc
htl		SDWORD		?               ;  hops to live - defaults to 25
regress		SDWORD		?               ;  days to regress when retrying failed date-redirects
raw		SDWORD		?               ;  set to disable auto metadata handling
verbose		SDWORD		?               ;  set to enable status printfs to stdout
keysize		SWORD		?               ;  with requests, this is the size of key data
bytesread	SDWORD		?               ;  num bytes read from key so far
openmode	SDWORD		?
protocol	SDWORD		?
node		SBYTE		128t DUP (?)
rawMetadata	@t_31		?               ;  raw metadata read from file when in raw mode
meta		@t_32		?               ;  structure containing parsed metadata
fields		@t_33		?
mimeType	SBYTE		80t DUP (?)
wr_info		@tag_18		<>
conn		FCPCONN		<>
keyindex	FCP_KEYINDEX	<>
created_uri	SBYTE		128t DUP (?)    ;  filled in by library after writing key
pubkey		SBYTE		128t DUP (?)    ;  filled in after writing a key
privkey		SBYTE		128t DUP (?)    ;  filled in after writing a key
failReason	SBYTE		256t DUP (?)    ;  reason sent back with failure msg
split		splitJobIns	<>              ;  control structure for insert split job
@tag_17		ENDS

HFCP		TYPEDEF		@tag_17


;
;   Function prototypes
;

GetAddr PROTO :DWORD,:DWORD,:DWORD
GetMimeType PROTO :DWORD
WSAGetLastError PROTO
WSAStartup PROTO :DWORD,:DWORD
_beginthread PROTO :DWORD,:DWORD,:DWORD
_endthread PROTO
_fcpFreeUri PROTO :DWORD
_fcpInitSplit PROTO :DWORD
_fcpParseUri PROTO :DWORD,:DWORD
_fcpPutKeyFromMem PROTO :DWORD,:DWORD,:DWORD,:DWORD,:DWORD,:DWORD
_fcpReadBlk PROTO :DWORD,:DWORD,:DWORD
_fcpRecvResponse PROTO :DWORD
_fcpSockConnect PROTO :DWORD
_fcpSockDisconnect PROTO :DWORD
_fcpSockInit PROTO
_fcpSockReceive PROTO :DWORD,:DWORD,:DWORD
_fcpSockSend PROTO :DWORD,:DWORD,:DWORD
cdocFindDoc PROTO :DWORD,:DWORD
cdocHexVal PROTO :DWORD,:DWORD,:DWORD,:DWORD
cdocIntVal PROTO :DWORD,:DWORD,:DWORD,:DWORD
cdocLookupKey PROTO :DWORD,:DWORD
cdocStrVal PROTO :DWORD,:DWORD,:DWORD,:DWORD
chunkThread PROTO :DWORD
closesocket PROTO :DWORD
connect PROTO :DWORD,:DWORD,:DWORD
crLaunchThread PROTO :DWORD,:DWORD
crQuitThread PROTO :DWORD
crSleep PROTO :DWORD,:DWORD
dumpQueue PROTO
fcpCloseKey PROTO :DWORD
fcpCloseKeyRead PROTO :DWORD
fcpCloseKeyWrite PROTO :DWORD
fcpCreateHandle PROTO
fcpDestroyHandle PROTO :DWORD
fcpGetKeyToFile PROTO :DWORD,:DWORD,:DWORD,:DWORD
fcpGetKeyToMem PROTO :DWORD,:DWORD,:DWORD,:DWORD
fcpInitHandle PROTO :DWORD
fcpInsSplitFile PROTO :DWORD,:DWORD,:DWORD,:DWORD
fcpLogCallback PROTO :DWORD,:DWORD
fcpMakeChkFromFile PROTO
fcpMakeChkFromMem PROTO
fcpMakeSvkKeypair PROTO :DWORD,:DWORD,:DWORD
fcpOpenKey PROTO :DWORD,:DWORD,:DWORD
fcpOpenKeyIndex PROTO :DWORD,:DWORD,:DWORD,:DWORD
fcpOpenKeyRead PROTO :DWORD,:DWORD,:DWORD
fcpOpenKeyWrite PROTO :DWORD,:DWORD
fcpPutKeyFromFile PROTO :DWORD,:DWORD,:DWORD,:DWORD
fcpPutKeyFromMem PROTO :DWORD,:DWORD,:DWORD,:DWORD,:DWORD
fcpRawMode PROTO :DWORD,:DWORD
fcpReadKey PROTO :DWORD,:DWORD,:DWORD
fcpReadKeyIndex PROTO :DWORD,:DWORD,:DWORD
fcpSetHost PROTO :DWORD
fcpSetHtl PROTO :DWORD,:DWORD
fcpSetRegress PROTO :DWORD,:DWORD
fcpSetSplitSize PROTO :DWORD
fcpSetSplitThreads PROTO :DWORD
fcpStartup PROTO :DWORD,:DWORD,:DWORD,:DWORD,:DWORD
fcpWriteKey PROTO :DWORD,:DWORD,:DWORD
fcpWriteKeyIndex PROTO :DWORD,:DWORD
fcpWriteKeyMeta PROTO :DWORD,:DWORD,:DWORD
getLine PROTO :DWORD,:DWORD,:DWORD
gethostbyname PROTO :DWORD
getrespDataNotFound PROTO :DWORD
getrespDatachunk PROTO :DWORD
getrespDatafound PROTO :DWORD
getrespFailed PROTO :DWORD
getrespFormaterror PROTO :DWORD
getrespHello PROTO :DWORD
getrespKeycollision PROTO :DWORD
getrespRouteNotFound PROTO :DWORD
getrespSuccess PROTO :DWORD
getrespUrierror PROTO :DWORD
getrespblock PROTO :DWORD,:DWORD,:DWORD
getrespline PROTO :DWORD,:DWORD
htons PROTO :DWORD
insertSplitManifest PROTO :DWORD,:DWORD,:DWORD,:DWORD
metaFree PROTO :DWORD
metaParse PROTO :DWORD,:DWORD
opentemp PROTO :DWORD
recv PROTO :DWORD,:DWORD,:DWORD,:DWORD
safeMalloc PROTO :DWORD
send PROTO :DWORD,:DWORD,:DWORD,:DWORD
socket PROTO :DWORD,:DWORD,:DWORD
splitAddJob PROTO :DWORD
splitLine PROTO :DWORD,:DWORD,:DWORD
splitMgrThread PROTO :DWORD
timeLastMidnight PROTO
xtoi PROTO :DWORD


; End of file ezFCPlib.h
