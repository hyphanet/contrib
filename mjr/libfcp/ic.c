#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>
#include <getopt.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcp.h>

int htl = 10;
int threads = 10;
int retries = 3;

FILE *log;

void insertdir (char *dir, int depth);
void insert (char *file, int depth);
int isdir (char *dir);

void
usage (char *me)
{
    fprintf(stderr, "Usage: %s [options] files/directories\n\n"
	    	    "  -h --htl            Hops to live.\n"
		    "  -t --threads        Concurrency.\n"
		    "  -r --retries        Number of retries per insert before abort.\n\n",
		    me);
    exit(2);
}

struct pair {
    char *ext;
    char *type;
};

struct pair associations[] = {
    {"csm", "application/cu-seeme"},
    {"cu", "application/cu-seeme"},
    {"tsp", "application/dsptype"},
    {"xls", "application/excel"},
    {"spl", "application/futuresplash"},
    {"hqx", "application/mac-binhex40"},
    {"doc", "application/msword"},
    {"dot", "application/msword"},
    {"bin", "application/octet-stream"},
    {"oda", "application/oda"},
    {"pdf", "application/pdf"},
    {"pgp", "application/pgp-signature"},
    {"ps", "application/postscript"},
    {"ai", "application/postscript"},
    {"eps", "application/postscript"},
    {"ppt", "application/powerpoint"},
    {"rtf", "application/rtf"},
    {"wp5", "application/wordperfect5.1"},
    {"zip", "application/zip"},
    {"wk", "application/x-123"},
    {"bcpio", "application/x-bcpio"},
    {"pgn", "application/x-chess-pgn"},
    {"cpio", "application/x-cpio"},
    {"deb", "application/x-debian-package"},
    {"dcr", "application/x-director"},
    {"dir", "application/x-director"},
    {"dxr", "application/x-director"},
    {"dvi", "application/x-dvi"},
    {"pfa", "application/x-font"},
    {"pfb", "application/x-font"},
    {"gsf", "application/x-font"},
    {"pcf", "application/x-font"},
    {"pcf.Z", "application/x-font"},
    {"gtar", "application/x-gtar"},
    {"tgz", "application/x-gtar"},
    {"hdf", "application/x-hdf"},
    {"phtml", "application/x-httpd-php"},
    {"pht", "application/x-httpd-php"},
    {"php", "application/x-httpd-php"},
    {"php3", "application/x-httpd-php3"},
    {"phps", "application/x-httpd-php3-source"},
    {"php3p", "application/x-httpd-php3-preprocessed"},
    {"class", "application/x-java"},
    {"latex", "application/x-latex"},
    {"frm", "application/x-maker"},
    {"maker", "application/x-maker"},
    {"frame", "application/x-maker"},
    {"fm", "application/x-maker"},
    {"fb", "application/x-maker"},
    {"book", "application/x-maker"},
    {"fbdoc", "application/x-maker"},
    {"mif", "application/x-mif"},
    {"com", "application/x-msdos-program"},
    {"exe", "application/x-msdos-program"},
    {"bat", "application/x-msdos-program"},
    {"dll", "application/x-msdos-program"},
    {"nc", "application/x-netcdf"},
    {"cdf", "application/x-netcdf"},
    {"pac", "application/x-ns-proxy-autoconfig"},
    {"o", "application/x-object"},
    {"pl", "application/x-perl"},
    {"pm", "application/x-perl"},
    {"shar", "application/x-shar"},
    {"swf", "application/x-shockwave-flash"},
    {"swfl", "application/x-shockwave-flash"},
    {"sit", "application/x-stuffit"},
    {"sv4cpio", "application/x-sv4cpio"},
    {"sv4crc", "application/x-sv4crc"},
    {"tar", "application/x-tar"},
    {"gf", "application/x-tex-gf"},
    {"pk", "application/x-tex-pk"},
    {"PK", "application/x-tex-pk"},
    {"texinfo", "application/x-texinfo"},
    {"texi", "application/x-texinfo"},
    {"~", "application/x-trash"},
    {"%", "application/x-trash"},
    {"bak", "application/x-trash"},
    {"old", "application/x-trash"},
    {"sik", "application/x-trash"},
    {"t", "application/x-troff"},
    {"tr", "application/x-troff"},
    {"roff", "application/x-troff"},
    {"man", "application/x-troff-man"},
    {"me", "application/x-troff-me"},
    {"ms", "application/x-troff-ms"},
    {"ustar", "application/x-ustar"},
    {"src", "application/x-wais-source"},
    {"wz", "application/x-wingz"},
    {"au", "audio/basic"},
    {"snd", "audio/basic"},
    {"mid", "audio/midi"},
    {"midi", "audio/midi"},
    {"mpga", "audio/mpeg"},
    {"mpega", "audio/mpeg"},
    {"mp2", "audio/mpeg"},
    {"mp3", "audio/mpeg"},
    {"m3u", "audio/mpegurl"},
    {"aif", "audio/x-aiff"},
    {"aiff", "audio/x-aiff"},
    {"aifc", "audio/x-aiff"},
    {"gsm", "audio/x-gsm"},
    {"ra", "audio/x-pn-realaudio"},
    {"rm", "audio/x-pn-realaudio"},
    {"ram", "audio/x-pn-realaudio"},
    {"rpm", "audio/x-pn-realaudio-plugin"},
    {"wav", "audio/x-wav"},
    {"gif", "image/gif"},
    {"ief", "image/ief"},
    {"jpeg", "image/jpeg"},
    {"jpg", "image/jpeg"},
    {"jpe", "image/jpeg"},
    {"png", "image/png"},
    {"tiff", "image/tiff"},
    {"tif", "image/tiff"},
    {"ras", "image/x-cmu-raster"},
    {"bmp", "image/x-ms-bmp"},
    {"pnm", "image/x-portable-anymap"},
    {"pbm", "image/x-portable-bitmap"},
    {"pgm", "image/x-portable-graymap"},
    {"ppm", "image/x-portable-pixmap"},
    {"rgb", "image/x-rgb"},
    {"xbm", "image/x-xbitmap"},
    {"xpm", "image/x-xpixmap"},
    {"xwd", "image/x-xwindowdump"},
    {"csv", "text/comma-separated-values"},
    {"html", "text/html"},
    {"htm", "text/html"},
    {"mml", "text/mathml"},
    {"txt", "text/plain"},
    {"rtx", "text/richtext"},
    {"tsv", "text/tab-separated-values"},
    {"h++", "text/x-c++hdr"},
    {"hpp", "text/x-c++hdr"},
    {"hxx", "text/x-c++hdr"},
    {"hh", "text/x-c++hdr"},
    {"c++", "text/x-c++src"},
    {"cpp", "text/x-c++src"},
    {"cxx", "text/x-c++src"},
    {"cc", "text/x-c++src"},
    {"h", "text/x-chdr"},
    {"csh", "text/x-csh"},
    {"c", "text/x-csrc"},
    {"java", "text/x-java"},
    {"moc", "text/x-moc"},
    {"p", "text/x-pascal"},
    {"pas", "text/x-pascal"},
    {"etx", "text/x-setext"},
    {"sh", "text/x-sh"},
    {"tcl", "text/x-tcl"},
    {"tk", "text/x-tcl"},
    {"tex", "text/x-tex"},
    {"ltx", "text/x-tex"},
    {"sty", "text/x-tex"},
    {"cls", "text/x-tex"},
    {"vcs", "text/x-vCalendar"},
    {"vcf", "text/x-vCard"},
    {"dl", "video/dl"},
    {"fli", "video/fli"},
    {"gl", "video/gl"},
    {"mpeg", "video/mpeg"},
    {"mpg", "video/mpeg"},
    {"mpe", "video/mpeg"},
    {"qt", "video/quicktime"},
    {"mov", "video/quicktime"},
    {"asf", "video/x-ms-asf"},
    {"asx", "video/x-ms-asf"},
    {"avi", "video/x-msvideo"},
    {"movie", "video/x-sgi-movie"},
    {"vrm", "x-world/x-vrml"},
    {"vrml", "x-world/x-vrml"},
    {"wrl", "x-world/x-vrml"},
    {"ogg", "application/x-ogg"},
    {NULL, NULL}
};

