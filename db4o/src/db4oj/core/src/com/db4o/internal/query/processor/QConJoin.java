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
import com.db4o.foundation.*;
import com.db4o.internal.*;


/**
 * 
 * Join constraint on queries
 * 
 * @exclude
 */
public class QConJoin extends QCon {
	
	// FIELDS MUST BE PUBLIC TO BE REFLECTED ON UNDER JDK <= 1.1

	public boolean i_and;
	public QCon i_constraint1;
	public QCon i_constraint2;
	
	
	public QConJoin(){
		// C/S
	}

	QConJoin(Transaction a_trans, QCon a_c1, QCon a_c2, boolean a_and) {
		super(a_trans);
		i_constraint1 = a_c1;
		i_constraint2 = a_c2;
		i_and = a_and;
	}

	void doNotInclude(QCandidate a_root) {
		i_constraint1.doNotInclude(a_root);
		i_constraint2.doNotInclude(a_root);
	}

	void exchangeConstraint(QCon a_exchange, QCon a_with) {
		super.exchangeConstraint(a_exchange, a_with);
		if (a_exchange == i_constraint1) {
			i_constraint1 = a_with;
		}
		if (a_exchange == i_constraint2) {
			i_constraint2 = a_with;
		}
	}

	void evaluatePending(
		QCandidate a_root,
		QPending a_pending,
		int a_secondResult) {

		boolean res =
			i_evaluator.not(
				i_and
					? ((a_pending._result + a_secondResult) > 0)
					: (a_pending._result + a_secondResult) > -4);
					
		if (hasJoins()) {
			Iterator4 i = iterateJoins();
			while (i.moveNext()) {
				QConJoin qcj = (QConJoin) i.current();
				if (Debug.queries) {
					System.out.println(
						"QConJoin creates pending this:"
							+ i_id
							+ " Join:"
							+ qcj.i_id
							+ " res:"
							+ res);
				}
				a_root.evaluate(new QPending(qcj, this, res));
			}
		} else {
			if (!res) {
				if (Debug.queries) {
					System.out.println(
						"QConJoin evaluatePending FALSE "
							+ i_id
							+ " Calling: "
							+ i_constraint1.i_id
							+ ", "
							+ i_constraint2.i_id);
				}
				i_constraint1.doNotInclude(a_root);
				i_constraint2.doNotInclude(a_root);
			}else{
				if (Debug.queries) {
					System.out.println(
						"QConJoin evaluatePending TRUE "
							+ i_id
							+ " NOT calling: "
							+ i_constraint1.i_id
							+ ", "
							+ i_constraint2.i_id);
				}
			}

		}
	}

	public QCon getOtherConstraint(QCon a_constraint) {
		if (a_constraint == i_constraint1) {
			return i_constraint2;
		} else if (a_constraint == i_constraint2) {
			return i_constraint1;
		}
		throw new IllegalArgumentException();
	}
	
	String logObject(){
		if (Debug.queries) {
			String msg = i_and ? "&" : "|";
			return " " + i_constraint1.i_id + msg + i_constraint2.i_id;
		}
		return "";
	}
	
	
	boolean removeForParent(QCon a_constraint) {
		if (i_and) {
			QCon other = getOtherConstraint(a_constraint);
			other.removeJoin(this); // prevents circular call
			other.remove();
			return true;
		}
		return false;
	}
	
	public String toString(){
		String str = "QConJoin " + (i_and ? "AND ": "OR");
		if(i_constraint1 != null){
			str += "\n   " + i_constraint1;  
		}
		if(i_constraint2 != null){
			str += "\n   " + i_constraint2;  
		}
		return str;
	}

	public boolean isOr() {
		return !i_and;
	}
	

}
