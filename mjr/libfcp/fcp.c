#include <fcp.h>

FILE * fcp_connect ();
int fcp_get (FILE *data, int *len, int *type, char *uri, int htl);
int splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads);
void * big_brother (void *arg);
void * winston_smith (void *arg);
int fcp_internal_close (fcp_document *d);
int calc_partsize (int length);
int calc_partcount (int partsize, int length);
void fcp_internal_free (fcp_metadata *m);
void * insert_thread (void *arg);

int parse_control_doc (fcp_metadata *m, FILE *data);
int parse_redirect (fcp_metadata *m, FILE *data);
int parse_date_redirect (fcp_metadata *m, FILE *data);
int parse_splitfile (fcp_metadata *m, FILE *data);
int parse_info (fcp_metadata *m, FILE *data);
int parse_unknown (fcp_metadata *m, FILE *data);

fcp_metadata *
fcp_metadata_new ()
{
    fcp_metadata *m = malloc(sizeof(fcp_metadata));
    m->uri = NULL;
    m->r = NULL;
    m->dr = NULL;
    m->sf = NULL;
    m->i = NULL;
    m->r_count = 0;
    m->dr_count = 0;
    m->sf_count = 0;
    m->i_count = 0;
    return m;
}

fcp_document *
fcp_document_new ()
{
    fcp_document *d = malloc(sizeof(fcp_document));
    pthread_mutex_init(&d->mutex, NULL);
    pthread_cond_init(&d->cond, NULL);
    d->closed = 1;
    d->cur_part = 0;
    d->chunk_count = 0;
    d->keys = NULL;
    d->status = NULL;
    d->streams = NULL;
    d->document_name = NULL;
    return d;
}


int
fcp_request (fcp_metadata *m, fcp_document *d, char *uri, int htl,
	     int threads)
{
    if (m && m->uri && strncmp(m->uri, uri, strlen(m->uri)) == 0) {
	int i;
	char *docname = strstr(uri, "//");
	if (!docname) return FCP_INVALID_URI;
	docname += 2;
	for (i = 0 ; i < m->r_count ; i++)
	    if (strcmp(docname, m->r[i]->document_name) == 0)
	        return fcp_request(m, d, m->r[i]->target_uri, htl,
			           threads);
	for (i = 0 ; i < m->dr_count ; i++)
	    if (strcmp(docname, m->dr[i]->document_name) == 0) {
		char target[512];
		fcp_calc_dbr(target, m->dr[i]->predate, m->dr[i]->postdate,
			     m->dr[i]->baseline, m->dr[i]->increment, 0);
		return fcp_request(m, d, target, htl, threads);
	    }
        for (i = 0 ; i < m->sf_count ; i++)
	    if (strcmp(docname, m->sf[i]->document_name) == 0)
	        return splitfile_get(d, m->sf[i], htl, threads);
	return FCP_PART_NOT_FOUND;
    } else {
	char nuri[strlen(uri)], *docname = strstr(uri, "//");
	int status, len, type;
	FILE *data = tmpfile();
	if (!data) return FCP_IO_ERROR;
	if (docname) {
	    int n = strlen(uri) - strlen(docname);
	    strncpy(nuri, uri, n);
	    nuri[n] = '\0';
	    docname += 2;
	} else strcpy(nuri, uri);
	status = fcp_get(data, &len, &type, nuri, htl);
	if (status != FCP_SUCCESS) return status;
	if (type == FCP_CONTROL) { // read the control doc and recurse
	    fcp_metadata *m2 = fcp_metadata_new();
	    status = parse_control_doc(m2, data);
	    if (status != FCP_SUCCESS) goto fail;
	    m2->uri = strdup(nuri);
	    status = fcp_request(m2, d, uri, htl, threads);
	    if (status < 0) goto fail;
	    if (m) {
		fcp_internal_free(m);
		*m = *m2;
		free(m2);
	    } else fcp_metadata_free(m2);
	    fclose(data);
	    return status;
fail:	    fcp_metadata_free(m2);
	    fclose(data);
	    return status;
	} else { // only one part, not a splitfile
	    pthread_mutex_init(&d->mutex, NULL);
	    pthread_cond_init(&d->cond, NULL);
	    d->closed = 0;
	    d->cur_part = 0;
	    d->chunk_count = 1;
	    d->keys = malloc(sizeof(char *));
	    d->keys[0] = strdup(nuri);
	    d->status = malloc(1);
	    d->status[0] = FCP_SUCCESS;
	    d->streams = malloc(sizeof(FILE *));
	    d->streams[0] = data;
            d->document_name = strdup("");
	    return len;
	}
    }
}

