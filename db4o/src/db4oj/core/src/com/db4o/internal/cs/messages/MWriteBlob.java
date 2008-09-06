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

import com.db4o.ext.*;
import com.db4o.foundation.network.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;


public class MWriteBlob extends MsgBlob implements ServerSideMessage {
	
	public void processClient(Socket4 sock) throws IOException {
        Msg message = Msg.readMessage(messageDispatcher(), transaction(), sock);
        if (message.equals(Msg.OK)) {
            try {
                _currentByte = 0;
                _length = this._blob.getLength();
                _blob.getStatusFrom(this);
                _blob.setStatus(Status.PROCESSING);
                FileInputStream inBlob = this._blob.getClientInputStream();
                copy(inBlob,sock,true);
                sock.flush();
                ObjectContainerBase stream = stream();
                message = Msg.readMessage(messageDispatcher(), transaction(), sock);
                if (message.equals(Msg.OK)) {

                    // make sure to load the filename to i_blob
                    // to allow client databasefile switching
                    stream.deactivate(transaction(), _blob, Integer.MAX_VALUE);
                    stream.activate(transaction(), _blob, new FullActivationDepth());

                    this._blob.setStatus(Status.COMPLETED);
                } else {
                    this._blob.setStatus(Status.ERROR);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

	public boolean processAtServer() {
        try {
            BlobImpl blobImpl = this.serverGetBlobImpl();
            if (blobImpl != null) {
                blobImpl.setTrans(transaction());
                File file = blobImpl.serverFile(null, true);
                Socket4 sock = serverMessageDispatcher().socket();
                Msg.OK.write(sock);
                FileOutputStream fout = new FileOutputStream(file);
                copy(sock,fout,blobImpl.getLength(),false);
                Msg.OK.write(sock);
            }
        } catch (Exception e) {
        }
        return true;
    }
}