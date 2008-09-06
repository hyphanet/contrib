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

import java.io.*;

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * @exclude
 */
public class MCommittedInfo extends MsgD implements ClientSideMessage {

	public MCommittedInfo encode(CallbackObjectInfoCollections callbackInfo) {
		byte[] bytes = encodeInfo(callbackInfo);
		MCommittedInfo committedInfo = (MCommittedInfo) getWriterForLength(transaction(),
			bytes.length);
		committedInfo._payLoad.append(bytes);
		return committedInfo;
	}

	private byte[] encodeInfo(CallbackObjectInfoCollections callbackInfo) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		
		encodeObjectInfoCollection(os, callbackInfo.added, new InternalIDEncoder());
		encodeObjectInfoCollection(os, callbackInfo.deleted, new FrozenObjectInfoEncoder());
		encodeObjectInfoCollection(os, callbackInfo.updated, new InternalIDEncoder());
		
		return os.toByteArray();
	}
	
	private final class FrozenObjectInfoEncoder implements ObjectInfoEncoder {
		public void encode(ByteArrayOutputStream os, ObjectInfo info) {
			writeLong(os, info.getInternalID());
	        long sourceDatabaseId = ((FrozenObjectInfo)info).sourceDatabaseId(transaction());
            writeLong(os, sourceDatabaseId);
	        writeLong(os, ((FrozenObjectInfo)info).uuidLongPart());
			writeLong(os, info.getVersion());
		}

		public ObjectInfo decode(ByteArrayInputStream is) {
			long id = readLong(is);
			if (id == -1) {
				return null;
			}
			long sourceDatabaseId = readLong(is);
			Db4oDatabase sourceDatabase = null;
			if (sourceDatabaseId > 0 ){
			    sourceDatabase  = (Db4oDatabase) stream().getByID(transaction(), sourceDatabaseId);
			}
			long uuidLongPart = readLong(is);
			long version = readLong(is);
			return new FrozenObjectInfo(null, id, sourceDatabase, uuidLongPart, version);
		}
	}

	private final class InternalIDEncoder implements ObjectInfoEncoder {
		public void encode(ByteArrayOutputStream os, ObjectInfo info) {
			writeLong(os, info.getInternalID());
		}

		public ObjectInfo decode(ByteArrayInputStream is) {
			long id = readLong(is);
			if (id == -1) {
				return null;
			}
			return new LazyObjectReference(transaction(), (int) id);
		}
	}

	interface ObjectInfoEncoder {
		void encode(ByteArrayOutputStream os, ObjectInfo info);
		ObjectInfo decode(ByteArrayInputStream is);
	}
	
	private void encodeObjectInfoCollection(ByteArrayOutputStream os,
			ObjectInfoCollection collection, final ObjectInfoEncoder encoder) {
		Iterator4 iter = collection.iterator();
		while (iter.moveNext()) {
			ObjectInfo obj = (ObjectInfo) iter.current();
			encoder.encode(os, obj);
		}
		writeLong(os, -1);
	}
	
	public CallbackObjectInfoCollections decode() {
		ByteArrayInputStream is = new ByteArrayInputStream(_payLoad._buffer);
		
		final ObjectInfoCollection added = decodeObjectInfoCollection(is, new InternalIDEncoder());
		final ObjectInfoCollection deleted = decodeObjectInfoCollection(is, new FrozenObjectInfoEncoder());
		final ObjectInfoCollection updated = decodeObjectInfoCollection(is, new InternalIDEncoder());
		return new CallbackObjectInfoCollections(added, updated, deleted);
	}

	private ObjectInfoCollection decodeObjectInfoCollection(ByteArrayInputStream is, ObjectInfoEncoder encoder){
		final Collection4 collection = new Collection4();
		while (true) {
			ObjectInfo info = encoder.decode(is);
			if (null == info) {
				break;
			}
			collection.add(info);
		}
		return new ObjectInfoCollectionImpl(collection);
	}

	private void writeLong(ByteArrayOutputStream os, long l) {
		for (int i = 0; i < 64; i += 8) {
			os.write((int) (l >> i));
		}
	}

	private long readLong(ByteArrayInputStream is) {
		long l = 0;
		for (int i = 0; i < 64; i += 8) {
			l += ((long) (is.read())) << i;
		}
		return l;
	}

	public boolean processAtClient() {
		final CallbackObjectInfoCollections callbackInfos = decode();
		new Thread(new Runnable() {
			public void run() {
				if(stream().isClosed()){
					return;
				}
				stream().callbacks().commitOnCompleted(transaction(), callbackInfos);
			}
		}).start();
		return true;
	}
	
	protected void writeByteArray(ByteArrayOutputStream os, byte[] signaturePart) throws IOException {
		writeLong(os, signaturePart.length);
		os.write(signaturePart);
	}
}
