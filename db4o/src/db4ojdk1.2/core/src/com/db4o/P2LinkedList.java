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

import com.db4o.internal.*;
import com.db4o.reflect.*;
import com.db4o.types.*;

/**
 * database aware linked list implementation
 * @exclude 
 * @persistent
 * @deprecated since 7.0
 * @decaf.ignore.jdk11
 */
public class P2LinkedList extends P1Collection implements Db4oList {

    public P1ListElement i_first;
    public P1ListElement i_last;

    public void add(int index, Object element) {
        synchronized (streamLock()) {
            checkActive();
            if (index == 0) {
                i_first = new P1ListElement(getTrans(), i_first, element);
                store(i_first);
                checkLastAndUpdate(null, i_first);
            } else {
                P2ListElementIterator i = iterator4();
                P1ListElement previous = i.move(index - 1);
                if (previous == null) {
                    throw new IndexOutOfBoundsException();
                }
                P1ListElement newElement = new P1ListElement(getTrans(),
                        previous.i_next, element);
                store(newElement);
                previous.i_next = newElement;
                previous.update();
                checkLastAndUpdate(previous, newElement);
            }
        }
    }

    public boolean add(Object o) {
        synchronized (streamLock()) {
            checkActive();
            if (o == null) {
                throw new NullPointerException();
            }
            add4(o);
            update();
            return true;
        }
    }

    private boolean add4(Object o) {
        if (o != null) {
            P1ListElement newElement = new P1ListElement(getTrans(), null, o);
            store(newElement);
            if (i_first == null) {
                i_first = newElement;
            } else {
                i_last.checkActive();
                i_last.i_next = newElement;
                i_last.update();
            }
            i_last = newElement;
            return true;
        }
        return false;
    }

    public boolean addAll(Collection c) {
        synchronized (streamLock()) {
            checkActive();
            boolean modified = false;
            Iterator i = c.iterator();
            while (i.hasNext()) {
                if (add4(i.next())) {
                    modified = true;
                }
            }
            if (modified) {
                update();
            }
            return modified;
        }
    }

    public boolean addAll(int index, Collection c) {
        synchronized (streamLock()) {
            checkActive();
            Object first = null;
            Iterator it = c.iterator();
            while (it.hasNext() && (first == null)) {
                first = it.next();
            }
            if (first != null) {
                P1ListElement newElement = null;
                P1ListElement nextElement = null;
                if (index == 0) {
                    nextElement = i_first;
                    newElement = new P1ListElement(getTrans(), i_first, first);
                    i_first = newElement;
                } else {
                    P2ListElementIterator i = iterator4();
                    P1ListElement previous = i.move(index - 1);
                    if (previous == null) {
                        throw new IndexOutOfBoundsException();
                    }
                    nextElement = previous.i_next;
                    newElement = new P1ListElement(getTrans(), previous.i_next,
                            first);
                    previous.i_next = newElement;
                    previous.update();
                }
                while (it.hasNext()) {
                    Object obj = it.next();
                    if (obj != null) {
                        newElement.i_next = new P1ListElement(getTrans(),
                                nextElement, obj);
                        store(newElement);
                        newElement = newElement.i_next;
                    }
                }
                store(newElement);
                if (nextElement == null) {
                    i_last = newElement;
                }
                update();
                return true;
            }
            return false;
        }
    }
    
    private void checkLastAndUpdate(P1ListElement a_oldLast,
            P1ListElement a_added) {
        if (i_last == a_oldLast) {
            i_last = a_added;
        }
        update();
    }

    void checkRemoved(P1ListElement a_previous, P1ListElement a_removed) {
        boolean needsUpdate = false;
        if (a_removed == i_first) {
            i_first = a_removed.i_next;
            needsUpdate = true;
        }
        if (a_removed == i_last) {
            i_last = a_previous;
            needsUpdate = true;
        }
        if (needsUpdate) {
            update();
        }
    }

