/*
  This code is part of ezFCPlib.
  
  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab
  
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

#include <string.h>

#include "ezFCPlib.h"

/*
	Set the host address of the Freenet node.
	Also takes care of memory allocation.
*/
void fcpSetHost(char *host)
{
	if (_fcpHost) free(_fcpHost);
	_fcpHost = malloc(strlen(host) + 1);
	
  strcpy(_fcpHost, host);
}
