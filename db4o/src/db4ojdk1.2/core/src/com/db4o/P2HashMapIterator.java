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
package com.db4o;

import java.util.*;

/**
 * @persistent 
 * @deprecated since 7.0
 * @decaf.ignore.jdk11
 */
class P2HashMapIterator implements Iterator {

    private P1HashElement i_current;

    private final P2HashMap i_map;
    private int i_nextIndex;

    private P1HashElement i_previous;

    P2HashMapIterator(P2HashMap a_map) {
        i_map = a_map;
        i_nextIndex = -1;
        getNextCurrent();
    }

    private int currentIndex() {
        if (i_current == null) {
            return -1;
        }
        return i_current.i_hashCode & i_map.i_mask;
    }

    private void getNextCurrent() {
        i_previous = i_current;
        i_current = (P1HashElement)nextElement();
        if (i_current != null) {
            i_current.checkActive();
        }
    }

    public boolean hasNext() {
        synchronized (i_map.streamLock()) {
            return i_current != null;
        }
    }

    public Object next() {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            Object ret = null;
            if (i_current != null) {
                ret = i_current.activatedKey(i_map.elementActivationDepth());
            }
            getNextCurrent();
            return ret;
        }
    }

    private P1ListElement nextElement() {
        if (i_current != null && i_current.i_next != null) {
            return i_current.i_next;
        }
        if (i_nextIndex <= currentIndex()) {
            searchNext();
        }
        if (i_nextIndex >= 0) {
            return i_map.i_table[i_nextIndex];
        }
        return null;
    }

    public void remove() {
        synchronized (i_map.streamLock()) {
            i_map.checkActive();
            if (i_previous != null) {
                int index = i_previous.i_hashCode & i_map.i_mask;
                if (index >= 0 && index < i_map.i_table.length) {
                    P1HashElement last = null;
                    P1HashElement phe = i_map.i_table[index];
                    while (phe != i_previous && phe != null) {
                        phe.checkActive();
                        last = phe;
                        phe = (P1HashElement)phe.i_next;
                    }
                    if (phe != null) {
                        i_map.i_size--;
                        if (last == null) {
                            i_map.i_table[index] = (P1HashElement)phe.i_next;
                        } else {
                            last.i_next = phe.i_next;
                            last.update();
                        }
                        i_map.modified();
                        phe.delete(i_map.i_deleteRemoved);
                    }
                }
                i_previous = null;
            }
        }
    }

    private void searchNext() {
        if (i_nextIndex > -2) {
            while (++i_nextIndex < i_map.i_tableSize) {
                if (i_map.i_table[i_nextIndex] != null) {
                    return;
                }
            }
            i_nextIndex = -2;
        }
    }

}
