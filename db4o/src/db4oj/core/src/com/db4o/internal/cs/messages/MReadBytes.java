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
package com.db4o.internal.cs.messages;

import com.db4o.internal.*;
import com.db4o.internal.slots.*;

public final class MReadBytes extends MsgD implements ServerSideMessage {
	
	public final ByteArrayBuffer getByteLoad() {
		int address = _payLoad.readInt();
		int length = _payLoad.length() - (Const4.INT_LENGTH);
        Slot slot = new Slot(address, length);
		_payLoad.removeFirstBytes(Const4.INT_LENGTH);
		_payLoad.useSlot(slot);
		return this._payLoad;
	}

	public final MsgD getWriter(StatefulBuffer bytes) {
		MsgD message = getWriterForLength(bytes.transaction(), bytes.length() + Const4.INT_LENGTH);
		message._payLoad.writeInt(bytes.getAddress());
		message._payLoad.append(bytes._buffer);
		return message;
	}
	
	public final boolean processAtServer() {
		int address = readInt();
		int length = readInt();
		synchronized (streamLock()) {
			StatefulBuffer bytes =
				new StatefulBuffer(this.transaction(), address, length);
			try {
				stream().readBytes(bytes._buffer, address, length);
				write(getWriter(bytes));
			} catch (Exception e) {
				// TODO: not nicely handled on the client side yet
				write(Msg.NULL);
			}
		}
		return true;
	}
}