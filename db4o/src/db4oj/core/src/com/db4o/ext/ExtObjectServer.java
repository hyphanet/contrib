/* Copyright (C) 2004 - 2008  db4objects Inc.  http://www.db4o.com

This file is part of the db4o open source object database.

db4o is free software; you can redistribute it and/or modify it under
the terms of version 2 of the GNU General Public License as published
by the Free Software Foundation and as clarified by db4objects' GPL 
interpretation policy, available at
http://www.db4o.com/about/company/legalpolicies/gplinterpretation/
Alternatively you can write to db4objects, Inc., 1900 S Norfolk Street,
Suite 350, San Mateo, CA 94403, USA.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. */
package com.db4o.ext;

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;

/**
 * extended functionality for the ObjectServer interface.
 * <br><br>Every ObjectServer also always is an ExtObjectServer
 * so a cast is possible.<br><br>
 * {@link com.db4o.ObjectServer#ext}
 * is a convenient method to perform the cast.<br><br>
 * The functionality is split to two interfaces to allow newcomers to
 * focus on the essential methods.
 */
public interface ExtObjectServer extends ObjectServer{
    
    /**
     * backs up the database file used by the ObjectServer.
     * <br><br>While the backup is running, the ObjectServer can continue to be
     * used. Changes that are made while the backup is in progress, will be applied to
     * the open ObjectServer and to the backup.<br><br>
     * While the backup is running, the ObjectContainer should not be closed.<br><br>
     * If a file already exists at the specified path, it will be overwritten.<br><br>
     * @param path a fully qualified path
     */
    public void backup(String path) throws IOException;
    
    /**
     * returns the number of connected clients.
     */
    public int clientCount();
	
	 /**
	 * returns the {@link Configuration} context for this ObjectServer.
	 * <br><br>
	 * Upon opening an ObjectServer with any of the factory methods in the
	 * {@link Db4o} class, the global {@link Configuration} context
	 * is copied into the ObjectServer. The {@link Configuration}
	 * can be modified individually for
	 * each ObjectServer without any effects on the global settings.<br><br>
	 * @return the Configuration context for this ObjectServer
     * @see com.db4o.Db4o#configure
     */
	public Configuration configure();
	
	/**
	 * returns the ObjectContainer used by the server.
	 * <br><br>
	 * @return the ObjectContainer used by the server
	 */
	public ObjectContainer objectContainer();
	
	/**
	 * removes client access permissions for the specified user.
	 * <br><br>
	 * @param userName the name of the user
	 */
	public void revokeAccess(String userName);
	
	/**
	 * @return The local port this server uses, 0 if disconnected or in embedded mode
	 */
    public int port();
}
