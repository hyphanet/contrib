
/*
  This code is part of FreeWeb - an FCP-based client for Freenet

  Designed and implemented by David McNab, david@rebirthing.co.nz
  CopyLeft (c) 2001 by David McNab

  The FreeWeb website is at http://freeweb.sourceforge.net
  The website for Freenet is at http://freenet.sourceforge.net

  This code is distributed under the GNU Public Licence (GPL) version 2.
  See http://www.gnu.org/ for further details of the GPL.
*/


#include "ezFCPlib.h"


/*
  Function:    _fcpReadBlk()

  Arguments:   hfcp        freenet fcp handle
               buf         buffer into which to write data
               len         number of bytes to retrieve

  Description: gets a number of bytes
               this is called after a datafound message.
               It automatically unwraps any incoming datachunk messages, and
               transparently delivers a steady stream of data
*/

int _fcpReadBlk(HFCP *hfcp, char *buf, int len)
{
  char    *tmp0, *tmp1, *tmpend;
  int     needed = len;

  /* if there's anything sitting in buffer, add it */
  if ((tmp0 = hfcp->conn.response.body.datachunk.data) != NULL) {
	 tmp1 = hfcp->conn.response.body.datachunk.dataptr;
	 tmpend = hfcp->conn.response.body.datachunk.dataend;
	 
	 /* copy available data, or reqd data, whichever is smaller */
	 if (tmpend - tmp1 >= needed) {

		 /* trivial - got enough in buffer */
		 memcpy(buf, tmp1, needed);
		 hfcp->conn.response.body.datachunk.dataptr += needed;
		
		 /* didch block if it's exhausted */
		 if (tmpend - tmp1 == needed) {
			 free(hfcp->conn.response.body.datachunk.data);
			 hfcp->conn.response.body.datachunk.data = NULL;
		 }
		 
		 return needed;      /* all done - see ya next time */
	 }
	 
	 /* non-trivial - we need to drain what we have, then get some more */
	 memcpy(buf, tmp1, tmpend - tmp1);
	 hfcp->conn.response.body.datachunk.dataptr += tmpend - tmp1;
	 buf += tmpend - tmp1;
	 needed -= tmpend - tmp1;
	 
	 /* ditch the data pointer */
	 free(hfcp->conn.response.body.datachunk.data);
	 hfcp->conn.response.body.datachunk.data = NULL;
	 
  } /* drop thru */


  /* read fresh data - XXXXXXXXXXXXXXXX - fix all this!!! */
  while (needed > 0) {
		
		/* try to pull a DataChunk message */
		if (_fcpRecvResponse(hfcp) != FCPRESP_TYPE_DATACHUNK) {
			
			/*free(hfcp->conn.response.body.datachunk.data);*/
			hfcp->conn.response.body.datachunk.data = NULL;
			break;
		}
		if (hfcp->conn.response.body.datachunk.length >= needed) {
			
			/* easy case - we've got enough data */
			memcpy(buf, hfcp->conn.response.body.datachunk.dataptr, needed);
			hfcp->conn.response.body.datachunk.dataptr += needed;
			needed = 0;
		} else {
			/* pain in the ass - grab and everything in this datachunk */
			memcpy(buf,
						 hfcp->conn.response.body.datachunk.dataptr,
						 hfcp->conn.response.body.datachunk.length);
			needed -= hfcp->conn.response.body.datachunk.length;
			buf += hfcp->conn.response.body.datachunk.length;
			
			/* turf this data */
			free(hfcp->conn.response.body.datachunk.data);
			hfcp->conn.response.body.datachunk.data = NULL;
		}
	 
		/* ditch data if it's exhausted */
		if (hfcp->conn.response.body.datachunk.dataptr >=
				hfcp->conn.response.body.datachunk.dataend) {
			free(hfcp->conn.response.body.datachunk.data);
			hfcp->conn.response.body.datachunk.data = NULL;
		}
  }
  
  return len - needed;
} /* '_fcpReadBlk()' */
