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
package com.db4o.internal.freespace;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * @exclude
 */
public final class FreeSlotNode extends TreeInt {
	static int sizeLimit;

	FreeSlotNode _peer;

	FreeSlotNode(int a_key) {
		super(a_key);
	}

	public Object shallowClone() {
		FreeSlotNode frslot = new FreeSlotNode(_key);
		frslot._peer = _peer;
		return super.shallowCloneInternal(frslot);
	}

	final void createPeer(int a_key) {
		_peer = new FreeSlotNode(a_key);
		_peer._peer = this;
	}

	public boolean duplicates() {
		return true;
	}

	public final int ownLength() {
		return Const4.INT_LENGTH * 2;
	}

	final static Tree removeGreaterOrEqual(FreeSlotNode a_in,
			TreeIntObject a_finder) {
		if (a_in == null) {
			return null;
		}
		int cmp = a_in._key - a_finder._key;
		if (cmp == 0) {
			a_finder._object = a_in; // the highest node in the hierarchy !!!
			return a_in.remove();
		} 
		if (cmp > 0) {
			a_in._preceding = removeGreaterOrEqual(
					(FreeSlotNode) a_in._preceding, a_finder);
			if (a_finder._object != null) {
				a_in._size--;
				return a_in;
			}
			a_finder._object = a_in;
			return a_in.remove();
		} 
		a_in._subsequent = removeGreaterOrEqual(
				(FreeSlotNode) a_in._subsequent, a_finder);
		if (a_finder._object != null) {
			a_in._size--;
		}
		return a_in;
	}

	public Object read(ByteArrayBuffer buffer) {
		int size = buffer.readInt();
		int address = buffer.readInt();
		if (size > sizeLimit) {
			FreeSlotNode node = new FreeSlotNode(size);
			node.createPeer(address);
			if (Deploy.debug  && Debug.xbytes) {
				debugCheckBuffer(buffer, node);
			}
			return node;
		}
		return null;
	}

	private void debugCheckBuffer(ByteArrayBuffer buffer, FreeSlotNode node) {
		if (!(buffer instanceof StatefulBuffer)) {
			return;
		}
		Transaction trans = ((StatefulBuffer) buffer).transaction();
		if (!(trans.container() instanceof IoAdaptedObjectContainer)) {
			return;
		}
		StatefulBuffer checker = trans.container().getWriter(trans,
				node._peer._key, node._key);
		checker.read();
		for (int i = 0; i < node._key; i++) {
			if (checker.readByte() != (byte) 'X') {
				System.out.println("!!! Free space corruption at:"
						+ node._peer._key);
				break;
			}
		}
	}
	
	

	public final void write(ByteArrayBuffer a_writer) {
		// byte order: size, address
		a_writer.writeInt(_key);
		a_writer.writeInt(_peer._key);
	}

	// public static final void debug(FreeSlotNode a_node){
	// if(a_node == null){
	// return;
	// }
	// System.out.println("Address:" + a_node.i_key);
	// System.out.println("Length:" + a_node.i_peer.i_key);
	// debug((FreeSlotNode)a_node.i_preceding);
	// debug((FreeSlotNode)a_node.i_subsequent);
	// }

	public String toString() {
		if (!Debug.freespace) {
			return super.toString();

		}
		String str = "FreeSlotNode " + _key;
		if (_peer != null) {
			str += " peer: " + _peer._key;
		}
		return str;
	}
}
