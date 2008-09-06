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
package com.db4o.db4ounit.jre12.blobs;

import java.io.*;

import com.db4o.ext.*;
import com.db4o.foundation.io.*;
import com.db4o.types.*;

import db4ounit.extensions.*;
import db4ounit.extensions.util.*;

public class BlobThreadCloseTestCase extends Db4oClientServerTestCase {

	public static void main(String[] args) {
		new BlobThreadCloseTestCase().runClientServer();
	}

	private static final String TEST_FILE = "test.db4o";
	
	private static class Data {
		private Blob _blob;

		public Data() {
			_blob = null;
		}

		public Blob blob() {
			return _blob;
		}
	}

	protected void db4oTearDownAfterClean() throws Exception {
		File4.delete(TEST_FILE);
		IOUtil.deleteDir("blobs");
	}

	public void test() throws Exception {
		if (isEmbeddedClientServer()) {
			return;
		}
		((ExtClient) db()).switchToFile(TEST_FILE);
		store(new Data());
//		((ExtClient) db()).switchToFile("test.yap");

		Data data = (Data) retrieveOnlyInstance(Data.class);
		data.blob().readFrom(
				new File(BlobThreadCloseTestCase.class.getResource(
						"BlobThreadCloseTestCase.class").getFile()));
		while (data.blob().getStatus() > Status.COMPLETED) {
			Thread.sleep(50);
		}
	}
}