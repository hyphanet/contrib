/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ForeignKeyDeleteAction.java,v 1.11 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

/**
 * The action taken when a referenced record in the foreign key database is
 * deleted.
 *
 * <p>The delete action applies to a secondary database that is configured to
 * have a foreign key integrity constraint.  The delete action is specified by
 * calling {@link SecondaryConfig#setForeignKeyDeleteAction}.</p>
 *
 * <p>When a record in the foreign key database is deleted, it is checked to
 * see if it is referenced by any record in the associated secondary database.
 * If the key is referenced, the delete action is applied.  By default, the
 * delete action is {@link #ABORT}.</p>
 *
 * @see SecondaryConfig
 */
public class ForeignKeyDeleteAction {

    private String name;

    private ForeignKeyDeleteAction(String name) {
	this.name = name;
    }

    /**
     * When a referenced record in the foreign key database is deleted, abort
     * the transaction by throwing a <code>DatabaseException</code>.
     */
    public final static ForeignKeyDeleteAction ABORT =
	new ForeignKeyDeleteAction("ABORT");

    /**
     * When a referenced record in the foreign key database is deleted, delete
     * the primary database record that references it.
     */
    public final static ForeignKeyDeleteAction CASCADE =
	new ForeignKeyDeleteAction("CASCADE");

    /**
     * When a referenced record in the foreign key database is deleted, set the
     * reference to null in the primary database record that references it,
     * thereby deleting the secondary key. @see ForeignKeyNullifier @see
     * ForeignMultiKeyNullifier
     */
    public final static ForeignKeyDeleteAction NULLIFY =
	new ForeignKeyDeleteAction("NULLIFY");

    @Override
    public String toString() {
	return "ForeignKeyDeleteAction." + name;
    }
}
