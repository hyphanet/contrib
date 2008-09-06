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
package com.db4o.types;

/**
 * marks objects as second class objects.
 * 
 * <br><br>Currently this interface is for internal use only
 * to help discard com.db4o.config.Entry objects in the
 * Defragment process.
 * <br><br>For future versions this interface is planned to 
 * mark objects that:<br>
 * - are not to be held in the reference mechanism<br>
 * - should always be activated with their parent objects<br>
 * - should always be deleted with their parent objects<br>
 * - should always be deleted if they are not referenced any
 * longer.<br>
 */
public interface SecondClass extends Db4oType{
}
