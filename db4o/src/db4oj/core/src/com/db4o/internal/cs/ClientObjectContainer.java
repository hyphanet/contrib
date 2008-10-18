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
import com.db4o.internal.activation.*;
import com.db4o.internal.convert.*;
import com.db4o.internal.cs.messages.*;
import com.db4o.internal.query.processor.*;
import com.db4o.internal.query.result.*;
import com.db4o.internal.slots.*;
import com.db4o.reflect.*;

/**
 * @exclude
 */
public class ClientObjectContainer extends ExternalObjectContainer implements ExtClient, BlobTransport, ClientMessageDispatcher {
	
	final Object blobLock = new Object();

	private BlobProcessor blobThread;

	private Socket4 i_socket;

	private BlockingQueue _synchronousMessageQueue = new BlockingQueue();
	
	private BlockingQueue _asynchronousMessageQueue = new BlockingQueue();

	private final String _password; // null denotes password not necessary

	int[] _prefetchedIDs;

	ClientMessageDispatcher _messageDispatcher;
	
	ClientAsynchronousMessageProcessor _asynchronousMessageProcessor;

	int remainingIDs;

	private String switchedToFile;

	private boolean _singleThreaded;

	private final String _userName;

	private Db4oDatabase i_db;

	protected boolean _doFinalize=true;
    
    private int _blockSize = 1;
    
	private Collection4 _batchedMessages = new Collection4();
	
	// initial value of _batchedQueueLength is YapConst.INT_LENGTH, which is
	// used for to write the number of messages.
	private int _batchedQueueLength = Const4.INT_LENGTH;

	private boolean _login;
	
	private final ClientHeartbeat _heartbeat;

	public ClientObjectContainer(Configuration config,Socket4 socket, String user, String password, boolean login) {
		super(config, null);
		_userName = user;
		_password = password;
		_login = login;
		_heartbeat = new ClientHeartbeat(this);
		setAndConfigSocket(socket);
		open();
	}

	private void setAndConfigSocket(Socket4 socket) {
		i_socket = socket;
		i_socket.setSoTimeout(_config.timeoutClientSocket());
	}

	protected final void openImpl() {
		_singleThreaded = configImpl().singleThreadedClient();
		// TODO: Experiment with packetsize and noDelay
		// socket.setSendBufferSize(100);
		// socket.setTcpNoDelay(true);
		// System.out.println(socket.getSendBufferSize());
		if (_login) {
			loginToServer(i_socket);
		}
		if (!_singleThreaded) {
			startDispatcherThread(i_socket, _userName);
		}
		logMsg(36, toString());
		startHeartBeat();
		readThis();
	}
	
	private void startHeartBeat(){
	    _heartbeat.start();
	}
	
	private void startDispatcherThread(Socket4 socket, String user) {
		if(! _singleThreaded){
			_asynchronousMessageProcessor = new ClientAsynchronousMessageProcessor(_asynchronousMessageQueue);
			_asynchronousMessageProcessor.startProcessing();
		}
		_messageDispatcher = new ClientMessageDispatcherImpl(this, socket, _synchronousMessageQueue, _asynchronousMessageQueue);
		_messageDispatcher.setDispatcherName(user);
		_messageDispatcher.startDispatcher();
	}

	public void backup(String path) throws NotSupportedException {
		throw new NotSupportedException();
	}
	
	public void reserve(int byteCount) {
		throw new NotSupportedException();
	}
    
    public void blockSize(int blockSize){
        _blockSize = blockSize;
    }
    
    public byte blockSize() {
        return (byte)_blockSize;
    }

    protected void close2() {
		if ((!_singleThreaded) && (_messageDispatcher == null || !_messageDispatcher.isMessageDispatcherAlive())) {
		    stopHeartBeat();
			shutdownObjectContainer();
			return;
		}
		try {
			commit1(_transaction);
		} catch (Exception e) {
			Exceptions4.catchAllExceptDb4oException(e);
		}
		try {
			write(Msg.CLOSE);
		} catch (Exception e) {
			Exceptions4.catchAllExceptDb4oException(e);
		}
		
		shutDownCommunicationRessources();
		
		try {
			i_socket.close();
		} catch (Exception e) {
			Exceptions4.catchAllExceptDb4oException(e);
		}
		
		shutdownObjectContainer();
	}
    
