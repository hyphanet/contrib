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
extern int       opentemp(char []);

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
  char     buf[L_SOCKET_REQUEST];
	char    *ptr;

  FCP_URI *uri;
  FCP_URI *uriTgt;

  char    *currKey;
  char    *newKey = NULL;

  META04  *meta;
  int      metaLen;

  FLDSET  *fldSet;

	int      len;
	int      n;

  char    *s;
  char    *path;
  char    *finalpath;
  int      docFound;
  int      redirecting;

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

		// ************** currKey = NULL ************* !
			
		// analyse current key
		uri = (FCP_URI *) malloc(sizeof (FCP_URI) );
		_fcpParseUri(uri, currKey);
			
		/* Grab key at current key URI
			 connect to Freenet FCP */
		if (_fcpSockConnect(hfcp) != 0) {
			_fcpFreeUri(uri);
			_fcpLog(FCP_LOG_CRITICAL, "Can't connect to node for key '%s'", key);
			free(currKey);
			return -1;
		}
			
		// create and send key request
		snprintf(buf, L_SOCKET_REQUEST, "ClientGet\nURI=%s\nHopsToLive=%x\nEndMessage\n", uri->uri_str, hfcp->htl);
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
		ptr = NULL;
		meta = NULL;
		if ((metaLen = hfcp->conn.response.body.datafound.metaLength) > 0) {

			ptr = (char *) malloc(metaLen+1);
				
			// get all the metadata
			_fcpReadBlk(hfcp, ptr, metaLen);
			ptr[metaLen++] = 0;

			// parse metadata into META04 struct, then copy into HFCP
			// check end of this function for code that sets hfcp->meta.
			meta = (META04 *) malloc( sizeof(META04) );
			metaParse(meta, ptr);

			hfcp->rawMetadata = (char *) malloc(metaLen + 1);
			memcpy(hfcp->rawMetadata, ptr, metaLen);

			_fcpLog(FCP_LOG_DEBUG, "Metadata:\n--------\n%s\n--------", ptr);
			fflush(stdout);

			// free the no longer necessary metadata pointer.
			free(ptr);
		}

		/* fcpRawMode means, do not follow redirects */
		if (_fcpRawMode) {
			redirecting = 0;
			continue;
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

 		/* Get the mimetype if it exists */
 		if ((s = cdocLookupKey(fldSet, "Info.Format")) != NULL)
 				strncpy(hfcp->mimeType, s, L_MIMETYPE);
 		finalpath = strstr(key, "//");
 		if (finalpath)
 			finalpath += 2; // gotta do it this way or we derefrence 0x2
 		fldSet = cdocFindDoc(meta, finalpath);
 		if (!fldSet && finalpath)
 			fldSet = cdocFindDoc(meta, NULL);
 			
 
 		if (fldSet) {
			redirecting=0;
			continue;
		}
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
			uriTgt = (FCP_URI *) malloc(sizeof (FCP_URI) );
			if(_fcpParseUri(uriTgt, s))
				exit(1);   // FIXME We're toast, but at least spit out a frigging error message.
				
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
//			fcpGetSplit(hfcp, fldSet);
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
	hfcp->wr_info.uri = (FCP_URI *) malloc(sizeof (FCP_URI) );
  _fcpParseUri(hfcp->wr_info.uri, key);
  
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


int _fcpParseUri(FCP_URI *uri, char *key)
{
	char *ORIGdupkey = strdup(key);
	char *dupkey;

	dupkey=ORIGdupkey;

	memset(uri, 0, sizeof(*uri));

	// set path to NULL; currently only used for SVK's
	uri->path = 0;
	
	// skip 'freenet:'
	if (!strncmp(dupkey, "freenet:", 8))
		dupkey += 8;
	
	// classify key header
	if (!strncmp(dupkey, "SSK@", 4)) {
		char *path = strchr(dupkey, '/');

		uri->type = KEY_TYPE_SSK;

		dupkey += 4; /* set to start of key value (after SSK@) */
		*path++ = 0; /* set the '/' char to null for handling */

		// do the malloc stuff
		uri->keyid = (char *) malloc(strlen(dupkey) + 1);
		strcpy(uri->keyid, dupkey);

		uri->path = (char *) malloc(strlen(path) + 1); // DUH! strlen(path)=n.  strlen(path+1) = n-1.  Braindead error.
		strcpy(uri->path, path);

		// 10 to be safe
		uri->uri_str = (char *) malloc(strlen(uri->keyid) + strlen(path) + 10);
		sprintf(uri->uri_str, "SSK@%s/%s", uri->keyid, path);
	}

	else if (!strncmp(dupkey, "CHK@", 4)) { // DONE; do TEST
		uri->type = KEY_TYPE_CHK;

		dupkey += 4;

		// malloc the string and then do a simple strcpy
		// (strncpy is unecessary since strlen located the NULL char).
		uri->keyid = (char *) malloc(strlen(dupkey) + 1);
		strcpy(uri->keyid, dupkey);

		// now do the same for the raw uri string field
		// (malloc size + 4 (CHK@) + 1 (NULL) = 5)
		uri->uri_str = (char *) malloc(strlen(uri->keyid) + 5);
		sprintf(uri->uri_str, "CHK@%s", uri->keyid);
	}

	else if (!strncmp(key, "KSK@", 4)) { // DONE; do TEST
		uri->type = KEY_TYPE_KSK;

		key += 4;

		// same as code for CHK@
		uri->keyid = (char *) malloc(strlen(dupkey) + 1);
		strcpy(uri->keyid, dupkey);

		uri->uri_str = (char *) malloc(strlen(uri->keyid) + 5);
		sprintf(uri->uri_str, "KSK@%s", uri->keyid);
	}

	else {
		// bad bad
		
		free(ORIGdupkey); // DUH.  dupkey+n.
		return 1;
	}

	// free the only dynamically allocated string that isn't a FCP_URI member.
	free(ORIGdupkey); // DUH.  dupkey+n.
	return 0;
}


/*
	destructor for URI struct returned by _fcpParseUri()
*/

void _fcpFreeUri(FCP_URI *uri)
{

	if (!uri) return;

	if (uri->keyid) free(uri->keyid);
	if (uri->path) free(uri->path);
	if (uri->uri_str) free(uri->uri_str);

	free(uri);
	_fcpLog(FCP_LOG_DEBUG, "freed FCP_URI struct");
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
