/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: SampleDatabase.java,v 1.26 2008/01/07 14:28:38 cwl Exp $
 */

package collections.ship.basic;

import java.io.File;
import java.io.FileNotFoundException;

import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

/**
 * SampleDatabase defines the storage containers, indices and foreign keys
 * for the sample database.
 *
 * @author Mark Hayes
 */
public class SampleDatabase {

    private static final String CLASS_CATALOG = "java_class_catalog";
    private static final String SUPPLIER_STORE = "supplier_store";
    private static final String PART_STORE = "part_store";
    private static final String SHIPMENT_STORE = "shipment_store";

    private Environment env;
    private Database partDb;
    private Database supplierDb;
    private Database shipmentDb;
    private StoredClassCatalog javaCatalog;

    /**
     * Open all storage containers, indices, and catalogs.
     */
    public SampleDatabase(String homeDirectory)
        throws DatabaseException, FileNotFoundException {

        // Open the Berkeley DB environment in transactional mode.
        //
        System.out.println("Opening environment in: " + homeDirectory);
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(new File(homeDirectory), envConfig);

        // Set the Berkeley DB config for opening all stores.
        //
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        // Create the Serial class catalog.  This holds the serialized class
        // format for all database records of serial format.
        //
        Database catalogDb = env.openDatabase(null, CLASS_CATALOG, dbConfig);
        javaCatalog = new StoredClassCatalog(catalogDb);

        // Open the Berkeley DB database for the part, supplier and shipment
        // stores.  The stores are opened with no duplicate keys allowed.
        //
        partDb = env.openDatabase(null, PART_STORE, dbConfig);

        supplierDb = env.openDatabase(null, SUPPLIER_STORE, dbConfig);

        shipmentDb = env.openDatabase(null, SHIPMENT_STORE, dbConfig);
    }

    /**
     * Return the storage environment for the database.
     */
    public final Environment getEnvironment() {

        return env;
    }

    /**
     * Return the class catalog.
     */
    public final StoredClassCatalog getClassCatalog() {

        return javaCatalog;
    }

    /**
     * Return the part storage container.
     */
    public final Database getPartDatabase() {

        return partDb;
    }

    /**
     * Return the supplier storage container.
     */
    public final Database getSupplierDatabase() {

        return supplierDb;
    }

    /**
     * Return the shipment storage container.
     */
    public final Database getShipmentDatabase() {

        return shipmentDb;
    }

    /**
     * Close all databases and the environment.
     */
    public void close()
        throws DatabaseException {

        partDb.close();
        supplierDb.close();
        shipmentDb.close();
        // And don't forget to close the catalog and the environment.
        javaCatalog.close();
        env.close();
    }
}
