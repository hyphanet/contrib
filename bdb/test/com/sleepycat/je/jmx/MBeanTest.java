/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MBeanTest.java,v 1.14.2.1 2007/02/01 14:50:11 cwl Exp $
 */

package com.sleepycat.je.jmx;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import javax.management.Attribute;
import javax.management.DynamicMBean;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

import jmx.JEApplicationMBean;
import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

/**
 * Instantiate and exercise the JEMonitor.
 */
public class MBeanTest extends TestCase {
    
    private static final boolean DEBUG = true;
    private File envHome;
    private String environmentDir;

    public MBeanTest() {
        environmentDir = System.getProperty(TestUtils.DEST_DIR); 
        envHome = new File(environmentDir);
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("tearDown", envHome, true);
    }

    /**
     * Test an mbean which is prohibited from configuring and opening an 
     * environment.
     */
    public void testNoOpenMBean() 
        throws Throwable {
        
        Environment env = null;
        try {
            
            /* Environment is not open, and we can't open. */
            DynamicMBean mbean = new JEMonitor(environmentDir);
            validateGetters(mbean, 2);
            validateOperations(mbean, 0, true, null, null);
            
            /* Now open the environment transactionally by other means. */
            env = openEnv(true);
            validateGetters(mbean, 2 ); // alas, takes two refreshes to
            validateGetters(mbean, 9 ); // see the change.
            validateOperations(mbean, 8, true, null, null);

            /* Close the environment. */
            env.close();
            validateGetters(mbean, 2);
            validateOperations(mbean, 0, true, null, null);

            /* 
             * Try this kind of mbean against an environment that's already
             * open.
             */
            env = openEnv(true);
            mbean = new JEMonitor(environmentDir);
            validateGetters(mbean, 9 ); // see the change.
            validateOperations(mbean, 8, true, null, null);

            /* 
             * Getting database stats against a non-existing db ought to
             * throw an exception.
             */
            try {
                validateOperations(mbean, 8, true, "bozo", null);
                fail("Should not have run stats on a non-existent db");
            } catch (MBeanException expected) {
                // ignore
            }

            /*
             * Make sure the vanilla db open within the helper can open
             * a db created with a non-default configuration.
             */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            Database db = env.openDatabase(null, "bozo", dbConfig);

            /* insert a record. */
            DatabaseEntry entry = new DatabaseEntry();
            IntegerBinding.intToEntry(1, entry);
            db.put(null, entry, entry);

            validateOperations(mbean, 8, true, "bozo", new String [] {"bozo"});
            db.close();

            env.close();
            validateGetters(mbean, 2);
            validateOperations(mbean, 0, true, null, null);

            checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();
            if (env != null) {
                env.close();
            }
            throw t;
        } 
    }

    /**
     * MBean which can configure and open an environment.
     */
    public void testOpenableBean() 
        throws Throwable {

        Environment env = null;
        try {
            /* Environment is not open, and we can open. */
            env = openEnv(false);
            env.close();

            DynamicMBean mbean = new JEApplicationMBean(environmentDir);
            validateGetters(mbean, 5);
            validateOperations(mbean, 1, false, null, null); // don't invoke
            
            /* Open the environment. */
            mbean.invoke(JEApplicationMBean.OP_OPEN, null, null);
                         
            validateGetters(mbean, 7 );
            validateOperations(mbean, 8, true, null, null);

            /* 
             * The last call to validateOperations ended up closing the
             * environment.
             */
            validateGetters(mbean, 5);
            validateOperations(mbean, 1, false, null, null);

            /* Should be no open handles. */
            checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();
            
            if (env != null) {
                env.close();
            }
            throw t;
        } 
    }

