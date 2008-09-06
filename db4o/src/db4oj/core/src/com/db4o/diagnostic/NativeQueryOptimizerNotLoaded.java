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

public class NativeQueryOptimizerNotLoaded extends DiagnosticBase {

	private int _reason;
	private final Exception _details;
	public final static int NQ_NOT_PRESENT 			= 1;
	public final static int NQ_CONSTRUCTION_FAILED 	= 2;

	
	public NativeQueryOptimizerNotLoaded(int reason, Exception details) {
		_reason = reason;
		_details = details;
	}
	public String problem() {
		return "Native Query Optimizer could not be loaded";
	}

	public Object reason() {
		switch (_reason) {
		case NQ_NOT_PRESENT:
			return AppendDetails("Native query not present.");
		case NQ_CONSTRUCTION_FAILED:
			return AppendDetails("Native query couldn't be instantiated.");
		default:
			return AppendDetails("Reason not specified.");
		}
	}

	public String solution() {
		return "If you to have the native queries optimized, please check that the native query jar is present in the class-path.";
	}

	private Object AppendDetails(String reason) {
		if (_details == null) {
			return reason;
		}
		
		return reason + "\n" + _details.toString();
	}
}
