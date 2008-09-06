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
package com.db4o.internal.query.processor;

import com.db4o.foundation.*;
import com.db4o.internal.*;


/**
 * @exclude
 */
public class QConEvaluation extends QCon {

	private transient Object i_evaluation;

	public byte[] i_marshalledEvaluation;

	public int i_marshalledID;

	public QConEvaluation() {
		// C/S only
	}

	public QConEvaluation(Transaction a_trans, Object a_evaluation) {
		super(a_trans);
		i_evaluation = a_evaluation;
	}

	void evaluateEvaluationsExec(QCandidates a_candidates, boolean rereadObject) {
		if (rereadObject) {
			a_candidates.traverse(new Visitor4() {
				public void visit(Object a_object) {
					((QCandidate) a_object).useField(null);
				}
			});
		}
		a_candidates.filter(this);
	}

    void marshall() {
        super.marshall();
		if(!Platform4.useNativeSerialization()){
			marshallUsingDb4oFormat();
		}else{
    		try{
    			i_marshalledEvaluation = Platform4.serialize(i_evaluation);
    		}catch (Exception e){
    			marshallUsingDb4oFormat();
    		}
		}
	}
    
    private void marshallUsingDb4oFormat(){
    	SerializedGraph serialized = Serializer.marshall(container(), i_evaluation);
    	i_marshalledEvaluation = serialized._bytes;
    	i_marshalledID = serialized._id;
    }

    void unmarshall(Transaction a_trans) {
    	if (i_trans == null) {
    		super.unmarshall(a_trans);
    		
            if(i_marshalledID > 0 || !Platform4.useNativeSerialization()){
            	i_evaluation = Serializer.unmarshall(container(), i_marshalledEvaluation, i_marshalledID);
            }else{
                i_evaluation = Platform4.deserialize(i_marshalledEvaluation);
            }
        }
    }

	public void visit(Object obj) {
		QCandidate candidate = (QCandidate) obj;
		
		// force activation outside the try block
		// so any activation errors bubble up
		forceActivation(candidate); 
		
		try {
			Platform4.evaluationEvaluate(i_evaluation, candidate);
		} catch (Exception e) {
			candidate.include(false);
			// TODO: implement Exception callback for the user coder
			// at least for test cases
		}
		if (!candidate._include) {
			doNotInclude(candidate.getRoot());
		}
	}

	private void forceActivation(QCandidate candidate) {
		candidate.getObject();
	}

	boolean supportsIndex() {
		return false;
	}
}
