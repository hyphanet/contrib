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
  MERCHANTABILITY or FITNESS FOR A PARTICU`LAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

#include "ezFCPlib.h"

#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "ez_sys.h"

static int doRoutingKeySection(hURI *uri, char **key);
static int doCryptoKeySection(hURI *uri, char **key);
static int doDocnameSection(hURI *uri, char **key);
static int doMetastringSection(hURI *uri, char **key);

/*
	fcpParseHURI()

	This function parses a string containing a fully-qualified Freenet URI
	into simpler components.  It is written to be re-entrant on the same
	hURI pointer (it can be called repeatedly without being re-created.
	
	FreenetURI handles parsing and creation of the Freenet URI format, defined
	as follows:
	
	freenet:[KeyType@]RoutingKey[,CryptoKey][/docname][//metastring]

	FCPLib supports the following formats for KSK's, SSK's, and CHK's:
	
	KSK's
	[freenet:][KSK@]RoutingKey[//metastring]
	
	SSK's
	[freenet:]SSK@RoutingKey[,CryptoKey][/docname][//metastring]

	CHK's
	[freenet:]CHK@RoutingKey[,CryptoKey][/docname]

*/
int fcpParseHURI(hURI *uri, char *key)
{
	int rc;
	int i;

	char *p_key;
	char *startNameVal;

	if (strlen(key) >= L_URI) {
		_fcpLog(FCP_LOG_DEBUG, "uri too large; maximum length is %d", L_URI);
		
		rc = 1;
		goto cleanup;
	}
		
	/* clear out the dynamic arrays before attempting to parse a new uri */
  if (uri->routingkey) free(uri->routingkey);
  if (uri->cryptokey) free(uri->cryptokey);
	if (uri->filename) free(uri->filename);
	if (uri->metastring) free(uri->metastring);
	if (uri->uri_str) free(uri->uri_str);
	
	/* save the key root */
	p_key = key;
	
	/* zero the block of memory */
	memset(uri, 0, sizeof (hURI));

	/* copy over the raw string */
	uri->uri_str = strdup(key);

	if (!strncmp(key, "freenet:", 8)) key += 8;
	
  /* classify key header */
  if (!strncasecmp(key, "CHK@", 4)) {

    uri->type = KEY_TYPE_CHK;
		key += 4;
	}

  else if (!strncasecmp(key, "SSK@", 4)) {
    uri->type = KEY_TYPE_SSK;
		key += 4;
	}
	
	/* when in doubt assume it's a KSK */
  else
    uri->type = KEY_TYPE_KSK;

	/*******************************************************************/
	
	if (doRoutingKeySection(uri, &key) != 0) {
		rc = -1;
		goto cleanup;
	}
	
	if (uri->type == KEY_TYPE_KSK) {

		if (*key=='/' && *(key+1)=='/') {
		
			key += 2;
			
			if (doMetastringSection(uri, &key) != 0) {
				rc = -1;
				goto cleanup;
			}
		}
	}
	
	else if (uri->type == KEY_TYPE_CHK) {
	
		if (*key==',') {
			
			key += 1;
			
			if (doCryptoKeySection(uri, &key)) {
				rc = -1;
				goto cleanup;
			}
		}
		
		if (*key=='/' && *(key+1)!='/') {
		
			key += 1;
			
			if (doDocnameSection(uri, &key)) {
				rc = -1;
				goto cleanup;
			}
		}
	}
		
	else if (uri->type == KEY_TYPE_SSK) {
		
		if (*key==',') {
			
			key += 1;
			
			if (doCryptoKeySection(uri, &key)) {
				rc = -1;
				goto cleanup;
			}
		}
		
		if (*key=='/' && *(key+1)!='/') {
		
			key += 1;
			
			if (doDocnameSection(uri, &key)) {
				rc = -1;
				goto cleanup;
			}
		}

		if (*key=='/' && *(key+1)=='/') {
		
			key += 1;
			
			if (doMetastringSection(uri, &key)) {
				rc = -1;
				goto cleanup;
			}
		}
	}
	
	/* DONE */
 
 success:
	
	_fcpLog(FCP_LOG_DEBUG, "uri: %s", uri->uri_str);
	rc = 0;
	
 cleanup:

  return rc;
}		


char *_fcpDBRString(hURI *uri, int future)
{
	char *uri_str;

	time_t now_t;
	time_t days_t;
	
	time(&now_t);

	/* calculate today */
	days_t = (now_t - (now_t % 86400));

	/* then add the number of 'future' days in DBR */
	days_t += (future * 86400);
	
	/* now use this number (in hex) and build up the new URI */
	uri_str = malloc(1025);

	switch (uri->type) {

	case KEY_TYPE_SSK:

		snprintf(uri_str, 1024, "freenet:SSK@%s/%x-%s//", uri->routingkey, (unsigned)days_t, uri->filename);
		break;

	case KEY_TYPE_KSK:

		snprintf(uri_str, 1024, "freenet:KSK@%x-%s", (unsigned)days_t, uri->routingkey);
		break;

	default:

		free(uri_str);
		return 0;
	}

	uri_str = realloc(uri_str, strlen(uri_str)+1);
	return uri_str;	
}
		

/**********************************************************************
	STATIC functions
**********************************************************************/


static int doRoutingKeySection(hURI *uri, char **key) {

	int i;
	
	uri->routingkey = malloc(L_KEY+1);
	
	if (uri->type == KEY_TYPE_KSK) {
		
		i = 0;
		while (i < L_KEY) {

			/* break when we hit null */
			if (*(*key+i) == '\0') break;
			
			/* break if we've hit the '//' pair */
			if ((*(*key+i) =='/') && (*(*key+i+1) == '/')) break;

			uri->routingkey[i] = *(*key+i);
			i++;
		}
				
		uri->routingkey[i] = 0;
		*key += i;
	}
	
	else if ((uri->type == KEY_TYPE_SSK) || (uri->type == KEY_TYPE_CHK)) {
	
		i = 0;
		while (i < L_KEY) {

			/* break when we hit null */
			if (*(*key+i) == '\0') break;
			
			if ((*(*key+i) == '/') || (*(*key+i) == ',')) break;
		
			uri->routingkey[i] = *(*key+i);
			i++;
		}
					
		uri->routingkey[i] = 0;
		*key += i;
	}

	uri->routingkey = realloc(uri->routingkey, strlen(uri->routingkey));
	return 0;
}


static int doCryptoKeySection(hURI *uri, char **key) {

	int i;
	
	uri->cryptokey = malloc(L_KEY+1);
	
	if ((uri->type == KEY_TYPE_SSK) || (uri->type == KEY_TYPE_CHK)) {

		i = 0;
		while (i < L_KEY) {
	
			if ((*(*key+i) == '/') || (*(*key+i) == ',')) break;
	
			/* break when we hit null */
			if (*(*key+i) == '\0') break;
					
			uri->cryptokey[i] = *(*key+i);
			i++;
		}
	
		uri->cryptokey[i] = 0;
		*key += i;
	}
	
	uri->cryptokey = realloc(uri->cryptokey, strlen(uri->cryptokey));
	return 0;
}


static int doDocnameSection(hURI *uri, char **key) {

	int i;

	uri->filename = malloc(L_KEY+1);
	
	if ((uri->type == KEY_TYPE_SSK) || (uri->type == KEY_TYPE_CHK)) {

		i = 0;
		while (i < L_KEY) {
	
			if ((*(*key+i) == '/') && (*(*key+i+1) == '/')) break;
	
			/* break when we hit null */
			if (*(*key+i) == '\0') break;
					
			uri->filename[i] = *(*key+i);
			i++;
		}
				
		uri->filename[i] = 0;
		*key += i;
	}

	uri->filename = realloc(uri->filename, strlen(uri->filename));
	return 0;
}


static int doMetastringSection(hURI *uri, char **key) {

	int i;

	uri->metastring = malloc(L_KEY+1);
	
	i = 0;
	while (i < L_KEY) {

		/* break when we hit null */
		if (*(*key+i) == '\0') break;
				
		uri->metastring[i] = *(*key+i);
		i++;
	}
			
	uri->metastring[i] = 0;
	*key += i;

	uri->metastring = realloc(uri->metastring, strlen(uri->metastring));
	return 0;

}

