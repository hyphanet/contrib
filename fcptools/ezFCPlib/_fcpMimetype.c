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

#include <string.h>

#include "ez_sys.h"

/* mime types imported from the debian standard mime types */

static struct mimeTabEnt
{
  char *ext;
  char *mimetype;

} MimeTab[] = {
  { "ez", "application/andrew-inset" },
  { "csm", "application/cu-seeme" },
  { "cu", "application/cu-seeme" },
  { "tsp", "application/dsptype" },
  { "spl", "application/futuresplash" },
  { "hta", "application/hta" },
  { "cpt", "application/mac-compactpro" },
  { "hqx", "application/mac-binhex40" },
  { "nb", "application/mathematica" },
  { "mdb", "application/msaccess" },
  { "doc", "application/msword" },
  { "dot", "application/msword" },
  { "bin", "application/octet-stream" },
  { "oda", "application/oda" },
  { "prf", "application/pics-rules" },
  { "pdf", "application/pdf" },
  { "pgp", "application/pgp-signature" },
  { "ps", "application/postscript" },
  { "ai", "application/postscript" },
  { "eps", "application/postscript" },
  { "rtf", "application/rtf" },
  { "smi", "application/smil" },
  { "smil", "application/smil" },
  { "wp5", "application/wordperfect5.1" },
  { "zip", "application/zip" },
  { "cdy", "application/vnd.cinderella" },
  { "mif", "application/vnd.mif" },
  { "xls", "application/vnd.ms-excel" },
  { "xlb", "application/vnd.ms-excel" },
  { "cat", "application/vnd.ms-pki.seccat" },
  { "stl", "application/vnd.ms-pki.stl" },
  { "ppt", "application/vnd.ms-powerpoint" },
  { "pps", "application/vnd.ms-powerpoint" },
  { "pot", "application/vnd.ms-powerpoint" },
  { "sdw", "application/vnd.stardivision.writer" },
  { "sgl", "application/vnd.stardivision.writer-global" },
  { "vor", "application/vnd.stardivision.writer" },
  { "sdc", "application/vnd.stardivision.calc" },
  { "sda", "application/vnd.stardivision.draw" },
  { "sdd", "application/vnd.stardivision.impress" },
  { "sdp", "application/vnd.stardivision.impress-packed" },
  { "smf", "application/vnd.stardivision.math" },
  { "sds", "application/vnd.stardivision.chart" },
  { "smd", "application/vnd.stardivision.mail" },
  { "wbxml", "application/vnd.wap.wbxml" },
  { "wmlc", "application/vnd.wap.wmlc" },
  { "wmlsc", "application/vnd.wap.wmlscriptc" },
  { "wk", "application/x-123" },
  { "bcpio", "application/x-bcpio" },
  { "cdf", "application/x-cdf" },
  { "vcd", "application/x-cdlink" },
  { "pgn", "application/x-chess-pgn" },
  { "cpio", "application/x-cpio" },
  { "csh", "application/x-csh" },
  { "deb", "application/x-debian-package" },
  { "dcr", "application/x-director" },
  { "dir", "application/x-director" },
  { "dxr", "application/x-director" },
  { "wad", "application/x-doom" },
  { "dms", "application/x-dms" },
  { "dvi", "application/x-dvi" },
  { "pfa", "application/x-font" },
  { "pfb", "application/x-font" },
  { "gsf", "application/x-font" },
  { "pcf", "application/x-font" },
  { "pcf.Z", "application/x-font" },
  { "spl", "application/x-futuresplash" },
  { "gnumeric", "application/x-gnumeric" },
  { "gcf", "application/x-graphing-calculator" },
  { "gtar", "application/x-gtar" },
  { "tgz", "application/x-gtar" },
  { "taz", "application/x-gtar" },
  { "hdf", "application/x-hdf" },
  { "phtml", "application/x-httpd-php" },
  { "pht", "application/x-httpd-php" },
  { "php", "application/x-httpd-php" },
  { "phps", "application/x-httpd-php-source" },
  { "php3", "application/x-httpd-php3" },
  { "php3p", "application/x-httpd-php3-preprocessed" },
  { "php4", "application/x-httpd-php4" },
  { "ica", "application/x-ica" },
  { "ins", "application/x-internet-signup" },
  { "isp", "application/x-internet-signup" },
  { "iii", "application/x-iphone" },
  { "jar", "application/x-java-archive" },
  { "jnlp", "application/x-java-jnlp-file" },
  { "ser", "application/x-java-serialized-object" },
  { "class", "application/x-java-vm" },
  { "js", "application/x-javascript" },
  { "chrt", "application/x-kchart" },
  { "kil", "application/x-killustrator" },
  { "kpr", "application/x-kpresenter" },
  { "kpt", "application/x-kpresenter" },
  { "skp", "application/x-koan" },
  { "skd", "application/x-koan" },
  { "skt", "application/x-koan" },
  { "skm", "application/x-koan" },
  { "ksp", "application/x-kspread" },
  { "kwd", "application/x-kword" },
  { "kwt", "application/x-kword" },
  { "latex", "application/x-latex" },
  { "lha", "application/x-lha" },
  { "lzh", "application/x-lzh" },
  { "lzx", "application/x-lzx" },
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
  { "msi", "application/x-msi" },
  { "nc", "application/x-netcdf" },
  { "pac", "application/x-ns-proxy-autoconfig" },
  { "o", "application/x-object" },
  { "ogg", "application/x-ogg" },
  { "oza", "application/x-oz-application" },
  { "pl", "application/x-perl" },
  { "pm", "application/x-perl" },
  { "p7r", "application/x-pkcs7-certreqresp" },
  { "crl", "application/x-pkcs7-crl" },
  { "qtl", "application/x-quicktimeplayer" },
  { "rpm", "application/x-redhat-package-manager" },
  { "shar", "application/x-shar" },
  { "swf", "application/x-shockwave-flash" },
  { "swfl", "application/x-shockwave-flash" },
  { "sh", "application/x-sh" },
  { "sit", "application/x-stuffit" },
  { "sv4cpio", "application/x-sv4cpio" },
  { "sv4crc", "application/x-sv4crc" },
  { "tar", "application/x-tar" },
  { "tcl", "application/x-tcl" },
  { "tex", "application/x-tex" },
  { "gf", "application/x-tex-gf" },
  { "pk", "application/x-tex-pk" },
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
  { "crt", "application/x-x509-ca-cert" },
  { "fig", "application/x-xfig" },
  { "aif", "#audio/aiff" },
  { "aifc", "#audio/aiff" },
  { "aiff", "#audio/aiff" },
  { "au", "audio/basic" },
  { "snd", "audio/basic" },
  { "mid", "audio/midi" },
  { "midi", "audio/midi" },
  { "kar", "audio/midi" },
  { "mpga", "audio/mpeg" },
  { "mpega", "audio/mpeg" },
  { "mp2", "audio/mpeg" },
  { "mp3", "audio/mpeg" },
  { "m3u", "audio/mpegurl" },
  { "sid", "audio/prs.sid" },
  { "wav", "#audio/x-wav" },
  { "aif", "audio/x-aiff" },
  { "aiff", "audio/x-aiff" },
  { "aifc", "audio/x-aiff" },
  { "gsm", "audio/x-gsm" },
  { "m3u", "audio/x-mpegurl" },
  { "rpm", "audio/x-pn-realaudio-plugin" },
  { "ra", "audio/x-pn-realaudio" },
  { "rm", "audio/x-pn-realaudio" },
  { "ram", "audio/x-pn-realaudio" },
  { "ra", "audio/x-realaudio" },
  { "pls", "audio/x-scpls" },
  { "sd2", "audio/x-sd2" },
  { "wav", "audio/x-wav" },
  { "pdb", "chemical/x-pdb" },
  { "xyz", "chemical/x-xyz" },
  { "bmp", "image/bmp" },
  { "gif", "image/gif" },
  { "ief", "image/ief" },
  { "jpeg", "image/jpeg" },
  { "jpg", "image/jpeg" },
  { "jpe", "image/jpeg" },
  { "pcx", "image/pcx" },
  { "png", "image/png" },
  { "svg", "image/svg+xml" },
  { "svgz", "image/svg+xml" },
  { "tiff", "image/tiff" },
  { "tif", "image/tiff" },
  { "wbmp", "image/vnd.wap.wbmp" },
  { "ras", "image/x-cmu-raster" },
  { "cdr", "image/x-coreldraw" },
  { "pat", "image/x-coreldrawpattern" },
  { "cdt", "image/x-coreldrawtemplate" },
  { "cpt", "image/x-corelphotopaint" },
  { "djvu", "image/x-djvu" },
  { "djv", "image/x-djvu" },
  { "art", "image/x-jg" },
  { "jng", "image/x-jng" },
  { "bmp", "image/x-ms-bmp" },
  { "psd", "image/x-photoshop" },
  { "pnm", "image/x-portable-anymap" },
  { "pbm", "image/x-portable-bitmap" },
  { "pgm", "image/x-portable-graymap" },
  { "ppm", "image/x-portable-pixmap" },
  { "rgb", "image/x-rgb" },
  { "xbm", "image/x-xbitmap" },
  { "xpm", "image/x-xpixmap" },
  { "xwd", "image/x-xwindowdump" },
  { "igs", "model/iges" },
  { "iges", "model/iges" },
  { "msh", "model/mesh" },
  { "mesh", "model/mesh" },
  { "silo", "model/mesh" },
  { "wrl", "model/vrml" },
  { "vrml", "model/vrml" },
  { "csv", "text/comma-separated-values" },
  { "css", "text/css" },
  { "323", "text/h323" },
  { "htm", "text/html" },
  { "html", "text/html" },
  { "xhtml", "text/html" },
  { "uls", "text/iuls" },
  { "mml", "text/mathml" },
  { "asc", "text/plain" },
  { "txt", "text/plain" },
  { "text", "text/plain" },
  { "diff", "text/plain" },
  { "rtx", "text/richtext" },
  { "rtf", "text/rtf" },
  { "sct", "text/scriptlet" },
  { "wsc", "text/scriptlet" },
  { "tsv", "text/tab-separated-values" },
  { "wml", "text/vnd.wap.wml" },
  { "wmls", "text/vnd.wap.wmlscript" },
  { "xml", "text/xml" },
  { "xsl", "text/xml" },
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
  { "vcs", "text/x-vcalendar" },
  { "vcf", "text/x-vcard" },
  { "avi", "#video/avi" },
  { "dl", "video/dl" },
  { "fli", "video/fli" },
  { "gl", "video/gl" },
  { "mpeg", "video/mpeg" },
  { "mpg", "video/mpeg" },
  { "mpe", "video/mpeg" },
  { "qt", "video/quicktime" },
  { "mov", "video/quicktime" },
  { "mxu", "video/vnd.mpegurl" },
  { "dif", "video/x-dv" },
  { "dv", "video/x-dv" },
  { "lsf", "video/x-la-asf" },
  { "lsx", "video/x-la-asf" },
  { "mng", "video/x-mng" },
  { "asf", "video/x-ms-asf" },
  { "asx", "video/x-ms-asf" },
  { "avi", "video/x-msvideo" },
  { "movie", "video/x-sgi-movie" },
  { "ice", "x-conference/x-cooltalk" },
  { "vrm", "x-world/x-vrml" },
  { "vrml", "x-world/x-vrml" },
  { "wrl", "x-world/x-vrml" },
  { 0, 0 }
};

char *_fcpGetMimetype(char *filename)
{
  int   i;
  char *s;
  
  /* find final slash */
  if ((s = strrchr(filename, '/')) == 0)
    s = filename;
	
	/* now s points to the last '/' */
	
  if ((s = strrchr(s, '.')) == 0) {
		
		/* no file extension - return default mimetype */
    return "application/octet-stream";
	}
	
  s++;    /* skip the '.' */
  
  for (i=0; MimeTab[i].ext != 0; i++) {
		
    if (!strcasecmp(s, MimeTab[i].ext)) {
			/* found mimetype */
			
      return MimeTab[i].mimetype;
		}
	}
	
	_fcpLog(FCP_LOG_DEBUG, "no mimetype found in table: returning default");
  
  /* no mimetype found */
  return "application/octet-stream";
}

