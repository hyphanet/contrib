/*
	This code is part of FreeWeb - an FCP-based client for Freenet

	Designed and implemented by David McNab, david@rebirthing.co.nz
	CopyLeft (c) 2001 by David McNab

	The FreeWeb website is at http://freeweb.sourceforge.net
	The website for Freenet is at http://freenet.sourceforge.net

	This code is distributed under the GNU Public Licence (GPL) version 2.
	See http://www.gnu.org/ for further details of the GPL.
*/

#include "ezFCPlib.h"

/*
	IMPORTED DECLARATIONS
*/
extern int      *metaParse(META04 *, char *);
extern void      metaFree(META04 *);

extern time_t   _mkgmtime(struct tm *p_tm); // thank God for this gem of a function!
extern long      xtoi(char *s);

extern char     _fcpHost[];
extern int      _fcpPort;
extern int      _fcpHtl;
extern int      _fcpRawMode;
extern char     _fcpID[];

/*
	PRIVATE DECLARATIONS
*/
static int      fcpOpenKeyRead(HFCP *hfcp, char *key, int maxRegress);
static int      fcpOpenKeyWrite(HFCP *hfcp, char *key);
static int      calc_new_date(char *newdate, char *baseline, int increment, int regress);
static time_t   date_to_secs(char *datestr);


/*
	Function:    fcpOpenKey()

	Arguments:
	  hfcp - hfcp handle, previously opened by call to fcpCreateHandle()
		key - freenet URI of the key to be opened

		mode -  _FCP_O_READ - open an existing Freenet key for reading
   	        _FCP_O_WRITE - create a new Freenet key for writing
						_FCP_O_RAW - disable automatic handling of metadata
						(enabled by default)

		read and write access are mutually exclusive
	
	Description: This is really two functions in one, because opening keys for reading and
	opening keys for writing are two completely different operations.
	
	Returns:     0 if successful
	-1 if failed
*/

int fcpOpenKey(HFCP *hfcp, char *key, int mode)
{
	// Validate flags
	if ((mode & _FCP_O_READ) && (mode & _FCP_O_WRITE))
		return -1;      // read/write access is impossible

	if ((mode & (_FCP_O_READ | _FCP_O_WRITE)) == 0)
		return -1;      // neither selected - illegal

	if (mode & _FCP_O_RAW)
		hfcp->raw = 1;
	
	if (mode & _FCP_O_READ) {
		hfcp->mimeType[0] = '\0';
		hfcp->openmode = mode;
		return fcpOpenKeyRead(hfcp, key, hfcp->regress);
	}
	else
		return fcpOpenKeyWrite(hfcp, key);

} // 'fcpOpenKey()'


