/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004,2007 Oracle.  All rights reserved.
 *
 * $Id: LevelRecorderTest.java,v 1.3.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.tree.IN;

public class LevelRecorderTest extends TestCase {

    public LevelRecorderTest() {
    }

    public void testRecording () {
        LevelRecorder recorder = new LevelRecorder();

        DatabaseId id1 = new DatabaseId(1);
        DatabaseId id5 = new DatabaseId(5);
        DatabaseId id10 = new DatabaseId(10);

        int level1 = IN.BIN_LEVEL;
        int level2 = level1 + 1;
        int level3 = level1 + 2;
        int level4 = level1 + 3;

        /* Mimic the recording of various INs for various databases. */
        recorder.record(id10, level1);
        recorder.record(id5,  level3);
        recorder.record(id5,  level2);
        recorder.record(id10, level1);
        recorder.record(id1,  level1);
        recorder.record(id10, level1);
        recorder.record(id1,  level4);

        /* 
         * We should only have to redo recovery for dbs 1 and 5. Db 10 had
         * INs all of the same level.
         */
        Set reprocessSet = recorder.getDbsWithDifferentLevels();
        assertEquals(2, reprocessSet.size());
        assertTrue(reprocessSet.contains(id5));
        assertTrue(reprocessSet.contains(id1));
        assertFalse(reprocessSet.contains(id10));
    }
}
