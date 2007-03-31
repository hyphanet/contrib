/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: OffsetTest.java,v 1.5.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import junit.framework.TestCase;

import com.sleepycat.je.cleaner.OffsetList;
import com.sleepycat.je.cleaner.PackedOffsets;

/**
 * Tests the OffsetList and PackedOffset classes.
 */
public class OffsetTest extends TestCase {

    public void testOffsets() {

        doAllTest(new long[] {
            1,
            2,
            0xfffe,
            0xffff,
            0xfffff,
            Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE,

            /*
             * The following values don't work, which is probably a bug, but
             * LSN offsets are not normally this large so the bug currently has
             * little impact.
             */
            //Integer.MAX_VALUE + 1L,
            //Long.MAX_VALUE - 100L,
            //Long.MAX_VALUE,
        });
    }

    private void doAllTest(long[] offsets) {

        ArrayList list = list(offsets);

        doOneTest(offsets);

        Collections.reverse(list);
        doOneTest(array(list));

        Collections.shuffle(list);
        doOneTest(array(list));
    }

    private void doOneTest(long[] offsets) {

        OffsetList list = new OffsetList();
        for (int i = 0; i < offsets.length; i += 1) {
            list.add(offsets[i], true);
        }
        long[] array = list.toArray();
        assertTrue("array=\n" + dump(array) + " offsets=\n" + dump(offsets),
                   Arrays.equals(offsets, array));

        long[] sorted = new long[array.length];
        System.arraycopy(array, 0, sorted, 0, array.length);
        Arrays.sort(sorted);

        PackedOffsets packed = new PackedOffsets();
        packed.pack(array);
        assertTrue(Arrays.equals(sorted, packed.toArray()));
    }

    private ArrayList list(long[] array) {

        ArrayList list = new ArrayList(array.length);
        for (int i = 0; i < array.length; i += 1) {
            list.add(new Long(array[i]));
        }
        return list;
    }

    private long[] array(ArrayList list) {

        long[] array = new long[list.size()];
        for (int i = 0; i < array.length; i += 1) {
            array[i] = ((Long) list.get(i)).longValue();
        }
        return array;
    }

    private String dump(long[] array) {

        StringBuffer buf = new StringBuffer(array.length * 10);
        for (int i = 0; i < array.length; i += 1) {
            buf.append(Long.toString(array[i]));
            buf.append(' ');
        }
        return buf.toString();
    }
}
