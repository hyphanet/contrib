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
package com.db4o.db4ounit.common.ta.collections;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.events.*;
import com.db4o.internal.*;


/**
 * Configures the support for paged collections.
 */
public class PagedListSupport implements ConfigurationItem {

	public void apply(InternalObjectContainer db) {
		eventRegistry(db).updating().addListener(new EventListener4() {
			public void onEvent(Event4 e, EventArgs args) {
				CancellableObjectEventArgs cancellable = (CancellableObjectEventArgs) args;
				if (cancellable.object() instanceof Page) {
					Page page = (Page) cancellable.object();
					if (!page.isDirty()) {
						cancellable.cancel();
					}
				}
			}
		});
	}
	
	private static EventRegistry eventRegistry(ObjectContainer db) {
		return EventRegistryFactory.forObjectContainer(db);
	}

	public void prepare(Configuration configuration) {
		// Nothing to do...
	}
}
