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

#include "ezFCPlib.h"

#include <stdlib.h>
#include <string.h>

/*
  EXPORTED DEFINITIONS
*/
int     metaParse(META04 *meta, char *buf);
void    metafree(META04 *meta);

long    cdocIntVal(META04 *meta, char *cdocName, char *keyName, long defVal);
long    cdocHexVal(META04 *meta, char *cdocName, char *keyName, long defVal);
char   *cdocStrVal(META04 *meta, char *cdocName, char *keyName, char *defVal);

FLDSET *cdocFindDoc(META04 *meta, char *cdocName);
char   *cdocLookupKey(FLDSET *fldset, char *keyName);

/*
  IMPORTED DEFINITIONS
*/
extern long xtoi(char *s);

/*
  PRIVATE DEFINITIONS
*/
static int getLine(char *, char *, int);
static int splitLine(char *, char *, char *);

/* parse states - internal use only */

#define STATE_BEGIN         0       /* awaiting 'Version' */
#define STATE_INHDR         1       /* awaiting 'Revision' */
#define STATE_WAITENDHDR    2       /* awaiting 'End' or 'EndPart' */
#define STATE_WAITDOC       3       /* awaiting 'Document' */
#define STATE_INDOC         4       /* processing field declarations */
#define STATE_END           5       /* after 'End' */

/*
  END OF DECLARATIONS
*/

/*
  metaParse
  
  converts a raw buffer of metadata into an organised
  and accessible data structure
*/
int metaParse(META04 *meta, char *buf)
{
  int   start;
  char  line[256];
  char  key[128];
  char  val[128];

  int	metacdoclen;
  int   state;

  int   thisdoc = 0;
  int   thiskey = 0;
 
  metacdoclen=64;
  state = STATE_BEGIN;
   
  meta->cdoc=malloc(64 * sizeof(meta->cdoc));
  meta->count=0;
  
  /* init metadata structure */
  meta->vers[0] = 0;
  meta->count = 0;
  meta->cdoc[0] = 0;
  
  start = getLine(line, buf, 0);
  while (start != -1) {
    
    splitLine(line, key, val);
    
    /* now process key=val pair */
    switch (state) {
    case STATE_BEGIN:
      if (!strcasecmp(key, "Version"))
	state = STATE_INHDR;
      else
	_fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'Version', got '%s'", key);
      break;
      
    case STATE_INHDR:
      if (!strcasecmp(key, "Revision")) {
	if (val[0]) {
	  strcpy(meta->vers, val);
	  state = STATE_WAITENDHDR;
	}
	else
	  _fcpLog(FCP_LOG_NORMAL, "Metadata: 'Revision' nas no value");
      }
      else
	_fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'Revision', got '%s'", key);
      break;
      
    case STATE_WAITENDHDR:
      if (!strcasecmp(key, "EndPart")) {
	state = STATE_WAITDOC;
	break;
      }
      else if (!strcasecmp(key, "End"))
	state = STATE_END;
      else
	_fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'EndPart' or 'End', got '%s'", key);
      break;
      
    case STATE_WAITDOC:
      if (!strcasecmp(key, "Document")) {
	
	thisdoc = meta->count++;
	if (meta->count >= metacdoclen) {
	  void *newcdoc;
	  int newlen=metacdoclen*2;
	  
	  newcdoc=realloc(meta->cdoc, 
			  newlen * sizeof(meta->cdoc));
	  if (!newcdoc)
	    exit(1);
	  meta->cdoc=newcdoc;
	  metacdoclen=newlen;
	}
	meta->cdoc[thisdoc] = (FLDSET *) malloc(sizeof (FLDSET) );
	meta->cdoc[thisdoc]->type = META_TYPE_04_NONE;
	meta->cdoc[thisdoc]->count = 0;
	meta->cdoc[thisdoc]->keys[0] = 0;
	state = STATE_INDOC;
      }
      else
	_fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'Document', got '%s'", key);
      break;
      
    case STATE_INDOC:
      if (!strcasecmp(key, "EndPart"))
	state = STATE_WAITDOC;
      
      else if (!strcasecmp(key, "End"))
	state = STATE_END;
      
      else {
	/* Set type if not already set */
	if (meta->cdoc[thisdoc]->type == META_TYPE_04_NONE) {
	  if (!strcasecmp(key, "Redirect.Target"))
	    meta->cdoc[thisdoc]->type = META_TYPE_04_REDIR;
	  
	  else if (!strcasecmp(key, "DateRedirect.Target"))
	    meta->cdoc[thisdoc]->type = META_TYPE_04_DBR;
	  
	  else if (!strncasecmp(key, "SplitFile", 9))
	    meta->cdoc[thisdoc]->type = META_TYPE_04_SPLIT;
	}
	
	/* append key-value pair */
	thiskey = meta->cdoc[thisdoc]->count++;
	meta->cdoc[thisdoc]->keys[thiskey] = (KEYVALPAIR *) malloc(sizeof (KEYVALPAIR));
	
	strcpy(meta->cdoc[thisdoc]->keys[thiskey]->name, key);
	if (val)
	  strcpy(meta->cdoc[thisdoc]->keys[thiskey]->value, val);
	else
	  meta->cdoc[thisdoc]->keys[thiskey]->value[0] = 0;
	
      }
      break;
    }
    start = getLine(line, buf, start);
  }
  
  return 0;
}