int
splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads)
{
    int i;
    pthread_t thread;
    d->document_name = strdup(sf->document_name);
    d->closed = 0;
    d->cur_part = 0;
    d->htl = htl;
    d->threads = threads;
    d->activethreads = 0;
    d->chunk_count = sf->chunk_count;
    d->status = malloc(d->chunk_count);
    d->keys = calloc(d->chunk_count, sizeof(char *));
    d->streams = calloc(d->chunk_count, sizeof(FILE *));
    for (i = 0 ; i < d->chunk_count ; i++) {
	d->status[i] = 1;
	d->keys[i] = strdup(sf->keys[i]);
	d->streams[i] = tmpfile();
    }
    pthread_create(&thread, NULL, big_brother, (void *) d);
    return sf->filesize;
}

typedef struct {
    int part;
    fcp_document *d;
} rtargs;

void *
big_brother (void *arg)
{
    int i;
    pthread_t thread;
    fcp_document *d = (fcp_document *) arg;
    for (i = 0 ; i < d->chunk_count ; i++) {
	rtargs *r = malloc(sizeof(rtargs));
	r->part = i;
	r->d = d;
	pthread_create(&thread, NULL, winston_smith, (void *) r);
	d->activethreads++;
	while (d->activethreads == d->threads) {
	    pthread_mutex_lock(&d->mutex);
	    pthread_cond_wait(&d->cond, &d->mutex);
	    if (d->closed) { // clean up, exit
		fcp_internal_close(d);
		pthread_exit(NULL);
	    }
	    pthread_mutex_unlock(&d->mutex);
	}   
    }
    while (d->activethreads) {
	pthread_mutex_lock(&d->mutex);
	pthread_cond_wait(&d->cond, &d->mutex);
	if (d->closed) { // clean up, exit
	    fcp_internal_close(d);
	    pthread_exit(NULL);
	}
	pthread_mutex_unlock(&d->mutex);
    }
    pthread_exit(NULL);
}

void *
winston_smith (void *arg)
{
    int status = 1, n = 3, len, type;
    rtargs *r = (rtargs *) arg;
    while (n-- && status != FCP_SUCCESS) {
	rewind(r->d->streams[r->part]);
	status = fcp_get(r->d->streams[r->part], &len, &type,
			 r->d->keys[r->part], r->d->htl);
	if (r->d->closed) status = FCP_SUCCESS;
    }
    pthread_mutex_lock(&r->d->mutex);
    r->d->status[r->part] = status;
    r->d->activethreads--;
    pthread_cond_broadcast(&r->d->cond);
    pthread_mutex_unlock(&r->d->mutex);
    pthread_exit(NULL);
}

int
fcp_close (fcp_document *d)
{
    if (d->closed) return FCP_SUCCESS;
    pthread_mutex_lock(&d->mutex);
    d->closed = 1;
    pthread_cond_broadcast(&d->cond);
    pthread_mutex_unlock(&d->mutex);
    return FCP_SUCCESS;
}

