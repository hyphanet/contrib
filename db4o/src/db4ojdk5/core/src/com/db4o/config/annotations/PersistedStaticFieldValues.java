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
package com.db4o.config.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * turns on storing static field values for this class. <br>
 * <br>
 * By default, static field values of classes are not stored to the database
 * file. By decoration a specific class with this switch, all non-simple-typed
 * static field values of this class are stored the first time an object of the
 * class is stored, and restored, every time a database file is opened
 * afterwards. <br>
 * <br>
 * This annotation will be ignored for simple types. <br>
 * <br>
 * Use {@code @PersistedStaticFieldValues } for constant static object members.
 * <br>
 * <br>
 * <br>
 * <br>
 * This option will slow down the process of opening database files and the
 * stored objects will occupy space in the database file.
 * @exclude
 * @decaf.ignore
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistedStaticFieldValues {
}