#include "fcp.h"

FILE * fcp_connect ();
int fcp_get (FILE *data, int *len, int *type, char *uri, int htl);
int splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads);
void * big_brother (void *arg);
void * winston_smith (void *arg);
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
    m->uri = NULL; m->r = NULL; m->dr = NULL; m->sf = NULL; m->i = NULL;
    m->r_count = 0; m->dr_count = 0; m->sf_count = 0; m->i_count = 0;
    return m;
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
	        return fcp_request(NULL, d, m->r[i]->target_uri, htl,
			threads);
	for (i = 0 ; i < m->dr_count ; i++)
	    if (strcmp(docname, m->dr[i]->document_name) == 0) {
	        long a = time(NULL) - m->dr[i]->baseline;
		long b = m->dr[i]->baseline + a - (a % m->dr[i]->increment);
		char target[512];
		sprintf(target, "%s%lx%s", m->dr[i]->predate, b,
			m->dr[i]->postdate);
		return fcp_request(NULL, d, target, htl, threads);
	    }
        for (i = 0 ; i < m->sf_count ; i++)
	    if (strcmp(docname, m->sf[i]->document_name) == 0)
	        return splitfile_get(d, m->sf[i], htl, threads);
	return FCP_PART_NOT_FOUND;
    } else {
	char nuri[strlen(uri)], *docname = strstr(uri, "//");
	int status, len, type;
	FILE *data = tmpfile();
	if (!data) return FCP_FUCK;
	if (docname) {
	    int n = strlen(uri) - strlen(docname);
	    strncpy(nuri, uri, n); nuri[n] = '\0';
	    docname += 2;
	} else strcpy(nuri, uri);
	status = fcp_get(data, &len, &type, nuri, htl);
	if (status != FCP_SUCCESS) return status;
	if (type == CONTROL) { // read the control doc and recurse
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
	    d->cur_part = 0;
	    d->p_count = 1;
	    d->chunks = malloc(sizeof(char *));
	    d->chunks[0] = strdup(nuri);
	    d->status = malloc(1);
	    d->status[0] = FCP_SUCCESS;
	    d->streams = malloc(sizeof(FILE *));
	    d->streams[0] = data;
            return len;
	}
    }
}

