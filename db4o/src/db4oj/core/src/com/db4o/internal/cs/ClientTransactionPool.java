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
package com.db4o.internal.cs;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;

public class ClientTransactionPool {

	private final Hashtable4 _transaction2Container; // Transaction -> ContainerCount
	
	private final Hashtable4 _fileName2Container; // String -> ContainerCount
	
	private final LocalObjectContainer _mainContainer;
	
	private boolean _closed;
		
	public ClientTransactionPool(LocalObjectContainer mainContainer) {
		ContainerCount mainEntry = new ContainerCount(mainContainer, 1);
		_transaction2Container = new Hashtable4();
		_fileName2Container = new Hashtable4();
		_fileName2Container.put(mainContainer.fileName(), mainEntry);
		_mainContainer = mainContainer;
	}

	public Transaction acquireMain() {
		return acquire(_mainContainer.fileName());
	}

	public Transaction acquire(String fileName) {
		synchronized(_mainContainer.lock()) {
			ContainerCount entry = (ContainerCount) _fileName2Container.get(fileName);
			if (entry == null) {
				LocalObjectContainer container = (LocalObjectContainer) Db4o.openFile(fileName);
		        container.configImpl().setMessageRecipient(_mainContainer.configImpl().messageRecipient());
				entry = new ContainerCount(container);
				_fileName2Container.put(fileName, entry);
			}
			Transaction transaction = entry.newTransaction();
			_transaction2Container.put(transaction, entry);
			return transaction;
		}
	}
	
	public void release(Transaction transaction, boolean rollbackOnClose) {
		transaction.close(rollbackOnClose);
		synchronized(_mainContainer.lock()) {
			ContainerCount entry = (ContainerCount) _transaction2Container.get(transaction);
			_transaction2Container.remove(transaction);
			entry.release();
			if(entry.isEmpty()) {
				_fileName2Container.remove(entry.fileName());
				entry.close();
			}
		}
	}
	
	public void close() {
		synchronized(_mainContainer.lock()) {
			Iterator4 entryIter = _fileName2Container.iterator();
			while(entryIter.moveNext()) {
				Entry4 hashEntry = (Entry4) entryIter.current();
				((ContainerCount)hashEntry.value()).close();
			}
			_closed = true;
		}
	}

	public int openTransactionCount(){
		return isClosed() ? 0 : _transaction2Container.size();
	}

	public int openFileCount() {
		return isClosed() ? 0 : _fileName2Container.size();
	}

    public boolean isClosed() {
		return _closed == true || _mainContainer.isClosed();
	}

	private static class ContainerCount {
		private LocalObjectContainer _container;
		private int _count;

		public ContainerCount(LocalObjectContainer container) {
			this(container, 0);
		}

		public ContainerCount(LocalObjectContainer container, int count) {
			_container = container;
			_count = count;
		}
		
		public boolean isEmpty() {
			return _count <= 0;
		}
		
		public Transaction newTransaction() {
			_count++;
			return _container.newUserTransaction();
		}
		
		public void release() {
			if(_count == 0) {
				throw new IllegalStateException();
			}
			_count--;
		}
		
		public String fileName() {
			return _container.fileName();
		}
		
		public void close() {
			_container.close();
			_container = null;
		}

		public int hashCode() {
			return fileName().hashCode();
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			final ContainerCount other = (ContainerCount) obj;
			return fileName().equals(other.fileName());
		}

		
	}
}
