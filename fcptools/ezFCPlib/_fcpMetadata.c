/*
  This code is part of FCPTools - an FCP-based client library for Freenet

  CopyLeft (c) 2001 by David McNab

  Developers:
  - David McNab <david@rebirthing.co.nz>
  - Jay Oliveri <ilnero@gmx.net>
  
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

#include "ez_sys.h"


/* private definitions */
static int getLine(char *, char *, int);
static int splitLine(char *, char *, char *);

static int parse_version(hMetadata *, char *);
static int parse_document(hMetadata *, char *);


/* Parse states.
	 The meaning has changed a bit from the old code. */
#define STATE_WAIT_VERSION  1
#define STATE_IN_VERSION    2
#define STATE_WAIT_DOCUMENT 3
#define STATE_IN_DOCUMENT   4

#define STATE_WAIT_END      5
#define STATE_END           6

/*
	FUNCTION:_fcpMetaParse()


	PARAMETERS:

	- meta: An allocated hMetadata struct.

	IN:
	-

	OUT:
	-

	RETURNS: Zero on success, <0 on error.
*/
int _fcpMetaParse(hMetadata *meta, char *buf)
{
  char  line[513];
	int   rc;

	if (!meta) {
		_fcpLog(FCP_LOG_DEBUG, "meta is NULL");
		return -1;
	}
	
	_fcpLog(FCP_LOG_DEBUG, "Entered _fcpMetaParse()");

	/* here's the silly use of _start; holds the 'start' value so that we can
		 return a 'state' value as well */
	
	meta->size += strlen(buf);
	
	/* prime the loop */
	meta->_start = getLine(line, buf, 0);

  while (meta->_start >= 0) { /* loop until we process all lines in the metadata string */
		
		if (!strncasecmp(line, "Version", 7)) {
			rc = parse_version(meta, buf);

			if ((rc != STATE_WAIT_DOCUMENT) && (rc != STATE_END)) {
				_fcpLog(FCP_LOG_DEBUG, "expected Version body");
				return -1;
			}
		}
		
		else if (!strncasecmp(line, "Document", 8)) {
			rc = parse_document(meta, buf);

			if ((rc != STATE_WAIT_DOCUMENT) && (rc != STATE_END)) {
				_fcpLog(FCP_LOG_DEBUG, "expected Document body");
				return -1;
			}
		}
		
		if (rc == STATE_END) {
			
			/* if there's a rest section to merge with another rest section, bail */
			if ((meta->rest) && (buf[meta->_start] != 0)) {
				_fcpLog(FCP_LOG_CRITICAL, "Cannot merge 2 sets of 'REST' section metadata");
				return -1;
			}
			
			if (buf[meta->_start] != 0) {
				meta->rest = strdup(buf+meta->_start);
			}
			
			meta->_start = -1;
		}
		
		else
			meta->_start = getLine(line, buf, meta->_start);
  }

	if (meta->raw) free(meta->raw);
	meta->raw = strdup(buf);
	
	_fcpLog(FCP_LOG_DEBUG, "Exiting _fcpMetaParse()");
	return 0;
}

