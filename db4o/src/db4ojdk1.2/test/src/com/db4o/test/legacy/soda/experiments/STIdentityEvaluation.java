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
package com.db4o.test.legacy.soda.experiments;

import com.db4o.*;
import com.db4o.query.*;
import com.db4o.test.legacy.soda.*;
import com.db4o.test.legacy.soda.experiments.*;

public class STIdentityEvaluation implements STClass1{
    
    public static transient SodaTest st;
    
    public Object[] store() {
        
        Helper helperA = new Helper("aaa");
        
        return new Object[] {
            new STIdentityEvaluation(null),
            new STIdentityEvaluation(helperA),
            new STIdentityEvaluation(helperA),
            new STIdentityEvaluation(helperA),
            new STIdentityEvaluation(new HelperDerivate("bbb")),
            new STIdentityEvaluation(new Helper("dod"))
            };
    }
    
    public Helper helper;
    
    public STIdentityEvaluation(){
    }
    
    public STIdentityEvaluation(Helper h){
        this.helper = h;
    }
    
    public void test(){
        Query q = st.query();
        Object[] r = store();
        q.constrain(new Helper("aaa"));
        ObjectSet os = q.execute();
        Helper helperA = (Helper)os.next();
        q = st.query();
        q.constrain(STIdentityEvaluation.class);
        q.descend("helper").constrain(helperA).identity();
        q.constrain(new Evaluation() {
            public void evaluate(Candidate candidate) {
                candidate.include(true);
            }
        });
        st.expect(q,new Object[]{r[1], r[2], r[3]});
    }
    
    public void testMemberClassConstraint(){
        Query q = st.query();
        Object[] r = store();
        q.constrain(STIdentityEvaluation.class);
        q.descend("helper").constrain(HelperDerivate.class);
        st.expect(q,new Object[]{r[4]});
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
