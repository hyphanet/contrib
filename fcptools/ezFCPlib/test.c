
#include <time.h>

static time_t mkgmtime(char *datestr);

main(int argc, char *argv[])
{
    struct tm base_tm, converted_tm;
    time_t base_secs;

    if (argc != 2)
    {
        printf("oops\n");
        exit(1);
    }

    base_secs = mkgmtime(argv[1]);
}


#define SECSPERDAY 86400


static time_t mkgmtime(char *datestr)
{
    static int mon_days[12] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
//  struct tm acttm;
    time_t basesecs;
    int basedays;
    int year, mon, day, hour, min, sec;

    // break up into individual fields
    sscanf(datestr, "%04d%02d%02d%02d%02d%02d", &year, &mon, &day, &hour, &min, &sec);

    // calculate days, not including leap years
    basedays = (year - 1970) * 365 + mon_days[mon - 1] + day - 1;

    // add the leap years
    basedays += (year + 2 - 1970) / 4;

    // exclude if this year is a leap year and prior to feb 29
    if (year % 4 && mon < 3 && year != 1970)
    {
        printf("docking a day for this years leap year\n");
        basedays--;
    }

    basesecs = basedays * SECSPERDAY + hour * 3600 + min * 60 + sec;

//  memcpy(&acttm, gmtime(&basesecs), sizeof(struct tm));
//  printf("datestr = '%s', basedays = %d, basesecs = %ld\n", datestr, basedays, basesecs);
//  printf("%04d-%02d-%02d %02d:%02d:%02d\n", acttm.tm_year+1900, acttm.tm_mon+1, acttm.tm_mday, acttm.tm_hour, acttm.tm_min, acttm.tm_sec);

    return basesecs;
}

//(year + 2 - 1970) / 4
/* force cvs update */
