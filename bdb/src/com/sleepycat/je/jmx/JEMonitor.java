/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: JEMonitor.java,v 1.5.2.1 2007/02/01 14:49:46 cwl Exp $
 */

package com.sleepycat.je.jmx;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;

/**
 * JEMonitor is a JMX MBean which manages a JE environment. 
 * The MBean may be installed as is, or used as a starting point for building
 * a MBean which includes JE support. JEMonitor expects another component in
 * the JVM to configure and open the JE environment; it will only access a JE
 * environment that is already active. It is intended for these use cases:
 * <ul>
 * <li>
 * The application wants to add database monitoring with minimal effort and
 * little knowledge of JMX. Configuring JEMonitor within the JMX container
 * provides monitoring without requiring application code changes. </li>
 * </li>
   <li>
 * An application already supports JMX and wants to add database monitoring
 * without modifying its existing MBean.  The user can configure JEMonitor in
 * the JMX container in conjunction with other application MBeans that are
 * non-overlapping with JE monitoring.  No application code changes are
 * required. </li>
 * </ul>
 * <p>
 * In this MBean, JE management is divided between the JEMonitor class and
 * JEMBeanHelper class. JEMonitor contains an instance of JEMBeanHelper, which
 * knows about JE attributes, operations and notifications. JEMonitor itself
 * has the responsibility of obtaining a temporary handle for the JE
 * environment.
 * <p>
 * The key implementation choice for a JE MBean is the approach taken for
 * accessing the JE environment. Some of the salient considerations are:
 * <ul>
 * <li>Applications may open one or many Environment objects per process 
 * against a given environment.</li> 
 *
 * <li>All Environment handles reference the same underlying JE environment
 * implementation object.</li>

 * <li> The first Environment object instantiated in the process does the real
 * work of configuring and opening the environment. Follow-on instantiations of
 * Environment merely increment a reference count. Likewise,
 * Environment.close() only does real work when it's called by the last
 * Environment object in the process. </li>
 * </ul>
 * <p>
 * Because of these considerations, JEMonitor avoids holding a JE environment
 * handle in order to not impact the environment lifetime. Any environment
 * handles used are held temporarily.
 */
public class JEMonitor implements DynamicMBean {

    private static final String DESCRIPTION = 
        "Monitor an open Berkeley DB, Java Edition environment.";
    
    private MBeanInfo mbeanInfo;    // this MBean's visible interface. 
    private JEMBeanHelper jeHelper; // gets JE management interface.

    /**
     * Instantiate a JEMonitor
     *
     * @param environmentHome home directory of the target JE environment.
     */
    public JEMonitor(String environmentHome) 
        throws MBeanException {

        File environmentDirectory = new File(environmentHome);
        jeHelper = new JEMBeanHelper(environmentDirectory, false);

        Environment targetEnv = getEnvironmentIfOpen();
        try {
            resetMBeanInfo(targetEnv);
        } finally {
            closeEnvironment(targetEnv);
        }
    }

    /**
     * @see DynamicMBean#getAttribute
     */
    public Object getAttribute(String attributeName)
        throws AttributeNotFoundException,
               MBeanException {

    	Object result = null;
        Environment targetEnv = getEnvironmentIfOpen();
        try {
            result =  jeHelper.getAttribute(targetEnv, attributeName);
            targetEnv = checkForMBeanReset(targetEnv);
        } finally {
            /* release resource. */
            closeEnvironment(targetEnv);
        }

        return result;
    }

    /**
     * @see DynamicMBean#setAttribute
     */
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException,
               InvalidAttributeValueException,
               MBeanException {

        Environment targetEnv = getEnvironmentIfOpen();
        try {
            jeHelper.setAttribute(targetEnv, attribute);
        } finally {
            /* release resources. */
            closeEnvironment(targetEnv);
        }
    }

