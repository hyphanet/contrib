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
import com.db4o.internal.handlers.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;
import com.db4o.query.*;
import com.db4o.reflect.*;
import com.db4o.typehandlers.*;

/**
 * Represents an actual object in the database. Forms a tree structure, indexed
 * by id. Can have dependents that are doNotInclude'd in the query result when
 * this is doNotInclude'd.
 * 
 * @exclude
 */
public class QCandidate extends TreeInt implements Candidate, Orderable {

	// db4o ID is stored in _key;

	// db4o byte stream storing the object
	ByteArrayBuffer _bytes;

	final QCandidates _candidates;

	// Dependant candidates
	private List4 _dependants;

	// whether to include in the result set
	// may use id for optimisation ???
	boolean _include = true;

	private Object _member;

	// Comparable
	Orderable _order;

	// Possible pending joins on children
	Tree _pendingJoins;

	// The evaluation root to compare all ORs
	private QCandidate _root;

	// the YapClass of this object
	ClassMetadata _yapClass;

	// temporary yapField and member for one field during evaluation
	FieldMetadata _yapField; // null denotes null object
    
    private int _handlerVersion;

	private QCandidate(QCandidates qcandidates) {
		super(0);
		_candidates = qcandidates;
	}

	public QCandidate(QCandidates candidates, Object obj, int id) {
		super(id);
		if (DTrace.enabled) {
			DTrace.CREATE_CANDIDATE.log(id);
		}
        _candidates = candidates;
		_order = this;
		_member = obj;
		_include = true;
        
        if(id == 0){
            _key = candidates.generateCandidateId();
        }
        if(Debug.queries){
            System.out.println("Candidate identified ID:" + _key );
        }
	}

	public Object shallowClone() {
		QCandidate qcan = new QCandidate(_candidates);
        qcan.setBytes(_bytes);
		qcan._dependants = _dependants;
		qcan._include = _include;
		qcan._member = _member;
		qcan._order = _order;
		qcan._pendingJoins = _pendingJoins;
		qcan._root = _root;
		qcan._yapClass = _yapClass;
		qcan._yapField = _yapField;

		return super.shallowCloneInternal(qcan);
	}

	void addDependant(QCandidate a_candidate) {
		_dependants = new List4(_dependants, a_candidate);
	}

	private void checkInstanceOfCompare() {
		if (_member instanceof Compare) {
			_member = ((Compare) _member).compare();
			LocalObjectContainer stream = container();
			_yapClass = stream.classMetadataForReflectClass(stream.reflector().forObject(_member));
			_key = stream.getID(transaction(), _member);
			if (_key == 0) {
				setBytes(null);
			} else {
				setBytes(stream.readReaderByID(transaction(), _key));
			}
		}
	}

	public int compare(Tree a_to) {
		return _order.compareTo(((QCandidate) a_to)._order);
	}
	
	public int compareTo(Object a_object) {
		if(a_object instanceof Order){
			return - ((Order)a_object).compareTo(this);
		}
		return _key - ((TreeInt) a_object)._key;
	}

