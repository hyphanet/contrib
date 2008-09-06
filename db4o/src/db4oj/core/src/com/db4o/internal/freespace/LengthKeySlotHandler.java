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

import com.db4o.foundation.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;


/**
 * @exclude
 */
public class LengthKeySlotHandler extends SlotHandler{
	
	public int compareTo(Object obj) {
		return _current.compareByLength((Slot)obj);
	}
	
	public PreparedComparison prepareComparison(Context context, Object slot) {
		final Slot sourceSlot = (Slot)slot;
		return new PreparedComparison() {
			public int compareTo(Object obj) {
				final Slot targetSlot = (Slot)obj;
				
				// FIXME: The comparison method in #compareByLength is the wrong way around.
				
				// Fix there and here after other references are fixed.
				
				return - sourceSlot.compareByLength(targetSlot);
			}
		};
	}



}
