/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 * 
 * 
 * Portions of the Software have been derived from source code
 * developed by Silver Egg Technology under the following license:
 * 
 * Copyright (c) 2001 Silver Egg Technology
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without 
 * restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sub-license, and/or 
 * sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 */

#ifndef MACOSX
#ifndef FREEBSD
#include <malloc.h>
#endif
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifdef WIN32

/* MS Visual Studio 8 went and deprecated the POXIX names for functions.
 *  Fixing them all would be a big headache for UNIX versions. */
#pragma warning(disable : 4996)

#else
#include <strings.h>
#include <limits.h>
#include <sys/time.h>
#if defined(IRIX)
#define PATH_MAX FILENAME_MAX
#endif
#endif

#include "logger.h"
#include "property.h"
#include "wrapper.h"

#define MAX_INCLUDE_DEPTH 10

int debugIncludes = FALSE;

/** Stores the time that the property file began to be loaded. */
struct tm loadPropertiesTM;

void setInnerProperty(Property *property, const char *propertyValue);

/**
 * Private function to find a Property structure.
 */
Property* getInnerProperty(Properties *properties, const char *propertyName) {
    Property *property;
    int cmp;
    char *oldValue;

    /* Loop over the properties which are in order and look for the specified property. */
    property = properties->first;
    while (property != NULL) {
        cmp = strcmpIgnoreCase(property->name, propertyName);
        if (cmp > 0) {
            /* This property would be after the one being looked for, so it does not exist. */
            return NULL;
        } else if (cmp == 0) {
            /* We found it. */

            if (strstr(property->value, "%")) {
                /* Reset the property.  If the unreplaced environment variables are now available
                 *  setting it again will cause it to be replaced correctly.  If not this will
                 *  only waste time.  The value will be freed in the process so we need to
                 *  keep it around. */
#ifdef _DEBUG
                printf( "Unreplaced property %s=%s\n", property->name, property->value );
#endif
                oldValue = malloc(strlen(property->value) + 1);
                if (!oldValue) {
                    outOfMemory("GIP", 1);
                } else {
                    strcpy(oldValue, property->value);
                    setInnerProperty(property, oldValue);
                    free(oldValue);
                }
#ifdef _DEBUG
                printf( "        -> property %s=%s\n", property->name, property->value );
#endif
            }

            return property;
        }
        /* Keep looking */
        property = property->next;
    }
    /* We did not find the property being looked for. */
    return NULL;
}

void insertInnerProperty(Properties *properties, Property *newProperty) {
    Property *property;
    int cmp;

    /* Loop over the properties which are in order and look for the specified property. */
    /* This function assumes that Property is not already in properties. */
    property = properties->first;
    while (property != NULL) {
        cmp = strcmpIgnoreCase(property->name, newProperty->name);
        if (cmp > 0) {
            /* This property would be after the new property, so insert it here. */
            newProperty->previous = property->previous;
            newProperty->next = property;
            if (property->previous == NULL) {
                /* This was the first property */
                properties->first = newProperty;
            } else {
                property->previous->next = newProperty;
            }
            property->previous = newProperty;

            /* We are done, so return */
            return;
        }

        property = property->next;
    }

    /* The new property needs to be added at the end */
    newProperty->previous = properties->last;
    if (properties->last == NULL) {
        /* This will be the first property. */
        properties->first = newProperty;
    } else {
        /* Point the old last property to the new last property. */
        properties->last->next = newProperty;
    }
    properties->last = newProperty;
    newProperty->next = NULL;
}

Property* createInnerProperty() {
    Property *property;

    property = malloc(sizeof(Property));
    if (!property) {
        outOfMemory("CIP", 1);
        return NULL;
    }
    property->name = NULL;
    property->next = NULL;
    property->previous = NULL;
    property->value = NULL;

    return property;
}

/**
 * Private function to dispose a Property structure.  Assumes that the
 *    Property is disconnected already.
 */
void disposeInnerProperty(Property *property) {
    free(property->name);
    property->name = NULL;
    free(property->value);
    property->value = NULL;
    free(property);
    property = NULL;
}

char generateValueBuffer[256];