	boolean createChild(final QCandidates a_candidates) {
		if (!_include) {
			return false;
		}

		if (_yapField != null) {
			TypeHandler4 handler = _yapField.getHandler();
			if (handler != null) {

			    final QueryingReadContext queryingReadContext = new QueryingReadContext(transaction(), marshallerFamily().handlerVersion(), _bytes); 
			    
				TypeHandler4 tempHandler = null;
				
				if(handler instanceof FirstClassHandler){
				    FirstClassHandler firstClassHandler = (FirstClassHandler) Handlers4.correctHandlerVersion(queryingReadContext, handler); 
				    tempHandler = Handlers4.correctHandlerVersion(queryingReadContext, firstClassHandler.readCandidateHandler(queryingReadContext));
				}

				if (tempHandler != null) {
				    
	                final TypeHandler4 arrayElementHandler = tempHandler;


					final int offset = queryingReadContext.offset();
					boolean outerRes = true;

					// The following construct is worse than not ideal.
					// For each constraint it completely reads the
					// underlying structure again. The structure could b
					// kept fairly easy. TODO: Optimize!

					Iterator4 i = a_candidates.iterateConstraints();
					while (i.moveNext()) {

						QCon qcon = (QCon) i.current();
						QField qf = qcon.getField();
						if (qf == null || qf.i_name.equals(_yapField.getName())) {

							QCon tempParent = qcon.i_parent;
							qcon.setParent(null);

							final QCandidates candidates = new QCandidates(
									a_candidates.i_trans, null, qf);
							candidates.addConstraint(qcon);

							qcon.setCandidates(candidates);
							
							readArrayCandidates(handler, queryingReadContext.buffer(), arrayElementHandler,
                                candidates);
							
							queryingReadContext.seek(offset);

							final boolean isNot = qcon.isNot();
							if (isNot) {
								qcon.removeNot();
							}

							candidates.evaluate();

							final Tree.ByRef pending = new Tree.ByRef();
							final boolean[] innerRes = { isNot };
							candidates.traverse(new Visitor4() {
								public void visit(Object obj) {

									QCandidate cand = (QCandidate) obj;

									if (cand.include()) {
										innerRes[0] = !isNot;
									}

									// Collect all pending subresults.

									if (cand._pendingJoins != null) {
										cand._pendingJoins
												.traverse(new Visitor4() {
													public void visit(
															Object a_object) {
														QPending newPending = ((QPending) a_object).internalClonePayload();

														// We need to change
														// the
														// constraint here, so
														// our
														// pending collector
														// uses
														// the right
														// comparator.
														newPending
																.changeConstraint();
														QPending oldPending = (QPending) Tree
																.find(
																		pending.value,
																		newPending);
														if (oldPending != null) {

															// We only keep one
															// pending result
															// for
															// all array
															// elements.
															// and memorize,
															// whether we had a
															// true or a false
															// result.
															// or both.

															if (oldPending._result != newPending._result) {
																oldPending._result = QPending.BOTH;
															}

														} else {
															pending.value = Tree
																	.add(
																			pending.value,
																			newPending);
														}
													}
												});
									}
								}
							});

							if (isNot) {
								qcon.not();
							}

							// In case we had pending subresults, we
							// need to communicate
							// them up to our root.
							if (pending.value != null) {
								pending.value.traverse(new Visitor4() {
									public void visit(Object a_object) {
										getRoot().evaluate((QPending) a_object);
									}
								});
							}

							if (!innerRes[0]) {

								if (Debug.queries) {
									System.out
											.println("  Array evaluation false. Constraint:"
													+ qcon.i_id);
								}

								// Again this could be double triggering.
								// 
								// We want to clean up the "No route"
								// at some stage.

								qcon.visit(getRoot(), qcon.evaluator().not(false));

								outerRes = false;
							}

							qcon.setParent(tempParent);

						}
					}

					return outerRes;
				}

				// We may get simple types here too, if the YapField was null
				// in the higher level simple evaluation. Evaluate these
				// immediately.

				if (Handlers4.handlesSimple(handler)) {
					a_candidates.i_currentConstraint.visit(this);
					return true;
				}
			}
		}
        
        if(_yapField == null || _yapField instanceof NullFieldMetadata){
            return false;
        }
        
        _yapClass.seekToField(transaction(), _bytes, _yapField);
        QCandidate candidate = readSubCandidate(a_candidates); 
		if (candidate == null) {
			return false;
		}

		// fast early check for YapClass
		if (a_candidates.i_yapClass != null
				&& a_candidates.i_yapClass.isStrongTyped()) {
			if (_yapField != null) {
				TypeHandler4 handler = _yapField.getHandler();
				if (handler instanceof ClassMetadata) {
					ClassMetadata classMetadata = (ClassMetadata) handler;
					if (classMetadata instanceof UntypedFieldHandler) {
						classMetadata = candidate.readYapClass();
					}
                    if(classMetadata == null){
                        return false;
                    }
                    if(! Handlers4.handlerCanHold(classMetadata, container().reflector(), a_candidates.i_yapClass.classReflector())){
                        return false;
                    }
				}
			}
		}

		addDependant(a_candidates.add(candidate));
		return true;
	}

    private void readArrayCandidates(TypeHandler4 fieldHandler, final ReadBuffer buffer,
        final TypeHandler4 arrayElementHandler, final QCandidates candidates) {
        if(! (arrayElementHandler instanceof FirstClassHandler)){
            return;
        }
        final SlotFormat slotFormat = SlotFormat.forHandlerVersion(_handlerVersion);
        slotFormat.doWithSlotIndirection(buffer, fieldHandler, new Closure4() {
            public Object run() {
                
                QueryingReadContext context = null;
                
                if(slotFormat.handleAsObject(arrayElementHandler)){
                    // TODO: Code is similar to FieldMetadata.collectIDs. Try to refactor to one place.
                    int collectionID = buffer.readInt();
                    ByteArrayBuffer arrayElementBuffer = container().readReaderByID(transaction(), collectionID);
                    ObjectHeader objectHeader = ObjectHeader.scrollBufferToContent(container(), arrayElementBuffer);
                    context = new QueryingReadContext(transaction(), candidates, _handlerVersion, arrayElementBuffer, collectionID);
                    objectHeader.classMetadata().collectIDs(context);
                    
                }else{
                    context = new QueryingReadContext(transaction(), candidates, _handlerVersion, buffer, 0);
                    ((FirstClassHandler)arrayElementHandler).collectIDs(context);
                }
                
                Tree.traverse(context.ids(), new Visitor4() {
                    public void visit(Object obj) {
                        TreeInt idNode = (TreeInt) obj;
                        candidates.add(new QCandidate(candidates, null, idNode._key));
                    }
                });
                
                Iterator4 i = context.objectsWithoutId();
                while(i.moveNext()){
                    Object obj = i.current();
                    candidates.add(new QCandidate(candidates, obj, 0));
                }
                
                return null;
            }
        
        });
    }