    private void stopHeartBeat(){
        _heartbeat.stop();
    }
    
    private void closeMessageDispatcher(){
        try {
            if (!_singleThreaded) {
                _messageDispatcher.close();
            }
        } catch (Exception e) {
            Exceptions4.catchAllExceptDb4oException(e);
        }
        try {
            if (!_singleThreaded) {
            	_asynchronousMessageProcessor.stopProcessing();
            }
        } catch (Exception e) {
            Exceptions4.catchAllExceptDb4oException(e);
        }
    }

	public final void commit1(Transaction trans) {
		trans.commit();
	}
    
    public int converterVersion() {
        return Converter.VERSION;
    }
	
	Socket4 createParalellSocket() throws IOException {
		write(Msg.GET_THREAD_ID);
		
		int serverThreadID = expectedByteResponse(Msg.ID_LIST).readInt();

		Socket4 sock = i_socket.openParalellSocket();
		loginToServer(sock);

		if (switchedToFile != null) {
			MsgD message = Msg.SWITCH_TO_FILE.getWriterForString(systemTransaction(),
					switchedToFile);
			message.write(sock);
			if (!(Msg.OK.equals(Msg.readMessage(this, systemTransaction(), sock)))) {
				throw new IOException(Messages.get(42));
			}
		}
		Msg.USE_TRANSACTION.getWriterForInt(_transaction, serverThreadID).write(
				sock);
		return sock;
	}

	public AbstractQueryResult newQueryResult(Transaction trans, QueryEvaluationMode mode) {
		throw new IllegalStateException();
	}

	final public Transaction newTransaction(Transaction parentTransaction, TransactionalReferenceSystem referenceSystem) {
		return new ClientTransaction(this, parentTransaction, referenceSystem);
	}

	public boolean createClassMetadata(ClassMetadata clazz, ReflectClass claxx, ClassMetadata superClazz) {		
		write(Msg.CREATE_CLASS.getWriterForString(systemTransaction(), config().resolveAliasRuntimeName(claxx.getName())));
		Msg resp = getResponse();
		if (resp == null) {
			return false;
		}
		
		if (resp.equals(Msg.FAILED)) {
			// if the class can not be created on the server, send class meta to the server.
			sendClassMeta(claxx);
			resp = getResponse();
		}
		
		if (resp.equals(Msg.FAILED)) {
			if (configImpl().exceptionsOnNotStorable()) {
				throw new ObjectNotStorableException(claxx);
			}
			return false;
		}
		if (!resp.equals(Msg.OBJECT_TO_CLIENT)) {
			return false;
		}

		MsgObject message = (MsgObject) resp;
		StatefulBuffer bytes = message.unmarshall();
		if (bytes == null) {
			return false;
		}
		bytes.setTransaction(systemTransaction());
		if (!super.createClassMetadata(clazz, claxx, superClazz)) {
			return false;
		}
		clazz.setID(message.getId());
		clazz.readName1(systemTransaction(), bytes);
		classCollection().addClassMetadata(clazz);
		classCollection().readClassMetadata(clazz, claxx);
		return true;
	}

	private void sendClassMeta(ReflectClass reflectClass) {
		ClassInfo classMeta = _classMetaHelper.getClassMeta(reflectClass);
		write(Msg.CLASS_META.getWriter(Serializer.marshall(systemTransaction(),classMeta)));
	}
	
	public long currentVersion() {
		write(Msg.CURRENT_VERSION);
		return ((MsgD) expectedResponse(Msg.ID_LIST)).readLong();
	}

	public final boolean delete4(Transaction ta, ObjectReference yo, int a_cascade, boolean userCall) {
		MsgD msg = Msg.DELETE.getWriterForInts(_transaction, new int[] { yo.getID(), userCall ? 1 : 0 });
		writeBatchedMessage(msg);
		return true;
	}

