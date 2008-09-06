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

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.marshall.*;
import com.db4o.query.*;
import com.db4o.reflect.*;


/**
 * Object constraint on queries
 *
 * @exclude
 */
public class QConObject extends QCon {

    // the constraining object
    public Object                        i_object;

    // cache for the db4o object ID
    public int                           i_objectID;

    // the YapClass
    transient ClassMetadata            i_yapClass;

    // needed for marshalling the request
    public int                           i_yapClassID;

    public QField                        i_field;

    transient PreparedComparison _preparedComparison;

    public ObjectAttribute               i_attributeProvider;

    private transient boolean     i_selfComparison = false;

    public QConObject() {
        // C/S only
    }

    public QConObject(Transaction a_trans, QCon a_parent, QField a_field,
        Object a_object) {
        super(a_trans);
        i_parent = a_parent;
        if (a_object instanceof Compare) {
            a_object = ((Compare) a_object).compare();
        }
        i_object = a_object;
        i_field = a_field;
    }

    private void associateYapClass(Transaction a_trans, Object a_object) {
        if (a_object == null) {
            //It seems that we need not result the following field
            //i_object = null;
            //i_comparator = Null.INSTANCE;
            //i_yapClass = null;
            
            // FIXME: Setting the YapClass to null will prevent index use
            // If the field is typed we can guess the right one with the
            // following line. However this does break some SODA test cases.
            // Revisit!
            
//            if(i_field != null){
//                i_yapClass = i_field.getYapClass();
//            }
            
        } else {
            i_yapClass = a_trans.container()
                .produceClassMetadata(a_trans.reflector().forObject(a_object));
            if (i_yapClass != null) {
                i_object = i_yapClass.getComparableObject(a_object);
                if (a_object != i_object) {
                    i_attributeProvider = i_yapClass.config().queryAttributeProvider();
                    i_yapClass = a_trans.container().produceClassMetadata(a_trans.reflector().forObject(i_object));
                }
                if (i_yapClass != null) {
                    i_yapClass.collectConstraints(a_trans, this, i_object,
                        new Visitor4() {

                            public void visit(Object obj) {
                                addConstraint((QCon) obj);
                            }
                        });
                } else {
                    associateYapClass(a_trans, null);
                }
            } else {
                associateYapClass(a_trans, null);
            }
        }
    }
    
    public boolean canBeIndexLeaf(){
        return (i_yapClass != null && i_yapClass.isPrimitive()) || evaluator().identity();
    }
    
    public boolean canLoadByIndex(){
        if(i_field == null){
            return false;
        }
        if(i_field.i_yapField == null){
            return false;
        }
        if(! i_field.i_yapField.hasIndex()){
            return false;
        }
        if (!i_evaluator.supportsIndex()) {
        	return false;
        }
        
        return i_field.i_yapField.canLoadByIndex();
    }

    boolean evaluate(QCandidate a_candidate) {
        try {
            return a_candidate.evaluate(this, i_evaluator);
        } catch (Exception e) {
        	if (Debug.atHome) {
				e.printStackTrace();
			}
            return false;
        }
    }

    void evaluateEvaluationsExec(final QCandidates a_candidates,
        boolean rereadObject) {
        if (i_field.isSimple()) {
            boolean hasEvaluation = false;
            Iterator4 i = iterateChildren();
            while (i.moveNext()) {
                if (i.current() instanceof QConEvaluation) {
                    hasEvaluation = true;
                    break;
                }
            }
            if (hasEvaluation) {
                a_candidates.traverse(i_field);
                Iterator4 j = iterateChildren();
                while (j.moveNext()) {
                    ((QCon) j.current()).evaluateEvaluationsExec(a_candidates,false);
                }
            }
        }
    }

