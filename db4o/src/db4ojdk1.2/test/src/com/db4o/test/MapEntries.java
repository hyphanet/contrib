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

import java.io.*;
import java.util.*;

import com.db4o.*;
import com.db4o.test.types.*;

public class MapEntries {
	
	static String FILE = "hm.yap";
	
	HashMap hm;

	public static void main(String[] args) {
		// createAndDelete();
		
		set();
		check();
		LogAll.run(FILE);
		update();
		check();
		LogAll.run(FILE);
	}
	
	static void createAndDelete(){
		new File(FILE).delete();
		ObjectContainer con = Db4o.openFile(FILE);
		HashMap map = new HashMap();
		map.put("delme", new Integer(99));
		con.store(map);
		con.close();
		con = Db4o.openFile(FILE);
		con.delete(con.queryByExample(new HashMap()).next());
		con.close();
		LogAll.run(FILE);
	}
	
	static void check(){
		ObjectContainer con = Db4o.openFile(FILE);
		System.out.println("Entry elements: " + con.queryByExample(new com.db4o.config.Entry()).size());
		con.close();
	}
	
	static void set(){
		new File(FILE).delete();
		ObjectContainer con = Db4o.openFile(FILE);
		MapEntries me = new MapEntries();
		me.hm = new HashMap();
		me.hm.put("t1", new ObjectSimplePublic());
		me.hm.put("t2", new ObjectSimplePublic());
		con.store(me);
		con.close();
	}
	
	static void update(){
		ObjectContainer con = Db4o.openFile(FILE);
		ObjectSet set = con.queryByExample(new MapEntries());
		while(set.hasNext()){
			MapEntries me = (MapEntries)set.next();
			me.hm.put("t1", new Integer(100));
			con.store(me.hm);
		}
		con.close();
	}
	
	
}
