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
 * JDK 2 Iterator
 * 
 * Intended state: i_next is always active.
 *  
 * @persistent
 * @deprecated since 7.0
 * @decaf.ignore.jdk11
 */
class P2ListElementIterator implements Iterator {

    private final P2LinkedList i_list;

    private P1ListElement i_preprevious;
    private P1ListElement i_previous;
    private P1ListElement i_next;

    P2ListElementIterator(P2LinkedList a_list, P1ListElement a_next) {
        i_list = a_list;
        i_next = a_next;
        checkNextActive();
    }

    private void checkNextActive() {
        if (i_next != null) {
            i_next.checkActive();
        }
    }

    public void remove() {
        if (i_previous != null) {
            synchronized (i_previous.streamLock()) {
                if (i_preprevious != null) {
                    i_preprevious.i_next = i_previous.i_next;
                    i_preprevious.update();
                }
                i_list.checkRemoved(i_preprevious, i_previous);
                i_previous.delete(i_list.i_deleteRemoved);
            }
        }
    }

    public boolean hasNext() {
        return i_next != null;
    }

    public Object next() {
        if (i_next != null) {
            synchronized (i_next.streamLock()) {
                i_preprevious = i_previous;
                i_previous = i_next;
                Object obj = i_next.activatedObject(i_list.elementActivationDepth());
                i_next = i_next.i_next;
                checkNextActive();
                return obj;
            }
        }
        return null;
    }

    P1ListElement nextElement() {
        i_preprevious = i_previous;
        i_previous = i_next;
        i_next = i_next.i_next;
        checkNextActive();
        return i_previous;
    }

    P1ListElement move(int a_elements) {
        if (a_elements < 0) {
            return null;
        }
        for (int i = 0; i < a_elements; i++) {
            if (hasNext()) {
                nextElement();
            } else {
                return null;
            }
        }
        if (hasNext()) {
            return nextElement();
        }
        return null;
    }

}
