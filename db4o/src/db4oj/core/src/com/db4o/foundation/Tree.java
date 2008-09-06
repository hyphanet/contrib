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
package com.db4o.foundation;


/**
 * @exclude
 */
public abstract class Tree implements ShallowClone , DeepClone{
	
	public static final class ByRef {
		
		public ByRef() {			
		}
		
		public ByRef(Tree initialValue) {
			value = initialValue;
		}

		public Tree value;
	}
    
	public Tree _preceding;
	public int _size = 1;
	public Tree _subsequent;
	
	public static final Tree add(Tree a_old, Tree a_new){
		if(a_old == null){
			return a_new;
		}
		return a_old.add(a_new);
	}
	
	public Tree add(final Tree a_new){
	    return add(a_new, compare(a_new));
	}
	
    /**
     * On adding a node to a tree, if it already exists, and if
     * Tree#duplicates() returns false, #isDuplicateOf() will be
     * called. The added node can then be asked for the node that
     * prevails in the tree using #duplicateOrThis(). This mechanism
     * allows doing find() and add() in one run.
     */
	public Tree add(final Tree a_new, final int a_cmp){
	    if(a_cmp < 0){
	        if(_subsequent == null){
	            _subsequent = a_new;
	            _size ++;
	        }else{
	            _subsequent = _subsequent.add(a_new);
	            if(_preceding == null){
	                return rotateLeft();
	            }
	            return balance();
	        }
	    }else if(a_cmp > 0 || a_new.duplicates()){
	        if(_preceding == null){
	            _preceding = a_new;
	            _size ++;
	        }else{
	            _preceding = _preceding.add(a_new);
	            if(_subsequent == null){
	                return rotateRight();
	            }
	            return balance();
	        }
	    }else{
	        a_new.onAttemptToAddDuplicate(this);
	    }
	    return this;
	}
    
    
    /**
     * On adding a node to a tree, if it already exists, and if
     * Tree#duplicates() returns false, #onAttemptToAddDuplicate() 
     * will be called and the existing node will be stored in
     * this._preceding.
     * This node node can then be asked for the node that prevails 
     * in the tree on adding, using the #addedOrExisting() method. 
     * This mechanism allows doing find() and add() in one run.
     */
    public Tree addedOrExisting(){
        if(wasAddedToTree()){
        	return this;
        }
        return _preceding;
    }
    
    public boolean wasAddedToTree(){
    	return _size != 0;
    }
	
	public final Tree balance(){
		int cmp = _subsequent.nodes() - _preceding.nodes(); 
		if(cmp < -2){
			return rotateRight();
		}else if(cmp > 2){
			return rotateLeft();
		}else{
            setSizeOwnPrecedingSubsequent();
		    return this;
		}
	}
	
	public Tree balanceCheckNulls(){
	    if(_subsequent == null){
	        if(_preceding == null){
                setSizeOwn();
	            return this;
	        }
	        return rotateRight();
	    }else if(_preceding == null){
	        return rotateLeft();
	    }
	    return balance();
	}
	
	public void calculateSize(){
		if(_preceding == null){
			if (_subsequent == null){
				setSizeOwn();
			}else{
                setSizeOwnSubsequent();
			}
		}else{
			if(_subsequent == null){
                setSizeOwnPreceding();
			}else{
                setSizeOwnPrecedingSubsequent();
			}
		}
	}
	
	
    /**
     * returns 0, if keys are equal
     * uses this - other  
     * returns positive if this is greater than a_to
     * returns negative if this is smaller than a_to
     */
	public abstract int compare(Tree a_to);
	
	public static Tree deepClone(Tree a_tree, Object a_param){
		if(a_tree == null){
			return null;
		}
		Tree newNode = (Tree)a_tree.deepClone(a_param);
		newNode._size = a_tree._size;
		newNode._preceding = Tree.deepClone(a_tree._preceding, a_param); 
		newNode._subsequent = Tree.deepClone(a_tree._subsequent, a_param); 
		return newNode;
	}
	
	
	public Object deepClone(Object a_param){
        return shallowClone();
	}
	
	public boolean duplicates(){
		return true;
	}
	
	public final Tree filter(final Predicate4 a_filter){
		if(_preceding != null){
			_preceding = _preceding.filter(a_filter);
		}
		if(_subsequent != null){
			_subsequent = _subsequent.filter(a_filter);
		}
		if(! a_filter.match(this)){
			return remove();
		}
		return this;
	}
	
	public static final Tree find(Tree a_in, Tree a_tree){
		if(a_in == null){
			return null;
		}
		return a_in.find(a_tree);
	}
	
	public final Tree find(final Tree a_tree){
		int cmp = compare(a_tree);
		if (cmp < 0){
			if(_subsequent != null){
				return _subsequent.find(a_tree);
			}
		}else{
			if (cmp > 0){
				if(_preceding != null){
					return _preceding.find(a_tree);
				}
			}else{
				return this;
			}
		}
		return null;
	}
	
	public static final Tree findGreaterOrEqual(Tree a_in, Tree a_finder){
		if(a_in == null){
			return null;
		}
		int cmp = a_in.compare(a_finder);
		if(cmp == 0){
			return a_in; // the highest node in the hierarchy !!!
		}
		if(cmp > 0){
			Tree node = findGreaterOrEqual(a_in._preceding, a_finder);
			if(node != null){
				return node;
			}
			return a_in;
		}
		return findGreaterOrEqual(a_in._subsequent, a_finder);
	}
	
	
	public final static Tree findSmaller(Tree a_in, Tree a_node){
		if(a_in == null){
			return null;
		}
		int cmp = a_in.compare(a_node);
		if(cmp < 0){
			Tree node = findSmaller(a_in._subsequent, a_node);
			if(node != null){
				return node;
			}
			return a_in;
		}
		return findSmaller(a_in._preceding, a_node);
	}
    
