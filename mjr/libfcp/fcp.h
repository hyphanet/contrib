#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#define FCP_SUCCESS		0
#define FCP_CONNECT_FAILED	-1
#define FCP_INVALID_URI		-2
#define FCP_PART_NOT_FOUND	-3
#define FCP_INVALID_METADATA    -4
#define FCP_REQUEST_FAILED	-5
#define FCP_READ_FAILED		-6

#define FCP_FUCK		-999

typedef struct {
    char *document_name;   // to which document this part applies
    char *target_uri;      // the target of this redirect
} redirect;

typedef struct {
    char *document_name;   // to which document this part applies
    char *predate;         // the portion of the key URI before the date
    char *postdate;        // the portion of the key URI after the date
    long baseline;         // the baseline, in seconds since the epoch
    int increment;         // the increment, in seconds
} date_redirect;

typedef struct {
    char *document_name;   // to which document this part applies
    int filesize;          // the final size of the original file
    int chunk_count;       // the number of data chunks
    char **chunks;         // an array of /chunk_count/ Freenet URIs
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
    int error;             // if this is !0, an error occurred - abort
    int cur_part;          // the index of the current part
    int p_count;
    FILE **parts;          // an array of parts, some in progress (NULL)
    int threads;           // maximum swarming threads
    int htl;               // hops to live
} fcp_document;

// return a pointer to a new, nulled fcp_metadata
fcp_metadata * fcp_metadata_new ();

// start a request. returns length of data, or error (<0)
// if !m, metadata will not be saved for later use.
int fcp_request (fcp_metadata *m, fcp_document *s,
	char *uri, int htl, int threads);

// read up to length bytes into buf. returns bytes read, or error (<0)
int fcp_read (fcp_document *d, char *buf, int length);

// insert length bytes from in. returns 0 on success, or error (<0)
// uri is updated with the final URI.
int fcp_insert (FILE *in, int length, char *uri, int htl, int threads);

// close and free a fcp_document
int fcp_close (fcp_document *d);

// free fcp_metadata
void fcp_metadata_free (fcp_metadata *m);

