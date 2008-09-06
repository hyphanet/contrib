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
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.network.*;
import com.db4o.internal.*;
import com.db4o.internal.cs.messages.*;

class ClientMessageDispatcherImpl extends Thread implements ClientMessageDispatcher {
	
	private ClientObjectContainer _container;
	private Socket4 _socket;
	private final BlockingQueue _synchronousMessageQueue;
	private final BlockingQueue _asynchronousMessageQueue;
	private boolean _isClosed;
	
	ClientMessageDispatcherImpl(
			ClientObjectContainer client, 
			Socket4 a_socket, 
			BlockingQueue synchronousMessageQueue, 
			BlockingQueue asynchronousMessageQueue){
		_container = client;
		_synchronousMessageQueue = synchronousMessageQueue;
		_asynchronousMessageQueue = asynchronousMessageQueue;
		_socket = a_socket;
	}
	
	public synchronized boolean isMessageDispatcherAlive() {
		return !_isClosed;
	}

	public synchronized boolean close() {
	    if(_isClosed){
	        return true;
	    }
		_isClosed = true;
		if(_socket != null) {
			try {
				_socket.close();
			} catch (Db4oIOException e) {
				
			}
		}
		_synchronousMessageQueue.stop();
		_asynchronousMessageQueue.stop();
		return true;
	}
	
	public void run() {
	    messageLoop();
	    close();
	}
	
	public void messageLoop() {
		while (isMessageDispatcherAlive()) {
			Msg message = null;
			try {
				message = Msg.readMessage(this, transaction(), _socket);
			} catch (Db4oIOException exc) {
				if(DTrace.enabled){
					DTrace.CLIENT_MESSAGE_LOOP_EXCEPTION.log(exc.toString());
				}
			    return;
            }
			if(message == null){
			    continue;
			}
			if (isClientSideMessage(message)) {
				_asynchronousMessageQueue.add(message);
			} else {
				_synchronousMessageQueue.add(message);
			}
		}
	}
	
	private boolean isClientSideMessage(Msg message) {
		return message instanceof ClientSideMessage;
	}
	
	public boolean write(Msg msg) {
		_container.write(msg);
		return true;
	}

	public void setDispatcherName(String name) {
		setName("db4o client side message dispatcher for " + name);
	}

	public void startDispatcher() {
		start();
	}
	
	private Transaction transaction(){
	    return _container.transaction();
	}
	
}