char* generateTimeValue(const char* format) {
    if (strcmpIgnoreCase(format, "YYYYMMDDHHIISS") == 0) {
        sprintf(generateValueBuffer, "%04d%02d%02d%02d%02d%02d",
            loadPropertiesTM.tm_year + 1900, loadPropertiesTM.tm_mon + 1, loadPropertiesTM.tm_mday, 
                loadPropertiesTM.tm_hour, loadPropertiesTM.tm_min, loadPropertiesTM.tm_sec );
    } else if (strcmpIgnoreCase(format, "YYYYMMDD_HHIISS") == 0) {
        sprintf(generateValueBuffer, "%04d%02d%02d_%02d%02d%02d",
            loadPropertiesTM.tm_year + 1900, loadPropertiesTM.tm_mon + 1, loadPropertiesTM.tm_mday, 
                loadPropertiesTM.tm_hour, loadPropertiesTM.tm_min, loadPropertiesTM.tm_sec );
    } else if (strcmpIgnoreCase(format, "YYYYMMDDHHII") == 0) {
        sprintf(generateValueBuffer, "%04d%02d%02d%02d%02d",
            loadPropertiesTM.tm_year + 1900, loadPropertiesTM.tm_mon + 1, loadPropertiesTM.tm_mday, 
                loadPropertiesTM.tm_hour, loadPropertiesTM.tm_min );
    } else if (strcmpIgnoreCase(format, "YYYYMMDDHH") == 0) {
        sprintf(generateValueBuffer, "%04d%02d%02d%02d",
            loadPropertiesTM.tm_year + 1900, loadPropertiesTM.tm_mon + 1, loadPropertiesTM.tm_mday, 
                loadPropertiesTM.tm_hour );
    } else if (strcmpIgnoreCase(format, "YYYYMMDD") == 0) {
        sprintf(generateValueBuffer, "%04d%02d%02d",
            loadPropertiesTM.tm_year + 1900, loadPropertiesTM.tm_mon + 1, loadPropertiesTM.tm_mday );
    } else {
        sprintf(generateValueBuffer, "{INVALID}");
    }
    return generateValueBuffer;
}

char* generateRandValue(const char* format) {
    if (strcmpIgnoreCase(format, "N") == 0) {
        sprintf(generateValueBuffer, "%01d", rand() % 10);
    } else if (strcmpIgnoreCase(format, "NN") == 0) {
        sprintf(generateValueBuffer, "%02d", rand() % 100);
    } else if (strcmpIgnoreCase(format, "NNN") == 0) {
        sprintf(generateValueBuffer, "%03d", rand() % 1000);
    } else if (strcmpIgnoreCase(format, "NNNN") == 0) {
        sprintf(generateValueBuffer, "%04d", rand() % 10000);
    } else if (strcmpIgnoreCase(format, "NNNNN") == 0) {
        sprintf(generateValueBuffer, "%04d%01d", rand() % 10000, rand() % 10);
    } else if (strcmpIgnoreCase(format, "NNNNNN") == 0) {
        sprintf(generateValueBuffer, "%04d%02d", rand() % 10000, rand() % 100);
    } else {
        sprintf(generateValueBuffer, "{INVALID}");
    }
    return generateValueBuffer;
}

/**
 * Parses a property value and populates any environment variables.  If the expanded
 *  environment variable would result in a string that is longer than bufferLength
 *  the value is truncated.
 */
void evaluateEnvironmentVariables(const char *propertyValue, char *buffer, int bufferLength) {
    const char *in;
    char *out;
    char envName[MAX_PROPERTY_NAME_LENGTH];
    char *envValue;
    char *start;
    char *end;
    size_t len;
    size_t outLen;
    size_t bufferAvailable;

#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "evaluateEnvironmentVariables('%s', buffer, %d)",
        propertyValue, bufferLength);
#endif

    buffer[0] = '\0';
    in = propertyValue;
    out = buffer;
    bufferAvailable = bufferLength - 1; /* Reserver room for the null terminator */

    /* Loop until we hit the end of string. */
    while (in[0] != '\0') {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "    initial='%s', buffer='%s'", propertyValue, buffer);
