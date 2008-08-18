/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: LogUtilsTest.java,v 1.24 2008/01/17 17:22:16 cwl Exp $
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;

import junit.framework.TestCase;

/**
 *  Test basic marshalling utilities
 */
public class LogUtilsTest extends TestCase {

    public void testMarshalling() {
        ByteBuffer dest = ByteBuffer.allocate(100);

        // unsigned ints
        long unsignedData = 10;
        dest.clear();
        LogUtils.writeUnsignedInt(dest, unsignedData);
        assertEquals(LogUtils.UNSIGNED_INT_BYTES, dest.position());
        dest.flip();
        assertEquals(unsignedData, LogUtils.readUnsignedInt(dest));

        unsignedData = 49249249L;
        dest.clear();
        LogUtils.writeUnsignedInt(dest, unsignedData);
        assertEquals(LogUtils.UNSIGNED_INT_BYTES, dest.position());
        dest.flip();
        assertEquals(unsignedData, LogUtils.readUnsignedInt(dest));

        // ints
        int intData = -1021;
        dest.clear();
        LogUtils.writeInt(dest, intData);
        assertEquals(LogUtils.INT_BYTES, dest.position());
        dest.flip();
        assertEquals(intData, LogUtils.readInt(dest));

        intData = 257;
        dest.clear();
        LogUtils.writeInt(dest, intData);
        assertEquals(LogUtils.INT_BYTES, dest.position());
        dest.flip();
        assertEquals(intData, LogUtils.readInt(dest));

        // longs
        long longData = -1021;
        dest.clear();
        LogUtils.writeLong(dest, longData);
        assertEquals(LogUtils.LONG_BYTES, dest.position());
        dest.flip();
        assertEquals(longData, LogUtils.readLong(dest));

        // byte arrays
        byte[] byteData = new byte[] {1,2,3,4,5,6,7,8,9,10,11,12};
        dest.clear();
        LogUtils.writeByteArray(dest, byteData);
        assertEquals(LogUtils.getPackedIntLogSize(12) + 12, dest.position());
        dest.flip();
        assertTrue(Arrays.equals(byteData,
                                 LogUtils.readByteArray(dest,
                                                        false/*unpacked*/)));

        // Strings
        String stringData = "Hello world!";
        dest.clear();
        LogUtils.writeString(dest, stringData);
        assertEquals(LogUtils.INT_BYTES + 9, dest.position());
        dest.flip();
        assertEquals(stringData, LogUtils.readString(dest, false/*unpacked*/));

        // Timestamps
        Timestamp timestampData =
            new Timestamp(Calendar.getInstance().getTimeInMillis());
        dest.clear();
        LogUtils.writeTimestamp(dest, timestampData);
        assertEquals(LogUtils.getTimestampLogSize(timestampData),
                     dest.position());
        dest.flip();
        assertEquals(timestampData, LogUtils.readTimestamp(dest, false));

        // Booleans
        boolean boolData = true;
        dest.clear();
        LogUtils.writeBoolean(dest, boolData);
        assertEquals(1, dest.position());
        dest.flip();
        assertEquals(boolData, LogUtils.readBoolean(dest));

        /*
         * Test packed values with both array and direct buffers because the
         * implementation is different when there is an array available
         * (ByteBuffer.hasArray) or not.
         */
        testPacked(dest);
        testPacked(ByteBuffer.allocateDirect(100));
    }

    private void testPacked(ByteBuffer dest) {

        // packed ints
        int intValue = 119;
        dest.clear();
        LogUtils.writePackedInt(dest, intValue);
        assertEquals(1, dest.position());
        dest.flip();
        assertEquals(intValue, LogUtils.readPackedInt(dest));

        intValue = 0xFFFF + 119;
        dest.clear();
        LogUtils.writePackedInt(dest, intValue);
        assertEquals(3, dest.position());
        dest.flip();
        assertEquals(intValue, LogUtils.readPackedInt(dest));

        intValue = Integer.MAX_VALUE;
        dest.clear();
        LogUtils.writePackedInt(dest, intValue);
        assertEquals(5, dest.position());
        dest.flip();
        assertEquals(intValue, LogUtils.readPackedInt(dest));

        // packed longs
        long longValue = 119;
        dest.clear();
        LogUtils.writePackedLong(dest, longValue);
        assertEquals(1, dest.position());
        dest.flip();
        assertEquals(longValue, LogUtils.readPackedLong(dest));

        longValue = 0xFFFFFFFFL + 119;
        dest.clear();
        LogUtils.writePackedLong(dest, longValue);
        assertEquals(5, dest.position());
        dest.flip();
        assertEquals(longValue, LogUtils.readPackedLong(dest));

        longValue = Long.MAX_VALUE;
        dest.clear();
        LogUtils.writePackedLong(dest, longValue);
        assertEquals(9, dest.position());
        dest.flip();
        assertEquals(longValue, LogUtils.readPackedLong(dest));
    }
}
