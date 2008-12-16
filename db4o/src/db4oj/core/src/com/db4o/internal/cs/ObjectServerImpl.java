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

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.network.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.messages.*;

public class ObjectServerImpl implements ObjectServer, ExtObjectServer, Runnable {
	
	private static final int START_THREAD_WAIT_TIMEOUT = 5000;

	private final String _name;

	private ServerSocket4 _serverSocket;
	
	private int _port;

	private int i_threadIDGen = 1;

	private final Collection4 _dispatchers = new Collection4();

	LocalObjectContainer _container;
	ClientTransactionPool _transactionPool;

	private final Object _startupLock=new Object();
	
	private Config4Impl _config;
	
	private BlockingQueue _committedInfosQueue = new BlockingQueue();
	
	private CommittedCallbacksDispatcher _committedCallbacksDispatcher;
    
    private boolean _caresAboutCommitted;

	private final NativeSocketFactory _socketFactory;

	private final boolean _isEmbeddedServer;
	
	public ObjectServerImpl(final LocalObjectContainer container, int port, NativeSocketFactory socketFactory) {
		this(container, (port < 0 ? 0 : port), port == 0, socketFactory);
	}
	
	public ObjectServerImpl(final LocalObjectContainer container, int port, boolean isEmbeddedServer, NativeSocketFactory socketFactory) {
		_isEmbeddedServer = isEmbeddedServer;
		_socketFactory = socketFactory;
		_container = container;
		_transactionPool = new ClientTransactionPool(container);
		_port = port;
		_config = _container.configImpl();
		_name = "db4o ServerSocket FILE: " + container.toString() + "  PORT:"+ _port;
		
		_container.setServer(true);	
		configureObjectServer();
		
		_container.classCollection().checkAllClassChanges();
		
		boolean ok = false;
		try {
			ensureLoadStaticClass();
			startCommittedCallbackThread(_committedInfosQueue);
			startServer();
			ok = true;
		} finally {
			if(!ok) {
				close();
			}
		}
	}

	private void startServer() {		
		if (isEmbeddedServer()) {
			return;
		}
		
		synchronized(_startupLock) {
			startServerSocket();
			startServerThread();
			boolean started=false;
			while(!started) {
				try {
					_startupLock.wait(START_THREAD_WAIT_TIMEOUT);
					started=true;
				}
				// not specialized to InterruptException for .NET conversion
				catch (Exception exc) {
				}
			}
		}
	}

	private void startServerThread() {
		synchronized(_startupLock) {
			final Thread thread = new Thread(this);
			thread.setDaemon(true);
			thread.start();
		}
	}

