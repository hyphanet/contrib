/*
	metaParse.c

	New simplified fieldset-based parser for ezFCPlib.

	This replaces the yacc-generated parser from older
	versions of ezFCPlib.
*/

#include "ezFCPlib.h"

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

// parse states - internal use only

#define STATE_BEGIN         0       // awaiting 'Version'
#define STATE_INHDR         1       // awaiting 'Revision'
#define STATE_WAITENDHDR    2       // awaiting 'End' or 'EndPart'
#define STATE_WAITDOC       3       // awaiting 'Document'
#define STATE_INDOC         4       // processing field declarations
#define STATE_END           5       // after 'End'


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
	
	int   state = STATE_BEGIN;
	int   thisdoc = 0;
	int   thiskey = 0;
	
	// init metadata structure
	meta->vers[0] = 0;
	meta->count = 0;
	meta->cdoc[0] = 0;
	
	start = getLine(line, buf, 0);
	while (start != -1) {

		_fcpLog(FCP_LOG_DEBUG, "DEBUG: line: %s", line);

		splitLine(line, key, val);
		_fcpLog(FCP_LOG_DEBUG, "DEBUG: key: %s, val: %s", key, val);

		// now process key=val pair
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
					// Set type if not already set
					if (meta->cdoc[thisdoc]->type == META_TYPE_04_NONE) {
						if (!strcasecmp(key, "Redirect.Target"))
							meta->cdoc[thisdoc]->type = META_TYPE_04_REDIR;

						else if (!strcasecmp(key, "DateRedirect.Target"))
							meta->cdoc[thisdoc]->type = META_TYPE_04_DBR;

						else if (!strncasecmp(key, "SplitFile", 9))
							meta->cdoc[thisdoc]->type = META_TYPE_04_SPLIT;
					}

					// append key-value pair
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

	} // 'for (each line of metadata)'

	// all done
	return 0;
	
} // 'metaParse()'


/*
	metaFree()

	a destructor routine for a META04 structure
*/
void metaFree(META04 *meta)
{
	int i, j;
	
	if (!meta) return;
	
	// free each cdoc
	for (i = 0; i < meta->count; i++) {
	
		// free all field-value pairs within current cdoc
		for (j = 0; j < meta->cdoc[i]->count; j++)
			free(meta->cdoc[i]->keys[j]);
		
		// now ditch this cdoc
		free(meta->cdoc[i]);
	}
	
	// now ditch whole structure
	free(meta);

} // 'metaFree()'


//
// Metadata lookup routines
//
// These functions look up any named key within any of the named cdocs
// (or the first unnamed cdoc), and convert them to the desired format
//

long cdocIntVal(META04 *meta, char *cdocName, char *keyName, long defVal)
{
    char *val;
    
    if (meta == NULL)
        return defVal;

    val = cdocStrVal(meta, cdocName, keyName, NULL);
    if (val == NULL)
        return defVal;
    else
        return atol(val);
}               // 'cdocStrVal()'


long cdocHexVal(META04 *meta, char *cdocName, char *keyName, long defVal)
{
    char *val;

    if (meta == NULL)
        return defVal;

    val = cdocStrVal(meta, cdocName, keyName, NULL);
    if (val == NULL)
        return defVal;
    else
        return xtoi(val);

}               // 'cdocStrVal()'


char *cdocStrVal(META04 *meta, char *cdocName, char *keyName, char *defVal)
{
    FLDSET *fldset;
    char *keyStr;

    if (meta == NULL)
        return NULL;

    fldset = cdocFindDoc(meta, cdocName);
    if (fldset == NULL)
        return NULL;        // no cdoc of that name sorry
    else if ((keyStr = cdocLookupKey(fldset, keyName)) == NULL)
        return defVal;      // found named cdoc but not key
    else
        return keyStr;      // got it
}               // 'cdocStrVal()'


//
// look up a named document within metadata
//

FLDSET *cdocFindDoc(META04 *meta, char *cdocName)
{
    int i;
    char *s;

    if (meta == NULL)
        return NULL;

    if (cdocName == NULL || cdocName[0] == '\0')
    {
        // search for first unnamed cdoc
        for (i = 0; i < meta->count; i++)
            if (cdocLookupKey(meta->cdoc[i], "Name") == NULL)
                // no name in this cdoc
                return meta->cdoc[i];
        // no unnamed cdocs
        return NULL;
    }
    else
    {
        // search for named cdoc
        for (i = 0; i < meta->count; i++)
            if ((s = cdocLookupKey(meta->cdoc[i], cdocName)) != NULL
                && !strcasecmp(s, cdocName)
            )
                return meta->cdoc[i];
        // no cdoc matching name
        return NULL;
    }
}               // 'cdocFindDoc()'


//
// cdocLookupKey
//
// given a fieldset pointer, look up a key's raw string value
//

char *cdocLookupKey(FLDSET *fldset, char *keyName)
{
    int i;

    if (fldset == NULL)
        return NULL;

    // spit if no key name given
    if (keyName == NULL || keyName[0] == '\0')
        return NULL;

    for (i = 0; i < fldset->count; i++)
        if (!strcasecmp(fldset->keys[i]->name, keyName))
            // found it
            return fldset->keys[i]->value;

    // no key of that name sorry
    return NULL;

} // 'cdocLookupKey()'

/*
	Private functions
*/

/*
	Split a line of the form 'key [= value] into the key/value pair
	return 0 on success.
*/

int splitLine(char *line, char *key, char *val)
{
	if (strchr(line, '=')) {
		while (*line != '=') *key++ = *line++;
		
		// add the trailing NULL
		*key = 0;
		line++;

		// now copy the key's value
		while (*val++ = *line++);
		return 0;
	}

	// here, the line is a key, no value
	while (*key++ = *line++);
	val[0] = 0;

	return 0;
}

/*
	This function should return a 'line', terminated by NULL only
	(no carriage-return/linefeed nonsense)
*/
int getLine(char *line, char *buf, int start)
{
	int  eol;

	if (!buf) return -1;

	line[0] = 0;
	while (buf[start]) {

		// find end of line
		eol = start;
		while ((buf[eol] != '\n') && (buf[eol] != 0)) eol++;

		// are we at \n, or NULL?
		if (buf[eol] == '\n') {
			strncpy(line, buf+start, eol-start);
			line[eol-start] = 0;
			
			return -1;
		}
		else {
			strcpy(line, buf+start);

			return -1;
		}
	}

	return 1;
} // 'getLine()'
