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
package com.db4o.internal;

import com.db4o.foundation.*;


/**
 * 
 */
class WeakReferenceCollector implements Runnable {
    
    final Object            _queue;
    private final ObjectContainerBase _stream;
    private SimpleTimer     _timer;
    public final boolean    _weak;

    WeakReferenceCollector(ObjectContainerBase a_stream) {
        _stream = a_stream;
        _weak = (!(a_stream instanceof TransportObjectContainer)
            && Platform4.hasWeakReferences() && a_stream.configImpl().weakReferences());
        _queue = _weak ? Platform4.createReferenceQueue() : null;
    }

    Object createYapRef(ObjectReference a_yo, Object obj) {
        
        if (!_weak) {  
            return obj;
        }
        
        return Platform4.createActiveObjectReference(_queue, a_yo, obj);
    }

    void pollReferenceQueue() {
        if (!_weak) {
        	return;
        }
        Platform4.pollReferenceQueue(_stream, _queue);
    }

    public void run() {
    	try {
    		pollReferenceQueue();
    	} catch (Exception e) {
    		// don't bring down the thread
    		e.printStackTrace();
    	}
    }

    void startTimer() {
    	if (!_weak) {
    		return;
    	}
        
        if(! _stream.configImpl().weakReferences()){
            return;
        }
    	
        if (_stream.configImpl().weakReferenceCollectionInterval() <= 0) {
        	return;
        }

        if (_timer != null) {
        	return;
        }
        
        _timer = new SimpleTimer(this, _stream.configImpl().weakReferenceCollectionInterval(), "db4o WeakReference collector");
        _timer.start();
    }

    void stopTimer() {
    	if (_timer == null){
            return;
        }
        _timer.stop();
        _timer = null;
    }
    
}