	private void startServerSocket() {
		try {
			_serverSocket = new ServerSocket4(_socketFactory, _port);
			_port = _serverSocket.getLocalPort();
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
		_serverSocket.setSoTimeout(_config.timeoutServerSocket());
	}

	private boolean isEmbeddedServer() {
		return _isEmbeddedServer;
	}

	private void ensureLoadStaticClass() {
		_container.produceClassMetadata(_container._handlers.ICLASS_STATICCLASS);
	}

	private void configureObjectServer() {
		_config.callbacks(false);
		_config.isServer(true);
		// the minium activation depth of com.db4o.User.class should be 1.
		// Otherwise, we may get null password.
		_config.objectClass(User.class).minimumActivationDepth(1);
	}

	public void backup(String path) throws IOException {
		_container.backup(path);
	}

	final void checkClosed() {
		if (_container == null) {
			Exceptions4.throwRuntimeException(Messages.CLOSED_OR_OPEN_FAILED, _name);
		}
		_container.checkClosed();
	}

	public synchronized boolean close() {
		closeServerSocket();
		stopCommittedCallbacksDispatcher();
		closeMessageDispatchers();
		return closeFile();
	}

	private void stopCommittedCallbacksDispatcher() {
		if(_committedCallbacksDispatcher != null){
			_committedCallbacksDispatcher.stop();
		}
	}

	private boolean closeFile() {
		if (_container != null) {
			_transactionPool.close();
			_container = null;
		}
		return true;
	}

	private void closeMessageDispatchers() {
		Iterator4 i = iterateDispatchers();
		while (i.moveNext()) {
			try {
				((ServerMessageDispatcher) i.current()).close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		i = iterateDispatchers();
		while (i.moveNext()) {
			try {
				((Thread) i.current()).join();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public Iterator4 iterateDispatchers() {
		synchronized (_dispatchers) {
			return new Collection4(_dispatchers).iterator();
		}
	}

	private void closeServerSocket() {
		try {
			if (_serverSocket != null) {
				_serverSocket.close();
			}
		} catch (Exception e) {
			if (Deploy.debug) {
				System.out
						.println("YapServer.close() ServerSocket failed to close.");
			}
		}
		_serverSocket = null;
	}

	public Configuration configure() {
		return _config;
	}

	public ExtObjectServer ext() {
		return this;
	}

	private ServerMessageDispatcherImpl findThread(int a_threadID) {
		synchronized (_dispatchers) {
			Iterator4 i = _dispatchers.iterator();
			while (i.moveNext()) {
				ServerMessageDispatcherImpl serverThread = (ServerMessageDispatcherImpl) i.current();
				if (serverThread._threadID == a_threadID) {
					return serverThread;
				}
			}
		}
		return null;
	}

	Transaction findTransaction(int threadID) {
		ServerMessageDispatcherImpl dispatcher = findThread(threadID);
		return (dispatcher == null ? null : dispatcher.getTransaction());
	}

	public synchronized void grantAccess(String userName, String password) {
		checkClosed();
		synchronized (_container._lock) {
			User existing = getUser(userName);
			if (existing != null) {
				setPassword(existing, password);
			} else {
				addUser(userName, password);
			}
			_container.commit();
		}
	}

	private void addUser(String userName, String password) {
		_container.store(new User(userName, password));
	}

	private void setPassword(User existing, String password) {
		existing.password = password;
		_container.store(existing);
	}

	public User getUser(String userName) {
		final ObjectSet result = queryUsers(userName);
		if (!result.hasNext()) {
			return null;
		}
		return (User) result.next();
	}

	private ObjectSet queryUsers(String userName) {
		_container.showInternalClasses(true);
		try {
			return _container.queryByExample(new User(userName, null));
		} finally {
			_container.showInternalClasses(false);
		}
	}

	public ObjectContainer objectContainer() {
		return _container;
	}

    /**
     * @deprecated
     */
	public ObjectContainer openClient() {
		return openClient(Db4o.cloneConfiguration());
	}

	public synchronized ObjectContainer openClient(Configuration config) {
		checkClosed();
		synchronized (_container._lock) {
		    return new EmbeddedClientObjectContainer(_container);
		}
 	    
//      The following uses old embedded C/S mode:      

//		ClientObjectContainer client = new ClientObjectContainer(config,
//				openClientSocket(), Const4.EMBEDDED_CLIENT_USER
//						+ (i_threadIDGen - 1), "", false);
//		client.blockSize(_container.blockSize());
//		return client;
 	    
	}
	

	void removeThread(ServerMessageDispatcherImpl dispatcher) {
		synchronized (_dispatchers) {
			_dispatchers.remove(dispatcher);
            checkCaresAboutCommitted();
		}
	}

	public synchronized void revokeAccess(String userName) {
		checkClosed();
		synchronized (_container._lock) {
			deleteUsers(userName);
			_container.commit();
		}
	}

	private void deleteUsers(String userName) {
		ObjectSet set = queryUsers(userName);
		while (set.hasNext()) {
			_container.delete(set.next());
		}
	}

	public void run() {
		setThreadName();
		logListeningOnPort();
		notifyThreadStarted();
		listen();
	}

	private void startCommittedCallbackThread(BlockingQueue committedInfosQueue) {
		if(isEmbeddedServer()) {
			return;
		}
		_committedCallbacksDispatcher = new CommittedCallbacksDispatcher(this, committedInfosQueue);
		Thread thread = new Thread(_committedCallbacksDispatcher);
		thread.setName("committed callback thread");
		thread.setDaemon(true);
		thread.start();
	}

	private void setThreadName() {
		Thread.currentThread().setName(_name);
	}

	private void listen() {
		while (_serverSocket != null) {
			try {
				ServerMessageDispatcher messageDispatcher = new ServerMessageDispatcherImpl(this, new ClientTransactionHandle(_transactionPool),
						_serverSocket.accept(), newThreadId(), false, _container.lock());
				addServerMessageDispatcher(messageDispatcher);
				messageDispatcher.startDispatcher();
			} catch (Exception e) {
//				e.printStackTrace();
			}
		}
	}

	private void notifyThreadStarted() {
		synchronized (_startupLock) {
			_startupLock.notifyAll();
		}
	}

	private void logListeningOnPort() {
		_container.logMsg(Messages.SERVER_LISTENING_ON_PORT, "" + _serverSocket.getLocalPort());
	}

	private int newThreadId() {
		return i_threadIDGen++;
	}

	private void addServerMessageDispatcher(ServerMessageDispatcher thread) {
		synchronized (_dispatchers) {
			_dispatchers.add(thread);
            checkCaresAboutCommitted();
		}
	}

	public void addCommittedInfoMsg(MCommittedInfo message) {
		_committedInfosQueue.add(message);			
	}
	
	public void broadcastMsg(Msg message, BroadcastFilter filter) {		
		Iterator4 i = iterateDispatchers();
		while(i.moveNext()){
			ServerMessageDispatcher dispatcher = (ServerMessageDispatcher) i.current();
			if(filter.accept(dispatcher)) {
				dispatcher.write(message);
			}
		}
	}
    
    public boolean caresAboutCommitted(){
        return _caresAboutCommitted;
    }
    
    public void checkCaresAboutCommitted(){
        _caresAboutCommitted = anyDispatcherCaresAboutCommitted();
    }

	private boolean anyDispatcherCaresAboutCommitted() {
        Iterator4 i = iterateDispatchers();
        while(i.moveNext()){
            ServerMessageDispatcher dispatcher = (ServerMessageDispatcher) i.current();
            if(dispatcher.caresAboutCommitted()){
                return true;
            }
        }
		return false;
	}

	public int port() {
		return _port;
	}
	
	public int clientCount(){
	    synchronized(_dispatchers){
	        return _dispatchers.size();
	    }
	}
}
