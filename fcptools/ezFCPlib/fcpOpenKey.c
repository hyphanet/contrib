//
//  This code is part of FreeWeb - an FCP-based client for Freenet
//
//  Designed and implemented by David McNab, david@rebirthing.co.nz
//  CopyLeft (c) 2001 by David McNab
//
//  The FreeWeb website is at http://freeweb.sourceforge.net
//  The website for Freenet is at http://freenet.sourceforge.net
//
//  This code is distributed under the GNU Public Licence (GPL) version 2.
//  See http://www.gnu.org/ for further details of the GPL.
//

#include "time.h"
#include "stdlib.h"

#ifndef WINDOWS
#include "unistd.h"
#endif

#include "ezFCPlib.h"

//
// IMPORTED DECLARATIONS
//

extern META04   *metaParse(char *buf);
extern void     metaFree(META04 *meta);

extern time_t   _mkgmtime(struct tm *p_tm); // thank God for this gem of a function!
extern long     xtoi(char *s);

extern char     *_fcpHost;
extern int      _fcpPort;
extern int      _fcpHtl;
extern char     *_fcpProgPath;
extern int      _fcpFileNum;    // temporary file count
extern char     _fcpID[];


//
// PRIVATE DECLARATIONS
//

static int      fcpOpenKeyRead(HFCP *hfcp, char *key, int maxRegress);
static int      fcpOpenKeyWrite(HFCP *hfcp, char *key);
static int      calc_new_date(char *newdate, char *baseline, int increment, int regress);
static time_t   date_to_secs(char *datestr);

#ifdef YYDEBUG
extern int meta_debug;
#endif


//
// Function:    fcpOpenKey()
//
// Arguments:   hfcp - hfcp handle, previously opened by call to fcpCreateHandle()
//
//              key - freenet URI of the key to be opened
//
//              mode -  _FCP_O_READ - open an existing Freenet key for reading
//                      _FCP_O_WRITE - create a new Freenet key for writing
//                      _FCP_O_RAW - disable automatic handling of metadata
//                                   (enabled by default)
//                      read and write access are mutually exclusive
//
// Description: This is really two functions in one, because opening keys for reading and
//              opening keys for writing are two completely different operations.
//
// Returns:     0 if successful
//              -1 if failed
//

int fcpOpenKey(HFCP *hfcp, char *key, int mode)
{
    // Validate flags
    if ((mode & _FCP_O_READ) && (mode & _FCP_O_WRITE))
        return -1;      // read/write access is impossible
    if ((mode & (_FCP_O_READ | _FCP_O_WRITE)) == 0)
        return -1;      // neither selected - illegal
    if (mode & _FCP_O_RAW)
        hfcp->raw = 1;

    if (mode & _FCP_O_READ)
    {
        hfcp->mimeType[0] = '\0';
		hfcp->openmode = mode;
        return fcpOpenKeyRead(hfcp, key, hfcp->regress);
    }
    else
        return fcpOpenKeyWrite(hfcp, key);

}       // 'fcpOpenKey()'




