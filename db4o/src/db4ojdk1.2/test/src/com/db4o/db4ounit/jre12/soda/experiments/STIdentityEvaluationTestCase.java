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
package com.db4o.db4ounit.jre12.soda.experiments;
import com.db4o.*;
import com.db4o.query.*;



public class STIdentityEvaluationTestCase extends com.db4o.db4ounit.common.soda.util.SodaBaseTestCase{
    
    public Object[] createData() {
        
        Helper helperA = new Helper("aaa");
        
        return new Object[] {
            new STIdentityEvaluationTestCase(null),
            new STIdentityEvaluationTestCase(helperA),
            new STIdentityEvaluationTestCase(helperA),
            new STIdentityEvaluationTestCase(helperA),
            new STIdentityEvaluationTestCase(new HelperDerivate("bbb")),
            new STIdentityEvaluationTestCase(new Helper("dod"))
            };
    }
    
    public Helper helper;
    
    public STIdentityEvaluationTestCase(){
    }
    
    public STIdentityEvaluationTestCase(Helper h){
        this.helper = h;
    }
    
    public void test(){
        Query q = newQuery();
        
        q.constrain(new Helper("aaa"));
        ObjectSet os = q.execute();
        Helper helperA = (Helper)os.next();
        q = newQuery();
        q.constrain(STIdentityEvaluationTestCase.class);
        q.descend("helper").constrain(helperA).identity();
        q.constrain(new AcceptAllEvaluation());
        expect(q, new int[] {1, 2, 3});
    }
    
    public void testMemberClassConstraint(){
        Query q = newQuery();
        
        q.constrain(STIdentityEvaluationTestCase.class);
        q.descend("helper").constrain(HelperDerivate.class);
        expect(q, new int[] {4});
    }
    
    public static class AcceptAllEvaluation implements Evaluation {
		public void evaluate(Candidate candidate) {
		    candidate.include(true);
		}
	}

	public static class Helper{
        
        public String hString;
        
        public Helper(){
        }
        
        public Helper(String str){
            hString = str;
        }
    }
    
    public static class HelperDerivate extends Helper{
        public HelperDerivate(){
        }
        
        public HelperDerivate(String str){
            super(str);
        }
        
    }
    
}
