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
package com.db4o.db4ounit.common.migration;

import com.db4o.db4ounit.common.freespace.*;
import com.db4o.db4ounit.common.handlers.*;
import com.db4o.db4ounit.util.*;
import com.db4o.foundation.*;

import db4ounit.*;


public class Db4oMigrationTestSuite implements TestSuiteBuilder {
	
	public static void main(String[] args) {
		new ConsoleTestRunner(Db4oMigrationTestSuite.class).run();
	}

	public Iterator4 iterator() {
		return new Db4oMigrationSuiteBuilder(testCases(), libraries()).iterator();
	}

	private String[] libraries() {
		if (true) {
			return Db4oMigrationSuiteBuilder.ALL;
		}
		
		if (true) {
			// run against specific libraries + the current one
			
			String netPath = "db4o.archives/net-2.0/7.2/Db4objects.Db4o.dll";
			String javaPath = "db4o.archives/java1.2/db4o-7.2.31.10304-java1.2.jar";
			
			return new String[] {
				WorkspaceServices.workspacePath(javaPath),
			};
		} 
		return Db4oMigrationSuiteBuilder.CURRENT;
	}

	protected Class[] testCases() {
	    Class[] classes = 
		new Class[] {
            BooleanHandlerUpdateTestCase.class,
            ByteHandlerUpdateTestCase.class,
            CascadedDeleteFileFormatUpdateTestCase.class,
            CharHandlerUpdateTestCase.class,
            DateHandlerUpdateTestCase.class,
            DoubleHandlerUpdateTestCase.class,
            FloatHandlerUpdateTestCase.class,
            IntHandlerUpdateTestCase.class,
            InterfaceHandlerUpdateTestCase.class,
            LongHandlerUpdateTestCase.class,
            MultiDimensionalArrayHandlerUpdateTestCase.class,
            NestedArrayUpdateTestCase.class,
            ObjectArrayUpdateTestCase.class,
            ShortHandlerUpdateTestCase.class,
            StringHandlerUpdateTestCase.class,
            
            // Order to run freespace tests last is
            // deliberate. Global configuration Db4o.configure()
            // is changed in the #setUp call and reused.
            
            IxFreespaceMigrationTestCase.class,
            FreespaceManagerMigrationTestCase.class,

		};
	    return addJavaTestCases(classes);
	}
	
    /**
     * @sharpen.remove null
     */
	protected Class[] javaOnlyTestCases(){
	    return new Class[] {
            ArrayListUpdateTestCase.class,
            HashtableUpdateTestCase.class,
            VectorUpdateTestCase.class,
	    };
	}
	
   protected Class[] addJavaTestCases(Class[] classes){
        Class[] javaTestCases = javaOnlyTestCases(); 
        if(javaTestCases == null){
            return classes;
        }
        int len = javaTestCases.length;
        Class[] allClasses = new Class[classes.length + len];
        System.arraycopy(javaTestCases, 0, allClasses, 0,len );
        System.arraycopy(classes, 0, allClasses, len,classes.length);
        return allClasses;
    }

}