    /**
     * Exercise setters. 
     */
    public void testMBeanSetters() 
        throws Throwable {

        Environment env = null;
        try {
            /* Mimic an application by opening an environment. */
            env = openEnv(false);

            /* Open an mbean and set the environment home. */
            DynamicMBean mbean = new JEMonitor(environmentDir);
            
            /* 
             * Try setting different attributes. Check against the
             * initial value, and the value after setting.
             */
            EnvironmentConfig config = env.getConfig();
            Class configClass = config.getClass();

            Method getCacheSize = configClass.getMethod("getCacheSize", (Class []) null);
            checkAttribute(env,
                           mbean,
                           getCacheSize,
                           JEMBeanHelper.ATT_CACHE_SIZE,
                           new Long(100000)); // new value

            Method getCachePercent =
                configClass.getMethod("getCachePercent", (Class []) null);
            checkAttribute(env,
                           mbean,
                           getCachePercent,
                           JEMBeanHelper.ATT_CACHE_PERCENT,
                           new Integer(10));
            env.close();

            checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();

            if (env != null) {
                env.close();
            }

            throw t;
        } 
    }

    private void checkAttribute(Environment env,
                                DynamicMBean mbean,
                                Method configMethod,
                                String attributeName,
                                Object newValue)
        throws Exception {
        /* check starting value. */
        EnvironmentConfig config = env.getConfig();
        Object result = configMethod.invoke(config, (Object []) null);
        assertTrue(!result.toString().equals(newValue.toString()));

        /* set through mbean */
        mbean.setAttribute(new Attribute(attributeName, newValue));

        /* check present environment config. */
        config = env.getConfig();
        assertEquals(newValue.toString(), 
                     configMethod.invoke(config, (Object []) null).toString());

        /* check through mbean. */
        Object mbeanNewValue = mbean.getAttribute(attributeName);
        assertEquals(newValue.toString(), mbeanNewValue.toString());
    }

    /*
     */
    private void validateGetters(DynamicMBean mbean, 
                                 int numExpectedAttributes)
        throws Throwable {

        MBeanInfo info = mbean.getMBeanInfo();

        MBeanAttributeInfo [] attrs = info.getAttributes();

        /* test getters. */
        int attributesWithValues = 0;
        for (int i = 0; i < attrs.length; i++) {
            String name = attrs[i].getName();
            Object result = mbean.getAttribute(name);
            if (DEBUG) {
                System.out.println("Attribute " + i + 
                                   " name=" + name +
                                   " result=" + result);
            }
            if (result != null) {
                attributesWithValues++;
                checkObjectType
                    ("Attribute", name, attrs[i].getType(), result);
            }
        }

        assertEquals(numExpectedAttributes, attributesWithValues);
    }

    /* 
     * Check that there are the expected number of operations.
     * If specified, invoke and check the results.
     * @param tryInvoke if true, invoke the operations. 
     * @param databaseName if not null, execute the database specific 
     * operations using the database name.
     */
    private void validateOperations(DynamicMBean mbean, 
                                    int numExpectedOperations,
                                    boolean tryInvoke,
                                    String databaseName,
                                    String[] expectedDatabases) 
        throws Throwable {

        MBeanInfo info = mbean.getMBeanInfo();

        MBeanOperationInfo [] ops = info.getOperations();
        if (DEBUG) {
            for (int i = 0; i < ops.length; i++) {
                System.out.println("op: " + ops[i].getName());
            }
        }
        assertEquals(numExpectedOperations, ops.length);
            
        if (tryInvoke) {
            for (int i = 0; i < ops.length; i++) {
                String opName = ops[i].getName();

                /* Try the per-database operations if specified. */
                if ((databaseName != null) &&
                    opName.equals(JEMBeanHelper.OP_DB_STAT)) {
                    /* invoke with the name of the database. */
                    Object result = mbean.invoke
                        (opName,
                         new Object [] {null, null, databaseName},
                         null);
                    assertTrue(result instanceof BtreeStats);
                    checkObjectType
                        ("Operation", opName, ops[i].getReturnType(), result);
                }

                if ((expectedDatabases != null) &&
                    opName.equals(JEMBeanHelper.OP_DB_NAMES)) {
                    Object result = mbean.invoke(opName, null, null);
                    List names = (List) result;
                    assertTrue(Arrays.equals(expectedDatabases,
                                             names.toArray()));
                    checkObjectType
                        ("Operation", opName, ops[i].getReturnType(), result);
                }

                /* 
                 * Also invoke all operations with null params, to sanity
                 * check.
                 */
                Object result = mbean.invoke(opName, null, null);
                if (result != null) {
                    checkObjectType
                        ("Operation", opName, ops[i].getReturnType(), result);
                }
            }
        }
    }

