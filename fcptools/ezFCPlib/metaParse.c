/*
	metaParse.c

	New simplified fieldset-based parser for ezFCPlib.

	This replaces the yacc-generated parser from older
	versions of ezFCPlib.
*/

#include "ezFCPlib.h"


//
// EXPORTED DEFINITIONS
//

META04  *metaParse(char *buf);
void    metafree(META04 *meta);
long    cdocIntVal(META04 *meta, char *cdocName, char *keyName, long defVal);
long    cdocHexVal(META04 *meta, char *cdocName, char *keyName, long defVal);
char    *cdocStrVal(META04 *meta, char *cdocName, char *keyName, char *defVal);
FLDSET  *cdocFindDoc(META04 *meta, char *cdocName);
char    *cdocLookupKey(FLDSET *fldset, char *keyName);


//
// IMPORTED DEFINITIONS
//

extern long xtoi(char *s);


//
// PRIVATE DEFINITIONS
//

static char    *getLine(char *buf, char **nextPos);
static char    *splitLine(char *buf);

// parse states - internal use only

#define STATE_BEGIN         0       // awaiting 'Version'
#define STATE_INHDR         1       // awaiting 'Revision'
#define STATE_WAITENDHDR    2       // awaiting 'End' or 'EndPart'
#define STATE_WAITDOC       3       // awaiting 'Document'
#define STATE_INDOC         4       // processing field declarations
#define STATE_END           5       // after 'End'


//////////////////////////////////////////////////////////////
//
// END OF DECLARATIONS
//
//////////////////////////////////////////////////////////////


//
// metaParse
//
// converts a raw buffer of metadata into an organised
// and accessible data structure
//

