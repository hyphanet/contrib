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
package com.db4o.db4ounit.jre5.concurrency.collections;

import java.util.Map;

import com.db4o.collections.ArrayMap4;
import com.db4o.config.Configuration;
import com.db4o.db4ounit.jre5.collections.ArrayMap4Asserter;
import com.db4o.db4ounit.jre5.collections.CollectionsUtil;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ta.TransparentActivationSupport;

import db4ounit.Assert;
import db4ounit.extensions.Db4oConcurrencyTestCase;

public class ArrayMap4TestCase extends Db4oConcurrencyTestCase {

    public static void main(String[] args) {
        new ArrayMap4TestCase().runEmbeddedConcurrency();
    }

    protected void store() throws Exception {
        ArrayMap4<String, Integer> map = new ArrayMap4<String, Integer>();
        putData(map);
        store(map);
    }

    protected void configure(Configuration config) throws Exception {
        config.add(new TransparentActivationSupport());
        super.configure(config);
    }

    private void putData(Map<String, Integer> map) {
        for (int i = 0; i < 10; i++) {
            map.put(String.valueOf(i), Integer.valueOf(i * 100));
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayMap4<String, Integer> retrieveOnlyInstance(
            ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = CollectionsUtil.retrieveMapFromDB(db,
                reflector());
        return map;
    }

    @SuppressWarnings("unchecked")
    public void conc(ExtObjectContainer db) throws Exception {
        retrieveOnlyInstance(db);
    }

    public void concClear(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);

        ArrayMap4Asserter.assertClear(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }

    public void checkClear(ExtObjectContainer db) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        ArrayMap4Asserter.checkClear(map);
    }

    @SuppressWarnings("unchecked")
    public void concClone(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);

        ArrayMap4Asserter.assertClone(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }

    public void concContainsKey(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertContainsKey(map);
    }
    
    public void concContainsValue(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertContainsValue(map);
    }
    
    public void concEntrySet(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertEntrySet(map);
    }

    public void concGet(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertGet(map);
    }

    public void concIsEmpty(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertIsEmpty(map);
    }

    public void concKeySet(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertKeySet(map);
    }

    public void concPut(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertPut(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkPut(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.checkPut(map);
    }

    public void concPutAll(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertPutAll(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkPutAll(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.checkMap(map, 0, 20);
    }

    public void concRemove_FromHead(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertRemove_FromHead(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkRemove_FromHead(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.checkRemove(map, 1, 10, "0");
    }
    
    public void concRemove_FromEnd(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertRemove_FromEnd(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkRemove_FromEnd(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        ArrayMap4Asserter.checkRemove(map, 0, 9, "9");
    }

    public void concRemove_FromMiddle(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertRemove_FromMiddle(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkRemove_FromMiddle(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.checkRemove_FromMiddle(map);
    }
    
    public void concSize(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertSize(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }

    public void checkSize(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        Assert.isNull(map.get("1"));
        Assert.areEqual(Integer.valueOf(1234), map.get("x"));
        Assert.areEqual(10, map.size());
    }
    
    public void concValues(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertValues(map);
    }

    public void concEquals(ExtObjectContainer db, int seq) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertEquals(map);
    }
    
    public void concIncreaseSize(ExtObjectContainer db, int seq) throws Exception {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.assertIncreaseSize(map);
        markTaskDone(seq, true);
        waitForAllTasksDone();
        db.store(map);
    }
    
    public void checkIncreaseSize(ExtObjectContainer db) {
        ArrayMap4<String, Integer> map = retrieveOnlyInstance(db);
        
        ArrayMap4Asserter.checkMap(map, 0, 50);
    }

}
