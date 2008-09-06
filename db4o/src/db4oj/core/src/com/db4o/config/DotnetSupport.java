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
package com.db4o.config;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.*;
import com.db4o.internal.cs.messages.*;
import com.db4o.internal.handlers.*;
import com.db4o.query.*;

/**
 * Adds the basic configuration settings required to access a
 * .net generated database from java.
 * 
 * The configuration only makes sure that database files can be
 * successfully open and things like UUIDs can be successfully
 * retrieved.
 * 
 * @sharpen.ignore
 */
public class DotnetSupport implements ConfigurationItem {

	private final boolean _addCSSupport;
	
	public DotnetSupport() {
		_addCSSupport = false;	
	}
	
	/**
	 * @param addCSSupport true if mappings required for Client/Server 
	 *                     support should be included also.
	 */
	public DotnetSupport(boolean addCSSupport) {
		_addCSSupport = addCSSupport;
	}

	public void prepare(Configuration config) {
		config.addAlias(new WildcardAlias("Db4objects.Db4o.Ext.*, Db4objects.Db4o", "com.db4o.ext.*"));		
		config.addAlias(new TypeAlias("Db4objects.Db4o.StaticField, Db4objects.Db4o", StaticField.class.getName()));
		config.addAlias(new TypeAlias("Db4objects.Db4o.StaticClass, Db4objects.Db4o", StaticClass.class.getName()));
		
		if (_addCSSupport) {
			config.addAlias(new TypeAlias("System.Exception, mscorlib", ChainedRuntimeException.class.getName()));
			
	//		config.addAlias(new TypeAlias("java.lang.Throwable", FullTypeNameFor(typeof(Exception))));
	//		config.addAlias(new TypeAlias("java.lang.RuntimeException", FullTypeNameFor(typeof(Exception))));
	//		config.addAlias(new TypeAlias("java.lang.Exception", FullTypeNameFor(typeof(Exception))));
	
	
			config.addAlias(new TypeAlias("Db4objects.Db4o.Query.IEvaluation, Db4objects.Db4o", Evaluation.class.getName()));
			config.addAlias(new TypeAlias("Db4objects.Db4o.Query.ICandidate, Db4objects.Db4o", Candidate.class.getName()));
	
			config.addAlias(new WildcardAlias("Db4objects.Db4o.Internal.Query.Processor.*, Db4objects.Db4o", "com.db4o.internal.query.processor.*"));
	
			config.addAlias(new TypeAlias("Db4objects.Db4o.Foundation.Collection4, Db4objects.Db4o", Collection4.class.getName()));
			config.addAlias(new TypeAlias("Db4objects.Db4o.Foundation.List4, Db4objects.Db4o", List4.class.getName()));
			config.addAlias(new TypeAlias("Db4objects.Db4o.User, Db4objects.Db4o", User.class.getName()));
	
			config.addAlias(new TypeAlias("Db4objects.Db4o.Internal.CS.ClassInfo, Db4objects.Db4o", ClassInfo.class.getName()));
			config.addAlias(new TypeAlias("Db4objects.Db4o.Internal.CS.FieldInfo, Db4objects.Db4o", FieldInfo.class.getName()));
	
			config.addAlias(
					new TypeAlias(
							"Db4objects.Db4o.Internal.CS.Messages.MUserMessage+UserMessagePayload, Db4objects.Db4o", 
							MUserMessage.UserMessagePayload.class.getName()));
			
			config.addAlias(new WildcardAlias("Db4objects.Db4o.Internal.CS.Messages.*, Db4objects.Db4o", "com.db4o.internal.cs.messages.*"));
		}
	}
	
	public void apply(InternalObjectContainer container) {
		NetTypeHandler[] handlers = Platform4.jdk().netTypes(container.reflector());
		for (int netTypeIdx = 0; netTypeIdx < handlers.length; netTypeIdx++) {
			NetTypeHandler handler = handlers[netTypeIdx];
			container.handlers().registerNetTypeHandler(handler);
		}
	}
}