#endif

        start = strchr(in, '%');
        if (start != NULL) {
            end = strchr(start + 1, '%');
            if (end != NULL) {
                /* A pair of '%' characters was found.  An environment */
                /*  variable name should be between the two. */
                len = (int)(end - start - 1);
                memcpy(envName, start + 1, len);
                envName[len] = '\0';
                
                /* See if it is a special dynamic environment variable */
                if (strstr(envName, "WRAPPER_TIME_") == envName) {
                    /* Found a time value. */
                    envValue = generateTimeValue(envName + 13);
                } else if (strstr(envName, "WRAPPER_RAND_") == envName) {
                    /* Found a time value. */
                    envValue = generateRandValue(envName + 13);
                } else {
                    /* Try looking up the environment variable. */
                    envValue = getenv(envName);
                }

                if (envValue != NULL) {
                    /* An envvar value was found. */
                    /* Copy over any text before the envvar */
                    outLen = (int)(start - in);
                    if (bufferAvailable < outLen) {
                        outLen = bufferAvailable;
                    }
                    if (outLen > 0) {
                        memcpy(out, in, outLen);
                        out += outLen;
                        bufferAvailable -= outLen;
                    }

                    /* Copy over the env value */
                    outLen = strlen(envValue);
                    if (bufferAvailable < outLen) {
                        outLen = bufferAvailable;
                    }
                    if (outLen > 0) {
                        memcpy(out, envValue, outLen);
                        out += outLen;
                        bufferAvailable -= outLen;
                    }

                    /* Terminate the string */
                    out[0] = '\0';

                    /* Set the new in pointer */
                    in = end + 1;
                } else {
                    /* Not found.  So copy over the input up until the */
                    /*  second '%'.  Leave it in case it is actually the */
                    /*  start of an environment variable name */
                    outLen = len = end - in;
                    if (bufferAvailable < outLen) {
                        outLen = bufferAvailable;
                    }
                    if (outLen > 0) {
                     memcpy(out, in, outLen);
                      out += outLen;
                        bufferAvailable -= outLen;
                    }
                    in += len;

                    /* Terminate the string */
                    out[0] = '\0';
                }
            } else {
                /* Only a single '%' char was found. Leave it as is. */
                outLen = len = strlen(in);
                if (bufferAvailable < outLen) {
                    outLen = bufferAvailable;
                }
                if (outLen > 0) {
                 memcpy(out, in, outLen);
                  out += outLen;
                    bufferAvailable -= outLen;
                }
                in += len;

                /* Terminate the string */
                out[0] = '\0';
            }
        } else {
            /* No more '%' chars in the string. Copy over the rest. */
            outLen = len = strlen(in);
            if (bufferAvailable < outLen) {
                outLen = bufferAvailable;
            }
            if (outLen > 0) {
             memcpy(out, in, outLen);
              out += outLen;
                bufferAvailable -= outLen;
            }
            in += len;

            /* Terminate the string */
            out[0] = '\0';
        }
    }
#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  final buffer='%s'", buffer);
#endif
}

void setInnerProperty(Property *property, const char *propertyValue) {
    int i, count;
    /* The property value is expanded into a large buffer once, but that is temporary.  The actual
     *  value is stored in the minimum required size. */
    char buffer[MAX_PROPERTY_VALUE_LENGTH];

    /* Free any existing value */
    if (property->value != NULL) {
        free(property->value);
        property->value = NULL;
    }

    /* Set the new value using a copy of the provided value. */
    if (propertyValue == NULL) {
        property->value = NULL;
    } else {
        evaluateEnvironmentVariables(propertyValue, buffer, MAX_PROPERTY_VALUE_LENGTH);

        property->value = malloc(sizeof(char) * (strlen(buffer) + 1));
        if (!property->value) {
            outOfMemory("SIP", 1);
        } else {
            /* Strip any non valid characters like control characters. Some valid characters are
             *  less than 0 when the char is unsigned. */
            for (i = 0, count = 0; i < (int)strlen(buffer); i++) {
                /* Only add valid chars, skip control chars.  We want all chars other than those
                 *  in the range 1..31.  0 is not possible as that would be end of the string.
                 *  On most platforms, char is signed, but on PowerPC, it is unsigned.  This
                 *  means that any comparison such as >= 0 will cause a compiler error as that
                 *  would always be true.
                 * The logic below is to get the correct behavior in either case assuming no 0. */
                if ((buffer[i] < 1) || (buffer[i] > 31)) {
                    property->value[count++] = buffer[i];
                }
            }
    
            /* Crop string to new size */
            property->value[count] = '\0';
        }
    }
}