char *
get_content_type (char *filename)
{
    int i;
    char *suffix = rindex(filename, '.');
    if (!suffix) return "application/unknown";
    for (i = 0 ; associations[i].ext ; i++)
	if (strcasecmp(associations[i].ext, suffix + 1) == 0)
	    return associations[i].type;
    return "application/unknown";
}

void
insertdir (char *dir, int depth)
{
    struct dirent **namelist;
    char arg[1024];
    int n, i;
    char *r = rindex(dir, '/');
    n = depth++;
    while (n--) fputc('\t', log);
    fprintf(log, "%s\n", r ? r + 1 : dir);
    n = scandir(dir, &namelist, 0, alphasort);
    for (i = 0 ; i < n ; i++) {
	if (namelist[i]->d_name[0] == '.') continue;
	if (strcmp(dir, ".") == 0) strcpy(arg, namelist[i]->d_name);
	else sprintf(arg, "%s/%s", dir, namelist[i]->d_name);
	if (isdir(arg)) insertdir(arg, depth);
	else insert(arg, depth);
	free(namelist[i]);
    }
    free(namelist);
}

void
insert (char *file, int depth)
{
    FILE *data;
    char uri[128], *t = rindex(file, '/');
    int status;
    int r = retries + 1;
    struct stat s;
    status = stat(file, &s);
    if (status != 0) {
	fprintf(stderr, "Can't stat file %s!\n", file);
	pthread_exit(NULL);
    }
    data = fopen(file, "r");
    if (!data) {
	fprintf(stderr, "Can't open file %s!\n", file);
	pthread_exit(NULL);
    }
    strcpy(uri, "freenet:CHK@");
    if (s.st_size < 256 * 1024) {
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_insert_raw(data, uri, s.st_size, FCP_DATA, htl);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed: %s.\n", file,
		    fcp_status_to_string(status));
	    pthread_exit(NULL);
	}
    } else {
	fcp_metadata *m = fcp_metadata_new();
	fcp_info(m, "", "Content-Type", get_content_type(file));
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_insert(m, "", data, s.st_size, htl, threads);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed: %s.\n", file,
		    fcp_status_to_string(status));
	    pthread_exit(NULL);
	}
	r = retries + 1;
	status = -1;
	while (status != FCP_SUCCESS && r--)
	    status = fcp_metadata_insert(m, uri, htl);
	if (status != FCP_SUCCESS) {
	    fprintf(stderr, "Inserting %s failed: %s.\n", file,
		    fcp_status_to_string(status));
	    pthread_exit(NULL);
	}
	fcp_metadata_free(m);
        sprintf(uri, "%s//", uri);
    }
    while (depth--) fputc('\t', log);
    fprintf(log, "%s=%s\n", t ? t + 1 : file, uri);
}