static int fcpOpenKeyRead(HFCP *hfcp, char *key, int maxRegress)
{
    char    buf[1024];
    int     n;
    int     len;
    FCP_URI *uri;
    FCP_URI *uriTgt;
    int redirecting;
    char *currKey;
    char *newKey = NULL;
    META04 *meta;
    int  metaLen;
    int chainPath; // the MSK 'chain path' number - pathi in SSK@blan/name//path1//,,,//pathn//
    FLDSET *fldSet;
    char *s;
    char *path;
    int docFound;

    _fcpLog(FCP_LOG_VERBOSE, "Request: %s", key);

    //
    // main loop for key retrieval
    //

    // follow the MSK chain
    chainPath = 0;
    currKey = strdup(key);
    uri = NULL;
    if (hfcp->meta != NULL)
    {
        metaFree(hfcp->meta);
        hfcp->meta = NULL;
        hfcp->fields = NULL;
    }

    do
    {
        // follow the chain of internal redirects
        for (redirecting = 1; redirecting;)
        {
            fldSet = NULL;

            // clean up if this is a loop re-entry
            if (uri != NULL)
            {
                _fcpSockDisconnect(hfcp);
                _fcpFreeUri(uri);
            }

            // analyse current key
            uri = _fcpParseUri(currKey);
            docFound = 0;

            //
            // Grab key at current key URI
            //

            // connect to Freenet FCP
            if (_fcpSockConnect(hfcp) != 0)
            {
                _fcpFreeUri(uri);
				_fcpLog(FCP_LOG_CRITICAL, "Can't connect to node for key '%s'", key);
                free(currKey);
                return -1;
            }

            // create and send key request
            sprintf(buf, "ClientGet\nURI=%s\nHopsToLive=%x\nEndMessage\n", uri->uri_str, hfcp->htl);
            len = strlen(buf);

            _fcpSockSend(hfcp, _fcpID, 4);

            n = _fcpSockSend(hfcp, buf, len);
            if (n < len)
            {
                _fcpSockDisconnect(hfcp);
                _fcpFreeUri(uri);
                free(currKey);
                return -1;
            }

            // expecting a datafound response
            if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_DATAFOUND)
            {
                _fcpSockDisconnect(hfcp);
                _fcpFreeUri(uri);
                free(currKey);
                return -1;
            }

            // record the key's data size
            hfcp->keysize = hfcp->conn.response.body.datafound.dataLength;

            // suck in the metadata, if any
            meta = NULL;
            if ((metaLen = hfcp->conn.response.body.datafound.metaLength) > 0)
            {
                char *ptr = safeMalloc(metaLen + 1);    // extra byte for '\0'
                int count;

                // get all the metadata
                hfcp->conn.response.body.datafound.metaData = ptr;
                count = _fcpReadBlk(hfcp, ptr, metaLen);
                ptr[count] = '\0';
                _fcpLog(FCP_LOG_DEBUG, "Metadata:\n--------\n%s\n--------", ptr);

				fflush(stdout);
                meta = metaParse(ptr);
                free(ptr);
            }

            // here's where we decide what we're actually going to do with this key
            // 1. is it an msk?
            // 2. is there any metadata?
            // 3. does it redirect?

            // is an msk redirect needed?
            if (chainPath >= uri->numdocs && (meta == NULL || uri->numdocs > 0))
                // don't follow internal redirects
                redirecting = 0;
            else
            {
                long offset = 0;
                long increment = 86400;
                long timeNow = (long)time(NULL);
                long tgtTime;

                // yes - try to find required cdoc
                if (meta == NULL)
                {
                    // no metadata - see if we needed any
                    if (uri->numdocs == 0
                        || (chainPath == uri->numdocs - 1 && uri->docname[chainPath][0] == '\0')
                        )
                    {
                        redirecting = 0;
                        break;
                    }
                    else
                    {
                        // MSK redirect requested and no metadata available
                        free(currKey);
                        _fcpSockDisconnect(hfcp);
                        _fcpFreeUri(uri);
                        return -1;  // 404
                    }
                }

                // there is metadata, but is there a matching cdoc?
                if (uri->numdocs > 0)
                    fldSet = cdocFindDoc(meta, uri->docname[chainPath]);
                else
                    fldSet = cdocFindDoc(meta, NULL);
                if (fldSet == NULL)
                {
                    // maybe we're upstream from map
                    if ((fldSet = cdocFindDoc(meta, NULL)) == NULL)
                    {
                        // no cdoc matching the one requested, and
                        // no default cdoc - fail
                        free(currKey);
                        _fcpSockDisconnect(hfcp);
                        _fcpFreeUri(uri);
                        metaFree(meta);
                        return -1;  // 404
                    }
                }
                else if (chainPath < uri->numdocs && uri->docname[chainPath][0] != '\0')
                    docFound = 1;

                // hunt for a content-type
                if ((s = cdocLookupKey(fldSet, "Info.Format")) != NULL)
                    strcpy(hfcp->mimeType, s);

                // found requested cdoc (or default) - do we need to redirect?
                switch (fldSet->type)
                {
                case META_TYPE_04_NONE:
                    // success
                    redirecting = 0;
                    break;

                case META_TYPE_04_REDIR:
                    s = cdocLookupKey(fldSet, "Redirect.Target");
                    sprintf(buf, s);
                    if (!docFound && chainPath < uri->numdocs)
                    {
                        strcat(buf, "//");
                        strcat(buf, uri->docname[chainPath]);
                    }
                    newKey = strdup(buf);
                    metaFree(meta);
                    _fcpLog(FCP_LOG_VERBOSE, "Redirect: %s", buf);
                    break;

                case META_TYPE_04_DBR:
                    s = cdocLookupKey(fldSet, "DateRedirect.Target");
                    uriTgt = _fcpParseUri(s);

                    if ((s = cdocLookupKey(fldSet, "DateRedirect.Offset")) != NULL)
                        offset = xtoi(s);
                    if ((s = cdocLookupKey(fldSet, "DateRedirect.Increment")) != NULL)
                        increment = xtoi(s);
                    tgtTime = timeNow - ((timeNow - offset) % increment);

                    if (!strncmp(uriTgt->uri_str, "KSK@", 4))
                    {
                        // convert KSK@name to KSK@secshex-name
                        path = uriTgt->path;           // path is the ksk keyname
                        sprintf(buf, "KSK@%lx-%s",
                                tgtTime,
                                uriTgt->uri_str + 4);
                        if (!docFound && uri->numdocs > 0)
                        {
                            strcat(buf, "//");
                            strcat(buf, uri->docname[chainPath]);
                        }
                        newKey = strdup(buf);
                        metaFree(meta);
                        _fcpLog(FCP_LOG_VERBOSE, "Redirect: %s", buf);
                    }
                    else if (!strncmp(uriTgt->uri_str, "SSK@", 4) && uriTgt->path != NULL)
                    {
                        // convert SSK@blah/name to SSK@blah/secshes-name
                        sprintf(buf, "SSK@%s/%lx-%s",
                                    uriTgt->keyid,
                                    tgtTime,
                                    uriTgt->path);
                        if (!docFound && chainPath < uri->numdocs)
                        {
                            strcat(buf, "//");
                            strcat(buf, uri->docname[chainPath]);
                        }
                        newKey = strdup(buf);
                        metaFree(meta);
                        _fcpLog(FCP_LOG_VERBOSE, "Redirect: %s", buf);
                    }
                    else
                    {
                        // no cdoc matching the one requested - fail
                        _fcpLog(FCP_LOG_NORMAL, "Invalid DBR target: \n%s\n -> %s",
                                currKey, uriTgt);
                        _fcpFreeUri(uri);
                        free(currKey);
                        free(uriTgt);
                        _fcpSockDisconnect(hfcp);
                        metaFree(meta);
                        return -1;  // 404
                    }

                    free(uriTgt);
                    break;

                case META_TYPE_04_SPLIT:

//					printf("Metadata: Splitfile!\n");
		redirecting=0;
                    break;
                }

            }

            if (docFound)
                chainPath++;

            free(currKey);
            currKey = newKey;

        }           // 'while (redirecting)'

    } while (++chainPath < uri->numdocs);

    // if we get here, we've succeeded
    hfcp->meta = meta;
    hfcp->fields = fldSet;
    _fcpFreeUri(uri);
    hfcp->keysize = hfcp->conn.response.body.datafound.dataLength;
    return 0;

}               // 'fcpOpenKeyRead()