int
fcp_internal_close (fcp_document *d)
{
    int i;
    if (!d->status) goto end;
    for (i = 0 ; i < d->chunk_count ; i++) {
	if (d->status[i] > 0)
	    pthread_cond_wait(&d->cond, &d->mutex);
	free(d->keys[i]);
	fclose(d->streams[i]);
    }
    if (d->status) free(d->status);
    if (d->keys) free(d->keys);
    if (d->streams) free(d->streams);
    if (d->document_name) free(d->document_name);
end:
    free(d);
    return FCP_SUCCESS;
}

int
fcp_read (fcp_document *d, char *buf, int length)
{
    int n;
    pthread_mutex_lock(&d->mutex);
    while (d->status[d->cur_part] > 0)
	pthread_cond_wait(&d->cond, &d->mutex);
    pthread_mutex_unlock(&d->mutex);
    if (d->status[d->cur_part] < 0) return d->status[d->cur_part];
    n = fread(buf, 1, length, d->streams[d->cur_part]);
    if (n != length) {
	if (++d->cur_part == d->chunk_count) return n;
	pthread_mutex_lock(&d->mutex);
	while (d->status[d->cur_part] > 0)
	    pthread_cond_wait(&d->cond, &d->mutex);
	pthread_mutex_unlock(&d->mutex);
	if (d->status[d->cur_part] < 0) return d->status[d->cur_part];
	n += fread(&buf[n], 1, length - n, d->streams[d->cur_part]);
    }
    return n;
}

int
fcp_get (FILE *data, int *len, int *type, char *uri, int htl)
{
    int i, n, status;
    long dlen = -1, mlen = 0;
    char buf[1024], name[512], val[512];
    FILE *sock = fcp_connect();
    if (!sock) return FCP_CONNECT_FAILED;
    fprintf(sock, "ClientGet\n"
	          "URI=%s\n"
		  "HopsToLive=%x\n"
		  "EndMessage\n",
	    	  uri, htl);
    if (fscanf(sock, "%s\n", buf) != 1)
	return FCP_IO_ERROR;
    if (strcmp(buf, "DataFound") != 0) {
	if (strcmp(buf, "URIError") == 0)
	    return FCP_INVALID_URI;
    	if (strcmp(buf, "RouteNotFound") == 0)
	    return FCP_ROUTE_NOT_FOUND;
	if (strcmp(buf, "DataNotFound") == 0)
	    return FCP_DATA_NOT_FOUND;
	return FCP_INVALID_MESSAGE;
    }
    while (fgets(buf, 512, sock)) {
	status = sscanf(buf, "%[^=]=%s", name, val);
        if (status != 2) break;
        if (strcmp(name, "DataLength") == 0)
	    dlen = strtol(val, NULL, 16);
        else if (strcmp(name, "MetadataLength") == 0)
            mlen = strtol(val, NULL, 16);
        else break;
    }
    if (dlen < 0 || mlen < 0 || mlen > dlen
	    || strncmp(buf, "EndMessage", 10) != 0)
	return FCP_INVALID_MESSAGE;
    *len = dlen;
    *type = mlen == dlen ? FCP_CONTROL : FCP_DATA;
    while (dlen) {
	status = fscanf(sock, "DataChunk\nLength=%x\nData", &n);
	fgetc(sock);
	if (status != 1 || n < 0) return FCP_IO_ERROR;
	dlen -= n;
	while (n) {
	    n -= (i = fread(buf, 1, n > 1024 ? 1024 : n, sock));
	    if (!i) return FCP_IO_ERROR;
	    fwrite(buf, 1, i, data);
	}
    }
    rewind(data);
    return FCP_SUCCESS;
}