/*
	FUNCTION:_fcpMetaString()

	Assembles a raw-metadata representation of the contents of an hMetadata
	struct. It can be sent in raw form to a node as part of a ClientPut.

	PARAMETERS:

	- meta: An allocated hMetadata struct containing parsed metadata.

	IN:

	OUT:

	RETURNS: Raw metadata c-string, 0 on error.
*/
char *_fcpMetaString(hMetadata *meta)
{
	char *buf;
	char s[513];

	int  i, j;

	snprintf(s, 512, "Version\nRevision=1\n");
	buf = malloc(strlen(s)+1);
	strcpy(buf, s);

	if (meta->encoding) {
		snprintf(s, 512, "Encoding=%s\n", meta->encoding);
		buf = realloc(buf, strlen(buf)+strlen(s)+1);
		strcat(buf, s);
	}

	for (i=0; i < meta->cdoc_count; i++) {

		if (meta->cdocs[i]->name)
			snprintf(s, 512, "EndPart\nDocument\nName=%s\n", meta->cdocs[i]->name);
		else
			snprintf(s, 512, "EndPart\nDocument\n");
		
		buf = realloc(buf, strlen(buf)+strlen(s)+1);
		strcat(buf, s);
		
		if (meta->cdocs[i]->format) {
			snprintf(s, 512, "Info.Format=%s\n", meta->cdocs[i]->format);
			buf = realloc(buf, strlen(buf)+strlen(s)+1);
			strcat(buf, s);
		}
		
		if (meta->cdocs[i]->description) {
			snprintf(s, 512, "Info.Description=%s\n", meta->cdocs[i]->description);
			buf = realloc(buf, strlen(buf)+strlen(s)+1);
			strcat(buf, s);
		}
		
		for (j=0; j < meta->cdocs[i]->field_count; j+=2) {

			snprintf(s, 512, "%s=%s\n",
							 meta->cdocs[i]->data[j],
							 meta->cdocs[i]->data[j+1]);

			buf = realloc(buf, strlen(buf)+strlen(s)+1);
			strcat(buf, s);
		}
	}

	buf = realloc(buf, strlen(buf)+strlen("End\n")+1);
	strcat(buf, "End\n");

	if (meta->rest) {
		buf = realloc(buf, strlen(buf)+strlen(meta->rest)+1);
		strcat(buf, meta->rest);
	}
	
	return buf;
}

hDocument *cdocAddDoc(hMetadata *meta, char *cdocName)
{
	int doc_index;

	/* doc_index holds position of new hDocument pointer */
	doc_index = meta->cdoc_count;

	meta->cdoc_count++;

	if (doc_index == 0)
		meta->cdocs = malloc(sizeof (hDocument *));
	else
		meta->cdocs = realloc(meta->cdocs, sizeof (hDocument *) * (doc_index+1));

	meta->cdocs[doc_index] = _fcpCreateHDocument();
		
	if (cdocName) meta->cdocs[doc_index]->name = strdup(cdocName);

	return meta->cdocs[doc_index];
}


int cdocAddKey(hDocument *doc, char *key, char *val)
{
	int field_index;

	field_index = doc->field_count * 2;
	
	/* add one to the field counter */
	doc->field_count++;
	
	if (field_index == 0)
		doc->data = malloc(sizeof (char *) * 2);
	else
		doc->data = realloc(doc->data, sizeof (char *) * (field_index+1) * 2);
	
	/* finally add the key and val */
	doc->data[field_index]   = strdup(key);
	doc->data[field_index+1] = strdup(val);
	
	_fcpLog(FCP_LOG_DEBUG, "stored %s/%s in locations %d,%d", key, val, field_index, field_index+1);
	
	return 0;
}