static int fcpOpenKeyWrite(HFCP *hfcp, char *key)
{
    // connect to Freenet FCP
    if (_fcpSockConnect(hfcp) != 0)
    {
//_endthread();

        return -1;
    }

    // save the key
    hfcp->wr_info.uri = _fcpParseUri(key);

    // generate unique filenames
#ifdef WINDOWS
    sprintf(hfcp->wr_info.data_temp_file, "%s\\fcp-%d.tmp", _fcpProgPath, _fcpFileNum++);
    sprintf(hfcp->wr_info.meta_temp_file, "%s\\fcp-%d.tmp", _fcpProgPath, _fcpFileNum++);
#else
    sprintf(hfcp->wr_info.data_temp_file, "%s/fcp-%d.tmp", _fcpProgPath, _fcpFileNum++);
    sprintf(hfcp->wr_info.meta_temp_file, "%s/fcp-%d.tmp", _fcpProgPath, _fcpFileNum++);
#endif

    // open the files
#ifdef WINDOWS
    if ((hfcp->wr_info.fd_data = _open(hfcp->wr_info.data_temp_file,
                                        _O_CREAT | _O_RDWR | _O_BINARY,
                                        _S_IREAD | _S_IWRITE)) < 0)
    {
        _fcpFreeUri(hfcp->wr_info.uri);
        return -1;
    }
    if ((hfcp->wr_info.fd_meta = _open(hfcp->wr_info.meta_temp_file,
                                        _O_CREAT | _O_RDWR | _O_BINARY,
                                        _S_IREAD | _S_IWRITE)) < 0)
#else
    if ((hfcp->wr_info.fd_data = open(hfcp->wr_info.data_temp_file, O_CREAT, S_IREAD | S_IWRITE)) < 0)
    {
        _fcpFreeUri(hfcp->wr_info.uri);
        return -1;
    }
    if ((hfcp->wr_info.fd_meta = open(hfcp->wr_info.meta_temp_file, O_CREAT, S_IREAD | S_IWRITE)) < 0)
#endif
    {
#ifdef WINDOWS
        _close(hfcp->wr_info.fd_data);
#else
        close(hfcp->wr_info.fd_data);
#endif
        _fcpFreeUri(hfcp->wr_info.uri);
        return -1;
    }

    hfcp->wr_info.num_data_wr = hfcp->wr_info.num_meta_wr = 0;
    hfcp->openmode = _FCP_O_WRITE;

    _fcpFreeUri(hfcp->wr_info.uri);
    return 0;
}