int
parse_control_doc (fcp_metadata *m, FILE *data)
{
    int status;
    char line[512];
    rewind(data);
    while (fgets(line, 512, data)) {
	if (strcmp(line, "\n") == 0 || strcmp(line, "\r\n") == 0)
	    continue;
	if (strncmp(line, "Redirect", 8) == 0) {
	    status = parse_redirect(m, data);
	    if (status != FCP_SUCCESS) return status;
	} else if (strncmp(line, "DateRedirect", 12) == 0) {
	    status = parse_date_redirect(m, data);
	    if (status != FCP_SUCCESS) return status;
	} else if (strncmp(line, "SplitFile", 9) == 0) {
	    status = parse_splitfile(m, data);
	    if (status != FCP_SUCCESS) return status;
	} else if (strncmp(line, "Info", 4) == 0) {
	    status = parse_info(m, data);
	    if (status != FCP_SUCCESS) return status;
	} else {
	    status = parse_unknown(m, data);
	    if (status != FCP_SUCCESS) return status;
	}
    }
    return FCP_SUCCESS;
}

int
parse_redirect (fcp_metadata *m, FILE *data)
{
    char line[512], name[512], val[512];
    int status, n = m->r_count++;
    m->r = realloc(m->r, sizeof(redirect *) * m->r_count);
    m->r[n] = malloc(sizeof(redirect));
    m->r[n]->document_name = NULL;
    m->r[n]->target_uri = NULL;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++;
	    val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0)
	    m->r[n]->document_name = strdup(val);
	else if (strcmp(name, "Target") == 0)
	    m->r[n]->target_uri = strdup(val);
    }
    if (!m->r[n]->document_name || !m->r[n]->target_uri
	    || strncmp(line, "End", 3) != 0)
	return FCP_INVALID_METADATA;
    return FCP_SUCCESS;
}

int
parse_date_redirect (fcp_metadata *m, FILE *data)
{
    char line[512], name[512], val[512];
    int status, n = m->dr_count++;
    m->dr = realloc(m->dr, sizeof(date_redirect *) * m->dr_count);
    m->dr[n] = malloc(sizeof(date_redirect));
    m->dr[n]->document_name = NULL;
    m->dr[n]->predate = NULL;
    m->dr[n]->postdate = NULL;
    m->dr[n]->baseline = -1;
    m->dr[n]->increment = 86400;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++;
	    val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0)
	    m->dr[n]->document_name = strdup(val);
	else if (strcmp(name, "Predate") == 0)
	    m->dr[n]->predate = strdup(val);
	else if (strcmp(name, "Postdate") == 0)
	    m->dr[n]->postdate = strdup(val);
	else if (strcmp(name, "Baseline") == 0)
	    m->dr[n]->baseline = strtol(val, NULL, 16);
	else if (strcmp(name, "Increment") == 0)
	    m->dr[n]->increment = strtol(val, NULL, 16);
    }
    if (!m->dr[n]->document_name || !m->dr[n]->predate
	    || !m->dr[n]->postdate || m->dr[n]->baseline < 0
	    || strncmp(line, "End", 3) != 0)
	return FCP_INVALID_METADATA;
    return FCP_SUCCESS;
}

int
parse_splitfile (fcp_metadata *m, FILE *data)
{
    char line[512], name[512], val[512];
    int status, n = m->sf_count++;
    m->sf = realloc(m->sf, sizeof(splitfile *) * m->sf_count);
    m->sf[n] = malloc(sizeof(splitfile));
    m->sf[n]->document_name = NULL;
    m->sf[n]->filesize = 0;
    m->sf[n]->chunk_count = 0;
    m->sf[n]->keys = NULL;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++;
	    val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0)
	    m->sf[n]->document_name = strdup(val);
	else if (strcmp(name, "FileSize") == 0)
	    m->sf[n]->filesize = strtol(val, NULL, 16);
	else if (strcmp(name, "Chunks") == 0) {
	    status = strtol(val, NULL, 16);
	    if (m->sf[n]->keys || status < 0)
		return FCP_INVALID_METADATA;
	    m->sf[n]->keys = calloc(status, sizeof(char *));
	    m->sf[n]->chunk_count = status;
	} else if (strncmp(name, "Chunk.", 6) == 0) {
	    status = strtol(&name[6], NULL, 16);
	    if (!m->sf[n]->keys || status < 0
		    || status >= m->sf[n]->chunk_count)
		return FCP_INVALID_METADATA;
	    m->sf[n]->keys[status] = strdup(val);
	}
    }
    if (!m->sf[n]->document_name || m->sf[n]->filesize <= 0
	    || m->sf[n]->chunk_count <= 0 || strncmp(line, "End", 3) != 0)
	return FCP_INVALID_METADATA;
    return FCP_SUCCESS;
}

