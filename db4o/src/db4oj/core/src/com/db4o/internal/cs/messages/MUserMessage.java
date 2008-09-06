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
package com.db4o.internal.cs.messages;

import com.db4o.*;
import com.db4o.internal.*;
import com.db4o.messaging.*;

public final class MUserMessage extends MsgObject implements ServerSideMessage, ClientSideMessage {
	
	public final boolean processAtServer() {
		return processUserMessage();
	}
	
	public boolean processAtClient() {
		return processUserMessage();
	}
	
	private class MessageContextImpl implements MessageContext {
	
		public MessageSender sender() {
			return new MessageSender() {
				public void send(Object message) {
					serverMessageDispatcher().write(Msg.USER_MESSAGE.marshallUserMessage(transaction(), message));
				}
			};
		}
	
		public ObjectContainer container() {
			return transaction().objectContainer();
		}
	};
	
	private boolean processUserMessage() {
		final MessageRecipient recipient = messageRecipient();
		if (recipient == null) {
			return true;
		}
		
		try {
			recipient.processMessage(new MessageContextImpl(), readUserMessage());
		} catch (Exception x) {
			// TODO: use MessageContext.sender() to send
			// error back to client
			x.printStackTrace();
		}
		return true;
	}

	private Object readUserMessage() {
		unmarshall();
		return ((UserMessagePayload)readObjectFromPayLoad()).message;
	}
	
	private MessageRecipient messageRecipient() {
		return config().messageRecipient();
	}
	
	public static final class UserMessagePayload {
		public Object message;
		
		public UserMessagePayload() {
		}
		
		public UserMessagePayload(Object message_) {
			message = message_;
		}
	}

	public Msg marshallUserMessage(Transaction transaction, Object message) {
		return getWriter(Serializer.marshall(transaction, new UserMessagePayload(message)));
	}
}