    /**
     * Checks that all parameters and return values are Serializable to
     * support JMX over RMI.
     */
    public void testSerializable()
        throws JMException, DatabaseException {

        /* Create and close the environment. */
        Environment env = openEnv(false);
        env.close();

        /* Test without an open environment. */
        DynamicMBean mbean = new JEApplicationMBean(environmentDir);
        doTestSerializable(mbean);

        /* Test with an open environment. */
        mbean.invoke(JEApplicationMBean.OP_OPEN, null, null);
        doTestSerializable(mbean);

        /* Close. */
        mbean.invoke(JEApplicationMBean.OP_CLOSE, null, null);
    }

    /**
     * Checks that all types for the given mbean are serializable.
     */
    private void doTestSerializable(DynamicMBean mbean) {

        MBeanInfo info = mbean.getMBeanInfo();

        MBeanAttributeInfo [] attrs = info.getAttributes();
        for (int i = 0; i < attrs.length; i++) {
            checkSerializable
                ("Attribute", attrs[i].getName(), attrs[i].getType());
        }

        MBeanOperationInfo [] ops = info.getOperations();
        for (int i = 0; i < ops.length; i += 1) {
            checkSerializable
                ("Operation",
                 ops[i].getName() + " return type",
                 ops[i].getReturnType());
            MBeanParameterInfo[] params = ops[i].getSignature();
            for (int j = 0; j < params.length; j += 1) {
                checkSerializable
                    ("Operation",
                     ops[i].getName() + " parameter " + j,
                     params[j].getType());
            }
        }

        MBeanConstructorInfo [] ctors = info.getConstructors();
        for (int i = 0; i < ctors.length; i++) {
            MBeanParameterInfo[] params = ctors[i].getSignature();
            for (int j = 0; j < params.length; j += 1) {
                checkSerializable
                    ("Constructor",
                     ctors[i].getName() + " parameter " + j,
                     params[j].getType());
            }
        }

        MBeanNotificationInfo [] notifs = info.getNotifications();
        for (int i = 0; i < notifs.length; i++) {
            String[] types = notifs[i].getNotifTypes();
            for (int j = 0; j < types.length; j += 1) {
                checkSerializable
                    ("Notification", notifs[i].getName(), types[j]);
            }
        }
    }

    /**
     * Checks that a given type is serializable.
     */
    private void checkSerializable(String identifier,
                                   String name,
                                   String type) {

        if ("void".equals(type)) {
            return;
        }
        String msg = identifier + ' ' + name + " is type " + type;
        try {
            Class cls = Class.forName(type);
            if (!Serializable.class.isAssignableFrom(cls)) {
                fail(msg + " -- not Serializable");
            }
        } catch (Exception e) {
            fail(msg + " -- " + e);
        }
    }

    /**
     * Checks that an object (parameter or return value) is of the type
     * specified in the BeanInfo.
     */
    private void checkObjectType(String identifier,
                                 String name,
                                 String type,
                                 Object object) {

        String msg = identifier + ' ' + name + " is type " + type;
        if ("void".equals(type)) {
            assertNull(msg + "-- should be null", object);
            return;
        }
        try {
            Class cls = Class.forName(type);
            assertTrue
                (msg + " -- object class is " + object.getClass().getName(),
                 cls.isAssignableFrom(object.getClass()));
        } catch (Exception e) {
            fail(msg + " -- " + e);
        }

        /*
         * The true test of serializable is to serialize.  This checks the
         * a elements of a list, for example.
         */
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
        } catch (Exception e) {
            fail(msg + " -- " + e);
        }
    }

    private void checkForNoOpenHandles(String environmentDir) {
        File envFile = new File(environmentDir);
        Environment testEnv = DbInternal.getEnvironmentShell(envFile);
        assertTrue(testEnv == null);
    }

    /*
     * Helper to open an environment. 
     */
    private Environment openEnv(boolean openTransactionally) 
        throws DatabaseException {
        
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(openTransactionally);
        return new Environment(envHome, envConfig);
    }

}