FCP_URI *_fcpParseUri(char *key)
{
    //char skip_keytype = 1;
    FCP_URI *uri = safeMalloc(sizeof(FCP_URI));

    char *dupkey = strdup(key);
    char *oldkey = dupkey;
    char *subpath;

    // set defaults
    uri->path[0] = uri->subpath[0] = '\0';
    uri->is_msk = 0;
    uri->numdocs = 0;

    // skip 'freenet:'
    if (!strncmp(dupkey, "freenet:", 8))
        dupkey += 8;

    // ignore redundant 'MSK@' prefix
    if (!strncmp(dupkey, "MSK@", 4))
        dupkey += 4;

    // check for tell-tale '//' MSK specifier
    if ((subpath = strstr(dupkey, "//")) != NULL)
    {
        char *delim;

        // found '//' - it's an MSK
        *subpath = '\0';
        subpath += 2;
        strcpy(uri->subpath, subpath);
        uri->is_msk = 1;

        // split this subpath into an array of them
        do
        {
            if ((delim = strstr(subpath, "//")) != NULL)
                *delim = '\0';
            uri = realloc(uri, sizeof(FCP_URI) + sizeof(char *) * (uri->numdocs + 1));
            uri->docname[uri->numdocs++] = strdup(subpath);
            if (delim != NULL)
                subpath = delim + 2;
        }
        while (delim != NULL);
    }

    // classify key header
    if (!strncmp(dupkey, "SSK@", 4))
    {
        char *path = strchr(dupkey, '/');

        dupkey += 4;
        uri->type = KEY_TYPE_SSK;
        *path++ = '\0';
        strcpy(uri->keyid, dupkey);
        strcpy(uri->path, path);
        sprintf(uri->uri_str, "SSK@%s/%s", uri->keyid, uri->path);
    }
    else if (!strncmp(dupkey, "CHK@", 4))
    {
        uri->type = KEY_TYPE_KSK;
        dupkey += 4;
        strcpy(uri->keyid, dupkey);
        sprintf(uri->uri_str, "CHK@%s", uri->keyid);
    }
    else if (!strncmp(dupkey, "KSK@", 4))
    {
        uri->type = KEY_TYPE_CHK;
        dupkey += 4;
        strcpy(uri->keyid, dupkey);
        sprintf(uri->uri_str, "KSK@%s", uri->keyid);
    }
    else
    {
        // just a KSK
        strcpy(uri->keyid, dupkey);
        uri->type = KEY_TYPE_KSK;
        sprintf(uri->uri_str, "KSK@%s", uri->keyid);
    }

    free(oldkey);
    return uri;

}