	public boolean detectSchemaChanges() {
		return false;
	}

	protected boolean doFinalize() {
		return _doFinalize;
	}
	
	final ByteArrayBuffer expectedByteResponse(Msg expectedMessage) {
		Msg msg = expectedResponse(expectedMessage);
		if (msg == null) {
			// TODO: throw Exception to allow
			// smooth shutdown
			return null;
		}
		return msg.getByteLoad();
	}

	public final Msg expectedResponse(Msg expectedMessage) {
		Msg message = getResponse();
		if (expectedMessage.equals(message)) {
			return message;
		}
		checkExceptionMessage(message);
		throw new IllegalStateException("Unexpected Message:" + message
				+ "  Expected:" + expectedMessage);
	}

	private void checkExceptionMessage(Msg msg) {
		if(msg instanceof MRuntimeException) {
			((MRuntimeException)msg).throwPayload();
		}
	}
		
	public AbstractQueryResult queryAllObjects(Transaction trans) {
		int mode = config().queryEvaluationMode().asInt();
		MsgD msg = Msg.GET_ALL.getWriterForInt(trans, mode);
		write(msg);
		return readQueryResult(trans);
	}

	/**
	 * may return null, if no message is returned. Error handling is weak and
	 * should ideally be able to trigger some sort of state listener (connection
	 * dead) on the client.
	 */
	public Msg getResponse() {
		while(true){
			Msg msg = _singleThreaded ? getResponseSingleThreaded(): getResponseMultiThreaded();
			if(isClientSideMessage(msg)){
				if(((ClientSideMessage)msg).processAtClient()){
					continue;
				}
			}
			return msg;
		}
	}
	
	private Msg getResponseSingleThreaded() {
		while (isMessageDispatcherAlive()) {
			try {
				final Msg message = Msg.readMessage(this, _transaction, i_socket);
				if(isClientSideMessage(message)) {
					if(((ClientSideMessage)message).processAtClient()){
						continue;
					}
				}
				return message;
	         } catch (Db4oIOException exc) {
	             onMsgError();
	         }
		}
		return null;
	}

	private Msg getResponseMultiThreaded() {
		Msg msg;
		try {
			msg = (Msg)_synchronousMessageQueue.next();
		} catch (BlockingQueueStoppedException e) {
			if(DTrace.enabled){
				DTrace.BLOCKING_QUEUE_STOPPED_EXCEPTION.log(e.toString());
			}
			msg = Msg.ERROR;
		}
		if(msg instanceof MError) {	
			onMsgError();
		}
		return msg;
	}
	
	private boolean isClientSideMessage(Msg message) {
		return message instanceof ClientSideMessage;
	}

	private void onMsgError() {
		close();
		throw new DatabaseClosedException();
	}
	
	public boolean isMessageDispatcherAlive() {
		return i_socket != null;
	}

	public ClassMetadata classMetadataForId(int clazzId) {
		if(clazzId == 0) {
			return null;
		}
		ClassMetadata yc = super.classMetadataForId(clazzId);
		if (yc != null) {
			return yc;
		}
		MsgD msg = Msg.CLASS_NAME_FOR_ID.getWriterForInt(systemTransaction(), clazzId);
		write(msg);
		MsgD message = (MsgD) expectedResponse(Msg.CLASS_NAME_FOR_ID);
		String className = config().resolveAliasStoredName(message.readString());
		if (className != null && className.length() > 0) {
			ReflectClass claxx = reflector().forName(className);
			if (claxx != null) {
				return produceClassMetadata(claxx);
			}
			// TODO inform client class not present
		}
		return null;
	}

	public boolean needsLockFileThread() {
		return false;
	}

	protected boolean hasShutDownHook() {
		return false;
	}