int
parse_info (fcp_metadata *m, FILE *data)
{
    char line[512], name[512], val[512];
    int status, x, n = m->i_count++;
    m->i = realloc(m->i, sizeof(info *) * m->i_count);
    m->i[n] = malloc(sizeof(info));
    m->i[n]->document_name = NULL;
    m->i[n]->f_count = 0;
    m->i[n]->fields = NULL;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s\n", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++;
	    val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0) {
	    m->i[n]->document_name = strdup(val);
	    continue;
	}
	x = m->i[n]->f_count++;
	m->i[n]->fields = realloc(m->i[n]->fields,
		sizeof(char *) * m->i[n]->f_count);
	m->i[n]->fields[x] = malloc(sizeof(char *) * 2);
	m->i[n]->fields[x][0] = strdup(name);
	m->i[n]->fields[x][1] = strdup(val);
    }
    if (!m->i[n]->document_name || strncmp(line, "End", 3) != 0)
	return FCP_INVALID_METADATA;
    return FCP_SUCCESS;
}

int
parse_unknown (fcp_metadata *m, FILE *data)
{
    char line[512], name[512], val[512];
    int status;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s\n", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++;
	    val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "Importance") == 0)
	    if (strcmp(val, "Required") == 0)
		return FCP_INVALID_METADATA;
    }
    if (strncmp(line, "End", 3) != 0)
	return FCP_INVALID_METADATA;
    return FCP_SUCCESS;
}

typedef struct {
    pthread_cond_t *cond;
    pthread_mutex_t *mutex;
    int *activethreads;
    int *error;
    int length;
    FILE *data;
    char *uri;
    int htl;
} intargs;

int
fcp_insert (fcp_metadata *m, char *document_name, FILE *in, int length,
	        int htl, int threads)
{
    int status;
    if (length < 128 * 1024) {
	char uri[128];
	strcpy(uri, "freenet:CHK@");
	status = fcp_insert_raw(in, NULL, uri, length, FCP_DATA, htl);
	if (status != FCP_SUCCESS) return status;
	status = fcp_redirect(m, document_name, uri);
	return status;
    } else {
	int partsize = calc_partsize(length/1024) * 1024;
	int partcount = calc_partcount(partsize, length);
	int n, t, r, s, activethreads = 0, error = 0, len = length;
	char buf[1024], keys[partcount][128];
	FILE *parts[partcount];
	pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
	pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
	for (t = 0 ; t < partcount ; t++) {
	    pthread_t thread;
	    intargs *i = malloc(sizeof(intargs));
	    len -= (n = len > partsize ? partsize : len);
	    parts[t] = tmpfile();
	    i->cond = &cond;
	    i->mutex = &mutex;
	    i->activethreads = &activethreads;
	    i->error = &error;
	    i->length = n;
	    i->data = parts[t];
	    i->uri = keys[t];
	    i->htl = htl;
	    strcpy(i->uri, "freenet:CHK@");
	    while (n) {
		n -= (r = n > 1024 ? 1024 : n);
		s = fread(buf, 1, r, in);
		if (s != r) return FCP_IO_ERROR;
		s = fwrite(buf, 1, r, parts[t]);
		if (s != r) return FCP_IO_ERROR;
	    }
	    rewind(parts[t]);
	    pthread_create(&thread, NULL, insert_thread, (void *) i);
	    if (++activethreads == threads) {
		pthread_mutex_lock(&mutex);
		pthread_cond_wait(&cond, &mutex);
		pthread_mutex_unlock(&mutex);
	    }
	    if (error) return error;
	}
	while (activethreads) {
	    pthread_mutex_lock(&mutex);
	    pthread_cond_wait(&cond, &mutex);
	    pthread_mutex_unlock(&mutex);
	    if (error) return error;
	}
        n = m->sf_count++;
        m->sf = realloc(m->sf, sizeof(splitfile *) * m->sf_count);
        m->sf[n] = malloc(sizeof(splitfile));
        m->sf[n]->document_name = strdup(document_name);
        m->sf[n]->filesize = length;
        m->sf[n]->chunk_count = partcount;
        m->sf[n]->keys = calloc(partcount, sizeof(FILE *));
	for (t = 0 ; t < partcount ; t++)
	    m->sf[n]->keys[t] = strdup(keys[t]);
	return FCP_SUCCESS;
    }
}

