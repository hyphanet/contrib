/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: JEConnectionMetaData.java,v 1.4 2006/09/12 19:16:48 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionMetaData;

public class JEConnectionMetaData
    implements ManagedConnectionMetaData {

    public JEConnectionMetaData() {
    }

    public String getEISProductName()
        throws ResourceException {

        return "Berkeley DB Java Edition JCA";
    }

    public String getEISProductVersion()
        throws ResourceException {

        return "2.0";
    }

    public int getMaxConnections()
        throws ResourceException {

	/* Make a je.* parameter? */
	return 100;
    }

    public String getUserName()
        throws ResourceException {

    	return null;
    }
}
