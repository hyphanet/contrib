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

import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.io.*;
import com.db4o.types.*;

import db4ounit.*;
import db4ounit.extensions.*;
import db4ounit.extensions.fixtures.*;
import db4ounit.extensions.util.*;

public class ExternalBlobsTestCase extends AbstractDb4oTestCase implements OptOutDefragSolo {

	public static void main(String[] args) {
		new ExternalBlobsTestCase().runClientServer();
	}
	
	private static final String BLOB_PATH = Path4.combine(Path4.getTempPath(), "db4oTestBlobs");
	private static final String BLOB_FILE_IN = BLOB_PATH + "/regressionBlobIn.txt"; 
	private static final String BLOB_FILE_OUT = BLOB_PATH + "/regressionBlobOut.txt"; 

	private static class Data {
		private Blob _blob;

		public Data() {
			_blob = null;
		}
		
		public Blob blob() {
			return _blob;
		}
	}

	protected void db4oSetupBeforeStore() throws Exception {
		deleteFiles();
		File4.mkdirs(BLOB_PATH);
	}
	
	protected void db4oTearDownAfterClean() throws Exception {
        deleteFiles();
	}
	
	protected void configure(Configuration config) throws IOException {
		config.setBlobPath(BLOB_PATH);
	}

	protected void store() throws Exception {
		store(new Data());
	}
	
	public void test() throws Exception {
		Data data = (Data) retrieveOnlyInstance(Data.class);
		Assert.isTrue(new File(BLOB_PATH).exists());
		char[] chout = new char[] { 'H', 'i', ' ', 'f', 'o', 'l', 'k', 's' };
		FileWriter fw = new FileWriter(BLOB_FILE_IN);
		fw.write(chout);
		fw.flush();
		fw.close();
		data.blob().readFrom(new File(BLOB_FILE_IN));
		double status = data.blob().getStatus();
		while (status > Status.COMPLETED) {
			Thread.sleep(50);
			status = data.blob().getStatus();
		}

		data.blob().writeTo(new File(BLOB_FILE_OUT));
		status = data.blob().getStatus();
		while (status > Status.COMPLETED) {
			Thread.sleep(50);
			status = data.blob().getStatus();
		}
		File resultingFile = new File(BLOB_FILE_OUT);
		Assert.isTrue(resultingFile.exists());

		FileReader fr = new FileReader(resultingFile);
		char[] chin = new char[chout.length];
		fr.read(chin);
		fr.close();
		ArrayAssert.areEqual(chout, chin);
		
		Assert.areEqual(Status.COMPLETED, data.blob().getStatus());
		data.blob().deleteFile();
		Assert.areEqual(Status.UNUSED, data.blob().getStatus());
	}

	private void deleteFiles() throws IOException {
		IOUtil.deleteDir(BLOB_PATH);
	}
}
