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
package com.db4o.internal.convert;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.convert.conversions.*;

/**
 * @exclude
 */
public class Converter {
    
    public static final int VERSION = ClassAspects_7_4.VERSION;
    
    private static Converter _converter;
    
    private Hashtable4 _conversions;
    
    private Converter() {
        _conversions = new Hashtable4();
        
        // TODO: There probably will be Java and .NET conversions
        //       Create Platform4.registerConversions() method ann
        //       call from here when needed.
        CommonConversions.register(this);
    }

    public static boolean convert(ConversionStage stage) {
    	if(!needsConversion(stage.systemData())) {
    		return false;
    	}
    	if(_converter == null){
    		_converter = new Converter();
    	}
    	return _converter.runConversions(stage);
    }

    private static boolean needsConversion(SystemData systemData) {
        return systemData.converterVersion() < VERSION;
    }

    public void register(int idx, Conversion conversion) {
        if(_conversions.get(idx) != null){
            throw new IllegalStateException();
        }
        _conversions.put(idx, conversion);
    }
    
    public boolean runConversions(ConversionStage stage) {
        SystemData systemData = stage.systemData();
        if(!needsConversion(systemData)){
            return false;
        }
        for (int i = systemData.converterVersion(); i <= VERSION; i++) {
            Conversion conversion = (Conversion)_conversions.get(i);
            if(conversion != null){
                stage.accept(conversion);
            }
        }
        return true;
    }
    
}
