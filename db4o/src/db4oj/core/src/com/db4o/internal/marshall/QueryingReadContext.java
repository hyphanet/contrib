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
package com.db4o.internal.marshall;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.query.processor.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;

/**
 * @exclude
 */
public class QueryingReadContext extends AbstractReadContext implements HandlerVersionContext, AspectVersionContext{
    
    private final QCandidates _candidates;
    
    private final int _collectionID;
    
    private final int _handlerVersion;
    
    private IdObjectCollector _collector;
    
	private int _aspectCount;

    
    private QueryingReadContext(Transaction transaction, QCandidates candidates, int handlerVersion, ReadBuffer buffer, int collectionID, IdObjectCollector collector) {
        super(transaction, buffer);
        _candidates = candidates;
        _activationDepth = new LegacyActivationDepth(0);
        _collectionID = collectionID;
        _handlerVersion = handlerVersion;
        _collector = collector;
    }
    
    public QueryingReadContext(Transaction transaction, QCandidates candidates, int handlerVersion, ReadBuffer buffer, int collectionID) {
        this(transaction, candidates, handlerVersion, buffer, collectionID, new IdObjectCollector());
    }
    
    public QueryingReadContext(Transaction transaction, int handlerVersion, ReadBuffer buffer) {
        this(transaction, null, handlerVersion, buffer, 0);
    }
    
    public QueryingReadContext(Transaction transaction, int handlerVersion, ReadBuffer buffer,
    		int collectionID, IdObjectCollector collector) {
        this(transaction, null, handlerVersion, buffer, collectionID, collector);
    }

    public int collectionID() {
        return _collectionID;
    }
    
    public QCandidates candidates(){
        return _candidates;
    }
    
    public int handlerVersion() {
        return _handlerVersion;
    }
    
    private void addId(int id) {
        _collector.addId(id);
    }
    
    public Tree ids() {
        return _collector.ids();
    }
    
    public void add(Object obj) {
        int id = container().getID(transaction(), obj);
        if(id > 0){
            addId(id);
            return;
        }
        addObjectWithoutId(obj);
    }
    
    public void readId(TypeHandler4 handler) {
        ObjectID objectID = ObjectID.NOT_POSSIBLE;
        try {
            int offset = offset();
            if(handler instanceof ReadsObjectIds){
                objectID = ((ReadsObjectIds)handler).readObjectID(this);
            }
            if(objectID.isValid()){
                addId(objectID._id);
                return;
            }
            if(objectID == ObjectID.NOT_POSSIBLE){
                seek(offset);
                Object obj = read(handler);
                if(obj != null){
                    addObjectWithoutId(obj);
                }
            }
            
        } catch (Exception e) {
            // FIXME: Catchall
        }
    }

    private void addObjectWithoutId(Object obj) {
        _collector.add(obj);
    }

    public void skipId(TypeHandler4 handler) {
        if(handler instanceof ReadsObjectIds){
            ((ReadsObjectIds)handler).readObjectID(this);
            return;
        }
        // TODO: Optimize for just doing a seek here.
        read(handler);
    }
    
    public Iterator4 objectsWithoutId(){
        return _collector.objects();
    }
    
	public int aspectCount() {
		return _aspectCount;
	}

	public void aspectCount(int count) {
		_aspectCount = count;
	}
    
}
