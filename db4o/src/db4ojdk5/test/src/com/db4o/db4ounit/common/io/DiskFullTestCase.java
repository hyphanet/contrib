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
package com.db4o.db4ounit.common.io;

import java.io.*;

public class DiskFullTestCase extends DiskFullTestCaseBase {

	private static final long NO_SIZE_LIMIT = -1;

	public void testReleasesFileLocks() {
		assertReleasesFileLocks(false);
	}

	public void testReleasesFileLocksWithCache() {
		assertReleasesFileLocks(false);
	}

	public void testKeepsCommittedDataReadOnlyLimited() {
		assertKeepsCommittedDataReadOnlyLimited(false);
	}

	public void testKeepsCommittedDataReadOnlyLimitedWithCache() {
		assertKeepsCommittedDataReadOnlyLimited(true);
	}

	public void testKeepsCommittedDataReadWriteUnlimited() {
		assertKeepsCommittedDataReadWriteUnlimited(false);
	}

	public void testKeepsCommittedDataReadWriteUnlimitedWithCache() {
		assertKeepsCommittedDataReadWriteUnlimited(true);
	}

	private void assertReleasesFileLocks(boolean doCache) {
		openDatabase(NO_SIZE_LIMIT, false, doCache);
		triggerDiskFullAndClose();
		openDatabase(NO_SIZE_LIMIT, true, false);
		closeDb();
	}

	private void assertKeepsCommittedDataReadOnlyLimited(boolean doCache) {
		storeOneAndFail(NO_SIZE_LIMIT, doCache);
		assertItemsStored(1, curFileLength(), true);
	}

	private void assertKeepsCommittedDataReadWriteUnlimited(boolean doCache) {
		storeOneAndFail(NO_SIZE_LIMIT, doCache);
		assertItemsStored(1, NO_SIZE_LIMIT, false);
	}

	@Override
	protected void configureForFailure(ThrowCondition condition) {
		((LimitedSizeThrowCondition)condition).size(curFileLength());
	}

	@Override
	protected ThrowCondition createThrowCondition(Object conditionConfig) {
		return new LimitedSizeThrowCondition((Long) conditionConfig);
	}
	
	private long curFileLength() {
		return new File(FILENAME).length();
	}

}
