/*
  This code is part of FCPTools - an FCP-based client library for Freenet
	
  Designed and implemented by David McNab <david@rebirthing.co.nz>
  CopyLeft (c) 2001 by David McNab
	
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

/*
	ALL metadata parsing routines are case-insensitive.
*/

#include "ezFCPlib.h"

#include <stdlib.h>
#include <string.h>


/* *********************** HERE DEFINE STRUCTS in EZFCPLIB.H and continue ***************/

/*
  EXPORTED DEFINITIONS
*/

#if 0
long       cdocIntVal(hMetadata *meta, char *cdocName, char *keyName, long  defVal);
long       cdocHexVal(hMetadata *meta, char *cdocName, char *keyName, long  defVal);
char      *cdocStrVal(hMetadata *meta, char *cdocName, char *keyName, char *defVal);
#endif

hDocument *cdocFindDoc(hMetadata *meta, char *cdocName);
char      *cdocLookupKey(hDocument *doc, char *keyName);

/*
  IMPORTED DEFINITIONS
*/
extern long xtoi(char *s);

/*
  PRIVATE DEFINITIONS
*/
static int getLine(char *, char *, int);
static int splitLine(char *, char *, char *);

static int parse_version(hMetadata *, char *);
static int parse_document(hMetadata *, char *);


/*
	Parse states.

	The meaning has changed a bit from the old code.
*/
#define STATE_WAIT_VERSION  1
#define STATE_IN_VERSION    2
#define STATE_WAIT_DOCUMENT 3
#define STATE_IN_DOCUMENT   4

#define STATE_WAIT_END      5
#define STATE_END           6

/*
  END OF DECLARATIONS
*/

/*
  metaParse
  
  converts a raw buffer of metadata into an organised
  and accessible data structure
*/
int _fcpMetaParse(hMetadata *meta, char *buf)
{
	/* 1k boundary + 1 (for null character) */
  char  line[513];
  char  key[257];
  char  val[257];

	int   rc;
	
	/* free *meta if it has old metadata */
	/* loop from 0-meta->cd_count; free(meta[i]); */

	/* here's the silly use of _start; holds the 'start' value so that we can
		 return a 'state' value as well */

	/* prime the loop */
	meta->_start = getLine(line, buf, 0);

  while (meta->_start >= 0) { /* loop until we process all lines in the metadata string */

		if (!strncasecmp(line, "Version", 7)) {
			rc = parse_version(meta, buf);

			if ((rc != STATE_WAIT_DOCUMENT) && (rc != STATE_END)) {
				_fcpLog(FCP_LOG_DEBUG, "error!");
				return -1;
			}
		}
		
		else if (!strncasecmp(line, "Document", 8)) {
			rc = parse_document(meta, buf);

			if ((rc != STATE_WAIT_DOCUMENT) && (rc != STATE_END)) {
				_fcpLog(FCP_LOG_DEBUG, "error!");
				return -1;
			}
		}
		
		if (rc == STATE_END) {
			/* done(); */
		}

		meta->_start = getLine(line, buf, meta->_start);
  }
  
  return 0;
}

void _fcpMetaDestroy(hMetadata *meta)
{
}

/**********************************************************************/

#if 0
/*
  Metadata lookup routines
*/

long cdocIntVal(hMetadata *meta, char *cdocName, char *keyName, long defVal)
{
}

long cdocHexVal(hMetadata *meta, char *cdocName, char *keyName, long defVal)
{
}

char *cdocStrVal(hMetadata *meta, char *cdocName, char *keyName, char *defVal)
{
}
#endif

/*
	Given a document name (null for default doc), return a pointer to the
	hDocument struct.
*/
hDocument *cdocFindDoc(hMetadata *meta, char *cdocName)
{
	int i;

	for (i=0; i < meta->cdoc_count; i++) {

		/* check for the default document, to be overly safe about NULL prts */
		if (!cdocName) {
			if (!meta->cdocs[i]->name) {
				return meta->cdocs[i];
			}
		}

		/* check for the case where stored docname is NULL for the def doc */
		else if (!meta->cdocs[i]->name) {
			if (!cdocName) return meta->cdocs[i];
		}

		/* finally the case where cdocName perhaps matches the stored docname */
		else if (!strncasecmp(meta->cdocs[i]->name, cdocName, strlen(meta->cdocs[i]->name)))					 
			return meta->cdocs[i];
	}
	
	/* here, we didn't find it in the doc list.. return null */
	return 0;
}

