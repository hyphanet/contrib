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
package com.db4o.foundation.network;

import com.db4o.ext.*;
import com.db4o.foundation.*;

/**
 * Transport buffer for C/S mode to simulate a
 * socket connection in memory.
 */
class BlockingByteChannel {

    private final static int DISCARD_BUFFER_SIZE = 500;
    protected byte[] i_cache;
    boolean i_closed = false;
    protected int i_readOffset;
    private int i_timeout;
    protected int i_writeOffset;
    protected final Lock4 i_lock = new Lock4();

    public BlockingByteChannel(int timeout) {
        i_timeout = timeout;
    }

    protected int available() {
        return i_writeOffset - i_readOffset;
    }

    protected void checkDiscardCache() {
        if (i_readOffset == i_writeOffset && i_cache.length > DISCARD_BUFFER_SIZE) {
            i_cache = null;
            i_readOffset = 0;
            i_writeOffset = 0;
        }
    }

    void close() {
        i_lock.run(new Closure4() {
            public Object run() {
            	i_closed = true;
            	i_lock.awake();
            	return null;
            }
        });
    }

    protected void makefit(int length) {
        if (i_cache == null) {
            i_cache = new byte[length];
        } else {
            // doesn't fit
            if (i_writeOffset + length > i_cache.length) {
                // move, if possible
                if (i_writeOffset + length - i_readOffset <= i_cache.length) {
                    byte[] temp = new byte[i_cache.length];
                    System.arraycopy(i_cache, i_readOffset, temp, 0, i_cache.length - i_readOffset);
                    i_cache = temp;
                    i_writeOffset -= i_readOffset;
                    i_readOffset = 0;

                    // else append
                } else {
                    byte[] temp = new byte[i_writeOffset + length];
                    System.arraycopy(i_cache, 0, temp, 0, i_cache.length);
                    i_cache = temp;
                }
            }
        }
    }

    public int read() throws Db4oIOException {
		Integer ret = (Integer) i_lock.run(new Closure4() {
			public Object run() {
				waitForAvailable();
				int retVal = i_cache[i_readOffset++];
				checkDiscardCache();
				return new Integer(retVal);
			}
		});
		return ret.intValue();

	}

    public int read(final byte[] bytes, final int offset, final int length)
			throws Db4oIOException {
		Integer ret = (Integer) i_lock.run(new Closure4() {
			public Object run() {
				waitForAvailable();
				int avail = available();
				int toRead = length;
				if (avail < length) {
					toRead = avail;
				}
				System.arraycopy(i_cache, i_readOffset, bytes, offset, toRead);
				i_readOffset += toRead;
				checkDiscardCache();
				return new Integer(toRead);
			}
		});
		return ret.intValue();
	}

    public void setTimeout(int timeout) {
        i_timeout = timeout;
    }

    protected void waitForAvailable()  {
    	long beginTime = System.currentTimeMillis();
        while (available() == 0) {
        	checkClosed();
            i_lock.snooze(i_timeout);
            if(isTimeout(beginTime)) {
            	throw new Db4oIOException();
            }
        }
    }

	private boolean isTimeout(long start) {
		return System.currentTimeMillis() - start >= i_timeout;
	}

    public void write(byte[] bytes) throws Db4oIOException {
    	write(bytes, 0, bytes.length);
    }
    
	public void write(final byte[] bytes, final int off, final int len) throws Db4oIOException {
		i_lock.run(new Closure4() {
			public Object run() {
				checkClosed();
				makefit(len);
				System.arraycopy(bytes, off, i_cache, i_writeOffset, len);
				i_writeOffset += len;
				i_lock.awake();
				return null;
			}
		});
	}

    public void write(final int i) throws Db4oIOException {
		i_lock.run(new Closure4() {
			public Object run() {
				checkClosed();
				makefit(1);
				i_cache[i_writeOffset++] = (byte) i;
				i_lock.awake();
				return null;
			}
		});
	}

	public void checkClosed() {
		if (i_closed) {
			throw new Db4oIOException();
		}
	}
}
