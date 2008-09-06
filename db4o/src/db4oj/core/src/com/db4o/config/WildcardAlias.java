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
package com.db4o.config;

import com.db4o.foundation.*;

/**
 * Wildcard Alias functionality to create aliases for packages, 
 * namespaces or multiple similar named classes. One single '*' 
 * wildcard character is supported in the names. 
 * <br><br>See {@link Alias} for concrete examples.
 */
public class WildcardAlias implements Alias {
	
	private final WildcardPattern _storedPattern;
    
	private final WildcardPattern _runtimePattern;

	/**
     * Create a WildcardAlias with two patterns, the 
     * stored pattern and the pattern that is to be used 
     * at runtime. One single '*' is allowed as a wildcard
     * character.
	 */
    public WildcardAlias(String storedPattern, String runtimePattern) {
        
		if (null == storedPattern) throw new ArgumentNullException("storedPattern");
		if (null == runtimePattern) throw new ArgumentNullException("runtimePattern");
		
		_storedPattern = new WildcardPattern(storedPattern);
		_runtimePattern = new WildcardPattern(runtimePattern);
    }
    
    /**
     * resolving is done through simple pattern matching  
     */
	public String resolveRuntimeName(String runtimeTypeName) {
		return resolve(_runtimePattern, _storedPattern, runtimeTypeName);
	}
	
    /**
     * resolving is done through simple pattern matching  
     */
	
	public String resolveStoredName(String storedTypeName) {
		return resolve(_storedPattern, _runtimePattern, storedTypeName);
	}

	private String resolve(final WildcardPattern from, final WildcardPattern to, String typeName) {
		String match = from.matches(typeName);
		return match != null
			? to.inject(match)
			: null;
	}	
	
	static class WildcardPattern {
		private String _head;
		private String _tail;

		public WildcardPattern(String pattern) {
			String[] parts = split(pattern);
			
			_head = parts[0];
			_tail = parts[1];
		}

		public String inject(String s) {
			return _head + s + _tail; 
		}

		public String matches(String s) {
			if (!s.startsWith(_head) || !s.endsWith(_tail)) return null;
			return s.substring(_head.length(), s.length()-_tail.length());
		}

		private void invalidPattern() {
			throw new  IllegalArgumentException("only one '*' character");
		}
		
		String[] split(String pattern) {
			int index = pattern.indexOf('*');
			if (-1 == index || index != pattern.lastIndexOf('*')) invalidPattern();
			return new String[] {
					pattern.substring(0, index),
					pattern.substring(index+1)
			};
		}
	}

}
