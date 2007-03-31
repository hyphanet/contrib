/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: VisitedObjects.java,v 1.9.2.1 2007/02/01 14:49:56 cwl Exp $
 */

package com.sleepycat.persist.impl;

/**
 * Keeps track of a set of visited objects and their corresponding offset in a
 * byte array.  This uses a resizable int array for speed and simplicity.  If
 * in the future the array resizing or linear search are performance issues, we
 * could try using an IdentityHashMap instead.
 *
 * @author Mark Hayes
 */
class VisitedObjects {

    private static final int INIT_LEN = 50;

    private Object[] objects;
    private int[] offsets;
    private int nextIndex;

    /**
     * Creates an empty set.
     */
    VisitedObjects() {
        objects = new Object[INIT_LEN];
        offsets = new int[INIT_LEN];
        nextIndex = 0;
    }

    /**
     * Adds a visited object and offset, growing the visited arrays as needed.
     */
    void add(Object o, int offset) {

        int i = nextIndex;
        nextIndex += 1;
        if (nextIndex > objects.length) {
            growVisitedArrays();
        }
        objects[i] = o;
        offsets[i] = offset;
    }

    /**
     * Returns the offset for a visited object, or -1 if never visited.
     */
    int getOffset(Object o) {
        for (int i = 0; i < nextIndex; i += 1) {
            if (objects[i] == o) {
                return offsets[i];
            }
        }
        return -1;
    }

    /**
     * Returns the visited object for a given offset, or null if never visited.
     */
    Object getObject(int offset) {
        for (int i = 0; i < nextIndex; i += 1) {
            if (offsets[i] == offset) {
                return objects[i];
            }
        }
        return null;
    }

    /**
     * Replaces a given object in the list.  Used when an object is converted
     * after adding it to the list.
     */
    void replaceObject(Object existing, Object replacement) {
        for (int i = nextIndex - 1; i >= 0; i -= 1) {
            if (objects[i] == existing) {
                objects[i] = replacement;
                return;
            }
        }
        assert false;
    }

    /**
     * Doubles the size of the visited arrays.
     */
    private void growVisitedArrays() {

        int oldLen = objects.length;
        int newLen = oldLen * 2;

        Object[] newObjects = new Object[newLen];
        int[] newOffsets = new int[newLen];

        System.arraycopy(objects, 0, newObjects, 0, oldLen);
        System.arraycopy(offsets, 0, newOffsets, 0, oldLen);

        objects = newObjects;
        offsets = newOffsets;
    }
}
