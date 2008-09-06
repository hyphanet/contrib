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
package  com.db4o.config;

import com.db4o.*;

/**
 * translator interface to translate objects on storage and activation.
 * <br><br>
 * By writing classes that implement this interface, it is possible to
 * define how application classes are to be converted to be stored more efficiently.
 * <br><br>
 * Before starting a db4o session, translator classes need to be registered. An example:<br>
 * <code>
 * Configuration config = Db4o.configure();<br>
 * ObjectClass oc = config.objectClass("package.className");<br>
 * oc.translate(new FooTranslator());</code><br><br>
 *
 */
public interface ObjectTranslator {

    /**
	 * db4o calls this method during storage and query evaluation.
     * @param container the ObjectContainer used
     * @param applicationObject the Object to be translated
     * @return return the object to store.<br>It needs to be of the class
	 * {@link #storedClass storedClass()}.
     */
    public Object onStore(ObjectContainer container, Object applicationObject);

    /**
	 * db4o calls this method during activation.
     * @param container the ObjectContainer used
     * @param applicationObject the object to set the members on
     * @param storedObject the object that was stored
     */
    public void onActivate(ObjectContainer container, Object applicationObject, Object storedObject);

    /**
	 * return the Class you are converting to.
     * @return the Class of the object you are returning with the method
	 * {@link #onStore onStore()}
	 */
	 public Class storedClass ();
}
