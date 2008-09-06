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
package com.db4o.test;

import java.util.*;

public class PrimitiveArrayFileSize {
	
	Object arr;
	
	public void testSimpleLongInObject(){
		int call = 0;
		PrimitiveArrayFileSize pafs = new PrimitiveArrayFileSize();
		for (int i = 0; i < 12; i++) {
			pafs.arr = new long[100];
			Test.store(pafs);
			checkFileSize(call++);
			Test.commit();
			checkFileSize(call++);
		}
	}
	
	public void testLongWrapperInObject(){
		int call = 0;
		PrimitiveArrayFileSize pafs = new PrimitiveArrayFileSize();
		for (int i = 0; i < 12; i++) {
			pafs.arr = longWrapperArray();
			Test.store(pafs);
			checkFileSize(call++);
			Test.commit();
			checkFileSize(call++);
		}
	}
	
	public void testSimpleLongInHashMap(){
		HashMap hm = new HashMap();
		int call = 0;
		for (int i = 0; i < 12; i++) {
			long[] lll = new long[100];
			lll[0] = 99999;
			hm.put("test", lll);
			Test.store(hm);
			checkFileSize(call++);
			Test.commit();
			checkFileSize(call++);
		}
	}
	
	public void testLongWrapperInHashMap(){
		HashMap hm = new HashMap();
		int call = 0;
		for (int i = 0; i < 12; i++) {
			hm.put("test", longWrapperArray());
			Test.store(hm);
			checkFileSize(call++);
			Test.commit();
			checkFileSize(call++);
		}
	}
	
	
	private Long[] longWrapperArray(){
		Long[] larr = new Long[100];
		for (int j = 0; j < larr.length; j++) {
			larr[j] = new Long(j);
		}
		return larr;
	}
	
	
	
	private void checkFileSize(int call){
		if(Test.canCheckFileSize()){
			int newFileLength = Test.fileLength();
			
			// Interesting for manual tests:
			// System.out.println(newFileLength);
			
			if(call == 6){
				// consistency reached, start testing
				jumps = 0;
				fileLength = newFileLength;
			}else if(call > 6){
				if(newFileLength > fileLength){
					if(jumps < 4){
						fileLength = newFileLength;
						jumps ++;
						// allow two further step in size
						// may be necessary for commit space extension
					}else{
						// now we want constant behaviour
						Test.error();
					}
				}
			}
		}
	}
	
	private static transient int fileLength;
	private static transient int jumps; 
	
	
}

