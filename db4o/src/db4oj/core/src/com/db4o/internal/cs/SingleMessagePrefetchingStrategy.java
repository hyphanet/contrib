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
package com.db4o.internal.cs;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.messages.*;

/**
 * Prefetchs multiples objects at once (in a single message).
 * 
 * @exclude
 */
public class SingleMessagePrefetchingStrategy implements PrefetchingStrategy {

	public static final PrefetchingStrategy INSTANCE = new SingleMessagePrefetchingStrategy();
	
	private SingleMessagePrefetchingStrategy() {
	}

	public int prefetchObjects(ClientObjectContainer container,
			IntIterator4 ids, Object[] prefetched, int prefetchCount) {
		int count = 0;

		int toGet = 0;
		int[] idsToGet = new int[prefetchCount];
		int[] position = new int[prefetchCount];

		while (count < prefetchCount) {
			if (!ids.moveNext()) {
				break;
			}
			int id = ids.currentInt();
			if (id > 0) {
                Object obj = container.transaction().objectForIdFromCache(id);
                if(obj != null){
                    prefetched[count] = obj;
                }else{
					idsToGet[toGet] = id;
					position[toGet] = count;
					toGet++;
				}
				count++;
			}
		}

		if (toGet > 0) {
		    Transaction trans = container.transaction();
			MsgD msg = Msg.READ_MULTIPLE_OBJECTS.getWriterForIntArray(trans, idsToGet, toGet);
			container.write(msg);
			MsgD response = (MsgD) container.expectedResponse(Msg.READ_MULTIPLE_OBJECTS);
			int embeddedMessageCount = response.readInt();
			for (int i = 0; i < embeddedMessageCount; i++) {
				MsgObject mso = (MsgObject) Msg.OBJECT_TO_CLIENT.publicClone();
				mso.setTransaction(trans);
				mso.payLoad(response.payLoad().readYapBytes());
				if (mso.payLoad() != null) {
					mso.payLoad().incrementOffset(Const4.MESSAGE_LENGTH);
					StatefulBuffer reader = mso.unmarshall(Const4.MESSAGE_LENGTH);
                    Object obj = trans.objectForIdFromCache(idsToGet[i]);
                    if(obj != null){
                        prefetched[position[i]] = obj;
                    }else{
    					prefetched[position[i]] = new ObjectReference(idsToGet[i]).readPrefetch(trans, reader);
                    }
				}
			}
		}
		return count;
	}

}