/*
	Given a document name (null for default doc), return a pointer to the
	hDocument struct.
*/
hDocument *cdocFindDoc(hMetadata *meta, char *cdocName)
{
	int i;

	/* start the search from the end, fulfilling the requirement that documents
		 with the same name will cause FCPLib to use the *last* document in 
		 the set (check the metadata spec). */

	for (i = meta->cdoc_count-1; i >= 0; i--) {

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
	int line_index = 0;
  
  if (!buf) return -1;

	memset(line, 0, 512);
	
	/* If we're done, return w/ -2; */
	if (buf[start] == 0) return -2;

	while (1) { /* :) */

		if ((buf[start + line_index] == '\n') ||
				(buf[start + line_index] == 0) ||
				(line_index > 512))
			break;

		else {
			line[line_index] = buf[start + line_index];
			line_index++;
		}
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
  char  line[513];
  char  key[257];
  char  val[257];

	long  l;
	
	while ((meta->_start = getLine(line, buf, meta->_start)) >= 0) {

		_fcpLog(FCP_LOG_DEBUG, "read line \"%s\"", line);

		if (!strncasecmp(line, "Revision=", 9)) {

			if (splitLine(line, key, val)) {
				_fcpLog(FCP_LOG_DEBUG, "expected value for Revision key");
				return -1;
			}

			l = xtol(val);

			if ((meta->revision != 0) && (meta->revision != l)) {
				_fcpLog(FCP_LOG_CRITICAL, "Metadata versions do not match");
				return -1;
			}

			meta->revision = l; /* redundant but ok */

			_fcpLog(FCP_LOG_DEBUG, "key Revision; val \"%s\"", val);
		}
	
		else if (!strncasecmp(line, "Encoding=", 9)) {
			
			if (splitLine(line, key, val)) {
				_fcpLog(FCP_LOG_DEBUG, "expected value for Encoding key");
				return -1;
			}

			meta->encoding = strdup(val);

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
  char  line[513];
  char  key[257];
  char  val[257];

	int   doc_index;

	hDocument *doc;

	doc_index = meta->cdoc_count;
	meta->cdoc_count++;

	key[0] = 0;
	val[0] = 0;

	if (doc_index == 0)
		meta->cdocs = malloc(sizeof (hDocument **));
	else
		meta->cdocs = realloc(meta->cdocs, sizeof (hDocument **) * meta->cdoc_count);

	doc = _fcpCreateHDocument();
	
	/* for some reason the following loop gets called twice
		under certain circumstances */
	
	while ((meta->_start = getLine(line, buf, meta->_start)) >= 0) {

		/* if this is a key/val pair.. */
		if (strchr(line, '=')) {
	
			splitLine(line, key, val);
			
			/* we handle "Name" seperately according to the spec */
			if (!strncasecmp(key, "Name", 4)) {

				doc->name = strdup(val);
				continue;
			}

			else if (!strncasecmp(key, "Info.Format", 11))
				doc->format = strdup(val);

			else if (!strncasecmp(key, "Info.Description", 16))
				doc->description = strdup(val);
			
			else {
				cdocAddKey(doc, key, val);
			}

			/* Set type if not already set */
			if (doc->type == 0) {

				if (!strncasecmp(key, "Redirect.", 9))
					doc->type = META_TYPE_REDIRECT;
				
				else if (!strncasecmp(key, "DateRedirect.", 13))
					doc->type = META_TYPE_DBR;
				
				else if (!strncasecmp(key, "SplitFile.", 10))
					doc->type = META_TYPE_SPLITFILE;
			}

		} /* end of processing key/val pairs */

		/* it's *not* a key/val pair */
		else {

			if (!strncasecmp(line, "EndPart", 7)) {

				meta->cdocs[doc_index] = doc;
				return STATE_WAIT_DOCUMENT;
			}

			else if (!strncasecmp(line, "End", 3)) {

				meta->cdocs[doc_index] = doc;
				return STATE_END;
			}
			
			else {
				_fcpLog(FCP_LOG_DEBUG, "encountered unexpected token; \"%s\"", line);
			}
		}
	}

	return 0;
}


#ifdef fcpMetadata_debug

int main(int argc, char *argv[])
{
	char *str;
	char *val;
 
	FILE *file;
	int   fno;
	int   i;

	hMetadata *meta;
	hDocument *doc;

	unsigned long ul;
	int rc;

	_fcpOpenLog(stdout, 4);

	/* bleah */
	ul = _fcpFilesize("meta.dat");

	str = malloc(ul + 1);

	file = fopen("meta.dat", "rb");
	fno = fileno(file);

	read(fno, str, ul);
	str[ul]=0;

	/*printf("\n*************************************************\n:%s:\n", str);
		printf("*************************************************\n");*/
	
	meta = _fcpCreateHMetadata();

	rc = _fcpMetaParse(meta, str);

	if (rc != 0) {
		printf("_fcpMetaParse returned error: %d\n", rc);
		return 1;
	}
	
	/* dump the read contents */

	printf("\nMETADATA:\n:%s:\n", _fcpMetaString(meta));
	
	printf("\nsize: %lu, revision: %d, encoding: :%s:, doc_count: %d\nraw: :%s:\nrest: :%s:\n\n",
				 meta->size, meta->revision, meta->encoding, meta->cdoc_count, meta->raw, meta->rest);
	
	for (i=0; i < meta->cdoc_count; i++) {
		int j;
		
		printf("doc %d/%d, doc_name: :%s:, field_count: %d, ", i+1, meta->cdoc_count,
					 meta->cdocs[i]->name, meta->cdocs[i]->field_count);
		printf("format: :%s:, description: :%s:\n", meta->cdocs[i]->format, meta->cdocs[i]->description);
		
		for (j=0; j < meta->cdocs[i]->field_count; j+=2) {
			printf("name: %s, val: %s\n", meta->cdocs[i]->data[j], meta->cdocs[i]->data[j+1]);
		}
		
		printf("\n");
	}

	return 0;
}

#endif

