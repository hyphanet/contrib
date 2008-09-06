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
package com.db4o.internal;

import com.db4o.foundation.*;


/**
 * @exclude
 */
public class LockedTree {
    
    private Tree _tree;
    
    private int _version;

    public void add(Tree tree) {
        changed();
        _tree = _tree == null ? tree : _tree.add(tree); 
    }

    private void changed() {
        _version++;
    }

    public void clear() {
        changed();
        _tree = null;
    }

    public Tree find(int key) {
        return TreeInt.find(_tree, key);
    }

    public void read(ByteArrayBuffer buffer, Readable template) {
        clear();
        _tree = new TreeReader(buffer, template).read();
        changed();
    }

    public void traverseLocked(Visitor4 visitor) {
        int currentVersion = _version;
        Tree.traverse(_tree, visitor);
        if(_version != currentVersion){
            throw new IllegalStateException();
        }
    }
    
    public void traverseMutable(Visitor4 visitor){
        final Collection4 currentContent = new Collection4();
        traverseLocked(new Visitor4() {
            public void visit(Object obj) {
                currentContent.add(obj);
            }
        });
        Iterator4 i = currentContent.iterator();
        while(i.moveNext()){
            visitor.visit(i.current());
        }
    }

}
