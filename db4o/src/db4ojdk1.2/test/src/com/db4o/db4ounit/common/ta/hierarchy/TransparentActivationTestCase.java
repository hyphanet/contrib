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
package com.db4o.db4ounit.common.ta.hierarchy;

import java.util.*;

import com.db4o.config.*;
import com.db4o.db4ounit.common.ta.*;
import com.db4o.db4ounit.common.ta.collections.*;

import db4ounit.*;

public class TransparentActivationTestCase
	extends TransparentActivationTestCaseBase {

	public static void main(String[] args) {
		new TransparentActivationTestCase().runAll();
	}
	
	private static final int PRIORITY = 42;

	protected void configure(Configuration config) {
		super.configure(config);
		config.add(new PagedListSupport());
	}
	
	protected void store() throws Exception {
		Project project = new PrioritizedProject("db4o",PRIORITY);
		project.logWorkDone(new UnitOfWork("ta kick-off", new Date(1000), new Date(2000)));
		store(project);
	}
	
	public void test() {
		final PrioritizedProject project = (PrioritizedProject) retrieveOnlyInstance(Project.class);
		
		Assert.areEqual(PRIORITY, project.getPriority());
		// Project.totalTimeSpent needs the UnitOfWork objects to be activated
		Assert.areEqual(1000, project.totalTimeSpent());
	}
}
