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
import com.db4o.internal.query.processor.*;


/**
 * Base class for balanced trees.
 * 
 * @exclude
 */
public class TreeInt extends Tree implements ReadWriteable {
	
	public static TreeInt add(TreeInt tree, int value) {
		return (TreeInt) Tree.add(tree, new TreeInt(value));
	}
	
	public static TreeInt removeLike(TreeInt tree, int value) {
		return (TreeInt) Tree.removeLike(tree, new TreeInt(value));
	}
	
	public static Tree addAll(Tree tree, IntIterator4 iter){
		if(! iter.moveNext()){
			return tree;
		}
		TreeInt firstAdded = new TreeInt(iter.currentInt());
		tree = Tree.add(tree, firstAdded);
		while(iter.moveNext()){
			tree = tree.add( new TreeInt(iter.currentInt()));
		}
		return tree;
	}

	public int _key;

	public TreeInt(int a_key) {
		this._key = a_key;
	}

	public int compare(Tree a_to) {
		return _key - ((TreeInt) a_to)._key;
	}

	Tree deepClone() {
		return new TreeInt(_key);
	}

	public boolean duplicates() {
		return false;
	}

	public static final TreeInt find(Tree a_in, int a_key) {
		if (a_in == null) {
			return null;
		}
		return ((TreeInt) a_in).find(a_key);
	}

	public final TreeInt find(int a_key) {
		int cmp = _key - a_key;
		if (cmp < 0) {
			if (_subsequent != null) {
				return ((TreeInt) _subsequent).find(a_key);
			}
		} else {
			if (cmp > 0) {
				if (_preceding != null) {
					return ((TreeInt) _preceding).find(a_key);
				}
			} else {
				return this;
			}
		}
		return null;
	}

	public Object read(ByteArrayBuffer a_bytes) {
		return new TreeInt(a_bytes.readInt());
	}

	public void write(ByteArrayBuffer a_writer) {
		a_writer.writeInt(_key);
	}
	
	public static void write(final ByteArrayBuffer a_writer, TreeInt a_tree){
        write(a_writer, a_tree, a_tree == null ? 0 : a_tree.size());
	}
    
    public static void write(final ByteArrayBuffer a_writer, TreeInt a_tree, int size){
        if(a_tree == null){
            a_writer.writeInt(0);
            return;
        }
        a_writer.writeInt(size);
        a_tree.traverse(new Visitor4() {
            public void visit(Object a_object) {
                ((TreeInt)a_object).write(a_writer);
            }
        });
    }

	public int ownLength() {
		return Const4.INT_LENGTH;
	}

	boolean variableLength() {
		return false;
	}

	QCandidate toQCandidate(QCandidates candidates) {
		QCandidate qc = new QCandidate(candidates, null, _key);
		qc._preceding = toQCandidate((TreeInt) _preceding, candidates);
		qc._subsequent = toQCandidate((TreeInt) _subsequent, candidates);
		qc._size = _size;
		return qc;
	}

	public static QCandidate toQCandidate(TreeInt tree, QCandidates candidates) {
		if (tree == null) {
			return null;
		}
		return tree.toQCandidate(candidates);
	}

	public String toString() {
		return "" + _key;
	}

	protected Tree shallowCloneInternal(Tree tree) {
		TreeInt treeint=(TreeInt)super.shallowCloneInternal(tree);
		treeint._key=_key;
		return treeint;
	}

	public Object shallowClone() {
		TreeInt treeint= new TreeInt(_key);
		return shallowCloneInternal(treeint);
	}
	
	public static int marshalledLength(TreeInt a_tree){
		if(a_tree == null){
			return Const4.INT_LENGTH;
		}
		return a_tree.marshalledLength();
	}
	
	public final int marshalledLength(){
		if(variableLength()){
			final IntByRef mint = new IntByRef(Const4.INT_LENGTH);
			traverse(new Visitor4(){
				public void visit(Object obj){
					mint.value += ((TreeInt)obj).ownLength();
				}
			});
			return mint.value;
		}
		return Const4.INT_LENGTH + (size() * ownLength());
	}
	
    public Object key(){
    	return new Integer(_key);
    }


}
