/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogUtilsTest.java,v 1.19.2.1 2007/02/01 14:50:15 cwl Exp $
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
        dest.flip();
        assertEquals(unsignedData, LogUtils.getUnsignedInt(dest));

        unsignedData = 49249249L;
        dest.clear();
        LogUtils.writeUnsignedInt(dest, unsignedData);
        dest.flip();
        assertEquals(unsignedData, LogUtils.getUnsignedInt(dest));

        // ints
        int intData = -1021;
        dest.clear();
        LogUtils.writeInt(dest, intData);
        dest.flip();
        assertEquals(intData, LogUtils.readInt(dest));

        intData = 257;
        dest.clear();
        LogUtils.writeInt(dest, intData);
        dest.flip();
        assertEquals(intData, LogUtils.readInt(dest));

        // longs
        long longData = -1021;
        dest.clear();
        LogUtils.writeLong(dest, longData);
        dest.flip();
        assertEquals(longData, LogUtils.readLong(dest));

        // byte arrays
        byte [] byteData = new byte [] {1,2,3,4,5,6,7,8,9,10,11,12};
        dest.clear();
        LogUtils.writeByteArray(dest, byteData);
        dest.flip();
        assertTrue(Arrays.equals(byteData, LogUtils.readByteArray(dest)));

        // Strings
        String stringData = "Hello world!";
        dest.clear();
        LogUtils.writeString(dest, stringData);
        dest.flip();
        assertEquals(stringData, LogUtils.readString(dest));

        // Timestamps
        Timestamp timestampData =
            new Timestamp(Calendar.getInstance().getTimeInMillis());
        dest.clear();
        LogUtils.writeTimestamp(dest, timestampData);
        dest.flip();
        assertEquals(timestampData, LogUtils.readTimestamp(dest));

        // Booleans
        boolean boolData = true;
        dest.clear();
        LogUtils.writeBoolean(dest, boolData);
        dest.flip();
        assertEquals(boolData, LogUtils.readBoolean(dest));
    }
}
