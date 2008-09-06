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
package com.db4o.internal.cs.messages;

import java.io.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.foundation.network.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;

public abstract class MsgBlob extends MsgD implements BlobStatus{

    public BlobImpl _blob;
    int _currentByte;
    int _length;

    public double getStatus() {
        if (_length != 0) {
            return (double) _currentByte / (double) _length;
        }
        return Status.ERROR;
    }

    public abstract void processClient(Socket4 sock) throws IOException;

    BlobImpl serverGetBlobImpl() {
        BlobImpl blobImpl = null;
        int id = _payLoad.readInt();
        ObjectContainerBase stream = stream();
        synchronized (stream._lock) {
            blobImpl = (BlobImpl) stream.getByID(transaction(), id);
            stream.activate(transaction(), blobImpl, new FixedActivationDepth(3));
        }
        return blobImpl;
    }

    protected void copy(Socket4 sock,OutputStream rawout,int length,boolean update) throws IOException {
        BufferedOutputStream out = new BufferedOutputStream(rawout);
        byte[] buffer=new byte[BlobImpl.COPYBUFFER_LENGTH];
        int totalread=0;
        while(totalread<length) {
            int stilltoread=length-totalread;
            int readsize=(stilltoread<buffer.length ? stilltoread : buffer.length);
            int curread=sock.read(buffer,0,readsize);
            
            if(curread < 0){
                throw new IOException();
            }
            
            out.write(buffer,0,curread);
            totalread+=curread;
            if(update) {
                _currentByte+=curread;
            }
        }
        out.flush();
        out.close();
    }

    protected void copy(InputStream rawin,Socket4 sock,boolean update) throws IOException {
        BufferedInputStream in = new BufferedInputStream(rawin);
        byte[] buffer=new byte[BlobImpl.COPYBUFFER_LENGTH];
        int bytesread=-1;
        while((bytesread=rawin.read(buffer))>=0) {
            sock.write(buffer,0,bytesread);
            if(update) {
                _currentByte+=bytesread;
            }
        }
        in.close();
    }
}