	public Db4oDatabase identity() {
		if (i_db == null) {
			write(Msg.IDENTITY);
			ByteArrayBuffer reader = expectedByteResponse(Msg.ID_LIST);
			showInternalClasses(true);
			try {
				i_db = (Db4oDatabase) getByID(reader.readInt());
				activate(systemTransaction(), i_db, new FixedActivationDepth(3));
			} finally {
				showInternalClasses(false);
			}
		}
		return i_db;
	}

	public boolean isClient() {
		return true;
	}

	private void loginToServer(Socket4 socket) throws InvalidPasswordException {
		UnicodeStringIO stringWriter = new UnicodeStringIO();
		int length = stringWriter.length(_userName)
				+ stringWriter.length(_password);
		MsgD message = Msg.LOGIN
				.getWriterForLength(systemTransaction(), length);
		message.writeString(_userName);
		message.writeString(_password);
		message.write(socket);
		Msg msg = readLoginMessage(socket);
		ByteArrayBuffer payLoad = msg.payLoad();
		_blockSize = payLoad.readInt();
		int doEncrypt = payLoad.readInt();
		if (doEncrypt == 0) {
			_handlers.oldEncryptionOff();
		}
	}
	
	private Msg readLoginMessage(Socket4 socket){
       Msg msg = Msg.readMessage(this, systemTransaction(), socket);
       while(Msg.PONG.equals(msg)){
           msg = Msg.readMessage(this, systemTransaction(), socket);
       }
       if (!Msg.LOGIN_OK.equals(msg)) {
            throw new InvalidPasswordException();
       }
       return msg;
	}

	public boolean maintainsIndices() {
		return false;
	}

	public final int newUserObject() {
		int prefetchIDCount = config().prefetchIDCount();
		ensureIDCacheAllocated(prefetchIDCount);
		ByteArrayBuffer reader = null;
		if (remainingIDs < 1) {
			MsgD msg = Msg.PREFETCH_IDS.getWriterForInt(_transaction, prefetchIDCount);
			write(msg);
			reader = expectedByteResponse(Msg.ID_LIST);
			for (int i = prefetchIDCount - 1; i >= 0; i--) {
				_prefetchedIDs[i] = reader.readInt();
			}
			remainingIDs = prefetchIDCount;
		}
		remainingIDs--;
		return _prefetchedIDs[remainingIDs];
	}

	void processBlobMessage(MsgBlob msg) {
		synchronized (blobLock) {
			boolean needStart = blobThread == null || blobThread.isTerminated();
			if (needStart) {
				blobThread = new BlobProcessor(this);
			}
			blobThread.add(msg);
			if (needStart) {
				blobThread.start();
			}
		}
	}

	public void raiseVersion(long a_minimumVersion) {
		write(Msg.RAISE_VERSION.getWriterForLong(_transaction, a_minimumVersion));
	}

	public void readBytes(byte[] bytes, int address, int addressOffset, int length) {
		throw Exceptions4.virtualException();
	}

	public void readBytes(byte[] a_bytes, int a_address, int a_length) {
		MsgD msg = Msg.READ_BYTES.getWriterForInts(_transaction, new int[] {
				a_address, a_length });
		write(msg);
		ByteArrayBuffer reader = expectedByteResponse(Msg.READ_BYTES);
		System.arraycopy(reader._buffer, 0, a_bytes, 0, a_length);
	}

	protected boolean rename1(Config4Impl config) {
		logMsg(58, null);
		return false;
	}
	
	public final StatefulBuffer readWriterByID(Transaction a_ta, int a_id) {
		return readWriterByID(a_ta, a_id, false);
	}
	
	public final StatefulBuffer readWriterByID(Transaction a_ta, int a_id, boolean lastCommitted) {
		MsgD msg = Msg.READ_OBJECT.getWriterForInts(a_ta, new int[]{a_id, lastCommitted?1:0});
		write(msg);
		StatefulBuffer bytes = ((MsgObject) expectedResponse(Msg.OBJECT_TO_CLIENT))
				.unmarshall();
		if(bytes != null){
			bytes.setTransaction(a_ta);
		}
		return bytes;
	}