    public final Tree first(){
        if(_preceding == null){
            return this;
        }
        return _preceding.first();
    }
    
    public static Tree last(Tree tree){
    	if(tree == null){
    		return null;
    	}
    	return tree.last();
    }
    
    public final Tree last(){
        if(_subsequent == null){
            return this;
        }
        return _subsequent.last();
    }
    
	public void onAttemptToAddDuplicate(Tree a_tree){
		_size = 0;
        _preceding = a_tree;
	}
	
    /**
     * @return the number of nodes in this tree for balancing
     */
    public int nodes(){
        return _size;
    }
    
    public int ownSize(){
	    return 1;
	}
	
	public Tree remove(){
		if(_subsequent != null && _preceding != null){
			_subsequent = _subsequent.rotateSmallestUp();
			_subsequent._preceding = _preceding;
			_subsequent.calculateSize();
			return _subsequent;
		}
		if(_subsequent != null){
			return _subsequent;
		}
		return _preceding;
	}
	
	public void removeChildren(){
		_preceding = null;
		_subsequent = null;
		setSizeOwn();
	}
    
    public Tree removeFirst(){
        if(_preceding == null){
            return _subsequent;
        }
        _preceding = _preceding.removeFirst();
        calculateSize();
        return this;
    }
	
	public static Tree removeLike(Tree from, Tree a_find){
		if(from == null){
			return null;
		}
		return from.removeLike(a_find);
	}
	
	public final Tree removeLike(final Tree a_find){
		int cmp = compare(a_find);
		if(cmp == 0){
			return remove();
		}
		if (cmp > 0){
			if(_preceding != null){
				_preceding = _preceding.removeLike(a_find);
			}
		}else{
			if(_subsequent != null){
				_subsequent = _subsequent.removeLike(a_find);
			}
		}
		calculateSize();
		return this;
	}
	
	public final Tree removeNode(final Tree a_tree){
		if (this == a_tree){
			return remove();
		}
		int cmp = compare(a_tree);
		if (cmp >= 0){
			if(_preceding != null){
				_preceding = _preceding.removeNode(a_tree);
			}
		}
		if(cmp <= 0){
			if(_subsequent != null){
				_subsequent = _subsequent.removeNode(a_tree);	
			}
		}
		calculateSize();
		return this;
	}
    
	public final Tree rotateLeft(){
		Tree tree = _subsequent;
		_subsequent = tree._preceding;
		calculateSize();
		tree._preceding = this;
		if(tree._subsequent == null){
            tree.setSizeOwnPlus(this);
		}else{
            tree.setSizeOwnPlus(this, tree._subsequent);
		}
		return tree;
	}

	public final Tree rotateRight(){
		Tree tree = _preceding;
		_preceding = tree._subsequent;
		calculateSize();
		tree._subsequent = this;
		if(tree._preceding == null){
            tree.setSizeOwnPlus(this);
		}else{
            tree.setSizeOwnPlus(this, tree._preceding);
		}
		return tree;
	}
	
	private final Tree rotateSmallestUp(){
		if(_preceding != null){
			_preceding = _preceding.rotateSmallestUp();
			return rotateRight();
		}
		return this;
	}
    
    public void setSizeOwn(){
        _size = ownSize();
    }
    
    public void setSizeOwnPrecedingSubsequent(){
        _size = ownSize() + _preceding._size + _subsequent._size;
    }
    
    public void setSizeOwnPreceding(){
        _size = ownSize() + _preceding._size;
    }
    
    public void setSizeOwnSubsequent(){
        _size = ownSize() + _subsequent._size;
    }
    
    public void setSizeOwnPlus(Tree tree){
        _size = ownSize() + tree._size;
    }
    
    public void setSizeOwnPlus(Tree tree1, Tree tree2){
        _size = ownSize() + tree1._size + tree2._size;
    }
	
	public static int size(Tree a_tree){
		if(a_tree == null){
			return 0;
		}
		return a_tree.size();
	}
	
    /**
     * @return the number of objects represented.
     */
	public int size(){
		return _size;
	}
    
    public static final void traverse(Tree tree, Visitor4 visitor){
        if(tree == null){
            return;
        }
        tree.traverse(visitor);
    }
    
	public final void traverse(final Visitor4 a_visitor){
		if(_preceding != null){
			_preceding.traverse(a_visitor);
		}
		a_visitor.visit(this);
		if(_subsequent != null){
			_subsequent.traverse(a_visitor);
		}
	}
	
	public final void traverseFromLeaves(Visitor4 a_visitor){
	    if(_preceding != null){
	        _preceding.traverseFromLeaves(a_visitor);
	    }
	    if(_subsequent != null){
	        _subsequent.traverseFromLeaves(a_visitor);
	    }
	    a_visitor.visit(this);
	}	
	
// Keep the debug methods to debug the depth	
	
//	final void debugDepth(){
//	    System.out.println("Tree depth: " + debugDepth(0));
//	}
//	
//	final int debugDepth(int d){
//	    int max = d + 1;
//	    if (i_preceding != null){
//	        max = i_preceding.debugDepth(d + 1);
//	    }
//	    if(i_subsequent != null){
//	        int ms = i_subsequent.debugDepth(d + 1);
//	        if(ms > max){
//	            max = ms;
//	        }
//	    }
//	    return max;
//	}

	protected Tree shallowCloneInternal(Tree tree) {
		tree._preceding=_preceding;
		tree._size=_size;
		tree._subsequent=_subsequent;
		return tree;
	}
	
	public Object shallowClone() {
		throw new com.db4o.foundation.NotImplementedException();
	}
	
	public abstract Object key();
	
	public Object root() {
		return this;
	}
}