	void doNotInclude() {
		include(false);
		if (_dependants != null) {
			Iterator4 i = new Iterator4Impl(_dependants);
			_dependants = null;
			while (i.moveNext()) {
				((QCandidate) i.current()).doNotInclude();
			}
		}
	}

	public boolean duplicates() {
		return _order.hasDuplicates();
	}

	boolean evaluate(final QConObject a_constraint, final QE a_evaluator) {
		if (a_evaluator.identity()) {
			return a_evaluator.evaluate(a_constraint, this, null);
		}
		if (_member == null) {
			_member = value();
		}
		return a_evaluator.evaluate(a_constraint, this, a_constraint
				.translate(_member));
	}

	boolean evaluate(QPending a_pending) {

		if (Debug.queries) {
			System.out.println("Pending arrived Join: " + a_pending._join.i_id
					+ " Constraint:" + a_pending._constraint.i_id + " res:"
					+ a_pending._result);
		}

		QPending oldPending = (QPending) Tree.find(_pendingJoins, a_pending);

		if (oldPending == null) {
			a_pending.changeConstraint();
			_pendingJoins = Tree.add(_pendingJoins, a_pending.internalClonePayload());
			return true;
		} 
		_pendingJoins = _pendingJoins.removeNode(oldPending);
		oldPending._join.evaluatePending(this, oldPending, a_pending._result);
		return false;
	}

	ReflectClass classReflector() {
		readYapClass();
		if (_yapClass == null) {
			return null;
		}
		return _yapClass.classReflector();
	}
	
	boolean fieldIsAvailable(){
		return classReflector() != null;
	}

	// / ***<Candidate interface code>***

	public ObjectContainer objectContainer() {
		return container();
	}

	public Object getObject() {
		Object obj = value(true);
		if (obj instanceof ByteArrayBuffer) {
			ByteArrayBuffer reader = (ByteArrayBuffer) obj;
			int offset = reader._offset;
            obj = readString(reader); 
			reader._offset = offset;
		}
		return obj;
	}
	
	public String readString(ByteArrayBuffer buffer){
	    return StringHandler.readString(transaction().context(), buffer);
	}

	QCandidate getRoot() {
		return _root == null ? this : _root;
	}

	final LocalObjectContainer container() {
		return transaction().file();
	}

	final LocalTransaction transaction() {
		return _candidates.i_trans;
	}
	
	public boolean hasDuplicates() {

		// Subcandidates are evaluated along with their constraints
		// in one big QCandidates object. The tree can have duplicates
		// so evaluation can be cascaded up to different roots.

		return _root != null;
	}

	public void hintOrder(int a_order, boolean a_major) {
		if(_order == this){
			_order = new Order();
		}
		_order.hintOrder(a_order, a_major);
	}

	public boolean include() {
		return _include;
	}

	/**
	 * For external interface use only. Call doNotInclude() internally so
	 * dependancies can be checked.
	 */
	public void include(boolean flag) {
		// TODO:
		// Internal and external flag may need to be handled seperately.
		_include = flag;
		if(Debug.queries){
		    System.out.println("Candidate include " + flag + " ID: " + _key);
	    }

	}

	public void onAttemptToAddDuplicate(Tree a_tree) {
		_size = 0;
		_root = (QCandidate) a_tree;
	}

	private ReflectClass memberClass() {
		return transaction().reflector().forObject(_member);
	}

	
	PreparedComparison prepareComparison(ObjectContainerBase container, Object constraint) {
	    Context context = container.transaction().context();
	    
		if (_yapField != null) {
			return _yapField.prepareComparison(context, constraint);
		}
		if (_yapClass != null) {
			return _yapClass.prepareComparison(context, constraint);
		}
		Reflector reflector = container.reflector();
		ClassMetadata classMetadata = null;
		if (_bytes != null) {
			classMetadata = container.produceClassMetadata(reflector.forObject(constraint));
		} else {
			if (_member != null) {
				classMetadata = container.classMetadataForReflectClass(reflector.forObject(_member));
			}
		}
		if (classMetadata != null) {
			if (_member != null && _member.getClass().isArray()) {
				TypeHandler4 arrayElementTypehandler = classMetadata.typeHandler(); 
				if (reflector.array().isNDimensional(memberClass())) {
					MultidimensionalArrayHandler mah = 
						new MultidimensionalArrayHandler(arrayElementTypehandler, false);
					return mah.prepareComparison(context, _member);
				} 
				ArrayHandler ya = new ArrayHandler(arrayElementTypehandler, false);
				return ya.prepareComparison(context, _member);
			} 
			return classMetadata.prepareComparison(context, constraint);
		}
		return null;
	}