	public final StatefulBuffer[] readWritersByIDs(Transaction a_ta, int[] ids) {
		MsgD msg = Msg.READ_MULTIPLE_OBJECTS.getWriterForIntArray(a_ta, ids, ids.length);
		write(msg);
		MsgD response = (MsgD) expectedResponse(Msg.READ_MULTIPLE_OBJECTS);
		int count = response.readInt();
		StatefulBuffer[] yapWriters = new StatefulBuffer[count];
		for (int i = 0; i < count; i++) {
			MsgObject mso = (MsgObject) Msg.OBJECT_TO_CLIENT.publicClone();
			mso.setTransaction(a_ta);
			mso.payLoad(response.payLoad().readYapBytes());
			if (mso.payLoad() != null) {
				mso.payLoad().incrementOffset(Const4.MESSAGE_LENGTH);
				yapWriters[i] = mso.unmarshall(Const4.MESSAGE_LENGTH);
				yapWriters[i].setTransaction(a_ta);
			}
		}
		return yapWriters;
	}

	public final ByteArrayBuffer readReaderByID(Transaction a_ta, int a_id, boolean lastCommitted) {
		// TODO: read lightweight reader instead
		return readWriterByID(a_ta, a_id, lastCommitted);
	}

	public final ByteArrayBuffer readReaderByID(Transaction a_ta, int a_id) {
		return readReaderByID(a_ta, a_id, false); 
	}

	private AbstractQueryResult readQueryResult(Transaction trans) {
		AbstractQueryResult queryResult = null;
		ByteArrayBuffer reader = expectedByteResponse(Msg.QUERY_RESULT);
		int queryResultID = reader.readInt();
		if(queryResultID > 0){
			queryResult = new LazyClientQueryResult(trans, this, queryResultID);
		}else{
			queryResult = new ClientQueryResult(trans);
		}
		queryResult.loadFromIdReader(reader);
		return queryResult;
	}

	void readThis() {
		write(Msg.GET_CLASSES.getWriter(systemTransaction()));
		ByteArrayBuffer bytes = expectedByteResponse(Msg.GET_CLASSES);
		classCollection().setID(bytes.readInt());
		createStringIO(bytes.readByte());
		classCollection().read(systemTransaction());
		classCollection().refreshClasses();
	}

	public void releaseSemaphore(String name) {
		synchronized (_lock) {
			checkClosed();
			if (name == null) {
				throw new NullPointerException();
			}
			write(Msg.RELEASE_SEMAPHORE.getWriterForString(_transaction, name));
		}
	}

	public void releaseSemaphores(Transaction ta) {
		// do nothing
	}

	private void reReadAll(Configuration config) {
		remainingIDs = 0;
		initialize1(config);
		initializeTransactions();
		readThis();
	}

	public final void rollback1(Transaction trans) {
		if (_config.batchMessages()) {
			clearBatchedObjects();
		} 
		write(Msg.ROLLBACK);
		trans.rollback();
	}

	public void send(Object obj) {
		synchronized (_lock) {
			if (obj != null) {
				final MUserMessage message = Msg.USER_MESSAGE;
				write(message.marshallUserMessage(_transaction, obj));
			}
		}
	}

	public final void setDirtyInSystemTransaction(PersistentBase a_object) {
		// do nothing
	}

	public boolean setSemaphore(String name, int timeout) {
		synchronized (_lock) {
			checkClosed();
			if (name == null) {
				throw new NullPointerException();
			}
			MsgD msg = Msg.SET_SEMAPHORE.getWriterForIntString(_transaction,
					timeout, name);
			write(msg);
			Msg message = getResponse();
			return (message.equals(Msg.SUCCESS));
		}
	}

    /**
     * @deprecated
     */
	 public void switchToFile(String fileName) {
		synchronized (_lock) {
			commit();
			MsgD msg = Msg.SWITCH_TO_FILE.getWriterForString(_transaction, fileName);
			write(msg);
			expectedResponse(Msg.OK);
			// FIXME NSC
			reReadAll(Db4o.cloneConfiguration());
			switchedToFile = fileName;
		}
	}