int
isdir (char *dir)
{
    DIR *d = opendir(dir);
    if (!d) return 0;
    closedir(d);
    return 1;
}


int
main (int argc, char **argv)
{
    int c, i;
    char *arg;
    extern int optind;
    extern char *optarg;
    
    static struct option long_options[] =
    {
	{"htl",       1, NULL, 'h'},
	{"threads",   1, NULL, 't'},
	{"retries",   1, NULL, 'r'},
	{0, 0, 0, 0}
    };
    
    while ((c = getopt_long(argc, argv, "h:t:r:", long_options, NULL)) != EOF) {
        switch (c) {
        case 'h':
            htl = atoi(optarg);
            break;
	case 't':
	    threads = atoi(optarg);
	    break;
	case 'r':
	    retries = atoi(optarg);
	    break;
        case '?':
            usage(argv[0]);
	    return 1;
        }
    }
    
    if (htl < 0) {
	fprintf(stdout, "Invalid hops to live.\n");
	return 1;
    }
    if (threads < 1) {
	fprintf(stdout, "Invalid number of threads.\n");
	return 1;
    }
    if (retries < 0) {
	fprintf(stdout, "Invalid number of retries.\n");
	return 1;
    }
    
    if (!argv[optind]) usage(argv[0]);
    
    log = stdout;    
    
    arg = argv[(c = optind)];
    for (i = 1 ; argv[c] ; arg = argv[++c]) {
	if (arg[strlen(arg)-1] == '/') arg[strlen(arg)-1] = '\0';
        if (isdir(arg)) insertdir(arg, 0);
	else insert(arg, 0);
    }

    return 0;
}