int
splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads)
{
    int i;
    pthread_t thread;
    pthread_mutex_init(&d->mutex, NULL);
    pthread_cond_init(&d->cond, NULL);
    d->cur_part = 0; d->htl = htl;
    d->threads = threads; d->activethreads = 0;
    d->p_count = sf->chunk_count;
    d->status = malloc(d->p_count);
    d->chunks = calloc(d->p_count, sizeof(char *));
    d->streams = calloc(d->p_count, sizeof(FILE *));
    for (i = 0 ; i < d->p_count ; i++) {
	d->status[i] = 1;
	d->chunks[i] = strdup(sf->chunks[i]);
	d->streams[i] = NULL;
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
    for (i = 0 ; i < d->p_count ; i++) {
	rtargs *r = malloc(sizeof(rtargs));
	r->part = i; r->d = d;
	pthread_create(&thread, NULL, winston_smith, (void *) r);
	d->activethreads++;
	while (d->activethreads == d->threads) usleep(10);
    }
    pthread_exit(NULL);
}

void *
winston_smith (void *arg)
{
    int n = 3, len, type;
    rtargs *r = (rtargs *) arg;
    r->d->streams[r->part] = tmpfile();
    while (n-- && r->d->status[r->part] != FCP_SUCCESS) {
	rewind(r->d->streams[r->part]);
	r->d->status[r->part] = fcp_get(r->d->streams[r->part], &len, &type,
		r->d->chunks[r->part], r->d->htl);
    }
    pthread_mutex_lock(&r->d->mutex);
    r->d->activethreads--;
    pthread_cond_broadcast(&r->d->cond);
    pthread_mutex_unlock(&r->d->mutex);
    pthread_exit(NULL);
}

int
fcp_close (fcp_document *d)
{
    int i;
    free(d->status);
    for (i = 0 ; i < d->p_count ; i++) {
	if (d->chunks[i]) free(d->chunks[i]);
	if (d->streams[i]) free(d->streams[i]);
    }
    free(d->chunks);
    free(d->streams);
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
	fclose(d->streams[d->cur_part++]);
	if (d->cur_part == d->p_count) return n;
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
    fgets(buf, 512, sock);
    if (strncmp(buf, "DataFound", 9) != 0) {
	if (strncmp(buf, "URIError", 8) == 0)
	    return FCP_INVALID_URI;
    	else
	    return FCP_REQUEST_FAILED;
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
    if (dlen < 0 || (mlen && mlen != dlen)
	    || strncmp(buf, "EndMessage", 10) != 0)
	return FCP_IO_ERROR;
    *len = dlen;
    *type = mlen == dlen ? CONTROL : DATA;
    while (dlen) {
	status = fscanf(sock, "DataChunk\nLength=%x\nData", &n);
	fgetc(sock);
	if (status != 1 || n < 0) return FCP_IO_ERROR;
	dlen -= n;
	while (n) {
	    i = fread(buf, 1, n > 1024 ? 1024 : n, sock);
	    if (!i) return FCP_IO_ERROR;
	    fwrite(buf, 1, i, data);
	    n -= i;
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
	    status++; val[0] = '\0';
	}
	if (status != 2) break;
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0)
	    m->r[n]->document_name = strdup(val);
	else if (strcmp(name, "Target") == 0)
	    m->r[n]->target_uri = strdup(val);
	else break;
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
    m->dr[n]->predate = NULL; m->dr[n]->postdate = NULL;
    m->dr[n]->baseline = -1; m->dr[n]->increment = 86400;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++; val[0] = '\0';
	}
	if (status != 2) break;
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
	else break;
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
    m->sf[n]->chunks = NULL;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status == 1 && (line[strlen(line)-2] == '='
		    || line[strlen(line)-3] == '=')) {
	    status++; val[0] = '\0';
	}
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0)
	    m->sf[n]->document_name = strdup(val);
	else if (strcmp(name, "FileSize") == 0)
	    m->sf[n]->filesize = strtol(val, NULL, 16);
	else if (strcmp(name, "Chunks") == 0) {
	    status = strtol(val, NULL, 16);
	    if (m->sf[n]->chunks || status < 0)
		return FCP_INVALID_METADATA;
	    m->sf[n]->chunks = calloc(status, sizeof(char *));
	    m->sf[n]->chunk_count = status;
	} else if (strncmp(name, "Chunk.", 6) == 0) {
	    status = strtol(&name[6], NULL, 16);
	    if (!m->sf[n]->chunks || status < 0
		    || status >= m->sf[n]->chunk_count)
		return FCP_INVALID_METADATA;
	    m->sf[n]->chunks[status] = strdup(val);
	} //else break;
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
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
	if (status != 2) break;
	if (strcmp(name, "DocumentName") == 0) {
	    m->dr[n]->document_name = val;
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
	status = sscanf(line, "%[^=]=%s", name, val);
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
	status = fcp_insert_raw(in, uri, length, DATA, htl);
	if (status != FCP_SUCCESS) return status;
	status = fcp_redirect(m, document_name, uri);
	return status;
    } else {
	int partsize = calc_partsize(length);
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
	    i->cond = &cond; i->mutex = &mutex;
	    i->activethreads = &activethreads;
	    i->error = &error; i->length = n;
	    i->data = parts[t]; i->uri = keys[t];
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
	    if (++activethreads == threads) pthread_cond_wait(&cond, &mutex);
	    if (error) return error;
	}
	while (activethreads) {
	    pthread_cond_wait(&cond, &mutex);
	    if (error) return error;
	}
        n = m->sf_count++;
        m->sf = realloc(m->sf, sizeof(splitfile *) * m->sf_count);
        m->sf[n] = malloc(sizeof(splitfile));
        m->sf[n]->document_name = strdup(document_name);
        m->sf[n]->filesize = length;
        m->sf[n]->chunk_count = partcount;
        m->sf[n]->chunks = calloc(partcount, sizeof(FILE *));
	for (t = 0 ; t < partcount ; t++)
	    m->sf[n]->chunks[t] = strdup(keys[t]);
	return FCP_SUCCESS;
    }
}

void *
insert_thread (void *arg)
{
    intargs *i = (intargs *) arg;
    int r = 3, status = -1;
    while (r-- && status != FCP_SUCCESS) {
	rewind(i->data);
	status = fcp_insert_raw(i->data, i->uri, i->length, DATA, i->htl);
    }
    pthread_mutex_lock(i->mutex);
    if (status != FCP_SUCCESS) *i->error = status;
    (*i->activethreads)--;
    pthread_cond_broadcast(i->cond);
    pthread_mutex_unlock(i->mutex);
    fclose(i->data);
    free(i);
    pthread_exit(NULL);
}

