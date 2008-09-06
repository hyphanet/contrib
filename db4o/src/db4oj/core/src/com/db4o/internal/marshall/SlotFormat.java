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
package com.db4o.internal.marshall;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.handlers.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public abstract class SlotFormat {
    
    private static final Hashtable4 _versions = new Hashtable4();
    
    private static final SlotFormat CURRENT_SLOT_FORMAT = new SlotFormatCurrent() ;
    
    static{
        new SlotFormat0();
        new SlotFormat2();
    }
    
    protected SlotFormat(){
        _versions.put(handlerVersion(), this);
    }
    
    public static final SlotFormat forHandlerVersion(int handlerVersion){
        if(handlerVersion == HandlerRegistry.HANDLER_VERSION){
            return CURRENT_SLOT_FORMAT; 
        }
        if(handlerVersion < 0  || handlerVersion > CURRENT_SLOT_FORMAT.handlerVersion()){
            throw new IllegalArgumentException();
        }
        SlotFormat slotFormat = (SlotFormat) _versions.get(handlerVersion);
        if(slotFormat != null){
            return slotFormat;
        }
        return forHandlerVersion(handlerVersion + 1);
    }
    
    public boolean equals(Object obj) {
        if(! (obj instanceof SlotFormat)){
            return false;
        }
        return handlerVersion() == ((SlotFormat)obj).handlerVersion();
    }
    
    public int hashCode() {
        return handlerVersion();
    }
    
    protected abstract int handlerVersion();

    public abstract boolean isIndirectedWithinSlot(TypeHandler4 handler);
    
    public static SlotFormat current(){
        return CURRENT_SLOT_FORMAT;
    }
    
    protected boolean isVariableLength(TypeHandler4 handler) {
        return handler instanceof VariableLengthTypeHandler;
    }
    
    public static final boolean isEmbedded(TypeHandler4 handler) {
        return handler instanceof EmbeddedTypeHandler;
    }

    public Object doWithSlotIndirection(ReadBuffer buffer, TypeHandler4 typeHandler, Closure4 closure){
        if(! isIndirectedWithinSlot(typeHandler)){
            return closure.run();
        }
        return doWithSlotIndirection(buffer, closure);
    }
    
    public Object doWithSlotIndirection(ReadBuffer buffer, Closure4 closure){
        int payLoadOffset = buffer.readInt();
        buffer.readInt(); // length, not used
        int savedOffset = buffer.offset();
        Object res = null;
        if(payLoadOffset != 0){
            buffer.seek(payLoadOffset);
            res = closure.run();
        }
        buffer.seek(savedOffset);
        return res;
    }
    
    public boolean handleAsObject(TypeHandler4 typeHandler){
        if(isEmbedded(typeHandler)){
            return false;
        }
        if(typeHandler instanceof UntypedFieldHandler){
            return false;
        }
        return true;
    }

    public void writeObjectClassID(ByteArrayBuffer buffer, int id) {
        buffer.writeInt(-id);
    }
    
    public void skipMarshallerInfo(ByteArrayBuffer reader) {
        reader.incrementOffset(1);
    }

    public ObjectHeaderAttributes readHeaderAttributes(ByteArrayBuffer reader) {
        return new ObjectHeaderAttributes(reader);
    }
    
}