/*
	Given a keyName for a particular document, return a pointer to the value.
*/
char *cdocLookupKey(hDocument *doc, char *keyName)
{
	int i;

	/* loop from 0 through field_count*2 (key/val pairs) */
	for (i=0; i < (doc->field_count << 1); i += 2) {

		/* found it? return a pointer to the value */
		if (!strncasecmp(doc->data[i], keyName, strlen(doc->data[i])))
			return doc->data[i+1];
	}

	/* here, we didn't find the key in the specified doc.. return null */
	return 0;
}

/**********************************************************************/
/* private/local to this module */
/**********************************************************************/

/*
  Split a line of the form 'key [= value] into the key/value pair
  return 0 on success.

	NOTE: it currently does NOT check for string overflow.

	Also, unlike the older version, this one doesn't handle key[no val] "pairs".
*/

static int splitLine(char *line, char *key, char *val)
{
	char *p;
	int bytes;

	p = line;

	while (1) {
		if ((*p == '=') || (*p == 0)) break;
		p++;
	}

	if (*p == 0) return -1;

	/* copy the key part of 'line' into 'key' */
	bytes = p - line;

	memcpy(key, line, bytes);
	*(key + bytes) = 0;

	/* p points to the '=' char */
	strcpy(val, p+1);

  return 0;
}

/*
  This function should return a 'line', terminated by 0 only
  (no carriage-return/linefeed nonsense)
	
	After processing the last line, getLine() should immediately
	return on next call w/ -2.  Return -1 on error.
*/
static int getLine(char *line, char *buf, int start)
{
	int line_index;
  
  if (!buf) return -1;

	/* If we're done, return w/ -2; */
	if (buf[start] == 0) return -2;

	line_index = 0;
	while (1) { /* :) */

		if ((buf[start + line_index] == '\n') ||
				(buf[start + line_index] == 0) ||
				(start + line_index > 512))
			break;

		else
			line[line_index++] = buf[start + line_index];
	}

	/* line_index indexes the desired location of the null char */
	line[line_index] = 0;

	if (line_index > 512)
		_fcpLog(FCP_LOG_DEBUG, "warning; line of metadata truncated at 512 bytes");

	if (buf[start + line_index] == '\n') line_index++;

	return start + line_index;
}


static int parse_version(hMetadata *meta, char *buf)
{
	/* 1k boundary + 1 (for null character) */
  char  line[513];
  char  key[257];
  char  val[257];
	
	while ((meta->_start = getLine(line, buf, meta->_start)) >= 0) {

		_fcpLog(FCP_LOG_DEBUG, "read line \"%s\"", line);

		if (!strncasecmp(line, "Revision=", 9)) {

			if (splitLine(line, key, val)) {
				_fcpLog(FCP_LOG_DEBUG, "expected value for Revision key");
				return -1;
			}

			meta->revision = xtoi(val);
			_fcpLog(FCP_LOG_DEBUG, "key Revision; val \"%s\"", val);
		}
	
		else if (!strncasecmp(line, "Encoding=", 9)) {
			
			if (splitLine(line, key, val)) {
				_fcpLog(FCP_LOG_DEBUG, "expected value for Encoding key");
				return -1;
			}

			meta->encoding = xtoi(val);
			_fcpLog(FCP_LOG_DEBUG, "key Encoding; val \"%s\"", val);
		}

		else if (!strncasecmp(line, "EndPart", 7)) {
			return STATE_WAIT_DOCUMENT;
		}
	
		else if (!strncasecmp(line, "End", 3)) {
			return STATE_END;
		}

		else {
			_fcpLog(FCP_LOG_DEBUG, "encountered unhandled pair; \"%s\"", line);
		}
	}

	/* we shouldn't reach here, except on error */
	return -1;
}


