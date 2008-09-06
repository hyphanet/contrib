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
package com.db4o.internal.diagnostic;

import com.db4o.diagnostic.*;
import com.db4o.diagnostic.DefragmentRecommendation.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.query.*;

/**
 * @exclude
 * 
 * FIXME: remove me from the core and make me a facade over Events
 */
public class DiagnosticProcessor implements DiagnosticConfiguration, DeepClone{
    
    private Collection4 _listeners;
    
    public DiagnosticProcessor() {
    }
    
    private DiagnosticProcessor(Collection4 listeners) {
    	_listeners = listeners;
    }

    public void addListener(DiagnosticListener listener) {
        if(_listeners == null){
            _listeners = new Collection4();
        }
        _listeners.add(listener);
    }
    
    public void checkClassHasFields(ClassMetadata classMetadata){
        if( (! classMetadata.aspectsAreNull()) && classMetadata.declaredAspectCount() == 0){
            String name = classMetadata.getName();
            String[] ignoredPackages = new String[]{
                "java.util."
            };
            for (int i = 0; i < ignoredPackages.length; i++) {
                if (name.indexOf(ignoredPackages[i]) == 0){
                    return;
                }
            }
            if(isDb4oClass(classMetadata)){
                return;
            }
            onDiagnostic(new ClassHasNoFields(name));
        }
    }

    public void checkUpdateDepth(int depth) {
        if (depth > 1) {
            onDiagnostic(new UpdateDepthGreaterOne(depth));
        }
    }

    public Object deepClone(Object context) {
        return new DiagnosticProcessor(cloneListeners());
    }
    
	public void deletionFailed() {
		onDiagnostic(new DeletionFailed());
	}
    
	public void defragmentRecommended(DefragmentRecommendationReason reason) {
		onDiagnostic(new DefragmentRecommendation(reason));
	}

	private Collection4 cloneListeners() {
		return _listeners != null
			? new Collection4(_listeners)
			: null;
	}

    public boolean enabled(){
        return _listeners != null;
    }
    
    private boolean isDb4oClass(ClassMetadata yc){
        return Platform4.isDb4oClass(yc.getName());
    }

    public void loadedFromClassIndex(ClassMetadata yc) {
        if(isDb4oClass(yc)){
            return;
        }
        onDiagnostic(new LoadedFromClassIndex(yc.getName()));
    }

    public void descendIntoTranslator(ClassMetadata parent,String fieldName) {
        onDiagnostic(new DescendIntoTranslator(parent.getName(),fieldName));
    }
    
    public void nativeQueryUnoptimized(Predicate predicate, Exception exception) {
        onDiagnostic(new NativeQueryNotOptimized(predicate, exception));
    }
    
    public void nativeQueryOptimizerNotLoaded(int reason, Exception e) {
        onDiagnostic(new NativeQueryOptimizerNotLoaded(reason, e));
    }

    public void onDiagnostic(Diagnostic d) {
        if(_listeners == null){
            return;
        }
        Iterator4 i = _listeners.iterator();
        while(i.moveNext()){
            ((DiagnosticListener)i.current()).onDiagnostic(d);
        }
    }
    
    public void removeAllListeners() {
        _listeners = null;
    }


}