    /**
     * @deprecated
     */
	 public void switchToMainFile() {
		synchronized (_lock) {
			commit();
			write(Msg.SWITCH_TO_MAIN_FILE);
			expectedResponse(Msg.OK);
			// FIXME NSC
			reReadAll(Db4o.cloneConfiguration());
			switchedToFile = null;
		}
	}

	public String name() {
		return toString();
	}

	public String toString() {
		// if(i_classCollection != null){
		// return i_classCollection.toString();
		// }
		return "Client Connection " + _userName;
	}

	public void shutdown() {
		// do nothing
	}

	public final void writeDirty() {
		// do nothing
	}

	public final boolean write(Msg msg) {
		writeMsg(msg, true);
		return true;
	}
	
	public final void writeBatchedMessage(Msg msg) {
		writeMsg(msg, false);
	}
	
	private final void writeMsg(Msg msg, boolean flush) {
		if(_config.batchMessages()) {
			if(flush && _batchedMessages.isEmpty()) {
				// if there's nothing batched, just send this message directly
				writeMessageToSocket(msg);
			} else {
				addToBatch(msg);
				if(flush || _batchedQueueLength > _config.maxBatchQueueSize()) {
					writeBatchedMessages();
				}
			}
		} else {
			writeMessageToSocket(msg);
		}
	}

	public boolean writeMessageToSocket(Msg msg) {
		return msg.write(i_socket);
	}
	
	public final void writeNew(Transaction trans, Pointer4 pointer, ClassMetadata classMetadata, ByteArrayBuffer buffer) {
		MsgD msg = Msg.WRITE_NEW.getWriter(trans, pointer, classMetadata, buffer);
		writeBatchedMessage(msg);
	}
    
	public final void writeTransactionPointer(int a_address) {
		// do nothing
	}

	public final void writeUpdate(Transaction trans, Pointer4 pointer, ClassMetadata classMetadata, ByteArrayBuffer buffer) {
		MsgD msg = Msg.WRITE_UPDATE.getWriter(trans, pointer, classMetadata, buffer);
		writeBatchedMessage(msg);
	}

	public boolean isAlive() {
		try {
			write(Msg.IS_ALIVE);
			return expectedResponse(Msg.IS_ALIVE) != null;
		} catch (Db4oException exc) {
			return false;
		}
	}

	// Remove, for testing purposes only
	public Socket4 socket() {
		return i_socket;
	}
	
	private void ensureIDCacheAllocated(int prefetchIDCount) {
		if(_prefetchedIDs==null) {
			_prefetchedIDs = new int[prefetchIDCount];
			return;
		}
		if(prefetchIDCount>_prefetchedIDs.length) {
			int[] newPrefetchedIDs=new int[prefetchIDCount];
			System.arraycopy(_prefetchedIDs, 0, newPrefetchedIDs, 0, _prefetchedIDs.length);
			_prefetchedIDs=newPrefetchedIDs;
		}
	}

    public SystemInfo systemInfo() {
        throw new NotImplementedException("Functionality not availble on clients.");
    }

	
    public void writeBlobTo(Transaction trans, BlobImpl blob, File file) throws IOException {
        MsgBlob msg = (MsgBlob) Msg.READ_BLOB.getWriterForInt(trans, (int) getID(blob));
        msg._blob = blob;
        processBlobMessage(msg);
    }
    
    public void readBlobFrom(Transaction trans, BlobImpl blob, File file) throws IOException {
        MsgBlob msg = null;
        synchronized (lock()) {
            store(blob);
            int id = (int) getID(blob);
            msg = (MsgBlob) Msg.WRITE_BLOB.getWriterForInt(trans, id);
            msg._blob = blob;
            blob.setStatus(Status.QUEUED);
        }
        processBlobMessage(msg);
    }
    
    public void deleteBlobFile(Transaction trans, BlobImpl blob){
        MDeleteBlobFile msg = (MDeleteBlobFile) Msg.DELETE_BLOB_FILE.getWriterForInt(trans, (int) getID(blob));
		writeMsg(msg, false);
    }