/**
 * Loads the contents of a file into the specified properties.
 *  Whenever a line which starts with #include is encountered, then the rest
 *  the line will be interpreted as a cascading include file.  If the file
 *  does not exist, the include definition is ignored.
 */
int loadPropertiesInner(Properties* properties, const char* filename, int depth) {
    FILE *stream;
    char buffer[MAX_PROPERTY_NAME_VALUE_LENGTH];
    char expBuffer[MAX_PROPERTY_NAME_VALUE_LENGTH];
    char *trimmedBuffer;
    size_t trimmedBufferLen;
    char *c;
    char *d;
    size_t i, j;
    size_t len;
    int quoted;
    char *absoluteBuffer;
#ifdef WIN32
    int size;
#endif
    

#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "loadPropertiesInner(props, '%s', %d)", filename, depth);
#endif

    /* Look for the specified file. */
    if ((stream = fopen(filename, "rt")) == NULL) {
        /* Unable to open the file. */
        if (debugIncludes) {
            if (depth > 0) {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "  Included configuration file, %s, was not found.", filename);
            } else {
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "Configuration file, %s, was not found.", filename);
            }
        } else {
#ifdef _DEBUG
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "Properties file not found: %s", filename);
#endif
        }
        return 1;
    }
    
    if (debugIncludes) {
        if (depth > 0) {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                "  Loading included configuration file, %s", filename);
        } else {
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                "Loading configuration file, %s", filename);
        }
    }

    /* Load in all of the properties */
    do {
        c = fgets(buffer, MAX_PROPERTY_NAME_VALUE_LENGTH, stream);
        if (c != NULL) {
            /* Always strip both ^M and ^J off the end of the line, this is done rather
             *  than simply checking for \n so that files will work on all platforms
             *  even if their line feeds are incorrect. */
            if ((d = strchr(buffer, 13 /* ^M */)) != NULL) { 
                d[0] = '\0';
            }
            if ((d = strchr(buffer, 10 /* ^J */)) != NULL) { 
                d[0] = '\0';
            }

            /* Strip any whitespace from the front of the line. */
            trimmedBuffer = buffer;
            while ((trimmedBuffer[0] == ' ') || (trimmedBuffer[0] == 0x08)) {
                trimmedBuffer++;
            }

            /* If the line does not start with a comment, make sure that
             *  any comment at the end of line are stripped.  If any any point, a
             *  double hash, '##', is encountered it should be interpreted as a
             *  hash in the actual property rather than the beginning of a comment. */
            if (trimmedBuffer[0] != '#') {
                len = strlen(trimmedBuffer);
                i = 0;
                quoted = 0;
                while (i < len) {
                    if (trimmedBuffer[i] == '"') {
                        quoted = !quoted;
                    } else if ((trimmedBuffer[i] == '#') && (!quoted)) {
                        /* Checking the next character will always be ok because it will be
                         *  '\0 at the end of the string. */
                        if (trimmedBuffer[i + 1] == '#') {
                            /* We found an escaped #. Shift the rest of the string
                             *  down by one character to remove the second '#'.
                             *  Include the shifting of the '\0'. */
                            for (j = i + 1; j <= len; j++) {
                                trimmedBuffer[j - 1] = trimmedBuffer[j];
                            }
                            len--;
                        } else {
                            /* We found a comment. So this is the end. */
                            trimmedBuffer[i] = '\0';
                            len = i;
                        }
                    }
                    i++;
                }
            }

            /* Strip any whitespace from the end of the line. */
            trimmedBufferLen = strlen(trimmedBuffer);
            while ((trimmedBufferLen > 0) && ((trimmedBuffer[trimmedBufferLen - 1] == ' ')
                || (trimmedBuffer[trimmedBufferLen - 1] == 0x08))) {

                trimmedBuffer[trimmedBufferLen - 1] = '\0';
                trimmedBufferLen--;
            }

            /* Only look at lines which contain data and do not start with a '#'
             *  If the line starts with '#include' then recurse to the include file */
            if (strlen(trimmedBuffer) > 0) {
                if (strcmpIgnoreCase(trimmedBuffer, "#include.debug") == 0) {
                    /* Enable include file debugging. */
                    debugIncludes = TRUE;
                } else if (strstr(trimmedBuffer, "#include") == trimmedBuffer) {
                    /* Include file, if the file does not exist, then ignore it */
                    /* Strip any leading whitespace */
                    c = trimmedBuffer + 8;
                    while ((c[0] != '\0') && (c[0] == ' ')) {
                        c++;
                    }

                    if (depth < MAX_INCLUDE_DEPTH) {
                        /* The filename may contain environment variables, so expand them. */
                        if (debugIncludes) {
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                "Found #include file in %s: %s", filename, c);
                        }
                        evaluateEnvironmentVariables(c, expBuffer, MAX_PROPERTY_NAME_VALUE_LENGTH);
                        
                        if (debugIncludes && (strcmp(c, expBuffer) != 0)) {
                            /* Only show this log if there were any environment variables. */
                            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                "  After environment variable replacements: %s", expBuffer);
                        }
                        
                        /* Now obtain the real absolute path to the include file. */
#ifdef WIN32
                        /* Find out how big the absolute path will be */
                        size = GetFullPathName(expBuffer, 0, NULL, NULL);
                        if (!size) {
                            if (debugIncludes) {
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                    "  Unable to resolve the full path of the configuration include file, %s: %s",
                                    expBuffer, getLastErrorText());
                                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                    "  Current working directory is: %s", wrapperData->originalWorkingDir);
                            }
                            absoluteBuffer = NULL;
                        } else {
                            absoluteBuffer = malloc(sizeof(char) * size);
                            if (!absoluteBuffer) {
                                outOfMemory("LPI", 1);
                            } else {
                                if (!GetFullPathName(expBuffer, size, absoluteBuffer, NULL)) {
                                    if (debugIncludes) {
                                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL,
                                            "  Unable to resolve the full path of the configuration include file, %s: %s",
                                            expBuffer, getLastErrorText());
                                        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                            "  Current working directory is: %s", wrapperData->originalWorkingDir);
                                    }
                                    free(absoluteBuffer);
                                    absoluteBuffer = NULL;
                                }
                            }
                        }
