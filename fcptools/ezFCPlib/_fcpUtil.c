
#include "ezFCPlib.h"


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

/*
  Sleep()

  Convenience function since the only portable sleep function which takes
  parameters in nanoseconds is a pain to call.  If you need to check
  signals while sleeping, you probably shouldn't call this function.
  Call nanosleep() directly and read the man pages :)
*/
int Sleep(unsigned int seconds, unsigned int nanoseconds)
{
  struct timespec delay;
  struct timespec remain;

  delay.tv_sec = seconds;
  delay.tv_nsec = nanoseconds;

  return nanosleep( &delay, &remain );
}


long timeLastMidnight()
{
    time_t timenow;

    time(&timenow);
    timenow -= timenow % 86400;
    return timenow;
}