int
fcp_insert_raw (FILE *in, char *uri, int length, int type, int htl)
{
    int n, status;
    char buf[1024], foo[128];
    FILE *sock = fcp_connect();
    if (!sock) return FCP_CONNECT_FAILED;
    fprintf(sock, "ClientPut\n"
	          "HopsToLive=%x\n"
		  "URI=%s\n"
		  "DataLength=%x\n"
		  "MetadataLength=%x\n"
		  "Data\n",
		  htl, uri, length,
		  type == DATA ? 0 : length);
    while (length) {
	length -= (n = length > 1024 ? 1024 : length);
	status = fread(buf, 1, n, in);
	if (status != n) goto ioerror;
	status = fwrite(buf, 1, n, sock);
	if (status != n) goto ioerror;
    }
    status = fscanf(sock, "%s\nURI=%s\nEndMessage\n", buf, foo);
    if (status == 1 && strcmp(buf, "URIError") == 0)
	goto invalid_uri;
    if (status == 2) {
	strcpy(uri, foo);
	if (strcmp(buf, "Success") == 0
		|| strcmp(buf, "KeyCollision") == 0)
	    goto success;
    }
ioerror:
    fclose(sock);
    return FCP_IO_ERROR;
invalid_uri:
    fclose(sock);
    return FCP_INVALID_URI;
success:
    fclose(sock);
    return FCP_SUCCESS;
}

int
calc_partsize (int length)
{
    int n = 0, tmp = length/1024;
    while (tmp) {
	tmp >>= 1;
       	n++;
    }
    if (length/1024 > tmp) n++;
    return (8 << (n/2)) * 1024;
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
fcp_metadata_insert (fcp_metadata *m, char *uri, int htl)
{
    int i, j;
    FILE *data = tmpfile();
    for (i = 0 ; i < m->r_count ; i++)
        fprintf(data, "Redirect\n"
	    	      "DocumentName=%s\n"
		      "Target=%s\n"
		      "End\n",
		      m->r[i]->document_name,
		      m->r[i]->target_uri);
    for (i = 0 ; i < m->dr_count ; i++)
        fprintf(data, "DateRedirect\n"
	    	      "DocumentName=%s\n"
		      "Predate=%s\n"
		      "Postdate=%s\n"
		      "Baseline=%lx\n"
		      "Increment=%lx\n"
		      "End\n",
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
	    fprintf(data, "Chunk.%x=%s\n", j, m->sf[i]->chunks[j]);
	fprintf(data, "End\n");
    }
    j = ftell(data);
    rewind(data);
    return fcp_insert_raw(data, uri, j, CONTROL, htl);
}

void
fcp_internal_free (fcp_metadata *m)
{
    int i, j;
    if (m->uri) free(m->uri);
    for (i = 0 ; i < m->r_count ; i++) {
        if (m->r[i]->document_name)
	    free(m->r[i]->document_name);
	if (m->r[i]->target_uri)
	    free(m->r[i]->target_uri);
	free(m->r[i]);
    }
    if (m->r) free(m->r);
    for (i = 0 ; i < m->dr_count ; i++) {
        if (m->dr[i]->document_name)
    	    free(m->dr[i]->document_name);
	if (m->dr[i]->predate)
	    free(m->dr[i]->predate);
	if (m->dr[i]->postdate)
	    free(m->dr[i]->postdate);
	free(m->dr[i]);
    }
    if (m->dr) free(m->dr);
    for (i = 0 ; i < m->sf_count ; i++) {
        if (m->sf[i]->document_name)
	    free(m->sf[i]->document_name);
	for (j = 0 ; j < m->sf[i]->chunk_count ; j++)
	    free(m->sf[i]->chunks[j]);
	free(m->sf[i]->chunks);
    }
/*	if (m->sf[i]->check_pieces)
	    free(m->sf[i]->check_pieces);
	if (m->sf[i]->checks) {
	    for (j = 0 ; m->sf[i]->checks[j] ; j++) {
	        for (k = 0 ; m->sf[i]->checks[j][k] ; k++)
		    free(m->sf[i]->checks[j][k]);
		free(m->sf[i]->checks[j]);
	    }
	}
	if (m->sf[i]->graph) {
	    for (j = 0 ; m->sf[i]->graph[j] ; j++)
	        free(m->sf[i]->graph[j]);
	    free(m->sf[i]->graph);
	}*/
    if (m->sf) free(m->sf);
    for (i = 0 ; i < m->i_count ; i++) {
        if (m->i[i]->document_name)
	    free(m->i[i]->document_name);
	for (j = 0 ; m->i[i]->fields[j] ; j++) {
	    if (m->i[i]->fields[j][0])
	        free(m->i[i]->fields[j][0]);
	    if (m->i[i]->fields[j][1])
	        free(m->i[i]->fields[j][1]);
	    free(m->i[i]->fields[j]);
	}
	free(m->i[i]);
    }
    if (m->i) free(m->i);
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
    serv = getservbyname("fcp", "tcp");
    if (!serv) return NULL;
    addr.s_addr = inet_addr("127.0.0.1");
    memset((char *) &address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = (serv->s_port);
    address.sin_addr.s_addr = addr.s_addr;
    connected_socket = socket(AF_INET, SOCK_STREAM, 0);
    connected = connect(connected_socket,
	    (struct sockaddr *) &address, sizeof(address));
    if (connected < 0) return NULL;
    return fdopen(connected_socket, "w+");
}