#else
                        absoluteBuffer = malloc(PATH_MAX);
                        if (!absoluteBuffer) {
                            outOfMemory("LPI", 2);
                        } else {
                            if (realpath(expBuffer, absoluteBuffer) == NULL) {
                                if (debugIncludes) {
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                        "  Unable to resolve the full path of the configuration include file, %s: %s",
                                        expBuffer, getLastErrorText());
                                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                                        "  Current working directory is: %s", wrapperData->originalWorkingDir);
                                }
                                free(absoluteBuffer);
                                absoluteBuffer = NULL;
                            }
                        }
#endif
                        if (absoluteBuffer) {
                            loadPropertiesInner(properties, absoluteBuffer, depth + 1);
                            free(absoluteBuffer);
                        }
                    }
                } else if (strstr(trimmedBuffer, "include") == trimmedBuffer) {
                    /* Users sometimes remove the '#' from include statements.  Add a warning to help them notice the problem. */
                    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_ADVICE,
                        "Include file reference missing leading '#': %s", trimmedBuffer);
                } else if (trimmedBuffer[0] != '#') {
                    /* printf("%s\n", trimmedBuffer); */

                    /* Locate the first '=' in the line, ignore lines that do not contain a '=' */
                    if ((d = strchr(trimmedBuffer, '=')) != NULL) {
                        /* Null terminate the first half of the line. */
                        *d = '\0';
                        d++;
                        addProperty(properties, trimmedBuffer, d, FALSE, FALSE);
                    }
                }
            }
        }
    } while (c != NULL);

    /* Close the file */
    fclose(stream);

    return 0;
}

int loadProperties(Properties *properties, const char* filename) {
    /* Store the time that the property file began to be loaded. */
#ifdef WIN32
    struct _timeb timebNow;
#else
    struct timeval timevalNow;
#endif
    time_t      now;
    struct tm   *nowTM;
    
#ifdef WIN32
    _ftime( &timebNow );
    now = (time_t)timebNow.time;
#else
    gettimeofday( &timevalNow, NULL );
    now = (time_t)timevalNow.tv_sec;
#endif
    nowTM = localtime( &now );
    memcpy(&loadPropertiesTM, nowTM, sizeof(struct tm));
    
    return loadPropertiesInner(properties, filename, 0);
}

