/*
	mimetype.c
*/

#include "ezFCPlib.h"

char *GetMimeType(char *pathname);

static struct mimeTabEnt
{
    char *ext;
    char *mimetype;
}
MimeTab[] = {
    { "csm", "application/cu-seeme" },
    { "csm", "application/cu-seeme" },
    { "cu", "application/cu-seeme" },
    { "tsp", "application/dsptype" },
    { "xls", "application/excel" },
    { "spl", "application/futuresplash" },
    { "hqx", "application/mac-binhex40" },
    { "doc", "application/msword" },
    { "dot", "application/msword" },
    { "bin", "application/octet-stream" },
    { "oda", "application/oda" },
    { "pdf", "application/pdf" },
    { "pgp", "application/pgp-signature" },
    { "ps", "application/postscript" },
    { "ai", "application/postscript" },
    { "eps", "application/postscript" },
    { "ppt", "application/powerpoint" },
    { "rtf", "application/rtf" },
    { "wp5", "application/wordperfect5.1" },
    { "zip", "application/zip" },
    { "wk", "application/x-123" },
    { "bcpio", "application/x-bcpio" },
    { "pgn", "application/x-chess-pgn" },
    { "cpio", "application/x-cpio" },
    { "deb", "application/x-debian-package" },
    { "dcr", "application/x-director" },
    { "dir", "application/x-director" },
    { "dxr", "application/x-director" },
    { "dvi", "application/x-dvi" },
    { "pfa", "application/x-font" },
    { "pfb", "application/x-font" },
    { "gsf", "application/x-font" },
    { "pcf", "application/x-font" },
    { "pcf.Z", "application/x-font" },
    { "gtar", "application/x-gtar" },
    { "tgz", "application/x-gtar" },
    { "hdf", "application/x-hdf" },
    { "phtml", "application/x-httpd-php" },
    { "pht", "application/x-httpd-php" },
    { "php", "application/x-httpd-php" },
    { "php3", "application/x-httpd-php3" },
    { "phps", "application/x-httpd-php3-source" },
    { "php3p", "application/x-httpd-php3-preprocessed" },
    { "class", "application/x-java" },
    { "latex", "application/x-latex" },
    { "frm", "application/x-maker" },
    { "maker", "application/x-maker" },
    { "frame", "application/x-maker" },
    { "fm", "application/x-maker" },
    { "fb", "application/x-maker" },
    { "book", "application/x-maker" },
    { "fbdoc", "application/x-maker" },
    { "mif", "application/x-mif" },
    { "com", "application/x-msdos-program" },
    { "exe", "application/x-msdos-program" },
    { "bat", "application/x-msdos-program" },
    { "dll", "application/x-msdos-program" },
    { "nc", "application/x-netcdf" },
    { "cdf", "application/x-netcdf" },
    { "pac", "application/x-ns-proxy-autoconfig" },
    { "o", "application/x-object" },
    { "pl", "application/x-perl" },
    { "pm", "application/x-perl" },
    { "shar", "application/x-shar" },
    { "swf", "application/x-shockwave-flash" },
    { "swfl", "application/x-shockwave-flash" },
    { "sit", "application/x-stuffit" },
    { "sv4cpio", "application/x-sv4cpio" },
    { "sv4crc", "application/x-sv4crc" },
    { "tar", "application/x-tar" },
    { "gf", "application/x-tex-gf" },
    { "pk", "application/x-tex-pk" },
    { "PK", "application/x-tex-pk" },
    { "texinfo", "application/x-texinfo" },
    { "texi", "application/x-texinfo" },
    { "~", "application/x-trash" },
    { "%", "application/x-trash" },
    { "bak", "application/x-trash" },
    { "old", "application/x-trash" },
    { "sik", "application/x-trash" },
    { "t", "application/x-troff" },
    { "tr", "application/x-troff" },
    { "roff", "application/x-troff" },
    { "man", "application/x-troff-man" },
    { "me", "application/x-troff-me" },
    { "ms", "application/x-troff-ms" },
    { "ustar", "application/x-ustar" },
    { "src", "application/x-wais-source" },
    { "wz", "application/x-wingz" },
    { "au", "audio/basic" },
    { "snd", "audio/basic" },
    { "mid", "audio/midi" },
    { "midi", "audio/midi" },
    { "mpga", "audio/mpeg" },
    { "mpega", "audio/mpeg" },
    { "mp2", "audio/mpeg" },
    { "mp3", "audio/mpeg" },
    { "m3u", "audio/mpegurl" },
    { "aif", "audio/x-aiff" },
    { "aiff", "audio/x-aiff" },
    { "aifc", "audio/x-aiff" },
    { "gsm", "audio/x-gsm" },
    { "ra", "audio/x-pn-realaudio" },
    { "rm", "audio/x-pn-realaudio" },
    { "ram", "audio/x-pn-realaudio" },
    { "rpm", "audio/x-pn-realaudio-plugin" },
    { "wav", "audio/x-wav" },
    { "gif", "image/gif" },
    { "ief", "image/ief" },
    { "jpeg", "image/jpeg" },
    { "jpg", "image/jpeg" },
    { "jpe", "image/jpeg" },
    { "png", "image/png" },
    { "tiff", "image/tiff" },
    { "tif", "image/tiff" },
    { "ras", "image/x-cmu-raster" },
    { "bmp", "image/x-ms-bmp" },
    { "pnm", "image/x-portable-anymap" },
    { "pbm", "image/x-portable-bitmap" },
    { "pgm", "image/x-portable-graymap" },
    { "ppm", "image/x-portable-pixmap" },
    { "rgb", "image/x-rgb" },
    { "xbm", "image/x-xbitmap" },
    { "xpm", "image/x-xpixmap" },
    { "xwd", "image/x-xwindowdump" },
    { "csv", "text/comma-separated-values" },
    { "html", "text/html" },
    { "htm", "text/html" },
    { "mml", "text/mathml" },
    { "txt", "text/plain" },
    { "rtx", "text/richtext" },
    { "tsv", "text/tab-separated-values" },
    { "h++", "text/x-c++hdr" },
    { "hpp", "text/x-c++hdr" },
    { "hxx", "text/x-c++hdr" },
    { "hh", "text/x-c++hdr" },
    { "c++", "text/x-c++src" },
    { "cpp", "text/x-c++src" },
    { "cxx", "text/x-c++src" },
    { "cc", "text/x-c++src" },
    { "h", "text/x-chdr" },
    { "csh", "text/x-csh" },
    { "c", "text/x-csrc" },
    { "java", "text/x-java" },
    { "moc", "text/x-moc" },
    { "p", "text/x-pascal" },
    { "pas", "text/x-pascal" },
    { "etx", "text/x-setext" },
    { "sh", "text/x-sh" },
    { "tcl", "text/x-tcl" },
    { "tk", "text/x-tcl" },
    { "tex", "text/x-tex" },
    { "ltx", "text/x-tex" },
    { "sty", "text/x-tex" },
    { "cls", "text/x-tex" },
    { "vcs", "text/x-vCalendar" },
    { "vcf", "text/x-vCard" },
    { "dl", "video/dl" },
    { "fli", "video/fli" },
    { "gl", "video/gl" },
    { "mpeg", "video/mpeg" },
    { "mpg", "video/mpeg" },
    { "mpe", "video/mpeg" },
    { "qt", "video/quicktime" },
    { "mov", "video/quicktime" },
    { "asf", "video/x-ms-asf" },
    { "asx", "video/x-ms-asf" },
    { "avi", "video/x-msvideo" },
    { "movie", "video/x-sgi-movie" },
    { "vrm", "x-world/x-vrml" },
    { "vrml", "x-world/x-vrml" },
    { "wrl", "x-world/x-vrml" },
    { "ogg", "application/x-ogg" },
    { NULL, NULL }
};


char *GetMimeType(char *pathname)
{
    int i;
    char buf[128];
    char *s;

    strcpy(buf, pathname);

    // find final slash
    if ((s = strrchr(buf, '/')) == NULL)
        s = buf;

    if ((s = strrchr(s, '.')) == NULL)
        return "application/octet-stream";        // no file extension - return default mimetype
    s++;    // skip the '.'

    for (i = 0; MimeTab[i].ext != NULL; i++)
        if (!strcasecmp(s, MimeTab[i].ext))
            return MimeTab[i].mimetype; // found mimetype

    // no mimetype found
    return "application/octet-stream";

}       // 'GetMimeType()'