//
// destructor for URI struct returned by _fcpParseUri()
//

void _fcpFreeUri(FCP_URI *uri)
{
    int i;

    if (uri == NULL)
        return;

    for (i = 0; i < uri->numdocs; i++)
        free(uri->docname[i]);
    free(uri);
}


static int calc_new_date(char *newdate, char *baseline, int increment, int daysRegress)
{
    struct tm tm_lastupdate;

    time_t secs_now;
    time_t secs_baseline;
    time_t secs_last_update;
    time_t secs_since_baseline;

    // get current time in seconds since epoch
    time(&secs_now);                 /* Get time in seconds */

    // convert baseline to tm format
//  sscanf(baseline, "%04d%02d%02d%02d%02d%02d",
//                  &tmb.tm_year, &tmb.tm_mon, &tmb.tm_mday, &tmb.tm_hour, &tmb.tm_min, &tmb.tm_sec);
//  tmb.tm_mon--;
//  tmb.tm_year -= 1900;

// convert baseline AS GMT to seconds since epoch
//  secs_baseline = mktime(&tmb);
//  secs_baseline = _mkgmtime(&tmb);    // thank God for this gem of a function!

    secs_baseline = date_to_secs(baseline);

    // calculate time of last update as seconds since epoch
    secs_since_baseline = secs_now - secs_baseline;
    secs_last_update = (secs_since_baseline / increment) * increment + secs_baseline;

    // go back zero or more days according to daysRegress
    secs_last_update -= daysRegress * 24 * 60 * 60;

    // now convert to a tm structure as GMT
    memcpy(&tm_lastupdate, gmtime(&secs_last_update), sizeof(struct tm));

    // Finally, convert to freenet format date-time string yyyymmddhhmmss
    strftime(newdate, 16, "%Y%m%d%H%M%S", &tm_lastupdate);

    return 0;
}


//
// warning - revolting function follows
//
// this is made necessary because unix lacks a GMT equivalent of mktime(),
// so we have to manually convert a date string to seconds since epoch
//

static time_t date_to_secs(char *datestr)
{
    static int mon_days[12] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
//  struct tm acttm;
    time_t basesecs;
    int basedays;
    int year, mon, day, hour, min, sec;

    // break up into individual fields
    sscanf(datestr, "%04d%02d%02d%02d%02d%02d", &year, &mon, &day, &hour, &min, &sec);

    // calculate days, not including leap years
    basedays = (year - 1970) * 365 + mon_days[mon - 1] + day - 1;

    // add the leap years
    basedays += (year + 2 - 1970) / 4;

    // exclude if this year is a leap year and prior to feb 29
    if (year % 4 && mon < 3 && year != 1970)
    {
        _fcpLog(FCP_LOG_DEBUG, "docking a day for this years leap year");
        basedays--;
    }

    basesecs = basedays * 86400 + hour * 3600 + min * 60 + sec;

//  memcpy(&acttm, gmtime(&basesecs), sizeof(struct tm));
//  _fcpLog("datestr = '%s', basedays = %d, basesecs = %ld\n", datestr, basedays, basesecs);
//  _fcpLog("%04d-%02d-%02d %02d:%02d:%02d\n", acttm.tm_year+1900, acttm.tm_mon+1, acttm.tm_mday, acttm.tm_hour, acttm.tm_min, acttm.tm_sec);

    return basesecs;
}