Properties* createProperties() {
    Properties *properties = malloc(sizeof(Properties));
    if (!properties) {
        outOfMemory("CP", 1);
        return NULL;
    }
    properties->first = NULL;
    properties->last = NULL;
    return properties;
}

void disposeProperties(Properties *properties) {
    /* Loop and dispose any Property structures */
    Property *tempProperty;
    Property *property = properties->first;
    properties->first = NULL;
    properties->last = NULL;
    while (property != NULL) {
        /* Save the next property */
        tempProperty = property->next;

        /* Clean up the current property */
        disposeInnerProperty(property);
        property = NULL;

        /* set the current property to the next. */
        property = tempProperty;
    }

    /* Dispose the Properties structure */
    free(properties);
    properties = NULL;
}

void removeProperty(Properties *properties, const char *propertyName) {
    Property *property;
    Property *next;
    Property *previous;

    /* Look up the property */
    property = getInnerProperty(properties, propertyName);
    if (property == NULL) {
        /* The property did not exist, so nothing to do. */
    } else {
        next = property->next;
        previous = property->previous;

        /* Disconnect the property */
        if (next == NULL) {
            /* This was the last property */
            properties->last = previous;
        } else {
            next->previous = property->previous;
        }
        if (previous ==  NULL) {
            /* This was the first property */
            properties->first = next;
        } else {
            previous->next = property->next;
        }

        /* Now that property is disconnected, if can be disposed. */
        disposeInnerProperty(property);
    }
}

/**
 * Sets an environment variable with the specified value.
 *  The function will only set the variable if its value is changed, but if
 *  it does, the call will result in a memory leak the size of the string:
 *   "name=value".
 *
 * Return TRUE if there were any problems.
 */
int setEnv( const char *name, const char *value )
{
    char *oldVal;
    char *envBuf;

    /* Get the current environment variable value so we can avoid allocating and
     *  setting the variable if it has not changed its value. */
    oldVal = getenv(name);
    if (value == NULL) {
        /*printf("clear %s=\n", name);*/
        if (oldVal != NULL) {
            /* Allocate a block of memory for the environment variable.  The system uses
             *  this memory so it is not freed after we set it. We only call this on
             *  startup, so the leak is minor. */
            envBuf = malloc(sizeof(char) * (strlen(name) + 2));
            if (!envBuf) {
                outOfMemory("SE", 1);
                return TRUE;
            } else {
                sprintf(envBuf, "%s=", name);
                /* The memory pointed to by envBuf becomes part of the environment so it can
                 *  not be freed by us here. */
                if (putenv(envBuf)) {
                    printf("Unable to clear environment variable: %s\n", envBuf);
                    return TRUE;
                }
            }
        }
    } else {
        /*printf("set %s=%s\n", name, value);*/
        if ((oldVal == NULL) || (strcmp(oldVal, value) != 0)) {
            /* Allocate a block of memory for the environment variable.  The system uses
             *  this memory so it is not freed after we set it. We only call this on
             *  startup, so the leak is minor. */
            envBuf = malloc(sizeof(char) * (strlen(name) + strlen(value) + 2));
            if (!envBuf) {
                outOfMemory("SE", 2);
                return TRUE;
            } else {
                sprintf(envBuf, "%s=%s", name, value);
                /* The memory pointed to by envBuf becomes part of the environment so it can
                 *  not be freed by us here. */
                if (putenv(envBuf)) {
                    printf("Unable to set environment variable: %s\n", envBuf);
                    return TRUE;
                }
            }
        }
    }

    return FALSE;
}

/* Trims any whitespace from the beginning and end of the in string
 *  and places the results in the out buffer.  Assumes that the out
 *  buffer is at least as large as the in buffer. */
void trim(const char *in, char *out)
{
    size_t len;
    size_t first;
    size_t last;

    len = strlen(in);
    first = 0;
    last = len - 1;

    /* Right Trim */
    while (((in[first] == ' ') || (in[first] == '\t')) && (first < last)) {
        first++;
    }
    /* Left Trim */
    while (((in[last] == ' ') || (in[last] == '\t')) && (last > first)) {
        last--;
    }

    /* Copy over what is left. */
    len = last - first + 1;
    if (len > 0) {
        memcpy(out, in + first, len);
    }
    out[len] = '\0';
}