void *
insert_thread (void *arg)
{
    intargs *i = (intargs *) arg;
    int r = 3, status = -1;
    while (r-- && status != FCP_SUCCESS && status != FCP_KEY_COLLISION) {
	if (*i->error) goto end;
	rewind(i->data);
	status = fcp_insert_raw(i->data, NULL, i->uri, i->length,
		                FCP_DATA, i->htl);
    }
    pthread_mutex_lock(i->mutex);
    if (status != FCP_SUCCESS && status != FCP_KEY_COLLISION)
	*i->error = status;
    (*i->activethreads)--;
    pthread_cond_broadcast(i->cond);
    pthread_mutex_unlock(i->mutex);
end:
    fclose(i->data);
    free(i);
    pthread_exit(NULL);
}

int
fcp_insert_raw (FILE *in, char *metadata, char *uri, int length, int type,
	        int htl)
{
    char buf[1024], rhead[256], ruri[256];
    FILE *sock = fcp_connect();
    if (!sock) return FCP_CONNECT_FAILED;
    fprintf(sock, "ClientPut\n"
	          "HopsToLive=%x\n"
		  "URI=%s\n"
		  "DataLength=%x\n"
		  "MetadataLength=%x\n"
		  "Data\n",
		  htl, uri, length,
		  type == FCP_DATA
		      ? (metadata ? strlen(metadata) : 0)
		      : length);
    if (type == FCP_DATA && metadata) fputs(metadata);
    while (length) {
	int n = length > 1024 ? 1024 : length;
	if (fread(buf, 1, n, in) != n) goto ioerror;
	if (fwrite(buf, 1, n, sock) != n) goto ioerror;
	length -= n;
    }
    if (fscanf(sock, "%s\n", rhead) != 1) goto ioerror;
    ruri[0] = 0;
    while (fgets(buf, 1024, sock)) {
	if (strncmp(buf, "EndMessage", 10) == 0) break;
	sscanf(buf, "URI=%s", ruri);
    }
    if (strncmp(buf, "EndMessage", 10) != 0) goto ioerror;
    fclose(sock);
    if (strcmp(rhead, "Success") == 0 && strlen(ruri)) {
	strcpy(uri, ruri);
	return FCP_SUCCESS;
    }
    if (strcmp(rhead, "KeyCollision") == 0 && strlen(ruri)) {
	strcpy(uri, ruri);
	return FCP_KEY_COLLISION;
    }
    if (strcmp(rhead, "RouteNotFound") == 0)
	return FCP_ROUTE_NOT_FOUND;
    if (strcmp(rhead, "DataRejected") == 0)
	return FCP_DATA_REJECTED;
    return FCP_INVALID_MESSAGE;
ioerror:
    fclose(sock);
    return FCP_IO_ERROR;
}

int
calc_partsize (int length)
{
    int n = 0, tmp = length;
    while (tmp) {
	tmp >>= 1;
	n++;
    }
    if (length > tmp) n++;
    return (8 << ((n-1)/2));
}

int
calc_partcount (int partsize, int length)
{
    int pc = length / partsize;
    if (pc * partsize < length) pc++;
    return pc;
}