    void evaluateSelf() {
        if(DTrace.enabled){
            DTrace.EVALUATE_SELF.log(i_id);
        }
        if (i_yapClass != null) {
            if (!(i_yapClass instanceof PrimitiveFieldHandler)) {
                if (!i_evaluator.identity()) {
//                	TODO: consider another strategy to avoid reevaluating the class constraint when
//                	the candidate collection is loaded from the class index
//                    if (i_yapClass == i_candidates.i_yapClass) {
//                        if (i_evaluator.isDefault() && (! hasJoins())) {
//                            return;
//                        }
//                    }
                    i_selfComparison = true;
                }
                Object transactionalObject = i_yapClass.wrapWithTransactionContext(transaction(), i_object);
                _preparedComparison = i_yapClass.prepareComparison(context(), transactionalObject);
            }
        }
        super.evaluateSelf();
        i_selfComparison = false;
    }

    private Context context() {
        return transaction().context();
    }

    void collect(QCandidates a_candidates) {
        if (i_field.isClass()) {
            a_candidates.traverse(i_field);
            a_candidates.filter(i_candidates);
        }
    }

    void evaluateSimpleExec(QCandidates a_candidates) {
    	
    	// TODO: The following can be skipped if we used the index on
    	//       this field to load the objects, if hasOrdering() is false
    	
    	if (i_field.isSimple() || isNullConstraint()) {
        	a_candidates.traverse(i_field);
            prepareComparison(i_field);
            a_candidates.filter(this);
    	}
    }
    
    PreparedComparison prepareComparison(QCandidate candidate){
    	if(_preparedComparison != null){
    		return _preparedComparison; 
    	}
    	return candidate.prepareComparison(container(), i_object);
    }

    ClassMetadata getYapClass() {
        return i_yapClass;
    }

    public QField getField() {
        return i_field;
    }

    int getObjectID() {
        if (i_objectID == 0) {
            i_objectID = i_trans.container().getID(i_trans, i_object);
            if (i_objectID == 0) {
                i_objectID = -1;
            }
        }
        return i_objectID;
    }

    public boolean hasObjectInParentPath(Object obj) {
        if (obj == i_object) {
            return true;
        }
        return super.hasObjectInParentPath(obj);
    }

    public int identityID() {
        if (i_evaluator.identity()) {
            int id = getObjectID();
            if (id != 0) {
                if( !(i_evaluator instanceof QENot) ){
                    return id;
                }
            }
        }
        return 0;
    }
    
    boolean isNullConstraint() {
        return i_object == null;
    }

    void log(String indent) {
        if (Debug.queries) {
            super.log(indent);
        }
    }

    String logObject() {
        if (Debug.queries) {
            if (i_object != null) {
                return i_object.toString();
            }
            return "[NULL]";
        } 
        return "";
    }

    void marshall() {
        super.marshall();
        getObjectID();
        if (i_yapClass != null) {
            i_yapClassID = i_yapClass.getID();
        }
    }
    
    public boolean onSameFieldAs(QCon other){
        if(! (other instanceof QConObject)){
            return false;
        }
        return i_field == ((QConObject)other).i_field;
    }

    void prepareComparison(QField a_field) {
        if (isNullConstraint() & !a_field.isArray()) {
            _preparedComparison = Null.INSTANCE;
        } else {
            _preparedComparison = a_field.prepareComparison(context(), i_object);
        }
    }

    void removeChildrenJoins() {
        super.removeChildrenJoins();
        _children = null;
    }

    QCon shareParent(Object a_object, boolean[] removeExisting) {
        if(i_parent == null){
            return null;
        }
        Object obj = i_field.coerce(a_object);
        if(obj == No4.INSTANCE){
            return null;
        }
        return i_parent.addSharedConstraint(i_field, obj);
    }

    QConClass shareParentForClass(ReflectClass a_class, boolean[] removeExisting) {
        if(i_parent == null){
            return null;
        }
        if (! i_field.canHold(a_class)) {
            return null;
        }
        QConClass newConstraint = new QConClass(i_trans, i_parent,i_field, a_class);
        i_parent.addConstraint(newConstraint);
        return newConstraint;
    }

