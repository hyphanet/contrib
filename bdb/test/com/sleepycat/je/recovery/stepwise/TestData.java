/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2007 Oracle.  All rights reserved.
 *
 * $Id: TestData.java,v 1.4.2.1 2007/02/01 14:50:18 cwl Exp $
 */
package com.sleepycat.je.recovery.stepwise;

import java.util.Arrays;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Wrapper class that encapsulates a record in a database used for recovery 
 * testing.
 */
public class TestData {
    private DatabaseEntry key;
    private DatabaseEntry data;

    public TestData(DatabaseEntry key, DatabaseEntry data) {
        this.key = new DatabaseEntry(key.getData());
        this.data = new DatabaseEntry(data.getData());
    }

    public boolean equals(Object o ) {
        if (this == o)
            return true;
        if (!(o instanceof TestData))
            return false;

        TestData other = (TestData) o;
        if (Arrays.equals(key.getData(), other.key.getData()) &&
            Arrays.equals(data.getData(), other.data.getData())) {
            return true;
        } else 
            return false;
    }

    public String toString() {
        return  " k=" + IntegerBinding.entryToInt(key) +
                " d=" + IntegerBinding.entryToInt(data);
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public DatabaseEntry getKey() {
        return key;
    }

    public DatabaseEntry getData() {
        return data;
    }
}
