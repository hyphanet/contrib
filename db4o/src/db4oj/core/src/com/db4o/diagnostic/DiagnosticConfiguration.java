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
package com.db4o.diagnostic;


/**
 * provides methods to configure the behaviour of db4o diagnostics.
 * <br><br>Diagnostic system can be enabled on a running db4o database 
 * to notify a user about possible problems or misconfigurations.
 * Diagnostic listeners can be be added and removed with calls
 * to this interface.
 * To install the most basic listener call:<br>
 * <code>Db4o.configure().diagnostic().addListener(new DiagnosticToConsole());</code>
 * @see com.db4o.config.Configuration#diagnostic()
 * @see DiagnosticListener
 */
public interface DiagnosticConfiguration {
    
    /**
     * adds a DiagnosticListener to listen to Diagnostic messages.
     */
    public void addListener(DiagnosticListener listener);
    
    /**
     * removes all DiagnosticListeners.
     */
    public void removeAllListeners();
}
