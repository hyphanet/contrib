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
package com.db4o.internal.events;

import com.db4o.*;
import com.db4o.events.*;
import com.db4o.internal.*;
import com.db4o.query.*;

/**
 * Platform dependent code for dispatching events.
 * 
 * @sharpen.ignore
 */
public class EventPlatform {

	public static void triggerQueryEvent(Transaction transaction, Event4Impl e, Query q) {
		if(!e.hasListeners()) {
			return;
		}
		e.trigger(new QueryEventArgs(transaction, q));
	}

	public static void triggerClassEvent(Event4Impl e, ClassMetadata clazz) {
		if(!e.hasListeners()) {
			return;
		}
		e.trigger(new ClassEventArgs(clazz));
	}

	public static boolean triggerCancellableObjectEventArgs(Transaction transaction, Event4Impl e, Object o) {
		if(!e.hasListeners()) {
			return true;
		}
		CancellableObjectEventArgs args = new CancellableObjectEventArgs(transaction, o);
		e.trigger(args);
		return !args.isCancelled();
	}
	
	public static void triggerObjectEvent(Transaction transaction, Event4Impl e, Object o) {
		if(!e.hasListeners()) {
			return;
		}
		e.trigger(new ObjectEventArgs(transaction, o));
	}

	public static void triggerCommitEvent(Transaction transaction, Event4Impl e, CallbackObjectInfoCollections collections) {
		if(!e.hasListeners()) {
			return;
		}
		e.trigger(new CommitEventArgs(transaction, collections));
	}
	
	public static void triggerObjectContainerEvent(ObjectContainer container, Event4Impl e) {
		if(!e.hasListeners()) {
			return;
		}
		e.trigger(new ObjectContainerEventArgs(container));
	}
	
	public static boolean hasListeners(Event4Impl e) {
		return e.hasListeners();
	}
}
