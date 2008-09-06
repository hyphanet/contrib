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
package com.db4o.internal.cs;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.messages.*;

final class ClientTransaction extends Transaction {

    private final ClientObjectContainer i_client;
    
    protected Tree i_yapObjectsToGc;
    
    ClientTransaction(ClientObjectContainer container, Transaction parentTransaction, TransactionalReferenceSystem referenceSystem) {
        super(container, parentTransaction, referenceSystem);
        i_client = container;
    }
    
    public void commit() {
    	commitTransactionListeners();
        clearAll();
        if(isSystemTransaction()){
        	i_client.write(Msg.COMMIT_SYSTEMTRANS);
        }else{
        	i_client.write(Msg.COMMIT);
        	i_client.expectedResponse(Msg.OK);
        }
    }
    
    protected void clear() {
    	removeYapObjectReferences();
    }

	private void removeYapObjectReferences() {
		if(i_yapObjectsToGc != null){
            i_yapObjectsToGc.traverse(new Visitor4() {
                public void visit(Object a_object) {
                    ObjectReference yo = (ObjectReference)((TreeIntObject) a_object)._object;
                    ClientTransaction.this.removeReference(yo);
                }
            });
        }
        i_yapObjectsToGc = null;
	}

    public boolean delete(ObjectReference ref, int id, int cascade) {
        if (! super.delete(ref, id, cascade)){
        	return false;
        }
        MsgD msg = Msg.TA_DELETE.getWriterForInts(this, new int[] {id, cascade});
        i_client.writeBatchedMessage(msg);
        return true;
    }

    public boolean isDeleted(int a_id) {

        // This one really is a hack.
        // It only helps to get information about the current
        // transaction.

        // We need a better strategy for C/S concurrency behaviour.
        MsgD msg = Msg.TA_IS_DELETED.getWriterForInt(this, a_id);
		i_client.write(msg);
        int res = i_client.expectedByteResponse(Msg.TA_IS_DELETED).readInt();
        return res == 1;
    }
    
    public final HardObjectReference getHardReferenceBySignature(final long a_uuid, final byte[] a_signature) {
        int messageLength = Const4.LONG_LENGTH + Const4.INT_LENGTH + a_signature.length;
        MsgD message = Msg.OBJECT_BY_UUID.getWriterForLength(this, messageLength);
        message.writeLong(a_uuid);
        message.writeBytes(a_signature);
        i_client.write(message);
        message = (MsgD)i_client.expectedResponse(Msg.OBJECT_BY_UUID);
        int id = message.readInt();
        if(id > 0){
            return container().getHardObjectReferenceById(this, id);
        }
        return HardObjectReference.INVALID;
    }
    
    
    public void processDeletes() {
        if (_delete != null) {
            _delete.traverse(new Visitor4() {
                public void visit(Object a_object) {
                    DeleteInfo info = (DeleteInfo) a_object;
                    if (info._reference != null) {
                        i_yapObjectsToGc = Tree.add(i_yapObjectsToGc, new TreeIntObject(info._key, info._reference));
                    }
                }
            });
        }
        _delete = null;
		i_client.writeBatchedMessage(Msg.PROCESS_DELETES);
    }
    
    public void rollback() {
        i_yapObjectsToGc = null;
        rollBackTransactionListeners();
        clearAll();
    }

    public void writeUpdateDeleteMembers(int a_id, ClassMetadata a_yc, int a_type,
        int a_cascade) {
    	MsgD msg = Msg.WRITE_UPDATE_DELETE_MEMBERS.getWriterForInts(this,
				new int[] { a_id, a_yc.getID(), a_type, a_cascade });
		i_client.writeBatchedMessage(msg);
    }

}