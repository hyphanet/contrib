#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <unistd.h>

#define GT_SPLIT_THRESHOLD	256 * 1024

#define FCP_SUCCESS		0
#define FCP_CONNECT_FAILED	-1
#define FCP_INVALID_URI		-2
#define FCP_PART_NOT_FOUND	-3
#define FCP_INVALID_METADATA    -4
#define FCP_REQUEST_FAILED	-5
#define FCP_IO_ERROR		-6

enum {DATA, CONTROL};

typedef struct {
    char *document_name;   // to which document this part applies
    char *target_uri;      // the target of this redirect
} redirect;

typedef struct {
    char *document_name;   // to which document this part applies
    char *predate;         // the portion of the key URI before the date
    char *postdate;        // the portion of the key URI after the date
    long baseline;         // the baseline, in seconds since the epoch
    long increment;        // the increment, in seconds
} date_redirect;

typedef struct {
    char *document_name;   // to which document this part applies
    int filesize;          // the final size of the original file
    int chunk_count;       // the number of data chunks
    char **keys;           // an array of /chunk_count/ Freenet URIs
//    int check_levels;      // the number of levels of check coding
//    int *check_pieces;     // the number of check pieces for each level.
//    char ***checks;        // the Freenet URI of each check piece in the matrix
//    int **graph;           // the graph for each check piece in the matrix
} splitfile;

typedef struct {
    char *document_name;   // to which document this part applies
    int f_count;
    char ***fields;        // arbitrary name=value pairs
} info;

typedef struct {
    char *uri;             // the URI of this metadata
    int r_count;
    redirect **r;          // array of redirect parts
    int dr_count;
    date_redirect **dr;    // array of date redirect parts
    int sf_count;
    splitfile **sf;        // array of splitfile parts
    int i_count;
    info **i;              // array of info parts
} fcp_metadata;

typedef struct {
    pthread_mutex_t mutex; // mutex for this document
    pthread_cond_t cond;   // signals part completion
    int cur_part;          // the index of the current chunk
    int chunk_count;
    char **keys;           // an array of URIs
    char *status;          // the status of each stream
    FILE **streams;        // an array of parts, some in progress (NULL)
    int htl;
    int threads;
    int activethreads;
} fcp_document;

// return a pointer to a new, nulled fcp_metadata
fcp_metadata * fcp_metadata_new ();

// return a pointer to a new, nulled fcp_document
fcp_document * fcp_document_new ();

// start a request. returns length of data, or error (<0)
// if !m, metadata will not be saved for later use.
int fcp_request (fcp_metadata *m, fcp_document *d, char *uri, int htl,
	int threads);

// read up to length bytes into buf. returns bytes read, or error (<0)
int fcp_read (fcp_document *d, char *buf, int length);

// close and free a fcp_document
int fcp_close (fcp_document *d);

// insert length bytes from in. returns 0 on success, or error (<0)
int fcp_insert (fcp_metadata *m, char *document_name, FILE *in, int length,
	int htl, int threads);

// insert a single-part file. returns 0 on success, or error (<0)
// type may be DATA or CONTROL
// updates uri with final uri
int fcp_insert_raw (FILE *in, char *uri, int length, int type, int htl);

// create a redirect
int fcp_redirect (fcp_metadata *m, char *document_name,	char *target_uri);

// create a date-based redirect
int fcp_date_redirect (fcp_metadata *m, char *document_name, char *predate,
	char *postdate, long baseline, long increment);

// inserts metadata
// updates uri with final uri
int fcp_metadata_insert (fcp_metadata *m, char *uri, int htl);

// free fcp_metadata
void fcp_metadata_free (fcp_metadata *m);

// return descriptive string for status code
char * fcp_status_to_string (int code);