/*
  metaFree()
  
  a destructor routine for a META04 structure
*/
void metaFree(META04 *meta)
{
  int i, j;
  
  if (!meta) return;
  
  /* free each cdoc */
  if (meta->cdoc) {
    for (i = 0; i < meta->count; i++) {
      
      /* free all field-value pairs within current cdoc */
      for (j = 0; j < meta->cdoc[i]->count; j++)
	free(meta->cdoc[i]->keys[j]);
      
      /* now ditch this cdoc */
      free(meta->cdoc[i]);
    }
    free(meta->cdoc);
  }
  
  
  /* now ditch whole structure */
  free(meta);
}

/**********************************************************************/

/*
  Metadata lookup routines
  
  These functions look up any named key within any of the named cdocs
  (or the first unnamed cdoc), and convert them to the desired format
*/

long cdocIntVal(META04 *meta, char *cdocName, char *keyName, long defVal)
{
  char *val;
  
  if (meta == 0)
    return defVal;
  
  val = cdocStrVal(meta, cdocName, keyName, 0);
  if (val == 0)
    return defVal;
  else
    return atol(val);
}

long cdocHexVal(META04 *meta, char *cdocName, char *keyName, long defVal)
{
  char *val;
  
  if (meta == 0)
    return defVal;
  
  val = cdocStrVal(meta, cdocName, keyName, 0);
  if (val == 0)
    return defVal;
  else
    return xtoi(val);
}

char *cdocStrVal(META04 *meta, char *cdocName, char *keyName, char *defVal)
{
  FLDSET *fldset;
  char *keyStr;
  
  if (meta == 0)
    return 0;
  
  fldset = cdocFindDoc(meta, cdocName);
  if (fldset == 0)
    return 0;        /* no cdoc of that name sorry */
  else if ((keyStr = cdocLookupKey(fldset, keyName)) == 0)
    return defVal;      /* found named cdoc but not key */
  else
    return keyStr;      /* got it */
}

/*
  look up a named document within metadata
*/

FLDSET *cdocFindDoc(META04 *meta, char *cdocName)
{
  int i;
  char *s;
  
  if (meta == 0)
    return 0;
  
  if (cdocName == 0 || cdocName[0] == '\0')
    {
      /* search for first unnamed cdoc */
      for (i = 0; i < meta->count; i++)
	if (cdocLookupKey(meta->cdoc[i], "Name") == 0)
	  /* no name in this cdoc */
	  return meta->cdoc[i];
      /* no unnamed cdocs */
      return 0;
    }
  else
    {
      /* search for named cdoc */
      for (i = 0; i < meta->count; i++)
	if ((s = cdocLookupKey(meta->cdoc[i], "Name")) != 0
	    && !strcasecmp(s, cdocName)
            )
	  return meta->cdoc[i];
      /* no cdoc matching name */
      return 0;
    }
}

/*
  cdocLookupKey
  
  given a fieldset pointer, look up a key's raw string value
*/

char *cdocLookupKey(FLDSET *fldset, char *keyName)
{
  int i;
  
  if (fldset == 0)
    return 0;
  
  /* spit if no key name given */
  if (keyName == 0 || keyName[0] == '\0')
    return 0;
  
  for (i = 0; i < fldset->count; i++)
    if (!strcasecmp(fldset->keys[i]->name, keyName))
      /* found it */
      return fldset->keys[i]->value;
  
  /* no key of that name sorry */
  return 0;
}

/**********************************************************************/

/*
  Split a line of the form 'key [= value] into the key/value pair
  return 0 on success.
*/

int splitLine(char *line, char *key, char *val)
{
  if (strchr(line, '=')) {
    while (*line != '=') *key++ = *line++;
    
    /* add the trailing 0 */
    *key = 0;
    line++;
    
    /* now copy the key's value */
    while (*val++ = *line++);
    return 0;
  }
  
  /* here, the line is a key, no value */
  while (*key++ = *line++);
  val[0] = 0;
  
  return 0;
}

/*
  This function should return a 'line', terminated by 0 only
  (no carriage-return/linefeed nonsense)
*/
int getLine(char *line, char *buf, int start)
{
  int  eol;
  
  if (!buf) return -1;

  line[0] = 0;
  while (buf[start]) {
    
    /* find end of linee */
    eol = start;
    while ((buf[eol] != '\n') && (buf[eol] != 0)) eol++;

    /* are we at \n, or 0? */
    if (buf[eol] == '\n') {
      strncpy(line, buf+start, eol-start);
      line[eol-start] = 0;
      
      return eol+1;
    } else {
      strcpy(line, buf+start);
      return eol;
    }
  }
  
  return -1;
}

