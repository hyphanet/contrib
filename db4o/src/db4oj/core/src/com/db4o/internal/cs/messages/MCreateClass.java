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

import com.db4o.ext.*;
import com.db4o.internal.*;
import com.db4o.reflect.*;

public final class MCreateClass extends MsgD implements ServerSideMessage {

	public final boolean processAtServer() {
		ObjectContainerBase stream = stream();
		Transaction trans = stream.systemTransaction();
		boolean ok = false;
		try {
			synchronized (streamLock()) {
			    ReflectClass claxx = trans.reflector().forName(readString());
	            if (claxx != null) {
					ClassMetadata classMetadata = stream.produceClassMetadata(claxx);
					if (classMetadata != null) {
						stream.checkStillToSet();
						StatefulBuffer returnBytes = stream.readWriterByID(trans, classMetadata.getID());
						MsgD createdClass = Msg.OBJECT_TO_CLIENT.getWriter(returnBytes);
						write(createdClass);
						ok = true;
					}
	            }
			}
		} catch (Db4oException e) {
			// TODO: send the exception to the client
		} finally {
			if (!ok) {
				write(Msg.FAILED);
			}
		}
		return true;
	}
}
