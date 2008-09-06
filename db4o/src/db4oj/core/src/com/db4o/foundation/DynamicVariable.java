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
 * A dynamic variable is a value associated to a specific thread and scope.
 * 
 * The value is brought into scope with the {@link #with} method.
 * 
 */
public class DynamicVariable {
	
	private static class ThreadSlot {
		public final Thread thread;
		public final Object value;
		public ThreadSlot next;
		
		public ThreadSlot(Object value_, ThreadSlot next_) {
			thread = Thread.currentThread();
			value = value_;
			next = next_;
		}
	}
	
	private final Class _expectedType;
	private ThreadSlot _values = null;
	
	public DynamicVariable() {
		this(null);
	}
	
	public DynamicVariable(Class expectedType) {
		_expectedType = expectedType;
	}
	
	/**
	 * @sharpen.property
	 */
	public Object value() {
		final Thread current = Thread.currentThread();
		synchronized (this) {
			ThreadSlot slot = _values;
			while (null != slot) {
				if (slot.thread == current) {
					return slot.value;
				}
				slot = slot.next;
			}
		}
		return defaultValue();
	}
	
	protected Object defaultValue() {
		return null;
	}
	
	public Object with(Object value, Closure4 block) {
		validate(value);
		
		ThreadSlot slot = pushValue(value);
		try {
			return block.run();
		} finally {
			popValue(slot);
		}
	}
	
	public void with(Object value, final Runnable block) {
		with(value, new Closure4() {
			public Object run() {
				block.run();
				return null;
			}
		});
	}

	private void validate(Object value) {
		if (value == null || _expectedType == null) {
			return;
		}
		if (_expectedType.isInstance(value)) {
			return;
		}
		throw new IllegalArgumentException("Expecting instance of '" + _expectedType + "' but got '" + value + "'");
	}

	private synchronized void popValue(ThreadSlot slot) {
		if (slot == _values) {
			_values = _values.next;
			return;
		}
		
		ThreadSlot previous = _values;
		ThreadSlot current = _values.next;
		while (current != null) {
			if (current == slot) {
				previous.next = current.next;
				return;
			}
			previous = current;
			current = current.next;
		}
	}

	private synchronized ThreadSlot pushValue(Object value) {
		final ThreadSlot slot = new ThreadSlot(value, _values);
		_values = slot;
		return slot;
	}
}
