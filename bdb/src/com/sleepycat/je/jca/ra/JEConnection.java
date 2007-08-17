/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JEConnection.java,v 1.13.2.2 2007/05/22 20:36:39 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

import javax.resource.ResourceException;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;

/**
 * A JEConnection provides access to JE services. See
 * &lt;JEHOME&gt;/examples/jca/HOWTO-**.txt and
 * &lt;JEHOME&gt;/examples/jca/simple/SimpleBean.java for more information on 
 * how to build the resource adaptor and use a JEConnection.
 */
public interface JEConnection {

    public void setManagedConnection(JEManagedConnection mc,
				     JELocalTransaction lt);

    public JELocalTransaction getLocalTransaction();

    public void setLocalTransaction(JELocalTransaction txn);

    public Environment getEnvironment()
	throws ResourceException;

    public Database openDatabase(String name, DatabaseConfig config)
	throws DatabaseException;

    public SecondaryDatabase openSecondaryDatabase(String name,
						   Database primaryDatabase,
						   SecondaryConfig config)
	throws DatabaseException;

    public void removeDatabase(String databaseName)
	throws DatabaseException;

    public long truncateDatabase(String databaseName, boolean returnCount)
	throws DatabaseException;

    public Transaction getTransaction()
	throws ResourceException;

    public void close()
	throws JEException;
}
