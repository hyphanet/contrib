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
package com.db4o.internal;

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.slots.*;
import com.db4o.io.*;


/**
 * @exclude
 */
public class IoAdaptedObjectContainer extends LocalObjectContainer {

    private final String _fileName;

    private IoAdapter          _file;
    private IoAdapter          _timerFile;                                 //This is necessary as a separate File because access is not synchronized with access for normal data read/write so the seek pointer can get lost.
    private volatile IoAdapter _backupFile;

    private Object             _fileLock;
    
    private final FreespaceFiller _freespaceFiller;

    IoAdaptedObjectContainer(Configuration config, String fileName) throws OldFormatException {
        super(config,null);
        _fileLock = new Object();
        _fileName = fileName;
        _freespaceFiller=createFreespaceFiller();
        open();
    }

    protected final void openImpl() throws OldFormatException,
			DatabaseReadOnlyException {
		IoAdapter ioAdapter = configImpl().io();
		boolean isNew = !ioAdapter.exists(fileName());
		if (isNew) {
			logMsg(14, fileName());
			checkReadOnly();
			_handlers.oldEncryptionOff();
		}
		boolean readOnly = configImpl().isReadOnly();
		boolean lockFile = Debug.lockFile && configImpl().lockFile()
				&& (!readOnly);
		_file = ioAdapter.open(fileName(), lockFile, 0, readOnly);
		if (needsLockFileThread()) {
			_timerFile = ioAdapter.delegatedIoAdapter().open(fileName(), false,	0, false);
		}
		if (isNew) {
			configureNewFile();
			if (configImpl().reservedStorageSpace() > 0) {
				reserve(configImpl().reservedStorageSpace());
			}
			commitTransaction();
			writeHeader(true, false);
		} else {
			readThis();
		}
	}
    
    public void backup(String path) throws DatabaseClosedException, Db4oIOException {
        synchronized (_lock) {
			checkClosed();
			if (_backupFile != null) {
				throw new BackupInProgressException();
			}
			_backupFile = configImpl().io().open(path, true,
					_file.getLength(), false);
			_backupFile.blockSize(blockSize());
		}
        long pos = 0;
        byte[] buffer = new byte[8192];
        while (true) {
			synchronized (_lock) {
				_file.seek(pos);
				int read = _file.read(buffer);
				if (read <= 0) {
					break;
				}
				_backupFile.seek(pos);
				_backupFile.write(buffer, read);
				pos += read;
			}
		}
        
		Cool.sleepIgnoringInterruption(1);

        synchronized (_lock) {
			_backupFile.close();
			_backupFile = null;
		}
    }
    
    public void blockSize(int size){
        _file.blockSize(size);
        if (_timerFile != null) {
            _timerFile.blockSize(size);
        }
    }

    public byte blockSize() {
        return (byte) _file.blockSize();
    }

    protected void freeInternalResources() {
		freePrefetchedPointers();
    }

    protected void shutdownDataStorage() {
		synchronized (_fileLock) {
			closeDatabaseFile();
			closeFileHeader();
			closeTimerFile();
		}
	}

	 /*
     * This method swallows IOException,
     * because it should not affect other close precedures.
     */
	private void closeDatabaseFile() {
		try {
			if (_file != null) {
				_file.close();
			}
		} finally {
			_file = null;
		}
	}
    
    /*
     * This method swallows IOException,
     * because it should not affect other close precedures.
     */
	private void closeFileHeader() {
		try {
			if (_fileHeader != null) {
				_fileHeader.close();
			}
		} finally {
			_fileHeader = null;
		}
	}
	
	/*
     * This method swallows IOException,
     * because it should not affect other close precedures.
     */
    private void closeTimerFile() {
		try {
			if (_timerFile != null) {
				_timerFile.close();
			}
		} finally {
			_timerFile = null;
		}
	}
    
    public void commit1(Transaction trans) {
        ensureLastSlotWritten();
        super.commit1(trans);
    }

    public void copy(int oldAddress, int oldAddressOffset, int newAddress, int newAddressOffset, int length) {

        if (Debug.xbytes && Deploy.overwrite) {
            checkXBytes(newAddress, newAddressOffset, length);
        }

        try {

            if (_backupFile == null) {
                _file
                    .blockCopy(oldAddress, oldAddressOffset, newAddress, newAddressOffset, length);
                return;
            }

            byte[] copyBytes = new byte[length];
            _file.blockSeek(oldAddress, oldAddressOffset);
            _file.read(copyBytes);

            _file.blockSeek(newAddress, newAddressOffset);
            _file.write(copyBytes);

            if (_backupFile != null) {
                _backupFile.blockSeek(newAddress, newAddressOffset);
                _backupFile.write(copyBytes);
            }

        } catch (Exception e) {
            Exceptions4.throwRuntimeException(16, e);
        }

    }

