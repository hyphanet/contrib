/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JEManagedConnectionFactory.java,v 1.9.2.2 2007/05/22 20:36:39 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.dbi.EnvironmentImpl;

public class JEManagedConnectionFactory
    implements ManagedConnectionFactory, Serializable {

    private String userName;
    private String password;

    public JEManagedConnectionFactory() {
    }

    public Object createConnectionFactory(ConnectionManager cxManager)
	throws ResourceException {

        return new JEConnectionFactoryImpl(cxManager, this);
    }

    public Object createConnectionFactory()
	throws ResourceException {

	throw new UnsupportedOperationException("must supply a connMgr");
    }

    public ManagedConnection
	createManagedConnection(Subject subject,
				ConnectionRequestInfo info)
	throws ResourceException {

	JERequestInfo jeInfo = (JERequestInfo) info;
	return new JEManagedConnection(subject, jeInfo);
    }

    public ManagedConnection
	matchManagedConnections(Set connectionSet,
				Subject subject,
				ConnectionRequestInfo info)
        throws ResourceException {

	JERequestInfo jeInfo = (JERequestInfo) info;
	Iterator iter = connectionSet.iterator();
	while (iter.hasNext()) {
	    Object next = iter.next();
	    if (next instanceof JEManagedConnection) {
		JEManagedConnection mc = (JEManagedConnection) next;
		EnvironmentImpl nextEnvImpl =
		    DbInternal.envGetEnvironmentImpl(mc.getEnvironment());
		/* Do we need to match on more than root dir and r/o? */
		if (nextEnvImpl.getEnvironmentHome().
		    equals(jeInfo.getJERootDir()) &&
		    nextEnvImpl.isReadOnly() ==
		    jeInfo.getEnvConfig().getReadOnly()) {
		    return mc;
		}
	    }
	}
        return null;
    }

    public void setUserName(String userName) {
	this.userName = userName;
    }

    public String getUserName() {
	return userName;
    }

    public void setPassword(String password) {
	this.password = password;
    }

    public String getPassword() {
	return password;
    }

    public void setLogWriter(PrintWriter out)
	throws ResourceException {

    }

    public PrintWriter getLogWriter()
	throws ResourceException {

        return null;
    }

    public boolean equals(Object obj) {
	if (obj == null) {
	    return false;
	}

	if (obj instanceof JEManagedConnectionFactory) {
	    return true;
	} else {
	    return false;
	}
    }

    public int hashCode() {
	return 0;
    }
}
