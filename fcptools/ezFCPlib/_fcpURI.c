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

#include "ezFCPlib.h"

#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "ez_sys.h"


/*
	fcpParseHURI()

	This function parses a string containing a fully-qualified Freenet URI
	into simpler components.  It is written to be re-entrant on the same
	hURI pointer (it can be called repeatedly without being re-created.
	
	FreenetURI handles parsing and creation of the Freenet URI format, defined
	as follows:
	
	freenet:[KeyType@]RoutingKey[,CryptoKey][,n1=v1,n2=v2,...][/docname][//metastring]
	
	where KeyType is the TLA of the key (currently SVK, SSK, KSK, or CHK). If
	omitted, KeyType defaults to KSK.
	
	For KSKs, the string keyword (docname) takes the RoutingKey position and the
	remainder of the fields are inapplicable (except metastring). Examples:
	freenet:KSK@foo//bar freenet:KSK@test.html freenet:test.html
	
	RoutingKey is the modified Base64 encoded key value. CryptoKey is the
	modified Base64 encoded decryption key.
	
	Following the RoutingKey and CryptoKey there may be a series of
	name=value pairs representing URI meta-information.
	
	The docname is only meaningful for SSKs, and is hashed with the PK
	fingerprint to get the key value.
	
	The metastring is meant to be passed to the metadata processing systems that
	act on the retrieved document.
*/
int fcpParseHURI(hURI *uri, char *key)
{
	int rc;
	int len;

	char *p_key;
	char *uri_s;

	uri_s = malloc(strlen(key)+128);

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

  /* skip 'freenet:' */
  if (!strncmp(key, "freenet:", 8))
    key += 8;
	
  /* classify key header */
  if (!strncmp(key, "SSK@", 4)) {
		int i;
		
    uri->type = KEY_TYPE_SSK;
		strcpy(uri_s, "freenet:SSK@");

		key += 4;

		/* next character can be {',', '/', '//', '\0'} */

		/* copy all the characters in the RoutingKey section */
		uri->routingkey = malloc(L_KEY+1);

		for (i=0; (*key != ',') && (*key != '/') && (*key != 0); key++)
			uri->routingkey[i++] = *key;

		/* grab the routingkey/RoutingKey */
		uri->routingkey[i] = 0;
		strcat(uri_s, uri->routingkey);

		/* if we're on a ',', then we either next have to parse a
			 CryptoKey or n1=v1 pair */
		if (*key == ',') {
			int doNameValPairs;

			key++;
			doNameValPairs = 0;

			/* test the current section to see if it's a CryptoKey */
			for (i=0; key[i] != '\0'; i++) {

				if (key[i] == ',') { /* we have a CryptoKey, *and* n1=v1 pairs */

					uri->cryptokey = malloc(i+1);
					memcpy(uri->cryptokey, key, i);
					uri->cryptokey[i] = 0;

					/* build the uri_s version */
					strcat(uri_s, ",");
					strcat(uri_s, uri->cryptokey);

					key += i;

					/* set so that in next section we look for the name/val pairs */
					doNameValPairs = 1;

					break;
				}
				
				if (key[i] == '/') { /* we have a CryptoKey, and we have *no* n1=v1 pairs */

					uri->cryptokey = malloc(i+1);
					
					memcpy(uri->cryptokey, key, i);
					uri->cryptokey[i] = 0;

					/* build the uri_s version */
					strcat(uri_s, ",");
					strcat(uri_s, uri->cryptokey);

					key += i;

					break;
				}

				if (key[i] == '=') { /* we have *no* CryptoKey, but only n1=v1 pairs */

					/* set so that in next section we look for the name/val pairs */
					doNameValPairs = 1;

					break;
				}
			}

			/* if we hit a NULL char then we have a crypto key and that's it! */
			if (*key=='\0') {
				
				uri->cryptokey = malloc(i+1);
				memcpy(uri->cryptokey, key, i);
				uri->cryptokey[i] = '\0';

				/* build the uri_s version */
				strcat(uri_s, ",");
				strcat(uri_s, uri->cryptokey);
				
				goto success;
			}

			/* we have the crypto key (if there was one); process the name/val
				 pairs*/

			if (doNameValPairs) {

				/* parse em */
				/* ensure key points to a '/' or '\0' */
			}
		}

		/* here, key points to the start of the docname section, the start of the
			 metastring section or '\0' */

		/* check if key is a '/' */
		if ((*key == '/') && (*(key+1) != '/')) {

			key++;
			if (*key == '\0') {

				rc = -1;
				goto cleanup;
			}

			/* iterate through docname and stop at '//' or '\0' */
			for (i=0; key[i] != '\0'; i++) {

				/* if key is a '//' then break */
				if ((key[i]=='/') && (key[i+1]== '/')) break;
			}					

			uri->filename = malloc(i+1);
			memcpy(uri->filename, key, i);
			uri->filename[i] = '\0';

			/* build the uri_s version */
			strcat(uri_s, "/");
			strcat(uri_s, uri->filename);
		}
		
		/* advance past the actual filename part */
		key += i;
		
		/* if we also have a metastring.. */
		if ((*key=='/') && (*(key+1)=='/')) {

			/* position after the '//' */
			key += 2;

			/* copy over the metastring */
			uri->metastring = strdup(key);
			
			/* build the uri_s version */
			strcat(uri_s, "//");
			strcat(uri_s, uri->metastring);
		}
	}
	
  else if (!strncmp(key, "CHK@", 4)) {
	
		uri->type = KEY_TYPE_CHK;

		strcpy(uri_s, "freenet:CHK@");
		key += 4;

		len = strlen(key);

		if (len) {
			int i;

			for (i=0; (key[i]!='\0') && (key[i]!=',') && (key[i]!='/'); i++);
			
			uri->routingkey = malloc(i+1);
			memcpy(uri->routingkey, key, i);
			uri->routingkey[i] = '\0';

			strcat(uri_s, uri->routingkey);
			
			if (key[i]=='\0') goto success;
			
			/* get the CryptoKey if it exists */
			if (key[i]==',') {
				
				key += i+1;
				
				for (i=0; (key[i]!='\0') && (key[i]!='/'); i++);

				uri->cryptokey = malloc(i+1);
				memcpy(uri->cryptokey, key, i);
				uri->cryptokey[i] = '\0';				

				strcat(uri_s, ",");
				strcat(uri_s, uri->cryptokey);
			}
			
			if (key[i]=='\0') goto success;
			
			key += i;
			uri->filename = strdup(key);
			
			strcat(uri_s, "/");
			strcat(uri_s, uri->filename);
		}
	}
	
	/* freenet:KSK@freetext.html */
  else if (!strncmp(key, "KSK@", 4)) {

    uri->type = KEY_TYPE_KSK;

		strcpy(uri_s, "freenet:KSK@");
    key += 4;

		uri->routingkey = strdup(key);
		strcat(uri_s, uri->routingkey);

		/* TODO add logic for handling 'metastring' */
  }
  
  else {
		_fcpLog(FCP_LOG_CRITICAL, "Error attempting to parse invalid key: %s", p_key);
    rc = -1;
		goto cleanup;
  }

 success:

	uri->uri_str = strdup(uri_s);
	_fcpLog(FCP_LOG_DEBUG, "uri: %s", uri->uri_str);

	rc = 0;
	
 cleanup:

	free(uri_s);
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
