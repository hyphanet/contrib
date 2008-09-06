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

import com.db4o.events.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;

/**
 * @exclude
 * @sharpen.ignore
 */
public class Event4Impl implements Event4 {
	
	private Collection4 _listeners;
	
	public Event4Impl() {
	}
	
	public final void addListener(EventListener4 listener) {
		validateListener(listener);
		
		Collection4 listeners = new Collection4();
		listeners.add(listener);
		addExistingListenersTo(listeners);
		_listeners = listeners;
		
		onListenerAdded();
	}
	
	private void addExistingListenersTo(Collection4 newListeners){
		if(_listeners == null){
			return;
		}
		Iterator4 i = _listeners.iterator();
		while(i.moveNext()){
			newListeners.add(i.current());
		}
		
	}

	/**
	 * Might be overriden whenever specific events need
	 * to know when listeners subscribe to the event.
	 */
	protected void onListenerAdded() {
	}

	public final void removeListener(EventListener4 listener) {
		validateListener(listener);
		
		if (null == _listeners) {
			return;
		}
		
		Collection4 listeners = new Collection4();
		addExistingListenersTo(listeners);
		listeners.remove(listener);
		
		_listeners = listeners;
	}
	
	public final void trigger(EventArgs args) {
		if (null == _listeners) {
			return;
		}
		Iterator4 iterator = _listeners.iterator();
		while (iterator.moveNext()) {
			EventListener4 listener = (EventListener4)iterator.current();
			onEvent(listener, this, args);
		}
	}
	
	private void onEvent(EventListener4 listener, Event4 e, EventArgs args) {
		try {
			listener.onEvent(e, args);
		} catch(Db4oException db4oException) {
			throw db4oException;
		} catch (Throwable exc) {
			throw new EventException(exc);
		}
	}
	
	private void validateListener(EventListener4 listener) {
		if (null == listener) {
			throw new ArgumentNullException();
		}
	}

	public boolean hasListeners() {
		return _listeners != null;
	}
}
