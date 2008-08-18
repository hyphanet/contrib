/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DataAccessor.java,v 1.1 2008/05/09 03:16:01 chao Exp $
 */

package persist.sqlapp;

import java.util.ArrayList;
import java.util.Iterator;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityIndex;
import com.sleepycat.persist.EntityJoin;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.ForwardCursor;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;

/**
 * The data accessor class for the entity model.
 * 
 * @author chao
 */
class DataAccessor {
    /* Employee Accessors */
    PrimaryIndex<Integer, Employee> employeeById;
    SecondaryIndex<String, Integer, Employee> employeeByName;
    SecondaryIndex<Float, Integer, Employee> employeeBySalary;
    SecondaryIndex<Integer, Integer, Employee> employeeByManagerId;
    SecondaryIndex<Integer, Integer, Employee> employeeByDepartmentId;


    /* Department Accessors */
    PrimaryIndex<Integer, Department> departmentById;
    SecondaryIndex<String, Integer, Department> departmentByName;

    /** Opens all primary and secondary indices. */
    public DataAccessor(EntityStore store)
            throws DatabaseException {


        /* Primary key for Employee classes. */
        employeeById =
                store.getPrimaryIndex(Integer.class, Employee.class);

        /* Secondary key for Employee classes. */
        employeeByName = store.getSecondaryIndex(employeeById,
                                                 String.class,
                                                 "employeeName");
        employeeBySalary = store.getSecondaryIndex(employeeById,
                                                   Float.class,
                                                   "salary");
        employeeByManagerId = store.getSecondaryIndex(employeeById,
                                                      Integer.class,
                                                      "managerId");
        employeeByDepartmentId = store.getSecondaryIndex(employeeById,
                                                         Integer.class,
                                                         "departmentId");

        /* Primary key for Department classes. */
        departmentById =
                store.getPrimaryIndex(Integer.class, Department.class);
        /* Secondary key for Department classes. */
        departmentByName = store.getSecondaryIndex(departmentById,
                                                   String.class,
                                                   "departmentName");
    }


    /**
     * Do prefix query, similar to the SQL statement:
     * <blockquote><pre>
     * SELECT * FROM table WHERE col LIKE 'prefix%';
     * </pre></blockquote>
     *
     * @param index
     * @param prefix
     * @return
     * @throws DatabaseException
     */
    public <V> EntityCursor<V> doPrefixQuery(EntityIndex<String, V> index,
                                             String prefix)
            throws DatabaseException {

        assert (index != null);
        assert (prefix.length() > 0);

        /* Opens a cursor for traversing entities in a key range. */
        char[] ca = prefix.toCharArray();
        final int lastCharIndex = ca.length - 1;
        ca[lastCharIndex]++;
        return doRangeQuery(index, prefix, true, String.valueOf(ca), false);
    }

    /**
     * Do range query, similar to the SQL statement:
     * <blockquote><pre>
     * SELECT * FROM table WHERE col >= fromKey AND col <= toKey;
     * </pre></blockquote>
     *
     * @param index
     * @param fromKey
     * @param fromInclusive
     * @param toKey
     * @param toInclusive
     * @return
     * @throws DatabaseException
     */
    public <K, V> EntityCursor<V> doRangeQuery(EntityIndex<K, V> index,
                                               K fromKey,
                                               boolean fromInclusive,
                                               K toKey,
                                               boolean toInclusive)
            throws DatabaseException {

        assert (index != null);

        /* Opens a cursor for traversing entities in a key range. */
        return index.entities(fromKey,
                              fromInclusive,
                              toKey,
                              toInclusive);
    }

    /**
     * Do a "AND" join on a single primary database, similar to the SQL:
     * <blockquote><pre>
     * SELECT * FROM table WHERE col1 = key1 AND col2 = key2;
     * </pre></blockquote>
     *
     * @param pk
     * @param sk1
     * @param key1
     * @param sk2
     * @param key2
     * @return
     * @throws DatabaseException
     */
    public <PK, SK1, SK2, E> ForwardCursor<E>
            doTwoConditionsJoin(PrimaryIndex<PK, E> pk,
                                SecondaryIndex<SK1, PK, E> sk1,
                                SK1 key1,
                                SecondaryIndex<SK2, PK, E> sk2,
                                SK2 key2)
            throws DatabaseException {

        assert (pk != null);
        assert (sk1 != null);
        assert (sk2 != null);

        EntityJoin<PK, E> join = new EntityJoin<PK, E>(pk);
        join.addCondition(sk1, key1);
        join.addCondition(sk2, key2);

        return join.entities();
    }
    
    /**
     * Do a natural join on Department and Employee by DepartmentId, similar to
     * the SQL:
     * <blockquote><pre>
     * SELECT * FROM employee e, department d
     *  WHERE e.departmentId = d.departmentId;
     * </pre></blockquote>
     * 
     * @param iterable
     * @throws DatabaseException
     */
    public void doDepartmentEmployeeJoin(Iterable<Department> iterable)
            throws DatabaseException {

        /* Do a filter on Department by DepartmentName. */
        Iterator<Department> deptIter = iterable.iterator();
        while (deptIter.hasNext()) {            
            Department dept = deptIter.next();            
            /* Do a natural join on Department and Employee by DepartmentId. */
            EntityCursor<Employee> empCursor = this.employeeByDepartmentId.
                subIndex(dept.getDepartmentId()).entities();
            Iterator<Employee> empIter = empCursor.iterator();
            while (empIter.hasNext()) {
                System.out.println(empIter.next());
            }
            empCursor.close();
        }
        System.out.println();
    }

    /**
     * Query the Employee database by Department's secondary-key: deptName. 
     * 
     * @param deptName
     * @throws DatabaseException
     */
    public void getEmployeeByDeptName(String deptName)
            throws DatabaseException {

        EntityCursor<Department> deptCursor =
            doRangeQuery(this.departmentByName, deptName, true, deptName, true);
        doDepartmentEmployeeJoin(deptCursor);
        deptCursor.close();
    }
    
    /**
     * Query the Employee database by adding a filter on Department's
     * non-secondary-key: deptLocation.
     * 
     * @param deptLocation
     * @throws DatabaseException
     */
    public void getEmployeeByDeptLocation(String deptLocation)
            throws DatabaseException {

        /* Do a filter on Department by DepartmentName. */
        ArrayList<Department> list = new ArrayList<Department>();
        
        Iterator<Department> it =
            this.departmentById.sortedMap().values().iterator();
        while (it.hasNext()) {
            Department dept = it.next();
            if (dept.getLocation().equals(deptLocation)) {
                list.add(dept);
            }
        }
        doDepartmentEmployeeJoin(list);
    }
}
