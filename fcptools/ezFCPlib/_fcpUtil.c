
#include "ezFCPlib.h"

// Make use of stdio.h:#define P_tmpdir "/tmp"

/*
  xtoi()

  Convert a hexadecimal number string into an int
  this is the hex version of atoi
*/
long xtoi(char *s)
{
    long val = 0;

    if (s == NULL)
        return 0L;

    for (; *s != '\0'; s++)
        if (*s >= '0' && *s <= '9')
            val = val * 16 + *s - '0';
        else if (*s >= 'a' && *s <= 'f')
            val = val * 16 + (*s - 'a' + 10);
        else if (*s >= 'A' && *s <= 'F')
            val = val * 16 + (*s - 'A' + 10);
        else
            break;

    return val;
}

long timeLastMidnight()
{
    time_t timenow;

    time(&timenow);
    timenow -= timenow % 86400;
    return timenow;
}
