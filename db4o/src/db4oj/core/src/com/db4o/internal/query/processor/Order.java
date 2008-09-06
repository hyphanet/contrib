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
package com.db4o.internal.query.processor;

import com.db4o.ext.*;
import com.db4o.foundation.*;

class Order implements Orderable {
	
	private int i_major;
	private IntArrayList i_minors = new IntArrayList();
	
	public int compareTo(Object obj) {
		if(obj instanceof Order){
			Order other = (Order)obj;
			int res = i_major - other.i_major;
			if(res != 0){
				return res;
			}
			return compareMinors(other.i_minors);
		}
		return -1;
	}

	public void hintOrder(int a_order, boolean a_major) {
		if(a_major){
			i_major = a_order;
		}else{
		    appendMinor(a_order);
		}
	}
	
	public boolean hasDuplicates(){
		return true;
	}
	
	public String toString() {
	    String str = "Order " + i_major;
	    for (int i = 0; i < i_minors.size(); i++) {
	        str = str + " " + i_minors.get(i);
	    }
		return str;
	}

	public void swapMajorToMinor() {
		insertMinor(i_major);
		i_major = 0;
	}
	
	private void appendMinor(int minor) {
	    i_minors.add(minor);
	}
	
	private void insertMinor(int minor) {
	    i_minors.add(0, minor);
	}
	
	private int compareMinors(IntArrayList other) {
	    if (i_minors.size() != other.size()) {
	        throw new Db4oException("Unexpected exception: this..size()=" + i_minors.size()
	                + ", other.size()=" + other.size());
	    }
	    
	    int result = 0; 
	    for (int i = 0; i < i_minors.size(); i++) {
	        if (i_minors.get(i) == other.get(i)) {
	            continue;
	        } 
	        return (i_minors.get(i) - other.get(i));
	    }
	    return result;
	}
}