void addProperty(Properties *properties, const char *propertyName, const char *propertyValue, int finalValue, int quotable) {
    int setValue;
    Property *property;
    char *propertyNameTrim;
    char *propertyValueTrim;

#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "addProperty(%p, '%s', '%s', %d, %d)",
        properties, propertyName, propertyValue, finalValue, quotable);
#endif

    /* It is possible that the propertyName and or properyValue contains extra spaces. */
    propertyNameTrim = malloc(sizeof(char) * (strlen(propertyName) + 1));
    if (!propertyNameTrim) {
        outOfMemory("AP", 1);
        return;
    }
    trim(propertyName, propertyNameTrim);
    propertyValueTrim = malloc(sizeof(char) * (strlen(propertyValue) + 1));
    if (!propertyValueTrim) {
        outOfMemory("AP", 2);
        return;
    }
    trim(propertyValue, propertyValueTrim);

#ifdef _DEBUG
    log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "  trimmed name='%s', value='%s'",
        propertyNameTrim, propertyValueTrim);
#endif

    /* See if the property already exists */
    setValue = TRUE;
    property = getInnerProperty(properties, propertyNameTrim);
    if (property == NULL) {
        /* This is a new property */
        property = createInnerProperty();
        if (!property) {
            return;
        }

        /* Store a copy of the name */
        property->name = malloc(sizeof(char) * (strlen(propertyNameTrim) + 1));
        if (!property->name) {
            outOfMemory("AP", 3);
            return;
        }
        strcpy(property->name, propertyNameTrim);

        /* Insert this property at the correct location. */
        insertInnerProperty(properties, property);
    } else {
        /* The property was already set.  Only change it if non final */
        if ( property->finalValue ) {
            setValue = FALSE;
        }
    }

    if (setValue) {
        /* Set the property value. */
        setInnerProperty(property, propertyValueTrim);

        /* Store the final flag */
        property->finalValue = finalValue;

        /* Store the quotable flag. */
        property->quotable = quotable;

        /* See if this is a special property */
        if ((strlen(propertyNameTrim) > 12) && (strstr(propertyNameTrim, "set.default.") == propertyNameTrim)) {
            /* This property is an environment variable definition that should only
             *  be set if the environment variable does not already exist.  Get the
             *  value back out of the property as it may have had environment
             *  replacements. */
            if (getenv(propertyNameTrim + 12) == NULL) {
#ifdef _DEBUG
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "set default env('%s', '%s')",
                    propertyNameTrim + 12, property->value);
#endif
                setEnv(propertyNameTrim + 12, property->value);
            } else {
#ifdef _DEBUG
                log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS,
                    "not setting default env('%s', '%s'), already set to '%s'",
                    propertyNameTrim + 12, property->value, getenv(propertyNameTrim + 12));
#endif
            }
        } else if ((strlen(propertyNameTrim) > 4) && (strstr(propertyNameTrim, "set.") == propertyNameTrim)) {
            /* This property is an environment variable definition.  Get the
             *  value back out of the property as it may have had environment
             *  replacements. */
#ifdef _DEBUG
            log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "set env('%s', '%s')",
                propertyNameTrim + 4, property->value);
#endif
            setEnv(propertyNameTrim + 4, property->value);
        }
    }

    /* Free up the trimmed buffers */
    free(propertyNameTrim);
    free(propertyValueTrim);
}

/**
 * Takes a name/value pair in the form <name>=<value> and attempts to add
 * it to the specified properties table.
 *
 * Returns 0 if successful, otherwise 1
 */
int addPropertyPair(Properties *properties, const char *propertyNameValue, int finalValue, int quotable) {
    char buffer[MAX_PROPERTY_NAME_VALUE_LENGTH];
    char *d;

    /* Make a copy of the pair that we can edit */
    if (strlen(propertyNameValue) + 1 >= MAX_PROPERTY_NAME_VALUE_LENGTH) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_FATAL, 
            "The following property name value pair is too large.  Need to increase the internal buffer size: %s", propertyNameValue);
        return 1;
    }
    strcpy(buffer, propertyNameValue);

    /* Locate the first '=' in the pair */
    if ((d = strchr(buffer, '=')) != NULL) {
        /* Null terminate the first half of the line. */
        *d = '\0';
        d++;
        addProperty(properties, buffer, d, finalValue, quotable);

        return 0;
    } else {
        return 1;
    }
}

