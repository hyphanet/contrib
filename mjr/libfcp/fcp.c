#include "fcp.h"

FILE * fcp_connect ();
enum {DATA, CONTROL};
int fcp_get (FILE *data, int *len, int *type, char *uri, int htl);
int splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads);
int calc_partsize (int bytes);

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
    m->r_count = 0; m->dr_count = 0; m->sf_count = 0; m->i_count = 0;
    m->uri = NULL; m->r = NULL; m->dr = NULL; m->sf = NULL; m->i = NULL;
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
	if (m->r) {
	    for (i = 0 ; m->r[i] ; i++)
		if (strcmp(docname, m->r[i]->document_name) == 0)
		    return fcp_request(NULL, d, m->r[i]->target_uri,
			    htl, threads);
	}
	if (m->dr) {
	    for (i = 0 ; m->dr[i] ; i++) {
		if (strcmp(docname, m->dr[i]->document_name) == 0) {
		    long a = time(NULL) - m->dr[i]->baseline;
		    long b = m->dr[i]->baseline + a
			- (a % m->dr[i]->increment);
		    char target[512];
		    sprintf(target, "%s%lx%s", m->dr[i]->predate, b,
			    m->dr[i]->postdate);
		    return fcp_request(NULL, d, target, htl, threads);
		}
	    }
	}
	if (m->sf) {
	    for (i = 0 ; m->sf[i] ; i++)
		if (strcmp(docname, m->sf[i]->document_name) == 0)
		    return splitfile_get(d, m->sf[i], htl, threads);
	}
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
		fcp_metadata_free(m);
		*m = *m2;
		free(m2);
	    } else fcp_metadata_free(m2);
	    fclose(data);
	    return status;
fail:	    fcp_metadata_free(m2);
	    fclose(data);
	    return status;
	} else { // only one part, not a splitfile
	    d->cur_part = 0;
	    d->p_count = 1;
	    d->parts = malloc(sizeof(FILE *));
	    d->parts[0] = data;
	    d->threads = threads;
	    d->htl = htl;
            return len;
	}
    }
}

int
fcp_read (fcp_document *d, char *buf, int length)
{
    int n;
    n = fread(buf, 1, length, d->parts[d->cur_part]);
    if (n != length) {
	fclose(d->parts[d->cur_part++]);
	if (d->cur_part == d->p_count) return n;
	n += fread(&buf[n], 1, length - n, d->parts[d->cur_part]);
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
    fprintf(sock, "ClientGet\r\nURI=%s\r\nHopsToLive=%x\r\nEndMessage\r\n",
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
	return FCP_READ_FAILED;
    *len = dlen;
    *type = mlen == dlen ? CONTROL : DATA;
    while (dlen) {
	status = fscanf(sock, "DataChunk\nLength=%x\nData", &n);
	fgetc(sock);
	if (status != 1 || n < 0) return FCP_READ_FAILED;
	dlen -= n;
	while (n) {
	    i = fread(buf, 1, n > 1024 ? 1024 : n, sock);
	    if (!i) return FCP_READ_FAILED;
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
    m->sf = realloc(m->sf, sizeof(date_redirect *) * m->sf_count);
    m->sf[n] = malloc(sizeof(date_redirect));
    m->sf[n]->document_name = NULL;
    m->sf[n]->filesize = -1;
    m->sf[n]->chunk_count = 0;
    m->sf[n]->chunks = NULL;
    while (fgets(line, 512, data)) {
	status = sscanf(line, "%[^=]=%s", name, val);
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

int
splitfile_get (fcp_document *d, splitfile *sf, int htl, int threads)
{
    return FCP_SUCCESS;
}
/*
int
fcp_insert (FILE *in, int length, char *uri, int htl, int threads)
{
    int partsize = calc_partsize(length);
    int partcount = ceil(length/(double)partsize);printf("partsize: %d\npartcount: %d\n", partsize/1024, partcount);
    FILE *parts[partcount];
    char buf[1024], *chks[partcount];
    int n, tmp = length, activethreads = 0;
    pthread_t thread;
    for (n = 0 ; n < partcount ; n++) {
	int read, i = partsize < tmp ? partsize : tmp;
	parts[n] = tmpfile();
	while (i) {
	    read = fread(buf, 1, i > 1024 ? 1024 : i, in);
	    if (!read) return FCP_READ_FAILED;
	    fwrite(buf, 1, read, parts[n]);
	    i -= read;
	    tmp -= read;
	}
	activethreads++;
	//pthread_create(&thread, NULL, insert_chk_thread, (void *) &chks[n]);
    }
    
    return FCP_SUCCESS;
}

int
calc_partsize (int bytes)
{
    int n = 0, tmp = bytes/1024;
    while (tmp) {
	tmp >>= 1;
       	n++;
    }
    if (bytes/1024 > tmp) n++;
    return (8 << (n/2)) * 1024;
}
*/
void
fcp_metadata_free (fcp_metadata *m)
{
    int i, j;
    if (m->uri) free(m->uri);
    if (m->r_count) {
	for (i = 0 ; i < m->r_count ; i++) {
	    if (m->r[i]->document_name)
		free(m->r[i]->document_name);
	    if (m->r[i]->target_uri)
		free(m->r[i]->target_uri);
	    free(m->r[i]);
	}
	free(m->r);
    }
    if (m->dr_count) {
	for (i = 0 ; i < m->dr_count ; i++) {
	    if (m->dr[i]->document_name)
		free(m->dr[i]->document_name);
	    if (m->dr[i]->predate)
		free(m->dr[i]->predate);
	    if (m->dr[i]->postdate)
		free(m->dr[i]->postdate);
	    free(m->dr[i]);
	}
	free(m->dr);
    }
    if (m->sf_count) {
	for (i = 0 ; i < m->sf_count ; i++) {
	    if (m->sf[i]->document_name)
		free(m->sf[i]->document_name);
	    if (m->sf[i]->chunks) {
		for (j = 0 ; m->sf[i]->chunks[j] ; j++)
		    free(m->sf[i]->chunks[j]);
	        free(m->sf[i]->chunks);
	    }
/*	    if (m->sf[i]->check_pieces)
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
	}    
    }
    if (m->i_count) {
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
	free(m->i);
    }
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
    connected = connect(connected_socket, (struct sockaddr *) &address,
	    sizeof(address));
    if (connected < 0) return NULL;
    return fdopen(connected_socket, "w+");
}

