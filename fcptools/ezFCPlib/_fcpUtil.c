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

/* we don't need to include ezfcplib.h is we include these */

#include <sys/types.h>
#include <sys/stat.h>

#include <string.h>
#include <stdlib.h>

/*
  function xtoi()

  Convert a hexadecimal string into an int. This is the hex version of
	atoi.
*/
long xtoi(char *s)
{
  long val = 0;
  
  if (!s) return 0L;
  
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
	function memtoi()
	extension of atoi()

	this func recognises suffices on numbers
	eg '64k' will get parsed as 65536
	recognises the suffices 'k', 'K', 'm', 'M', 'g', 'G'
	(Thanks to mjr)
*/

int memtoi(char *s)
{
	int n = atoi(s);
	switch (s[strlen(s)-1])
	{
		case 'G':
		case 'g':
			return n << 30;
		case 'M':
		case 'm':
			return n << 20;
		case 'K':
		case 'k':
			return n << 10;
		default:
			return n;
	 }
}