	private void read() {
		if (_include) {
			if (_bytes == null) {
				if (_key > 0) {
					if (DTrace.enabled) {
						DTrace.CANDIDATE_READ.log(_key);
					}
                    setBytes(container().readReaderByID(transaction(), _key));
					if (_bytes == null) {
						include(false);
					}
				} else {
				    include(false);
				}
			}
		}
	}
	
	private int currentOffSet(){
	    return _bytes._offset;
	}

	private QCandidate readSubCandidate(QCandidates candidateCollection) {
		read();
		if (_bytes == null || _yapField == null) {
		    return null;
		}
		final int offset = currentOffSet();
        QueryingReadContext context = newQueryingReadContext();
        TypeHandler4 handler = Handlers4.correctHandlerVersion(context, _yapField.getHandler());
        QCandidate subCandidate = candidateCollection.readSubCandidate(context, handler);
		seek(offset);
		if (subCandidate != null) {
			subCandidate._root = getRoot();
			return subCandidate;
		}
		return null;
	}
	
	private void seek(int offset){
	    _bytes._offset = offset;
	}

    private QueryingReadContext newQueryingReadContext() {
        return new QueryingReadContext(transaction(), _handlerVersion, _bytes);
    }

	private void readThis(boolean a_activate) {
		read();

		final ObjectContainerBase container = transaction().container();
		
		_member = container.getByID(transaction(), _key);
		if (_member != null && (a_activate || _member instanceof Compare)) {
			container.activate(transaction(), _member);
			checkInstanceOfCompare();
		}
	}

	ClassMetadata readYapClass() {
		if (_yapClass == null) {
			read();
			if (_bytes != null) {
			    seek(0);
                ObjectContainerBase stream = container();
                ObjectHeader objectHeader = new ObjectHeader(stream, _bytes);
				_yapClass = objectHeader.classMetadata();
                
				if (_yapClass != null) {
					if (stream._handlers.ICLASS_COMPARE
							.isAssignableFrom(_yapClass.classReflector())) {
						readThis(false);
					}
				}
			}
		}
		return _yapClass;
	}

	public String toString() {
		String str = "QCandidate ";
		if (_yapClass != null) {
			str += "\n   YapClass " + _yapClass.getName();
		}
		if (_yapField != null) {
			str += "\n   YapField " + _yapField.getName();
		}
		if (_member != null) {
			str += "\n   Member " + _member.toString();
		}
		if (_root != null) {
			str += "\n  rooted by:\n";
			str += _root.toString();
		} else {
			str += "\n  ROOT";
		}
		return str;
	}

	void useField(QField a_field) {
		read();
		if (_bytes == null) {
			_yapField = null;
            return;
		} 
		readYapClass();
		_member = null;
		if (a_field == null) {
			_yapField = null;
            return;
		} 
		if (_yapClass == null) {
			_yapField = null;
            return;
		} 
		_yapField = a_field.getYapField(_yapClass);
		if(_yapField == null){
		    fieldNotFound();
		    return;
		}
        
		HandlerVersion handlerVersion = _yapClass.seekToField(transaction(), _bytes, _yapField);
        
		if (handlerVersion == HandlerVersion.INVALID ) {
		    fieldNotFound();
		    return;
		}
		
		_handlerVersion = handlerVersion._number;
	}
	
	private void fieldNotFound(){
        if (_yapClass.holdsAnyClass()) {
            // retry finding the field on reading the value 
            _yapField = null;
        } else {
            // we can't get a value for the field, comparisons should definitely run against null
            _yapField = new NullFieldMetadata();
        }
        _handlerVersion = HandlerRegistry.HANDLER_VERSION;  
	}
	

	Object value() {
		return value(false);
	}

	// TODO: This is only used for Evaluations. Handling may need
	// to be different for collections also.
	Object value(boolean a_activate) {
		if (_member == null) {
			if (_yapField == null) {
				readThis(a_activate);
			} else {
				int offset = currentOffSet();
				_member = _yapField.read(newQueryingReadContext());
				seek(offset);
				checkInstanceOfCompare();
			}
		}
		return _member;
	}
    
    void setBytes(ByteArrayBuffer bytes){
        _bytes = bytes;
    }
    
    private MarshallerFamily marshallerFamily(){
        return MarshallerFamily.version(_handlerVersion);
    }

    
}
