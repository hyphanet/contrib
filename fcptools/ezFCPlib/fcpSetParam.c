/*
	This code is part of ezFCPlib - an FCP-based C client library for Freenet

	Designed and implemented by David McNab, david@rebirthing.co.nz
	CopyLeft (c) 2001 by David McNab

	The FreeWeb website is at http://freeweb.sourceforge.net
	The website for Freenet is at http://freenet.sourceforge.net

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

extern char  _fcpHost[];

void fcpSetHost(char *newHost)
{
    strncpy(_fcpHost, newHost, L_HOST);
}


//
// Function:    fcpSetHtl
//
// Arguments:   hfcp    pointer to FCP handle, created with fcpCreateHandle()
//				htl		hops to live value
//
// Description: Sets the default HTL for all future freenet operations on given handle
//

void fcpSetHtl(HFCP *hfcp, int htl)
{
    hfcp->htl = htl;

}       // 'fcpDestroyHandle()'


//
// Function:    fcpSetRegress
//
// Arguments:   hfcp    pointer to FCP handle, created with fcpCreateHandle()
//
// Description: Sets the number of days to regress in retrying failed date redirects
//

void fcpSetRegress(HFCP *hfcp, int regress)
{
    hfcp->regress = regress;

}       // 'fcpDestroyHandle()'