static int parse_document(hMetadata *meta, char *buf)
{
	/* 1k boundary + 1 (for null character) */
  char  line[513];
  char  key[257];
  char  val[257];

	int   doc_index;
	int field_index;

	doc_index = meta->cdoc_count;

	/* allocate space for new document */
	meta->cdoc_count++;

	/* is this the first time we've been through this function? */
	if (meta->cdoc_count == 1)
		meta->cdocs = (hDocument **)malloc(sizeof (hDocument *));
	else
		realloc(meta->cdocs, meta->cdoc_count * sizeof(hDocument *));

	/* add the document to the meta structure */
	meta->cdocs[doc_index] = (hDocument *)malloc(sizeof (hDocument));
	memset(meta->cdocs[doc_index], 0, sizeof (hDocument));

	_fcpLog(FCP_LOG_DEBUG, "document index %d", doc_index);
	
	while ((meta->_start = getLine(line, buf, meta->_start)) >= 0) {

		/* if this is a key/val pair.. */
		if (strchr(line, '=')) {
	
			splitLine(line, key, val);
			/*_fcpLog(FCP_LOG_DEBUG, "encountered key \"%s\" with value \"%s\"", key, val);*/
			
			/* Set type if not already set */
			if (meta->cdocs[doc_index]->type == 0) {

				if (!strncasecmp(key, "Redirect.", 9))
					meta->cdocs[doc_index]->type = META_TYPE_REDIRECT;
				
				else if (!strncasecmp(key, "DateRedirect.", 13))
					meta->cdocs[doc_index]->type = META_TYPE_DBR;
				
				else if (!strncasecmp(key, "SplitFile.", 10))
					meta->cdocs[doc_index]->type = META_TYPE_SPLITFILE;
				
				/* potentially none of the above may execute.. this is by design */
			}
			
			/* we handle "Name" seperately according to the spec */
			if (!strncasecmp(key, "Name", 4)) {
				meta->cdocs[doc_index]->name = strdup(val);
			}

			else { /* add the key/val pair to the current document */
				/* find the first available position in the array for the new pair */
				field_index = meta->cdocs[doc_index]->field_count * 2;

				/* add one to the field counter */
				meta->cdocs[doc_index]->field_count++;

				/* finally add the key and val */
				meta->cdocs[doc_index]->data[field_index]   = strdup(key);
				meta->cdocs[doc_index]->data[field_index+1] = strdup(val);
				
				_fcpLog(FCP_LOG_DEBUG, "stored %s/%s in locations %d,%d", key, val, field_index, field_index+1);
			}
		} /* end of processing key/val pairs */

		/* it's *not* a key/val pair */
		else {

			if (!strncasecmp(line, "EndPart", 7))
				return STATE_WAIT_DOCUMENT;

			else if (!strncasecmp(line, "End", 3))
				return STATE_END;
		
			else {
			_fcpLog(FCP_LOG_DEBUG, "encountered unhandled pair; \"%s\"", line);
			}
		}
	}

	return 0;
}


#ifdef fcpMetadata_debug

int main(int argc, char *argv[])
{
	char *mdata;
	char *val;

	hMetadata *meta;
	hDocument *doc;

	int rc;

	/* yes this is correct; string literals are allocated by compiler generated
		 code in read-only memory space */
	mdata = "Version\n" \
    "Revision=1\n" \
    "EndPart\n" \
    "Document\n" \
    "Redirect.Target=CHK@aabbccddee\n" \
    "EndPart\n" \
    "Document\n" \
    "Name=split\n" \
    "SplitFile.Size=102400\n" \
    "SplitFile.BlockCount=3\n" \
    "SplitFile.Block.1=freenet:CHK@aabbccddee1\n" \
    "SplitFile.Block.2=freenet:CHK@aabbccddee2\n" \
    "SplitFile.Block.3=freenet:CHK@aabbccddee3\n" \
    "Info.Format=text/plain\n" \
    "EndPart\n" \
    "Document\n" \
    "Name=date-redirect\n" \
    "DateRedirect.Increment=93a80\n" \
    "DateRedirect.Offset=a8c0\n" \
    "DateRedirect.Target=SSK@aabbccddee/something\n" \
    "End\n";
      
	meta = (hMetadata *)malloc(sizeof (hMetadata));
	memset(meta, 0, sizeof (hMetadata));

	rc = _fcpMetaParse(meta, mdata);
	if (rc != 0) {
		printf("_fcpMetaParse returned error: %d\n", rc);
		return 1;
	}

	/* test doc lookup */
	if (!(doc = cdocFindDoc(meta, "date-redirect"))) {
		printf("cdocFindDoc returned NULL");
		return -1;
	}

	/* test field lookup within doc */
	if (!(val = cdocLookupKey(doc, "DateRedirect.Target"))) {
		printf("cdocLookupKey returned NULL");
		return -1;
	}

	printf("val: %s\n", val);

	return 0;
}

#endif

