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

import com.db4o.*;
import com.db4o.internal.*;

public final class MReadMultipleObjects extends MsgD implements ServerSideMessage {
	
	public final boolean processAtServer() {
		int size = readInt();
		MsgD[] ret = new MsgD[size];
		int length = (1 + size) * Const4.INT_LENGTH;
		synchronized (streamLock()) {
			for (int i = 0; i < size; i++) {
				int id = this._payLoad.readInt();
				try {
					StatefulBuffer bytes = stream().readWriterByID(transaction(),id);
					if(bytes != null){
						ret[i] = Msg.OBJECT_TO_CLIENT.getWriter(bytes);
						length += ret[i]._payLoad.length();
					}
				} catch (Exception e) {
					if(Debug.atHome){
						e.printStackTrace();
					}
				}
			}
		}
		
		MsgD multibytes = Msg.READ_MULTIPLE_OBJECTS.getWriterForLength(transaction(), length);
		multibytes.writeInt(size);
		for (int i = 0; i < size; i++) {
			if(ret[i] == null){
				multibytes.writeInt(0);
			}else{
				multibytes.writeInt(ret[i]._payLoad.length());
				multibytes._payLoad.append(ret[i]._payLoad._buffer);
			}
		}
		write(multibytes);
		return true;
	}
}