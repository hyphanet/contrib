/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: SupplierKey.java,v 1.12 2008/01/07 14:28:41 cwl Exp $
 */

package collections.ship.tuple;

/**
 * A SupplierKey serves as the key in the key/data pair for a supplier entity.
 *
 * <p> In this sample, SupplierKey is bound to the key's tuple storage entry
 * using a TupleBinding.  Because it is not used directly as storage data, it
 * does not need to be Serializable. </p>
 *
 * @author Mark Hayes
 */
public class SupplierKey {

    private String number;

    public SupplierKey(String number) {

        this.number = number;
    }

    public final String getNumber() {

        return number;
    }

    public String toString() {

        return "[SupplierKey: number=" + number + ']';
    }
}
