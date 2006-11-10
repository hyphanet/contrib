package com.onionnetworks.util;

import java.util.Iterator;

/**
 * @author Ry4an Brase
 */
public class JoiningIterator implements Iterator {
    private Iterator first, second;
    public JoiningIterator(Iterator f, Iterator s) {
        first = f; second = s;
    }

    public boolean hasNext() {
        return first.hasNext() || second.hasNext();
    }

    public Object next() {
        return (first.hasNext())?first.next():second.next(); // throws NSEEx
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