const char* getStringProperty(Properties *properties, const char *propertyName, const char *defaultValue) {
    Property *property;
    property = getInnerProperty(properties, propertyName);
    if (property == NULL) {
        if (defaultValue != NULL) {
            addProperty(properties, propertyName, defaultValue, FALSE, FALSE);
        }

        return defaultValue;
    } else {
        return property->value;
    }
}

/**
 * Performs a case insensitive check of the property value against the value provided.
 *  If the property is not set then it is compared with the defaultValue.
 */
int checkPropertyEqual(Properties *properties, const char *propertyName, const char *defaultValue, const char *value) {
    Property *property;
    const char *propertyValue;

    property = getInnerProperty(properties, propertyName);
    if (property == NULL) {
        propertyValue = defaultValue;
    } else {
        propertyValue = property->value;
    }

    return strcmpIgnoreCase(propertyValue, value) == 0;
}

int getIntProperty(Properties *properties, const char *propertyName, int defaultValue) {
    char buffer[16];
    Property *property;

    property = getInnerProperty(properties, propertyName);
    if (property == NULL) {
        sprintf(buffer, "%d", defaultValue);
        addProperty(properties, propertyName, buffer, FALSE, FALSE);

        return defaultValue;
    } else {
        return (int)strtol(property->value, NULL, 0);
    }
}

int getBooleanProperty(Properties *properties, const char *propertyName, int defaultValue) {
    if (defaultValue) {
        return checkPropertyEqual(properties, propertyName, "true", "true");
    } else {
        return checkPropertyEqual(properties, propertyName, "false", "true");
    }
}


int isQuotableProperty(Properties *properties, const char *propertyName) {
    Property *property;
    property = getInnerProperty(properties, propertyName);
    if (property == NULL) {
        return FALSE;
    } else {
        return property->quotable;
    }
}

void dumpProperties(Properties *properties) {
    Property *property;
    property = properties->first;
    while (property != NULL) {
        log_printf(WRAPPER_SOURCE_WRAPPER, LEVEL_STATUS, "    name:%s value:%s", property->name, property->value);
        property = property->next;
    }
}

/** Creates a linearized representation of all of the properties.
 *  The returned buffer must be freed by the calling code. */
char *linearizeProperties(Properties *properties, char separator) {
    Property *property;
    size_t size;
    char *c;
    char *fullBuffer;
    char *buffer;
    char *work;
    
    /* First we need to figure out how large a buffer will be needed to linearize the properties. */
    size = 0;
    property = properties->first;
    while (property != NULL) {
        /* Add the length of the basic property. */
        size += strlen(property->name);
        size++; /* '=' */
        size += strlen(property->value);
        
        /* Handle any characters that will need to be escaped. */
        c = property->name;
        while ((c = strchr(c, separator)) != NULL) {
            size++;
            c++;
        }
        c = property->value;
        while ((c = strchr(c, separator)) != NULL) {
            size++;
            c++;
        }
        
        size++; /* separator */
        
        property = property->next;
    }
    size++; /* null terminated. */
    
    /* Now that we know how much space this will all take up, allocate a buffer. */
    fullBuffer = buffer = malloc(size);
    if (!fullBuffer) {
        outOfMemory("LP", 1);
        return NULL;
    }
    
    /* Now actually build up the output.  Any separator characters need to be escaped with themselves. */
    property = properties->first;
    while (property != NULL) {
        /* name */
        work = property->name;
        while ((c = strchr(work, separator)) != NULL) {
            memcpy(buffer, work, c - work + 1);
            buffer += c - work + 1;
            buffer[0] = separator;
            buffer++;
            work = c + 1;
        }
        strcpy(buffer, work);
        buffer += strlen(work);
        
        /* equals */
        buffer[0] = '=';
        buffer++;
        
        /* value */
        work = property->value;
        while ((c = strchr(work, separator)) != NULL) {
            memcpy(buffer, work, c - work + 1);
            buffer += c - work + 1;
            buffer[0] = separator;
            buffer++;
            work = c + 1;
        }
        strcpy(buffer, work);
        buffer += strlen(work);
        
        /* separator */
        buffer[0] = separator;
        buffer++;
        
        property = property->next;
    }
    
    /* null terminate. */
    buffer[0] = 0;
    buffer++;
    
    return fullBuffer;
}