META04 *metaParse(char *buf)
{
    char    *next;

    char    *key;
    char    *val;

    META04  *meta = safeMalloc(sizeof(META04));

    int     state = STATE_BEGIN;
    int     thisdoc = 0;
    int     thiskey = 0;

    // init metadata structure
    meta->vers[0] = '\0';
    meta->numDocs = 0;
    meta->trailingInfo = NULL;
    meta->cdoc = NULL;

    // main parser loop
    for (; (key = getLine(buf, &next)) != NULL; buf = next)
    {
        val = splitLine(key);

        // debug statements
        //if (val == NULL)
        //    printf("Key only: '%s'\n", key);
        //else
        //    printf("Key = '%s', value = '%s'\n", key, val);

        buf = next; // point after this line for next iteration

        // now process key=val pair
        switch (state)
        {
        case STATE_BEGIN:
            if (!strcmp(key, "Version"))
                state = STATE_INHDR;
            else
                _fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'Version', got '%s'", key);
            break;

        case STATE_INHDR:
            if (!strcmp(key, "Revision"))
            {
                if (val != NULL)
                {
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
            if (!strcmp(key, "EndPart"))
            {
                state = STATE_WAITDOC;
                break;
            }
            else if (!strcmp(key, "End"))
                state = STATE_END;
            else
                _fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'EndPart' or 'End', got '%s'", key);
            break;

        case STATE_WAITDOC:
            if (!strcmp(key, "Document"))
            {
                // create empty fieldset struct
                thisdoc = meta->numDocs++;
                meta->cdoc = realloc(meta->cdoc,
		    sizeof(FLDSET *) * meta->numDocs);
                meta->cdoc[thisdoc] = safeMalloc(sizeof(FLDSET));
                meta->cdoc[thisdoc]->type = META_TYPE_04_NONE;
                meta->cdoc[thisdoc]->numFields = 0;
		meta->cdoc[thisdoc]->key = NULL;
                state = STATE_INDOC;
            }
            else
                _fcpLog(FCP_LOG_NORMAL, "Metadata: expected 'Document', got '%s'", key);
            break;

        case STATE_INDOC:
            if (!strcmp(key, "EndPart"))
                state = STATE_WAITDOC;
            else if (!strcmp(key, "End"))
                state = STATE_END;
            else
            {
                // Set type if not already set
                if (meta->cdoc[thisdoc]->type == META_TYPE_04_NONE)
                {
                    if (!strcmp(key, "Redirect.Target"))
                        meta->cdoc[thisdoc]->type = META_TYPE_04_REDIR;
                    else if (!strcmp(key, "DateRedirect.Target"))
                        meta->cdoc[thisdoc]->type = META_TYPE_04_DBR;
                    else if (!strncmp(key, "SplitFile", 9))
                        meta->cdoc[thisdoc]->type = META_TYPE_04_SPLIT;
                }

                // append key-value pair
                thiskey = meta->cdoc[thisdoc]->numFields++;
                meta->cdoc[thisdoc]->key =
                    realloc(meta->cdoc[thisdoc]->key,
                        sizeof(KEYVALPAIR) * meta->cdoc[thisdoc]->numFields);
                meta->cdoc[thisdoc]->key[thiskey].name = strdup(key);
                meta->cdoc[thisdoc]->key[thiskey].value = val ? strdup(val) : NULL;
            }
            break;
        }
    }       // 'for (each line of metadata)'

    // grab trailing info, if any
    if (buf != NULL && *buf != '\0')
        meta->trailingInfo = strdup(buf);

    // all done
    return meta;

}           // 'metaParse()'


//
// metaFree()
//
// a destructor routine for a META04 structure
//

void metaFree(META04 *meta)
{
    int i, j;

    // ignore NULL ptr
    if (meta == NULL)
        return;

    // turf trailing info if any
    if (meta->trailingInfo != NULL)
        free(meta->trailingInfo);

    // free each cdoc
    for (i = 0; i < meta->numDocs; i++)
    {
        // free all field-value pairs within current cdoc
        for (j = 0; j < meta->cdoc[i]->numFields; j++)
        {
            if (meta->cdoc[i]->key[j].name != NULL)
                free(meta->cdoc[i]->key[j].name);
            if (meta->cdoc[i]->key[j].value != NULL)
                free(meta->cdoc[i]->key[j].value);
        }

        // now ditch this cdoc
        free(meta->cdoc[i]);
    }

    // now ditch whole structure
    free(meta);
    // phew! :)
}               // 'metaFree()'


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
        for (i = 0; i < meta->numDocs; i++)
            if (cdocLookupKey(meta->cdoc[i], "Name") == NULL)
                // no name in this cdoc
                return meta->cdoc[i];
        // no unnamed cdocs
        return NULL;
    }
    else
    {
        // search for named cdoc
        for (i = 0; i < meta->numDocs; i++)
            if ((s = cdocLookupKey(meta->cdoc[i], "Name")) != NULL
                && !strcmp(s, cdocName)
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

    for (i = 0; i < fldset->numFields; i++)
        if (!strcmp(fldset->key[i].name, keyName))
            // found it
            return fldset->key[i].value;

    // no key of that name sorry
    return NULL;

}               // 'cdocLookupKey()'


//
// Private functions
//


// split a line of the form 'key [= value] into the key/value pair

static char *splitLine(char *buf)
{
    char *eq = strchr(buf, '=');
    char *s, *s1;

    if (eq == NULL)
        return NULL;

    *eq = '\0';

    // delete whitespace after key
    for (s = eq - 1; strchr(" \t", *s) != NULL; s--)
        *s = '\0';

    // delete whitespace before value
    for (s = eq + 1; strchr(" \t", *s) != NULL; s++)
        ;

    // bail if nothing left in value
    if (*s == '\0')
        return NULL;

    // delete whitespace after value
    for (s1 = s + strlen(s) - 1; strchr(" \t", *s1) != NULL; s1--)
        *s1 = '\0';

    return (strlen(s1) > 0) ? s : NULL;
}


//
// This routine is used to break up a buffer into a stream of lines,
// deleting leading and trailing whitespace,
// ignoring empty lines,
// ignoring comment lines (first non-white is '#' or ';'
//

char *getLine(char *buf, char **nextPos)
{
    char *whites = " \t\r\n";
    char *eol;

    // bail if done
    if (buf == NULL)
        return NULL;

    // skip leading whitespace and comment lines
    for (;*buf != '\0'; buf++)
    {
        // skip whitespace
        if (strchr(whites, *buf) != NULL)
            continue;

        // skip comment lines
        if (strchr("#;", *buf) != NULL)
        {
            while (*buf != '\0' && strchr("\r\n", *buf) == NULL)
                buf++;
            while (*buf != '\0' && strchr("\r\n", *buf) != NULL)
                buf++;
            if (*buf == '\0')
                return NULL;
            else
            {
                buf--;
                continue;
            }
        }

        // find end of line
        if ((eol = strpbrk(buf, "\r\n")) == NULL)
        {
            // no end of line - return what we have
            *nextPos = NULL;
            eol = buf + strlen(buf) - 1;
            while (strchr(whites, *eol) != NULL)
                *eol-- = '\0';
            return buf;
        }

        // terminate line
        *eol++ = '\0';

        // skip past any additional line terminator(s)
        while (*eol != '\0' && strchr("\r\n", *eol) != NULL)
            eol++;

        // anything left after line terminator?
        if (*eol == '\0')
            *nextPos = NULL;
        else
            *nextPos = eol;

        eol = buf + strlen(buf) - 1;
        while (strchr(whites, *eol) != NULL)
            *eol-- = '\0';
        return buf;
    }

    return NULL;
}               // 'getLine()'