int
fcp_redirect (fcp_metadata *m, char *document_name, char *target_uri)
{
    int n = m->r_count++;
    m->r = realloc(m->r, sizeof(redirect *) * m->r_count);
    m->r[n] = malloc(sizeof(redirect));
    m->r[n]->document_name = strdup(document_name);
    m->r[n]->target_uri = strdup(target_uri);
    return FCP_SUCCESS;    
}

int
fcp_date_redirect (fcp_metadata *m, char *document_name, char *predate,
	char *postdate, long baseline, long increment)
{
    int n = m->dr_count++;
    m->dr = realloc(m->dr, sizeof(date_redirect *) * m->dr_count);
    m->dr[n] = malloc(sizeof(date_redirect));
    m->dr[n]->document_name = strdup(document_name);
    m->dr[n]->predate = strdup(predate);
    m->dr[n]->postdate = strdup(postdate);
    m->dr[n]->baseline = baseline;
    m->dr[n]->increment = increment;
    return FCP_SUCCESS;
}

int
fcp_info (fcp_metadata *m, char *document_name, char *name, char *value)
{
    int i, n;
    for (i = 0 ; i < m->i_count ; i++)
        if (strcmp(m->i[i]->document_name, document_name) == 0) {
	    n = m->i[i]->f_count++;
	    m->i[i]->fields = realloc(m->i[i]->fields, sizeof(char *)
				      * m->i[i]->f_count);
	    m->i[i]->fields[n] = malloc(sizeof(char *) * 2);
	    m->i[i]->fields[n][0] = strdup(name);
	    m->i[i]->fields[n][1] = strdup(value);
	    return FCP_SUCCESS;
	}
    n = m->i_count++;
    m->i = realloc(m->i, sizeof(info *) * m->i_count);
    m->i[n] = malloc(sizeof(info));
    m->i[n]->document_name = strdup(document_name);
    m->i[n]->f_count = 0;
    m->i[n]->fields = NULL;
    return fcp_info(m, document_name, name, value);
}

int
fcp_metadata_insert (fcp_metadata *m, char *uri, int htl)
{
    int i, j;
    FILE *data = tmpfile();
    for (i = 0 ; i < m->r_count ; i++)
        fprintf(data, "Redirect\n"
	    	      "DocumentName=%s\n"
		      "Target=%s\n"
		      "EndPart\n",
		      m->r[i]->document_name,
		      m->r[i]->target_uri);
    for (i = 0 ; i < m->dr_count ; i++)
        fprintf(data, "DateRedirect\n"
	    	      "DocumentName=%s\n"
		      "Predate=%s\n"
		      "Postdate=%s\n"
		      "Baseline=%lx\n"
		      "Increment=%lx\n"
		      "EndPart\n",
		      m->dr[i]->document_name,
		      m->dr[i]->predate,
		      m->dr[i]->postdate,
		      m->dr[i]->baseline,
		      m->dr[i]->increment);
    for (i = 0 ; i < m->sf_count ; i++) {
        fprintf(data, "SplitFile\n"
		      "DocumentName=%s\n"
		      "FileSize=%x\n"
		      "Chunks=%x\n",
		      m->sf[i]->document_name,
		      m->sf[i]->filesize,
		      m->sf[i]->chunk_count);
	for (j = 0 ; j < m->sf[i]->chunk_count ; j++)
	    fprintf(data, "Chunk.%x=%s\n", j, m->sf[i]->keys[j]);
	fprintf(data, "EndPart\n");
    }
    for (i = 0 ; i < m->i_count ; i++) {
	fprintf(data, "Info\n"
		      "DocumentName=%s\n",
		      m->i[i]->document_name);
	for (j = 0 ; j < m->i[i]->f_count ; j++)
	    fprintf(data, "%s=%s\n", m->i[i]->fields[j][0],
	            m->i[i]->fields[j][1]);
	fprintf(data, "EndPart\n");
    }
    j = ftell(data) - 4;
    fseek(data, -5, SEEK_CUR);
    fprintf(data, "\n");
    rewind(data);
    return fcp_insert_raw(data, NULL, uri, j, FCP_CONTROL, htl);
}

