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
package com.db4o.db4ounit.common.ta.diagnostics;

import com.db4o.activation.*;
import com.db4o.config.*;
import com.db4o.db4ounit.common.ta.*;
import com.db4o.diagnostic.*;
import com.db4o.internal.*;
import com.db4o.ta.*;

import db4ounit.*;
import db4ounit.extensions.fixtures.*;
import db4ounit.extensions.util.*;

public class TransparentActivationDiagnosticsTestCase
	extends TransparentActivationTestCaseBase
	implements OptOutCS, OptOutDefragSolo {

	public static class SomeTAAwareData {
		public int _id;

		public SomeTAAwareData(int id) {
			_id = id;
		}
	}

	public static class SomeOtherTAAwareData implements Activatable {		
		public SomeTAAwareData _data;
		
		public void bind(Activator activator) {
		}
		
		public void activate(ActivationPurpose purpose) {			
		}

		public SomeOtherTAAwareData(SomeTAAwareData data) {
			_data = data;
		}
	}
	
	public static class NotTAAwareData {
		public SomeTAAwareData _data;

		public NotTAAwareData(SomeTAAwareData data) {
			_data = data;
		}
	}
	
	private static class DiagnosticsRegistered {
		public int _registeredCount = 0;
	}
	
	private final DiagnosticsRegistered _registered = new DiagnosticsRegistered();
	private final DiagnosticListener _checker;
	private DiagnosticConfiguration _diagnostic;
	
	public TransparentActivationDiagnosticsTestCase() {
		 _checker = new DiagnosticListener() {
			public void onDiagnostic(Diagnostic diagnostic) {
				if (!(diagnostic instanceof NotTransparentActivationEnabled)) {
					return;
				}
				NotTransparentActivationEnabled taDiagnostic=(NotTransparentActivationEnabled)diagnostic;
				Assert.areEqual(CrossPlatformServices.fullyQualifiedName(NotTAAwareData.class), ((ClassMetadata)taDiagnostic.reason()).getName());
				_registered._registeredCount++;
			}
		};
	}
	
	protected void configure(Configuration config) {
		super.configure(config);
		_diagnostic = config.diagnostic();
		_diagnostic.addListener(_checker);
	}
	
	protected void db4oTearDownBeforeClean() throws Exception {
		workaroundOsgiConfigCloningBehavior();
		
		db().ext().configure().diagnostic().removeAllListeners();
		super.db4oTearDownBeforeClean();
	}

	private void workaroundOsgiConfigCloningBehavior() {
		// fix for Osgi config cloning behavior - see Db4oOSGiBundleFixture
		_diagnostic.removeAllListeners();
	}
	
	public void testTADiagnostics() {
		store(new SomeTAAwareData(1));
		Assert.areEqual(0, _registered._registeredCount);
		store(new SomeOtherTAAwareData(new SomeTAAwareData(2)));
		Assert.areEqual(0, _registered._registeredCount);
		store(new NotTAAwareData(new SomeTAAwareData(3)));
		Assert.areEqual(1, _registered._registeredCount);
	}
	
	public static void main(String[] args) {
		new TransparentActivationDiagnosticsTestCase().runAll();
	}
}
