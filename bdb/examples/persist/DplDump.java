/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DplDump.java,v 1.3 2008/01/07 14:28:43 cwl Exp $
 */

package persist;

import java.io.File;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.EntityMetadata;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.raw.RawObject;
import com.sleepycat.persist.raw.RawStore;
import com.sleepycat.persist.raw.RawType;

/**
 * Dumps a store or all stores to standard output in raw XML format.  This
 * sample is intended to be modifed to dump in application specific ways.
 * @see #usage
 */
public class DplDump {

    private File envHome;
    private String storeName;
    private boolean dumpMetadata;
    private Environment env;

    public static void main(String[] args) {
        try {
            DplDump dump = new DplDump(args);
            dump.open();
            dump.execute();
            dump.close();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private DplDump(String[] args) {

        for (int i = 0; i < args.length; i += 1) {
            String name = args[i];
            String val = null;
            if (i < args.length - 1 && !args[i + 1].startsWith("-")) {
                i += 1;
                val = args[i];
            }
            if (name.equals("-h")) {
                if (val == null) {
                    usage("No value after -h");
                }
                envHome = new File(val);
            } else if (name.equals("-s")) {
                if (val == null) {
                    usage("No value after -s");
                }
                storeName = val;
            } else if (name.equals("-meta")) {
                dumpMetadata = true;
            } else {
                usage("Unknown arg: " + name);
            }
        }

        if (envHome == null) {
            usage("-h not specified");
        }
    }

    private void usage(String msg) {

        if (msg != null) {
            System.out.println(msg);
        }

        System.out.println
            ("usage:" +
             "\njava "  + DplDump.class.getName() +
             "\n   -h <envHome>" +
             "\n      # Environment home directory" +
             "\n  [-meta]" +
             "\n      # Dump metadata; default: false" +
             "\n  [-s <storeName>]" +
             "\n      # Store to dump; default: dump all stores");

        System.exit(2);
    }

    private void open()
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setReadOnly(true);
        env = new Environment(envHome, envConfig);
    }

    private void close()
        throws DatabaseException {

        env.close();
    }

    private void execute()
        throws DatabaseException {

        if (storeName != null) {
            dump();
        } else {
            for (String name : EntityStore.getStoreNames(env)) {
                storeName = name;
                dump();
            }
        }
    }

    private void dump()
        throws DatabaseException {

        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setReadOnly(true);
        RawStore store = new RawStore(env, storeName, storeConfig);

        EntityModel model = store.getModel();
        for (String clsName : model.getKnownClasses()) {
            EntityMetadata meta = model.getEntityMetadata(clsName);
            if (meta != null) {
                if (dumpMetadata) {
                    for (RawType type : model.getAllRawTypeVersions(clsName)) {
                        System.out.println(type);
                    }
                } else {
                    PrimaryIndex<Object,RawObject> index =
                        store.getPrimaryIndex(clsName);
                    EntityCursor<RawObject> entities = index.entities();
                    for (RawObject entity : entities) {
                        System.out.println(entity);
                    }
                    entities.close();
                }
            }
        }

        store.close();
    }
}
