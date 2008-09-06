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
package com.db4o.foundation;

/**
 * @exclude
 */
public class BlockingQueue implements Queue4 {
    
	protected NonblockingQueue _queue = new NonblockingQueue();

	protected Lock4 _lock = new Lock4();
	
	protected boolean _stopped;

	public void add(final Object obj) {
		_lock.run(new Closure4() {
			public Object run() {
				_queue.add(obj);
				_lock.awake();
				return null;
			}
		});
	}

	public boolean hasNext() {
		Boolean hasNext = (Boolean) _lock.run(new Closure4() {
			public Object run() {
				return new Boolean(_queue.hasNext());
			}
		});
		return hasNext.booleanValue();
	}

	public Iterator4 iterator() {
		return (Iterator4) _lock.run(new Closure4() {
			public Object run() {
				return _queue.iterator();
			}
		});
	}

	public Object next() throws BlockingQueueStoppedException {
		return _lock.run(new Closure4() {
			public Object run() {
				if (_queue.hasNext()) {
					return _queue.next();
				}
				if(_stopped) {
					throw new BlockingQueueStoppedException();
				}
				_lock.snooze(Integer.MAX_VALUE);
				Object obj = _queue.next();
				if(obj == null){
					throw new BlockingQueueStoppedException();
				}
				return obj;
			}
		});
	}
	
	public void stop(){
		_lock.run(new Closure4() {
			public Object run() {
				_stopped = true;
				_lock.awake();
				return null;
			}
		});
	}

	public Object nextMatching(final Predicate4 condition) {
		return _lock.run(new Closure4() {
			public Object run() {
				return _queue.nextMatching(condition);
			}
		});
	}
}