static int fcpOpenKeyRead(HFCP *hfcp, char *key, int maxRegress)
{
  char     buf[1024];
	char    *ptr;         /* pointer to unparsed metadata */
  int      n;
	int      j;
  int      len;
  FCP_URI *uri;
  FCP_URI *uriTgt;
  int      redirecting;
  char    *currKey;
  char    *newKey = NULL;
  META04  *meta;
  int      metaLen;
  FLDSET  *fldSet;
  char    *s;
  char    *path;
  int      docFound;

  long     offset = 0;
  long     increment = 86400;
  long     timeNow;
  long     tgtTime;
  
  _fcpLog(FCP_LOG_VERBOSE, "Request: %s", key);

  /* Main loop for key retrieval */
  currKey = strdup(key);

  uri = NULL;
  if (hfcp->meta != NULL) {
		metaFree(hfcp->meta);
		hfcp->meta = NULL;
		hfcp->fields = NULL;
  }

	for (redirecting = 1; redirecting;) {
		fldSet = NULL;
	  
		// clean up if this is a loop re-entry
		if (uri != NULL) {
			_fcpSockDisconnect(hfcp);
			_fcpFreeUri(uri);
		}
			
		// analyse current key
		uri = _fcpParseUri(currKey);
			
		/* Grab key at current key URI
			 connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0) {
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
		if (n < len) {
			_fcpSockDisconnect(hfcp);
			_fcpFreeUri(uri);
			free(currKey);
			return -1;
		}
			
		// expecting a datafound response
		if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_DATAFOUND) {
			_fcpSockDisconnect(hfcp);
			_fcpFreeUri(uri);
			free(currKey);
			return -1;
		}
	  
		// record the key's data size
		hfcp->keysize = hfcp->conn.response.body.datafound.dataLength;
			
		// suck in the metadata, if any
		meta = NULL;
		if ((metaLen = hfcp->conn.response.body.datafound.metaLength) > 0) {
			ptr = (char *) malloc(metaLen + 1);    // extra byte for '\0'
				
			// get all the metadata
			hfcp->conn.response.body.datafound.metaData = ptr;
			_fcpReadBlk(hfcp, ptr, metaLen);
			ptr[metaLen] = '\0';
				
			_fcpLog(FCP_LOG_DEBUG, "Metadata:\n--------\n%s\n--------", ptr);
				
			fflush(stdout);
		}

		/* if rawmode is set, copy the raw metadata to the appropriate HFCP
			 member, then break out of the redirecting loop and return to caller */
		if (_fcpRawMode) {
			hfcp->rawMetadata = (char *) malloc(metaLen + 1);
			memcpy( hfcp->rawMetadata, ptr, metaLen );
			free(ptr);

			redirecting = 0;
			continue;
		}
		else {
			meta = (META04 *) malloc( sizeof(META04) );
			metaParse(meta, ptr);

			free(ptr);
		}
		
		/* Dump the metadata information from META04 (debug) */
		/*
		for (n=0; n < meta->count; n++) {
			for (j=0; j < meta->cdoc[n]->count; j++) {
				printf("keyname: %s, ", meta->cdoc[n]->keys[j]->name);
				printf("valname: %s\n", meta->cdoc[n]->keys[j]->value);
			}
		}
		*/

		timeNow = (long)time(NULL);

		//fldSet = cdocFindDoc(meta, NULL);

		/* If fldSet is NULL, there's no useful metadata */
		/* Effectively break out of the for loop and prepare return to caller */
		//		if (!fldSet) {
		//	redirecting = 0;
		//	continue;
		//}
		
		/* Get the mimetype if it exists */
		if ((s = cdocLookupKey(fldSet, "Info.Format")) != NULL)
				strcpy(hfcp->mimeType, s);

		switch (fldSet->type)	{
		case META_TYPE_04_NONE:
			// success
			redirecting = 0;
			break;

		case META_TYPE_04_REDIR:
			s = cdocLookupKey(fldSet, "Redirect.Target");
			sprintf(buf, s);
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
			
			if (!strncmp(uriTgt->uri_str, "KSK@", 4))	{
				// convert KSK@name to KSK@secshex-name
				path = uriTgt->path;           // path is the ksk keyname
				sprintf(buf, "KSK@%lx-%s",
								tgtTime,
								uriTgt->uri_str + 4);
				newKey = strdup(buf);
				metaFree(meta);
				_fcpLog(FCP_LOG_VERBOSE, "Redirect: %s", buf);
			}
			else if (!strncmp(uriTgt->uri_str, "SSK@", 4) && uriTgt->path != NULL)	{

				// convert SSK@blah/name to SSK@blah/secshes-name
				sprintf(buf, "SSK@%s/%lx-%s",
								uriTgt->keyid,
								tgtTime,
								uriTgt->path);
				newKey = strdup(buf);
				metaFree(meta);
				_fcpLog(FCP_LOG_VERBOSE, "Redirect: %s", buf);
			}
			else {
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
			redirecting=0;
			break;
		}
		
		free(currKey);
		currKey = newKey;
		
	} // 'while (redirecting)'
	
  /* If execution reaches here, we've succeeded in opening the key and
		 retrieving it's metadata.  Yay! */

	// YO!! MALLOC PLEASE !!
	//memcpy( hfcp->meta, meta, sizeof (META04) );

	hfcp->meta = meta;
	hfcp->fields = fldSet;
  _fcpFreeUri(uri);
  hfcp->keysize = hfcp->conn.response.body.datafound.dataLength;
  return 0;

} // 'fcpOpenKeyRead()


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
  
  // generate unique filenames and open the files
  
  if ((hfcp->wr_info.fd_data = opentemp(hfcp->wr_info.data_temp_file)) < 0) {
	 _fcpFreeUri(hfcp->wr_info.uri);
	 return -1;
  }
  
  if ((hfcp->wr_info.fd_meta = opentemp(hfcp->wr_info.meta_temp_file)) < 0) {
	 close(hfcp->wr_info.fd_data);
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


/*
	warning - revolting function follows

	this is made necessary because unix lacks a GMT equivalent of mktime(),
	so we have to manually convert a date string to seconds since epoch
*/

// How about gmtime() ?

static time_t date_to_secs(char *datestr)
{
    static int mon_days[12] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
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

    return basesecs;
}