    public long[] getIDsForClass(Transaction trans, ClassMetadata clazz){
    	MsgD msg = Msg.GET_INTERNAL_IDS.getWriterForInt(trans, clazz.getID());
    	write(msg);
    	ByteArrayBuffer reader = expectedByteResponse(Msg.ID_LIST);
    	int size = reader.readInt();
    	final long[] ids = new long[size];
    	for (int i = 0; i < size; i++) {
    	    ids[i] = reader.readInt();
    	}
    	return ids;
    }
    
    public QueryResult classOnlyQuery(Transaction trans, ClassMetadata clazz){
        long[] ids = clazz.getIDs(trans); 
        ClientQueryResult resClient = new ClientQueryResult(trans, ids.length);
        for (int i = 0; i < ids.length; i++) {
            resClient.add((int)ids[i]);
        }
        return resClient;
    }
    
    public QueryResult executeQuery(QQuery query){
    	Transaction trans = query.getTransaction();
    	query.evaluationMode(config().queryEvaluationMode());
        query.marshall();
		MsgD msg = Msg.QUERY_EXECUTE.getWriter(Serializer.marshall(trans,query));
		write(msg);
		return readQueryResult(trans);
    }

    public final void writeBatchedMessages() {
    	synchronized(lock()) {
			if (_batchedMessages.isEmpty()) {
				return;
			}
	
			Msg msg;
			MsgD multibytes = Msg.WRITE_BATCHED_MESSAGES.getWriterForLength(
					transaction(), _batchedQueueLength);
			multibytes.writeInt(_batchedMessages.size());
			Iterator4 iter = _batchedMessages.iterator();
			while(iter.moveNext()) {
				msg = (Msg) iter.current();
				if (msg == null) {
					multibytes.writeInt(0);
				} else {
					multibytes.writeInt(msg.payLoad().length());
					multibytes.payLoad().append(msg.payLoad()._buffer);
				}
			}
			writeMessageToSocket(multibytes);
			clearBatchedObjects();
    	}
	}

	public final void addToBatch(Msg msg) {
		synchronized(lock()) {
			_batchedMessages.add(msg);
			// the first INT_LENGTH is for buffer.length, and then buffer content.
			_batchedQueueLength += Const4.INT_LENGTH + msg.payLoad().length();
		}
	}

	private final void clearBatchedObjects() {
		_batchedMessages.clear();
		// initial value of _batchedQueueLength is YapConst.INT_LENGTH, which is
		// used for to write the number of messages.
		_batchedQueueLength = Const4.INT_LENGTH;
	}

	int timeout() {
	    return configImpl().timeoutClientSocket();
	}

	protected void shutdownDataStorage() {
	    shutDownCommunicationRessources();
	}
	
	private void shutDownCommunicationRessources() {
	    stopHeartBeat();
	    closeMessageDispatcher();
	    _synchronousMessageQueue.stop();
	    _asynchronousMessageQueue.stop();
	}

	public void setDispatcherName(String name) {
		// do nothing here		
	}

	public void startDispatcher() {
		// do nothing here for single thread, ClientObjectContainer is already running
	}
	
	public ClientMessageDispatcher messageDispatcher() {
		return _singleThreaded ? this : _messageDispatcher;
	}

	public void onCommittedListener() {
		if(_singleThreaded) {
			return;
		}
		write(Msg.COMMITTED_CALLBACK_REGISTER);
	}
	
	public int classMetadataIdForName(String name) {
        MsgD msg = Msg.CLASS_METADATA_ID_FOR_NAME.getWriterForString(systemTransaction(), name);
        msg.write(i_socket);
        MsgD response = (MsgD) expectedResponse(Msg.CLASS_ID);
        return response.readInt();
    }

	public int instanceCount(ClassMetadata clazz, Transaction trans) {
        MsgD msg = Msg.INSTANCE_COUNT.getWriterForInt(trans, clazz.getID());
        write(msg);
        MsgD response = (MsgD) expectedResponse(Msg.INSTANCE_COUNT);
        return response.readInt();
	}
	
}