    /**
     * @see DynamicMBean#getAttributes
     */
    public AttributeList getAttributes(String[] attributes) {

        /* Sanity checking. */
	if (attributes == null) {
	    throw new IllegalArgumentException("Attributes cannot be null");
	}

        /* Get each requested attribute. */
        AttributeList results = new AttributeList();
        Environment targetEnv = getEnvironmentIfOpen();

        try {
            for (int i = 0; i < attributes.length; i++) {
                try {
                    String name = attributes[i];
                    Object value = jeHelper.getAttribute(targetEnv, name);
                    
                    /* 
                     * jeHelper may notice that the environment state has
                     * changed. If so, this mbean must update its interface.
                     */
                    targetEnv = checkForMBeanReset(targetEnv);

                    results.add(new Attribute(name, value));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return results;
        } finally {
            try {
                /* release resources. */
                closeEnvironment(targetEnv);
            } catch (MBeanException ignore) {
            	/* ignore */
            }
        }
    }

    /**
     * @see DynamicMBean#setAttributes
     */
    public AttributeList setAttributes(AttributeList attributes) {

        /* Sanity checking. */
	if (attributes == null) {
	    throw new IllegalArgumentException("attribute list can't be null");
	}

        /* Set each attribute specified. */
	AttributeList results = new AttributeList();
        Environment targetEnv = getEnvironmentIfOpen();

        try {
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attr = (Attribute) attributes.get(i);
                try {
                    /* Set new value. */
                    jeHelper.setAttribute(targetEnv, attr);

                    /* 
                     * Add the name and new value to the result list. Be sure
                     * to ask the MBean for the new value, rather than simply
                     * using attr.getValue(), because the new value may not
                     * be same if it is modified according to the JE 
                     * implementation.
                     */
                    String name = attr.getName();
                    Object newValue = jeHelper.getAttribute(targetEnv, name); 
                    results.add(new Attribute(name, newValue));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return results;
        } finally {
            try {
                /* release resources. */
                closeEnvironment(targetEnv);
            } catch (MBeanException ignore) {
            	/* ignore */
            }
        }
    }

    /**
     * @see DynamicMBean#invoke
     */
    public Object invoke(String actionName,
                         Object[] params,
                         String[] signature)
        throws MBeanException {

        Object result = null;
        Environment targetEnv = getEnvironmentIfOpen();
        try {
            result = jeHelper.invoke(targetEnv, actionName,
                                     params, signature);
        } finally {
            /* release resources. */
            closeEnvironment(targetEnv);
        }

        return result;
    }

    /**
     * @see DynamicMBean#getMBeanInfo
     */
    public MBeanInfo getMBeanInfo() {

	return mbeanInfo;
    }

    /**
     * The JEHelper may detect a change in environment attributes that
     * results in a change in management functionality.  Reset the
     * MBeanInfo if needed and refresh the temporary environment handle.
     * 
     * @param targetEnv the temporary JE environment handle
     * @return new environment handle to replace targetEnv. Must be released
     * by the caller.
     */
    private Environment checkForMBeanReset(Environment targetEnv)
        throws MBeanException {

        Environment env = targetEnv;
        if (jeHelper.getNeedReset()) {

            /* Refresh the environmen handle. */
            closeEnvironment(env);
            env = getEnvironmentIfOpen();
            resetMBeanInfo(env);
        }
        return env;
    }

    /**
     * Create the available management interface for this environment.
     * The attributes and operations available vary according to
     * environment configuration.
     *
     * @param targetEnv an open environment handle for the
     * targetted application.
     */
    private void resetMBeanInfo(Environment targetEnv) {
        
        /*
         * Get JE attributes, operation and notification information
         * from JEMBeanHelper. An application may choose to add functionality
         * of its own when constructing the MBeanInfo.
         */
        
        /* Attributes. */
        List attributeList =  jeHelper.getAttributeList(targetEnv);
        MBeanAttributeInfo [] attributeInfo =
            new MBeanAttributeInfo[attributeList.size()];
        attributeList.toArray(attributeInfo);

        /* Constructors. */
        Constructor [] constructors = this.getClass().getConstructors();
        MBeanConstructorInfo [] constructorInfo =
            new MBeanConstructorInfo[constructors.length];
        for (int i = 0; i < constructors.length; i++) {
            constructorInfo[i] =
                new MBeanConstructorInfo(this.getClass().getName(),
                                         constructors[i]);
        }

        /* Operations. */
        List operationList = jeHelper.getOperationList(targetEnv);
        MBeanOperationInfo [] operationInfo =
            new MBeanOperationInfo[operationList.size()];
        operationList.toArray(operationInfo);

        /* Notifications. */
        MBeanNotificationInfo [] notificationInfo =
            jeHelper.getNotificationInfo(targetEnv);

        /* Generate the MBean description. */
        mbeanInfo = new MBeanInfo(this.getClass().getName(),
                                  DESCRIPTION,
                                  attributeInfo,
                                  constructorInfo,
                                  operationInfo,
                                  notificationInfo);
    }

    /**
     * This MBean has the policy of only accessing an environment when
     * it has already been configured and opened by other
     * application threads.
     *
     * @return a valid Environment or null if the environment is not open
     */
    protected Environment getEnvironmentIfOpen() {

        return jeHelper.getEnvironmentIfOpen();
    }

    /**
     * Be sure to close Environments when they are no longer used, because
     * they pin down resources.
     * 
     * @param targetEnv the open environment. May be null.
     */
    protected void closeEnvironment(Environment targetEnv) 
        throws MBeanException {

        try {
            if (targetEnv != null) {
                targetEnv.close();
            }
        } catch (DatabaseException e) {
            throw new MBeanException(e);
        }
    }
}
