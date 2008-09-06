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

import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.*;
import com.db4o.internal.query.result.*;

public abstract class MsgQuery extends MsgObject {
	
	private static final int ID_AND_SIZE = 2;
	
	private static int nextID;
	
	protected final void writeQueryResult(AbstractQueryResult queryResult, QueryEvaluationMode evaluationMode) {
		
		int queryResultId = 0;
		int maxCount = 0;
		
		if(evaluationMode == QueryEvaluationMode.IMMEDIATE){
			maxCount = queryResult.size();
		} else{
			queryResultId = generateID();
			maxCount = config().prefetchObjectCount();  
		}
		
		MsgD message = QUERY_RESULT.getWriterForLength(transaction(), bufferLength(maxCount));
		StatefulBuffer writer = message.payLoad();
		writer.writeInt(queryResultId);
		
        IntIterator4 idIterator = queryResult.iterateIDs();
        
    	writer.writeIDs(idIterator, maxCount);
        
        if(queryResultId > 0){
        	ServerMessageDispatcher serverThread = serverMessageDispatcher();
			serverThread.mapQueryResultToID(new LazyClientObjectSetStub(queryResult, idIterator), queryResultId);
        }
        
		write(message);
	}

	private int bufferLength(int maxCount) {
		return Const4.INT_LENGTH * (maxCount + ID_AND_SIZE);
	}
	
	private static synchronized int generateID(){
		nextID ++;
		if(nextID < 0){
			nextID = 1;
		}
		return nextID;
	}
	
	protected AbstractQueryResult newQueryResult(QueryEvaluationMode mode){
		return stream().newQueryResult(transaction(), mode);
	}

}
