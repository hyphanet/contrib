package com.onionnetworks.util;

/**
 *
 * @author Justin F. Chapweske
 */
public class Tuple {

    protected Object left;
    protected Object right;

    public Tuple(Object left, Object right) {
        this.left = left;
        this.right = right;
    }

    public Object getCar() {
        return left;
    }

    public Object getCdr() {
        return right;
    }

    public Object getLeft() {
        return left;
    }

    public Object getRight() {
        return right;
    }

    public int hashCode() {
        return left.hashCode() ^ right.hashCode();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Tuple)) {
            return false;
        }
        Tuple t = (Tuple) obj;
        if (left.equals(t.getLeft()) && right.equals(t.getRight())) {
            return true;
        }
        return false;
    }
}