char *
fcp_lookup (fcp_metadata *m, fcp_document *d, char *field)
{
    int i, j;
    if (!d->document_name) return NULL;
    for (i = 0 ; i < m->i_count ; i++)
	if (strcmp(m->i[i]->document_name, d->document_name) == 0)
	    for (j = 0 ; j < m->i[i]->f_count ; j++)
		if (strcmp(m->i[i]->fields[j][0], field) == 0)
		    return m->i[i]->fields[j][1];
    return NULL;
}

int
fcp_calc_dbr (char *uri, char *predate, char *postdate, long baseline,
	      int increment, int offset)
{
    long a = time(NULL) - baseline;
    long b = baseline + a - (a % increment) + (offset * increment);
    sprintf(uri, "%s%lx%s", predate, b, postdate);
    return FCP_SUCCESS;
}

void
fcp_internal_free (fcp_metadata *m)
{
    int i, j;
    if (m->uri) free(m->uri);
    for (i = 0 ; i < m->r_count ; i++) {
	free(m->r[i]->document_name);
	free(m->r[i]->target_uri);
	free(m->r[i]);
    }
    if (m->r_count) free(m->r);
    for (i = 0 ; i < m->dr_count ; i++) {
    	free(m->dr[i]->document_name);
	free(m->dr[i]->predate);
	free(m->dr[i]->postdate);
	free(m->dr[i]);
    }
    if (m->dr_count) free(m->dr);
    for (i = 0 ; i < m->sf_count ; i++) {
	free(m->sf[i]->document_name);
	for (j = 0 ; j < m->sf[i]->chunk_count ; j++)
	    free(m->sf[i]->keys[j]);
	free(m->sf[i]->keys);
    }
    if (m->sf_count) free(m->sf);
    for (i = 0 ; i < m->i_count ; i++) {
	free(m->i[i]->document_name);
	for (j = 0 ; j < m->i[i]->f_count ; j++) {
	    free(m->i[i]->fields[j][0]);
	    free(m->i[i]->fields[j][1]);
	    free(m->i[i]->fields[j]);
	}
	free(m->i[i]);
    }
    if (m->i_count) free(m->i);
}

void
fcp_metadata_free (fcp_metadata *m)
{
    fcp_internal_free(m);
    free(m);
}

FILE *
fcp_connect ()
{
    struct in_addr addr;
    struct sockaddr_in address;
    struct servent *serv;
    int connected_socket, connected;
    FILE *sock;
    serv = getservbyname("fcp", "tcp");
    addr.s_addr = inet_addr("127.0.0.1");
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = serv ? (serv->s_port) : htons(8082);
    address.sin_addr.s_addr = addr.s_addr;
    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket,
	    (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) return NULL;
    sock = fdopen(connected_socket, "w+");
    if (!sock) return NULL;
    fwrite("\0\0\0\2", 4, 1, sock);
    return sock;
}

char *
fcp_status_to_string (int code)
{
    switch (code) {
	case FCP_CONNECT_FAILED:
	    return "can't connect to Freenet node";
	case FCP_INVALID_URI:
	    return "invalid URI";
	case FCP_PART_NOT_FOUND:
            return "document not found in metadata";
	case FCP_INVALID_METADATA:
            return "invalid metadata";
	case FCP_REQUEST_FAILED:
            return "request failed";
	case FCP_IO_ERROR:
            return "input/output error";
	case FCP_KEY_COLLISION:
	    return "key collision";
	case FCP_INVALID_MESSAGE:
	    return "invalid FCP message";
	case FCP_ROUTE_NOT_FOUND:
	    return "route not found";
	case FCP_DATA_NOT_FOUND:
	    return "data not found";
	case FCP_DATA_REJECTED:
	    return "data rejected";
	default:
            return "unknown error";
    }
}