    public void clear() {
        synchronized (streamLock()) {
            checkActive();
            P2ListElementIterator i = iterator4();
            while (i.hasNext()) {
                P1ListElement elem = i.nextElement();
                elem.delete(i_deleteRemoved);
            }
            i_first = null;
            i_last = null;
            update();
        }
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    private boolean contains4(Object o) {
        return indexOf4(o) >= 0;
    }

    public boolean containsAll(Collection c) {
        synchronized (streamLock()) {
            checkActive();            
            Iterator i = c.iterator();
            while (i.hasNext()) {
                if (!contains4(i.next())) {
                    return false;
                }
            }
            return true;
        }
    }

    public Object createDefault(Transaction a_trans) {
        checkActive();
        P2LinkedList l4 = new P2LinkedList();
        l4.setTrans(a_trans);
        P2ListElementIterator i = iterator4();
        while (i.hasNext()) {
            l4.add4(i.next());
        }
        return l4;
    }

    public Object get(int index) {
        synchronized (streamLock()) {
            checkActive();
            P2ListElementIterator i = iterator4();
            P1ListElement elem = i.move(index);
            if (elem != null) {
                return elem.activatedObject(elementActivationDepth());
            }
            return null;
        }
    }

    public boolean hasClassIndex() {
        return true;
    }

    public int indexOf(Object o) {
        synchronized (streamLock()) {
            checkActive();
            return indexOf4(o);
        }
    }

    private int indexOf4(Object o) {
        int idx = 0;
        // TODO: may need to check for primitive wrappers to use
        // equals also.
        if (getTrans() != null && (! getTrans().container().handlers().isSecondClass(o))) {
            long id = getIDOf(o);
            if (id > 0) {
                P2ListElementIterator i = iterator4();
                while (i.hasNext()) {
                    P1ListElement elem = i.nextElement();
                    if (getIDOf(elem.i_object) == id) {
                        return idx;
                    }
                    idx++;
                }
            }
        } else {
            P2ListElementIterator i = iterator4();
            while (i.hasNext()) {
                P1ListElement elem = i.nextElement();
                if (elem.i_object.equals(o)) {
                    return idx;
                }
                idx++;
            }
        }
        return -1;
    }

    public boolean isEmpty() {
        synchronized (streamLock()) {
            checkActive();
            return i_first == null;
        }
    }

    public Iterator iterator() {
        synchronized (streamLock()) {
            checkActive();
            return iterator4();
        }
    }

    private P2ListElementIterator iterator4() {
        return new P2ListElementIterator(this, i_first);
    }

    public int lastIndexOf(Object o) {
        synchronized (streamLock()) {
            checkActive();
            int ret = -1;
            int idx = 0;
            if (getTrans() != null) {
                long id = getIDOf(o);
                if (id > 0) {
                    P2ListElementIterator i = iterator4();
                    while (i.hasNext()) {
                        P1ListElement elem = i.nextElement();
                        if (getIDOf(elem.i_object) == id) {
                            ret = idx;
                        }
                        idx++;
                    }
                }
            } else {
                P2ListElementIterator i = iterator4();
                while (i.hasNext()) {
                    P1ListElement elem = i.nextElement();
                    if (elem.i_object.equals(o)) {
                        ret = idx;
                    }
                    idx++;
                }
            }
            return ret;
        }
    }

    public ListIterator listIterator() {
        throw new UnsupportedOperationException();
    }

    public ListIterator listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    public Object remove(int index) {
        synchronized (streamLock()) {
            checkActive();
            return remove4(index);
        }
    }

    public boolean remove(Object o) {
        synchronized (streamLock()) {
            checkActive();
            return remove4(o);
        }
    }

    private Object remove4(int index) {
        Object ret = null;
        P1ListElement elem = null;
        P1ListElement previous = null;
        if (index == 0) {
            elem = i_first;
        } else {
            previous = iterator4().move(index - 1);
            if (previous != null) {
                elem = previous.i_next;
            }
        }
        if (elem != null) {
            elem.checkActive();
            if (previous != null) {
                previous.i_next = elem.i_next;
                previous.update();
            }
            checkRemoved(previous, elem);
            ret = elem.activatedObject(elementActivationDepth());
            elem.delete(i_deleteRemoved);
            return ret;
        }
        throw new IndexOutOfBoundsException();
    }

    private boolean remove4(Object o) {
        int idx = indexOf4(o);
        if (idx >= 0) {
            remove4(idx);
            return true;
        }
        return false;
    }

    public boolean removeAll(Collection c) {
        synchronized (streamLock()) {
            checkActive();
            boolean modified = false;
            Iterator i = c.iterator();
            while (i.hasNext()) {
                if (remove(i.next())) {
                    modified = true;
                }
            }
            return modified;
        }
    }
    
    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public Object set(int index, Object element) {
        synchronized (streamLock()) {
            checkActive();
            boolean needUpdate = false;
            Object ret = null;
            P1ListElement elem = null;
            P1ListElement previous = null;
            P1ListElement newElement = new P1ListElement(getTrans(), null,
                    element);
            if (index == 0) {
                elem = i_first;
                i_first = newElement;
                needUpdate = true;
            } else {
                P2ListElementIterator i = iterator4();
                previous = i.move(index - 1);
                if (previous != null) {
                    elem = previous.i_next;
                } else {
                    throw new IndexOutOfBoundsException();
                }
            }

            if (elem != null) {
                elem.checkActive();
                newElement.i_next = elem.i_next;
                if (previous != null) {
                    previous.i_next = newElement;
                    previous.update();
                }
                ret = elem.activatedObject(elementActivationDepth());
                elem.delete(i_deleteRemoved);
            } else {
                i_last = newElement;
                needUpdate = true;
            }
            if (needUpdate) {
                update();
            }
            return ret;
        }
    }

    public synchronized int size() {
        synchronized (streamLock()) {
            checkActive();
            return size4();
        }
    }

    private int size4() {
        int size = 0;
        P2ListElementIterator i = iterator4();
        while (i.hasNext()) {
            size++;
            i.nextElement();
        }
        return size;
    }

    public Object storedTo(Transaction a_trans) {
        if (getTrans() == null) {
            setTrans(a_trans);
        }
        return this;
    }

    public List subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        synchronized (streamLock()) {
            checkActive();
            Object[] arr = new Object[size4()];
            int i = 0;
            P2ListElementIterator j = iterator4();
            while (j.hasNext()) {
                P1ListElement elem = j.nextElement();
                arr[i++] = elem.activatedObject(elementActivationDepth());
            }
            return arr;
        }
    }

    public Object[] toArray(Object[] a) {
        synchronized (streamLock()) {
            checkActive();
            int size = size();
            if (a.length < size) {
                Transaction trans = getTrans();
                if(trans == null){
                    Exceptions4.throwRuntimeException(29);
                }
                Reflector reflector = trans.reflector();
                a =
                    (Object[])reflector.array().newInstance(
                        reflector.forObject(a).getComponentType(),
                        size);
            }
            int i = 0;
            P2ListElementIterator j = iterator4();
            while (j.hasNext()) {
                P1ListElement elem = j.nextElement();
                a[i++] = elem.activatedObject(elementActivationDepth());
            }
            if (a.length > size) {
                a[size] = null;
            }
            return a;
        }
    }

}
