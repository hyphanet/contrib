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
package com.db4o.internal.activation;

import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * Activates an object graph to a specific depth respecting any
 * activation configuration settings that might be in effect.
 */
public class LegacyActivationDepth extends ActivationDepthImpl {

	private final int _depth;
	
	public LegacyActivationDepth(int depth) {
		this(depth, ActivationMode.ACTIVATE);
	}

	public LegacyActivationDepth(int depth, ActivationMode mode) {
		super(mode);
		_depth = depth;
	}

	public ActivationDepth descend(ClassMetadata metadata) {
		if (null == metadata) {
			return new LegacyActivationDepth(_depth -1 , _mode);
		}
		return new LegacyActivationDepth(descendDepth(metadata), _mode);
	}

	private int descendDepth(ClassMetadata metadata) {
		int depth = configuredActivationDepth(metadata) - 1;
		if (metadata.isValueType()) {
			// 	We also have to instantiate structs completely every time.
			return Math.max(1, depth);
		}
		return depth;
	}

	private int configuredActivationDepth(ClassMetadata metadata) {
		Config4Class config = metadata.configOrAncestorConfig();
		if (config != null && _mode.isActivate()) {
			return config.adjustActivationDepth(_depth);
		}
		return _depth;
	}

	public boolean requiresActivation() {
		return _depth > 0;
	}

}
