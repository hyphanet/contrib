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
package com.db4o.test.jdk5;

// JDK1.5: static import
import static java.lang.Math.*;

import java.util.*;

// JDK1.5: annotations
@Jdk5Annotation(
		cascadeOnActivate=true,
		cascadeOnUpdate=true,
		maximumActivationDepth=3)

		// JDK1.5: generics
public class Jdk5Data<Item> {
    private Item item;
    // JDK1.5: typesafe enums
    private Jdk5Enum type;
    // JDK1.5: generics
    private List<Integer> list;
    
    public Jdk5Data(Item item,Jdk5Enum type) {
        this.item=item;
        this.type=type;
        list=new ArrayList<Integer>();
    }

    // JDK1.5: varargs
    public void add(int ... is) {
        // JDK1.5: enhanced for with array
        for(int i : is) {
            // JDK1.5: boxing
            list.add(i);
        }
    }
    
    public int getMax() {
        int max=Integer.MIN_VALUE;
        // JDK1.5: enhanced for with collection / unboxing
        
        for(int i : list) {
            max=max(i,max);
        }
        
        return max;
    }
    
    public int getSize() {
        return list.size();
    }
    
    public Item getItem() {
        return item;
    }
    
    public Jdk5Enum getType() {
        return type;
    }
}
