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
package com.db4o.internal.mapping;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.handlers.*;
import com.db4o.marshall.*;

/**
 * @exclude
 */
public class MappedIDPairHandler implements Indexable4 {

	private final IntHandler _origHandler;
	private final IntHandler _mappedHandler;
	
	public MappedIDPairHandler() {
		_origHandler=new IntHandler();
		_mappedHandler=new IntHandler();
	}

	public void defragIndexEntry(DefragmentContextImpl context) {
        throw new NotImplementedException();
	}

	public int linkLength() {
		return _origHandler.linkLength()+_mappedHandler.linkLength();
	}

	public Object readIndexEntry(ByteArrayBuffer reader) {
		int origID=readID(reader);
		int mappedID=readID(reader);
        return new MappedIDPair(origID,mappedID);
	}

	public void writeIndexEntry(ByteArrayBuffer reader, Object obj) {
		MappedIDPair mappedIDs=(MappedIDPair)obj;
		_origHandler.writeIndexEntry(reader, new Integer(mappedIDs.orig()));
		_mappedHandler.writeIndexEntry(reader, new Integer(mappedIDs.mapped()));
	}

	private int readID(ByteArrayBuffer a_reader) {
		return ((Integer)_origHandler.readIndexEntry(a_reader)).intValue();
	}

	public PreparedComparison prepareComparison(Context context, Object source) {
		MappedIDPair sourceIDPair = (MappedIDPair)source;
		final int sourceID = sourceIDPair.orig();
		return new PreparedComparison() {
			public int compareTo(Object target) {
				MappedIDPair targetIDPair = (MappedIDPair)target;
				int targetID = targetIDPair.orig();
				return sourceID == targetID ? 0 : (sourceID < targetID ? - 1 : 1); 
			}
		};
	}
}
