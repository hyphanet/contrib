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
import com.db4o.internal.callbacks.*;
import com.db4o.query.*;

/**
 * @exclude
 */
public class EventRegistryImpl  implements Callbacks, EventRegistry {
	
	private final InternalObjectContainer _container;
	
	protected final Event4Impl _queryStarted = new Event4Impl();
	protected final Event4Impl _queryFinished = new Event4Impl();
	protected final Event4Impl _creating = new Event4Impl();
	protected final Event4Impl _activating = new Event4Impl();
	protected final Event4Impl _updating = new Event4Impl();
	protected final Event4Impl _deleting = new Event4Impl();
	protected final Event4Impl _deactivating = new Event4Impl();
	protected final Event4Impl _created = new Event4Impl();
	protected final Event4Impl _activated = new Event4Impl();
	protected final Event4Impl _updated = new Event4Impl();
	protected final Event4Impl _deleted = new Event4Impl();
	protected final Event4Impl _deactivated = new Event4Impl();
	protected final Event4Impl _committing = new Event4Impl();
	protected final Event4Impl _committed = new CommittedEvent();
	protected final Event4Impl _instantiated = new Event4Impl();
	protected final Event4Impl _classRegistered = new Event4Impl();	
	protected final Event4Impl _closing = new Event4Impl();
	
	/**
	 * @sharpen.ignore
	 */
	protected class CommittedEvent extends Event4Impl {
		protected void onListenerAdded() {
			onCommittedListener();
		}
	}

	public EventRegistryImpl(InternalObjectContainer container) {
		_container = container;
	}

	// Callbacks implementation
	public void queryOnFinished(Transaction transaction, Query query) {
		EventPlatform.triggerQueryEvent(transaction, _queryFinished, query);
	}

	public void queryOnStarted(Transaction transaction, Query query) {
		EventPlatform.triggerQueryEvent(transaction, _queryStarted, query);
	}
	
	public boolean objectCanNew(Transaction transaction, Object obj) {
		return EventPlatform.triggerCancellableObjectEventArgs(transaction, _creating, obj);
	}
	
	public boolean objectCanActivate(Transaction transaction, Object obj) {
		return EventPlatform.triggerCancellableObjectEventArgs(transaction, _activating, obj);
	}
	
	public boolean objectCanUpdate(Transaction transaction, Object obj) {
		return EventPlatform.triggerCancellableObjectEventArgs(transaction, _updating, obj);
	}
	
	public boolean objectCanDelete(Transaction transaction, Object obj) {
		return EventPlatform.triggerCancellableObjectEventArgs(transaction, _deleting, obj);
	}
	
	public boolean objectCanDeactivate(Transaction transaction, Object obj) {
		return EventPlatform.triggerCancellableObjectEventArgs(transaction, _deactivating, obj);
	}
	
	public void objectOnActivate(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _activated, obj);
	}
	
	public void objectOnNew(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _created, obj);
	}
	
	public void objectOnUpdate(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _updated, obj);
	}
	
	public void objectOnDelete(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _deleted, obj);		
	}	

	public void classOnRegistered(ClassMetadata clazz) {
		EventPlatform.triggerClassEvent(_classRegistered, clazz);		
	}	

	public void objectOnDeactivate(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _deactivated, obj);
	}
	
	public void objectOnInstantiate(Transaction transaction, Object obj) {
		EventPlatform.triggerObjectEvent(transaction, _instantiated, obj);
	}
	
	public void commitOnStarted(Transaction transaction, CallbackObjectInfoCollections objectInfoCollections) {
		EventPlatform.triggerCommitEvent(transaction, _committing, objectInfoCollections);
	}
	
	public void commitOnCompleted(Transaction transaction, CallbackObjectInfoCollections objectInfoCollections) {
		EventPlatform.triggerCommitEvent(transaction, _committed, objectInfoCollections);
	}
	
	public void closeOnStarted(ObjectContainer container) {
		EventPlatform.triggerObjectContainerEvent(container, _closing);
	}

	public Event4 queryFinished() {
		return _queryFinished;
	}

	public Event4 queryStarted() {
		return _queryStarted;
	}

	public Event4 creating() {
		return _creating;
	}

	public Event4 activating() {
		return _activating;
	}

	public Event4 updating() {
		return _updating;
	}

	public Event4 deleting() {
		return _deleting;
	}

	public Event4 deactivating() {
		return _deactivating;
	}

	public Event4 created() {
		return _created;
	}

	public Event4 activated() {
		return _activated;
	}

	public Event4 updated() {
		return _updated;
	}

	public Event4 deleted() {
		return _deleted;
	}

	public Event4 deactivated() {
		return _deactivated;
	}
	
	public Event4 committing() {
		return _committing;
	}
	
	/**
	 * @sharpen.event.onAdd onCommittedListener
	 */
	public Event4 committed() {
		return _committed;
	}

	public Event4 classRegistered() {
		return _classRegistered;
	}

	public Event4 instantiated() {
		return _instantiated;
	}
	
	public Event4 closing() {
		return _closing;
	}
	
	protected void onCommittedListener() {
		// TODO: notify the server that we are interested in 
		// committed callbacks
		_container.onCommittedListener();
	}

	public boolean caresAboutCommitting() {
		return EventPlatform.hasListeners(_committing);
	}

	public boolean caresAboutCommitted() {
		return EventPlatform.hasListeners(_committed);
	}
	
    public boolean caresAboutDeleting() {
        return EventPlatform.hasListeners(_deleting);
    }

    public boolean caresAboutDeleted() {
        return EventPlatform.hasListeners(_deleted);
    }	
}