    private void checkXBytes(int newAddress, int newAddressOffset, int length) {
        if (Debug.xbytes && Deploy.overwrite) {
            try {
                byte[] checkXBytes = new byte[length];
                _file.blockSeek(newAddress, newAddressOffset);
                _file.read(checkXBytes);
                for (int i = 0; i < checkXBytes.length; i++) {
                    if (checkXBytes[i] != Const4.XBYTE) {
                        String msg = "XByte corruption adress:" + newAddress + " length:"
                            + length + " starting:" + i;
                        throw new Db4oException(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public long fileLength() {
        try {
            return _file.getLength();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public String fileName() {
        return _fileName;
    }


    public void readBytes(byte[] bytes, int address, int length) throws Db4oIOException {
        readBytes(bytes, address, 0, length);
    }

    public void readBytes(byte[] bytes, int address, int addressOffset,
			int length) throws Db4oIOException {

		if (DTrace.enabled) {
			DTrace.READ_BYTES.logLength(address + addressOffset, length);
		}
		_file.blockSeek(address, addressOffset);
		int bytesRead = _file.read(bytes, length);
		checkReadCount(bytesRead, length);
	}

	private void checkReadCount(int bytesRead, int expected) {
		if (bytesRead != expected) {
			throw new IncompatibleFileFormatException();
		}
	}
	
    public void reserve(int byteCount) throws DatabaseReadOnlyException {
    	checkReadOnly();
        synchronized (_lock) {
        	Slot slot = getSlot(byteCount);
            zeroReservedSlot(slot);
            free(slot);
        }
    }

    private void zeroReservedSlot(Slot slot) {
    	zeroFile(_file, slot);
    	zeroFile(_backupFile, slot);
    }
    
    private void zeroFile(IoAdapter io, Slot slot) {
    	if(io == null) {
    		return;
    	}
    	byte[] zeroBytes = new byte[1024];
        int left = slot.length();
        io.blockSeek(slot.address(), 0);
        while (left > zeroBytes.length) {
			io.write(zeroBytes, zeroBytes.length);
			left -= zeroBytes.length;
		}
        if(left > 0) {
        	io.write(zeroBytes, left);
        }
    }

    public void syncFiles() {
        _file.sync();
        if (_timerFile != null) {
            // _timerFile can be set to null here by other thread
            try{
                _timerFile.sync();
            }catch (Exception e){
            }
        }
    }

    public void writeBytes(ByteArrayBuffer buffer, int blockedAddress, int addressOffset) {
		if (Deploy.debug && !Deploy.flush) {
			return;
		}

		if (Debug.xbytes && Deploy.overwrite) {

			boolean doCheck = true;
			if (buffer instanceof StatefulBuffer) {
				StatefulBuffer writer = (StatefulBuffer) buffer;
				if (writer.getID() == Const4.IGNORE_ID) {
					doCheck = false;
				}
			}
			if (doCheck) {
				checkXBytes(blockedAddress, addressOffset, buffer.length());
			}
		}

		if (DTrace.enabled) {
			DTrace.WRITE_BYTES.logLength(blockedAddress + addressOffset, buffer
					.length());
		}

		_file.blockSeek(blockedAddress, addressOffset);
		_file.write(buffer._buffer, buffer.length());
		if (_backupFile != null) {
			_backupFile.blockSeek(blockedAddress, addressOffset);
			_backupFile.write(buffer._buffer, buffer.length());
		}
	}

    public void overwriteDeletedBytes(int address, int length) {
		if (!Deploy.flush) {
			return;
		}
		if (_freespaceFiller == null) {
			return;
		}
		if (address > 0 && length > 0) {
			if (DTrace.enabled) {
				DTrace.WRITE_XBYTES.logLength(address, length);
			}
			IoAdapterWindow window = new IoAdapterWindow(_file, address, length);
			try {
				createFreespaceFiller().fill(window);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				window.disable();
			}
		}

	}

	public IoAdapter timerFile() {
		return _timerFile;
	}
	
	private FreespaceFiller createFreespaceFiller() {
		FreespaceFiller freespaceFiller=config().freespaceFiller();
		if(Debug.xbytes) {
			freespaceFiller=new XByteFreespaceFiller();
		}
		return freespaceFiller;
	}
	
	private static class XByteFreespaceFiller implements FreespaceFiller {

		public void fill(IoAdapterWindow io) throws IOException {
			io.write(0,xBytes(io.length()));
		}

	    private byte[] xBytes(int len) {
	        byte[] bytes = new byte[len];
	        for (int i = 0; i < len; i++) {
	            bytes[i]=Const4.XBYTE;
	        }
	        return bytes;
	    }
	}
}