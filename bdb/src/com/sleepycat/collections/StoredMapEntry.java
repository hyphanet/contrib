/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: StoredMapEntry.java,v 1.19 2008/05/27 15:30:34 mark Exp $
 */

package com.sleepycat.collections;

/**
 * @author Mark Hayes
 */
final class StoredMapEntry extends MapEntryParameter {

    private BaseIterator iter;
    private StoredCollection coll;

    StoredMapEntry(Object key,
                   Object value,
                   StoredCollection coll,
                   BaseIterator iter) {

        super(key, value);
        this.coll = coll;
        this.iter = iter;
    }

    public Object setValue(Object newValue) {

        Object oldValue;
        if (iter != null && iter.isCurrentData(this)) {
            oldValue = getValue();
            iter.set(newValue);
        } else {
            if (coll.view.dupsAllowed) {
                throw new IllegalStateException("May not insert duplicates");
            }
            oldValue = coll.putKeyValue(getKey(), newValue);
        }
        setValueInternal(newValue);
        return oldValue;
    }
}