    final Object translate(Object candidate) {
        if (i_attributeProvider != null) {
            i_candidates.i_trans.container().activate(i_candidates.i_trans, candidate);
            return i_attributeProvider.attribute(candidate);
        }
        return candidate;
    }

    void unmarshall(Transaction trans) {
        if (i_trans == null) {
            super.unmarshall(trans);

            if (i_object == null) {
                _preparedComparison = Null.INSTANCE;
            }
            if (i_yapClassID != 0) {
                i_yapClass = trans.container().classMetadataForId(i_yapClassID);
            }
            if (i_field != null) {
                i_field.unmarshall(trans);
            }
            
            if(i_objectID > 0){
                Object obj = trans.container().getByID(trans, i_objectID);
                if(obj != null){
                    i_object = obj;
                }
            }
        }
    }

    public void visit(Object obj) {
        QCandidate qc = (QCandidate) obj;
        boolean res = true;
        boolean processed = false;
        if (i_selfComparison) {
            ClassMetadata yc = qc.readYapClass();
            if (yc != null) {
                res = i_evaluator
                    .not(i_yapClass.getHigherHierarchy(yc) == i_yapClass);
                processed = true;
            }
        }
        if (!processed) {
            res = evaluate(qc);
        }
        if (hasOrdering() && res && qc.fieldIsAvailable()) {
            Object cmp = qc.value();
            if (cmp != null && i_field != null) {
                PreparedComparison preparedComparisonBackup = _preparedComparison;
                _preparedComparison = i_field.prepareComparison(context(), qc.value());
                i_candidates.addOrder(new QOrder(this, qc));
                _preparedComparison = preparedComparisonBackup;
            }
        }
        visit1(qc.getRoot(), this, res);
    }

    public Constraint contains() {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEContains(true));
            return this;
        }
    }

    public Constraint equal() {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEEqual());
            return this;
        }
    }

    public Object getObject() {
        synchronized (streamLock()) {
            return i_object;
        }
    }

    public Constraint greater() {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEGreater());
            return this;
        }
    }

    public Constraint identity() {
        synchronized (streamLock()) {

        	if(i_object==null) {
        		return this;
        	}
        	
            int id = getObjectID();
            if(id <= 0){
                i_objectID = 0;
                Exceptions4.throwRuntimeException(51);
            }
            
            // TODO: this may not be correct for NOT
            // It may be necessary to add an if(i_evaluator.identity())
            removeChildrenJoins();
            i_evaluator = i_evaluator.add(new QEIdentity());
            return this;
        }
    }

    public Constraint byExample() {
        synchronized (streamLock()) {
            associateYapClass(i_trans, i_object);
            return this;
        }
    }
    
    /*
     * if the i_object is stored in db4o, set the evaluation mode as identity, 
     * otherwise, set the evaluation mode as example.
     */
    void setEvaluationMode() {
        if ((i_object == null) || evaluationModeAlreadySet()) {
            return;
        }

        int id = getObjectID();
        if (id < 0) {
            byExample();
        } else {
            i_yapClass = i_trans.container().produceClassMetadata(
                    i_trans.reflector().forObject(i_object));
            identity();
        }
    }
    
    boolean evaluationModeAlreadySet(){
        return i_yapClass != null;
    }
    
    public Constraint like() {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEContains(false));
            return this;
        }
    }

    public Constraint smaller() {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QESmaller());
            return this;
        }
    }

    public Constraint startsWith(boolean caseSensitive) {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEStartsWith(caseSensitive));
            return this;
        }
    }

    public Constraint endsWith(boolean caseSensitive) {
        synchronized (streamLock()) {
            i_evaluator = i_evaluator.add(new QEEndsWith(caseSensitive));
            return this;
        }
    }

    public String toString() {
        String str = "QConObject ";
        if (i_object != null) {
            str += i_object.toString();
        }
        return str;
    }
}