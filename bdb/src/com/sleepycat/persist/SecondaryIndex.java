/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: SecondaryIndex.java,v 1.11 2006/09/12 19:17:01 cwl Exp $
 */

package com.sleepycat.persist;

import java.util.Map;
import java.util.SortedMap;

import com.sleepycat.bind.EntityBinding;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;

/**
 * The secondary index for an entity class and a secondary key.
 *
 * <p>{@code SecondaryIndex} objects are thread-safe.  Multiple threads may
 * safely call the methods of a shared {@code SecondaryIndex} object.</p>
 *
 * <p>{@code SecondaryIndex} implements {@link EntityIndex} to map the
 * secondary key type (SK) to the entity type (E).  The {@link SecondaryKey}
 * annotation may be used to define a secondary key.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=MANY_TO_ONE)}
 *     String department;
 *
 *     String name;
 *
 *     private Employee() {}
 * }</pre>
 *
 * <p>For more information on defining secondary keys, see {@link
 * SecondaryKey}.</p>
 *
 * <p>Before obtaining a {@code SecondaryIndex}, the {@link PrimaryIndex} must
 * be obtained for the entity class.  To obtain the {@code SecondaryIndex} call
 * {@link EntityStore#getSecondaryIndex EntityStore.getSecondaryIndex}, passing
 * the primary index, the secondary key class and the secondary key name.  For
 * example:</p>
 *
 * <pre class="code">
 * EntityStore store = new EntityStore(...);
 *
 * {@code PrimaryIndex<Long,Employee>} primaryIndex =
 *     store.getPrimaryIndex(Long.class, Employee.class);
 *
 * {@code SecondaryIndex<String,Long,Employee>} secondaryIndex =
 *     store.getSecondaryIndex(primaryIndex, String.class, "department");</pre>
 *
 * <p>Since {@code SecondaryIndex} implements the {@link EntityIndex}
 * interface, it shares the common index methods for retrieving and deleting
 * entities, opening cursors and using transactions.  See {@link EntityIndex}
 * for more information on these topics.</p>
 *
 * <p>{@code SecondaryIndex} does <em>not</em> provide methods for inserting
 * and updating entities.  That must be done using the {@link
 * PrimaryIndex}.</p>
 *
 * <p>Note that a {@code SecondaryIndex} has three type parameters {@code
 * <String,Long,Employee>} while a {@link PrimaryIndex} has only two type
 * parameters {@code <Long,Employee>}.  This is because a {@code
 * SecondaryIndex} has an extra level of mapping:  It maps from secondary key
 * to primary key, and then from primary key to entity.  For example, consider
 * this entity:</p>
 *
 * <p><table class="code" border="1">
 *   <tr><th>ID</th><th>Department</th><th>Name</th></tr>
 *   <tr><td>1</td><td>Engineering</td><td>Jane Smith</td></tr>
 * </table></p>
 *
 * <p>The {@link PrimaryIndex} maps from id directly to the entity, or from
 * primary key 1 to the "Jane Smith" entity in the example.  The {@code
 * SecondaryIndex} maps from department to id, or from secondary key
 * "Engineering" to primary key 1 in the example, and then uses the {@code
 * PrimaryIndex} to map from the primary key to the entity.</p>
 *
 * <p>Because of this extra type parameter and extra level of mapping, a {@code
 * SecondaryIndex} can provide more than one mapping, or view, of the entities
 * in the primary index.  The main mapping of a {@code SecondaryIndex} is to
 * map from secondary key (SK) to entity (E), or in the example, from the
 * String department key to the Employee entity.  The {@code SecondaryIndex}
 * itself, by implementing {@code EntityIndex<SK,E>}, provides this
 * mapping.</p>
 *
 * <p>The second mapping provided by {@code SecondaryIndex} is from secondary
 * key (SK) to primary key (PK), or in the example, from the String department
 * key to the Long id key.  The {@link #keysIndex} method provides this
 * mapping.  When accessing the keys index, the primary key is returned rather
 * than the entity.  When only the primary key is needed and not the entire
 * entity, using the keys index is less expensive than using the secondary
 * index because the primary index does not have to be accessed.</p>
 *
 * <p>The third mapping provided by {@code SecondaryIndex} is from primary key
 * (PK) to entity (E), for the subset of entities having a given secondary key
 * (SK).  This mapping is provided by the {@link #subIndex} method.  A
 * sub-index is convenient when you are interested in working with the subset
 * of entities having a particular secondary key value, for example, all
 * employees in a given department.</p>
 *
 * <p>All three mappings, along with the mapping provided by the {@link
 * PrimaryIndex}, are shown using example data in the {@link EntityIndex}
 * interface documentation.  See {@link EntityIndex} for more information.</p>
 *
 * <h3>One-to-One Relationships</h3>
 *
 * <p>A {@link Relationship#ONE_TO_ONE ONE_TO_ONE} relationship, although less
 * common than other types of relationships, is the simplest type of
 * relationship.  A single entity is related to a single secondary key value.
 * For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=ONE_TO_ONE)}
 *     String ssn;
 *
 *     String name;
 *
 *     private Employee() {}
 * }
 *
 * {@code SecondaryIndex<String,Long,Employee>} employeeBySsn =
 *     store.getSecondaryIndex(primaryIndex, String.class, "ssn");</pre>
 *
 * <p>With a {@link Relationship#ONE_TO_ONE ONE_TO_ONE} relationship, the
 * secondary key must be unique; in other words, no two entities may have the
 * same secondary key value.  If an attempt is made to store and entity having
 * the same secondary key value as another existing entity, a {@link
 * DatabaseException} will be thrown.</p>
 *
 * <p>Because the secondary key is unique, it is useful to lookup entities by
 * secondary key using {@link EntityIndex#get}.  For example:</p>
 *
 * <pre class="code">
 * Employee employee = employeeBySsn.get(mySsn);</pre>
 *
 * <h3>Many-to-One Relationships</h3>
 *
 * <p>A {@link Relationship#MANY_TO_ONE MANY_TO_ONE} relationship is the most
 * common type of relationship.  One or more entities is related to a single
 * secondary key value.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=MANY_TO_ONE)}
 *     String department;
 *
 *     String name;
 *
 *     private Employee() {}
 * }
 *
 * {@code SecondaryIndex<String,Long,Employee>} employeeByDepartment =
 *     store.getSecondaryIndex(primaryIndex, String.class, "department");</pre>
 *
 * <p>With a {@link Relationship#MANY_TO_ONE MANY_TO_ONE} relationship, the
 * secondary key is not required to be unique; in other words, more than one
 * entity may have the same secondary key value.  In this example, more than
 * one employee may belong to the same department.</p>
 *
 * <p>The most convenient way to access the employees in a given department is
 * by using a sub-index.  For example:</p>
 *
 * <pre class="code">
 * {@code EntityIndex<Long,Entity>} subIndex = employeeByDepartment.subIndex(myDept);
 * {@code EntityCursor<Employee>} cursor = subIndex.entities();
 * try {
 *     for (Employee entity : cursor) {
 *         // Do something with the entity...
 *     }
 * } finally {
 *     cursor.close();
 * }</pre>
 *
 * <h3>One-to-Many Relationships</h3>
 *
 * <p>In a {@link Relationship#ONE_TO_MANY ONE_TO_MANY} relationship, a single
 * entity is related to one or more secondary key values.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=ONE_TO_MANY)}
 *     {@literal Set<String> emailAddresses = new HashSet<String>;}
 *
 *     String name;
 *
 *     private Employee() {}
 * }
 *
 * {@code SecondaryIndex<String,Long,Employee>} employeeByEmail =
 *     store.getSecondaryIndex(primaryIndex, String.class, "emailAddresses");</pre>
 *
 * <p>With a {@link Relationship#ONE_TO_MANY ONE_TO_MANY} relationship, the
 * secondary key must be unique; in other words, no two entities may have the
 * same secondary key value.  In this example, no two employees may have the
 * same email address.  If an attempt is made to store and entity having the
 * same secondary key value as another existing entity, a {@link
 * DatabaseException} will be thrown.</p>
 *
 * <p>Because the secondary key is unique, it is useful to lookup entities by
 * secondary key using {@link EntityIndex#get}.  For example:</p>
 *
 * <pre class="code">
 * Employee employee = employeeByEmail.get(myEmailAddress);</pre>
 *
 * <p>The secondary key field for a {@link Relationship#ONE_TO_MANY
 * ONE_TO_MANY} relationship must be an array or collection type.  To access
 * the email addresses of an employee, simply access the collection field
 * directly.  For example:</p>
 *
 * <pre class="code">
 * Employee employee = primaryIndex.get(1); // Get the entity by primary key
 * employee.emailAddresses.add(myNewEmail); // Add an email address
 * primaryIndex.putNoReturn(1, employee);   // Update the entity</pre>
 *
 * <h3>Many-to-Many Relationships</h3>
 *
 * <p>In a {@link Relationship#MANY_TO_MANY MANY_TO_MANY} relationship, a one
 * or more entities is related to one or more secondary key values.  For
 * example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=MANY_TO_MANY)}
 *     {@literal Set<String> organizations = new HashSet<String>;}
 *
 *     String name;
 *
 *     private Employee() {}
 * }
 *
 * {@code SecondaryIndex<String,Long,Employee>} employeeByOrganization =
 *     store.getSecondaryIndex(primaryIndex, String.class, "organizations");</pre>
 *
 * <p>With a {@link Relationship#MANY_TO_MANY MANY_TO_MANY} relationship, the
 * secondary key is not required to be unique; in other words, more than one
 * entity may have the same secondary key value.  In this example, more than
 * one employee may belong to the same organization.</p>
 *
 * <p>The most convenient way to access the employees in a given organization
 * is by using a sub-index.  For example:</p>
 *
 * <pre class="code">
 * {@code EntityIndex<Long,Entity>} subIndex = employeeByOrganization.subIndex(myOrg);
 * {@code EntityCursor<Employee>} cursor = subIndex.entities();
 * try {
 *     for (Employee entity : cursor) {
 *         // Do something with the entity...
 *     }
 * } finally {
 *     cursor.close();
 * }</pre>
 *
 * <p>The secondary key field for a {@link Relationship#MANY_TO_MANY
 * MANY_TO_MANY} relationship must be an array or collection type.  To access
 * the organizations of an employee, simply access the collection field
 * directly.  For example:</p>
 *
 * <pre class="code">
 * Employee employee = primaryIndex.get(1); // Get the entity by primary key
 * employee.organizations.remove(myOldOrg); // Remove an organization
 * primaryIndex.putNoReturn(1, employee);   // Update the entity</pre>
 *
 * <h3>Foreign Key Constraints</h3>
 *
 * <p>In all the examples above the secondary key is treated only as a simple
 * value, such as a {@code String} department field.  In many cases, that is
 * sufficient.  But in other cases, you may wish to constrain the secondary
 * keys of one entity class to be valid primary keys of another entity
 * class.  For example, a Department entity may also be defined:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Department {
 *
 *     {@literal @PrimaryKey}
 *     String name;
 *
 *     String missionStatement;
 *
 *     private Department() {}
 * }</pre>
 *
 * <p>You may wish to constrain the department field values of the Employee
 * class in the examples above to be valid primary keys of the Department
 * entity class.  In other words, you may wish to ensure that the department
 * field of an Employee will always refer to a valid Department entity.</p>
 *
 * <p>You can implement this constraint yourself by validating the department
 * field before you store an Employee.  For example:</p>
 *
 * <pre class="code">
 * {@code PrimaryIndex<String,Department>} departmentIndex =
 *     store.getPrimaryIndex(String.class, Department.class);
 *
 * void storeEmployee(Employee employee) throws DatabaseException {
 *     if (departmentIndex.contains(employee.department)) {
 *         primaryIndex.putNoReturn(employee);
 *     } else {
 *         throw new IllegalArgumentException("Department does not exist: " +
 *                                            employee.department);
 *     }
 * }</pre>
 *
 * <p>Or, instead you could define the Employee department field as a foreign
 * key, and this validation will be done for you when you attempt to store the
 * Employee entity.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@literal @SecondaryKey(relate=MANY_TO_ONE, relatedEntity=Department.class)}
 *     String department;
 *
 *     String name;
 *
 *     private Employee() {}
 * }</pre>
 *
 * <p>The {@code relatedEntity=Department.class} above defines the department
 * field as a foreign key that refers to a Department entity.  Whenever a
 * Employee entity is stored, its department field value will be checked to
 * ensure that a Department entity exists with that value as its primary key.
 * If no such Department entity exists, then a {@link DatabaseException} is
 * thrown, causing the transaction to be aborted (assuming that transactions
 * are used).</p>
 *
 * <p>This begs the question:  What happens when a Department entity is deleted
 * while one or more Employee entities have department fields that refer to
 * the deleted department's primary key?  If the department were allowed to be
 * deleted, the foreign key constraint for the Employee department field would
 * be violated, because the Employee department field would refer to a
 * department that does not exist.</p>
 *
 * <p>By default, when this situation arises the system does not allow the
 * department to be deleted.  Instead, a {@link DatabaseException} is thrown,
 * causing the transaction to be aborted.  In this case, in order to delete a
 * department, the department field of all Employee entities must first be
 * updated to refer to a different existing department, or set to null.  This
 * is the responsibility of the application.</p>
 *
 * <p>There are two alternatives for handling deletion of a Department entity.
 * These alternatives are configured using the {@link
 * SecondaryKey#onRelatedEntityDelete} annotation property.  Setting this
 * property to {@link DeleteAction#NULLIFY} causes the Employee department
 * field to be automatically set to null when the department they refer to is
 * deleted.  This may or may not be desirable, depending on application
 * policies.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Employee {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@code @SecondaryKey(relate=MANY_TO_ONE, relatedEntity=Department.class,
 *                                       onRelatedEntityDelete=NULLIFY)}
 *     String department;
 *
 *     String name;
 *
 *     private Employee() {}
 * }</pre>
 *
 * <p>The {@link DeleteAction#CASCADE} value, on the other hand, causes the
 * Employee entities to be automatically deleted when the department they refer
 * to is deleted.  This is probably not desirable in this particular example,
 * but is useful for parent-child relationships.  For example:</p>
 *
 * <pre class="code">
 * {@literal @Entity}
 * class Order {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     String description;
 *
 *     private Order() {}
 * }
 *
 * {@literal @Entity}
 * class OrderItem {
 *
 *     {@literal @PrimaryKey}
 *     long id;
 *
 *     {@code @SecondaryKey(relate=MANY_TO_ONE, relatedEntity=Order.class,
 *                                       onRelatedEntityDelete=CASCADE)}
 *     long orderId;
 *
 *     String description;
 *
 *     private OrderItem() {}
 * }</pre>
 *
 * <p>The OrderItem orderId field refers to its "parent" Order entity.  When an
 * Order entity is deleted, it may be useful to automatically delete its
 * "child" OrderItem entities.</p>
 *
 * <p>For more information, see {@link SecondaryKey#onRelatedEntityDelete}.</p>
 *
 * @author Mark Hayes
 */
public class SecondaryIndex<SK,PK,E> extends BasicIndex<SK,E> {

    private SecondaryDatabase secDb;
    private Database keysDb;
    private PrimaryIndex priIndex;
    private EntityBinding entityBinding;
    private EntityIndex<SK,PK> keysIndex;
    private SortedMap<SK,E> map;

    /**
     * Creates a secondary index without using an <code>EntityStore</code>.
     * When using an {@link EntityStore}, call {@link
     * EntityStore#getSecondaryIndex getSecondaryIndex} instead.
     *
     * <p>This constructor is not normally needed and is provided for
     * applications that wish to use custom bindings along with the Direct
     * Persistence Layer.  Normally, {@link EntityStore#getSecondaryIndex
     * getSecondaryIndex} is used instead.</p>
     *
     * @param database the secondary database used for all access other than
     * via a {@link #keysIndex}.
     *
     * @param keysDatabase another handle on the secondary database, opened
     * without association to the primary, and used only for access via a
     * {@link #keysIndex}.  If this argument is null and the {@link #keysIndex}
     * method is called, then the keys database will be opened automatically;
     * however, the user is then responsible for closing the keys database.  To
     * get the keys database in order to close it, call {@link
     * #getKeysDatabase}.
     *
     * @param primaryIndex the primary index associated with this secondary
     * index.
     *
     * @param secondaryKeyClass the class of the secondary key.
     *
     * @param secondaryKeyBinding the binding to be used for secondary keys.
     */
    public SecondaryIndex(SecondaryDatabase database,
                          Database keysDatabase,
                          PrimaryIndex<PK,E> primaryIndex,
                          Class<SK> secondaryKeyClass,
                          EntryBinding secondaryKeyBinding)
        throws DatabaseException {

        super(database, secondaryKeyClass, secondaryKeyBinding,
              new EntityValueAdapter(primaryIndex.getEntityClass(),
                                     primaryIndex.getEntityBinding(),
                                     true));
        secDb = database;
        keysDb = keysDatabase;
        priIndex = primaryIndex;
        entityBinding = primaryIndex.getEntityBinding();
    }

    /**
     * Returns the underlying secondary database for this index.
     *
     * @return the secondary database.
     */
    public SecondaryDatabase getDatabase() {
        return secDb;
    }

    /**
     * Returns the underlying secondary database that is not associated with
     * the primary database and is used for the {@link #keysIndex}.
     *
     * @return the keys database.
     */
    public Database getKeysDatabase() {
        return keysDb;
    }

    /**
     * Returns the primary index associated with this secondary index.
     *
     * @return the primary index.
     */
    public PrimaryIndex<PK,E> getPrimaryIndex() {
        return priIndex;
    }

    /**
     * Returns the secondary key class for this index.
     *
     * @return the class.
     */
    public Class<SK> getKeyClass() {
        return keyClass;
    }

    /**
     * Returns the secondary key binding for the index.
     *
     * @return the key binding.
     */
    public EntryBinding getKeyBinding() {
        return keyBinding;
    }

    /**
     * Returns a read-only keys index that maps secondary key to primary key.
     * When accessing the keys index, the primary key is returned rather than
     * the entity.  When only the primary key is needed and not the entire
     * entity, using the keys index is less expensive than using the secondary
     * index because the primary index does not have to be accessed.
     *
     * <p>Note the following in the unusual case that you are <em>not</em>
     * using an <code>EntityStore</code>: This method will open the keys
     * database, a second database handle for the secondary database, if it is
     * not already open.  In this case, if you are <em>not</em> using an
     * <code>EntityStore</code>, then you are responsible for closing the
     * database returned by {@link #getKeysDatabase} before closing the
     * environment.  If you <em>are</em> using an <code>EntityStore</code>, the
     * keys database will be closed automatically by {@link
     * EntityStore#close}.</p>
     *
     * @return the keys index.
     */
    public synchronized EntityIndex<SK,PK> keysIndex()
        throws DatabaseException {

        if (keysIndex == null) {
            if (keysDb == null) {
                DatabaseConfig config = secDb.getConfig();
                config.setReadOnly(true);
                keysDb = db.getEnvironment().openDatabase
                    (null, secDb.getDatabaseName(), config);
            }
            keysIndex = new KeysIndex<SK,PK>
                (keysDb, keyClass, keyBinding,
                 priIndex.getKeyClass(), priIndex.getKeyBinding());
        }
        return keysIndex;
    }

    /**
     * Returns an index that maps primary key to entity for the subset of
     * entities having a given secondary key (duplicates).  A sub-index is
     * convenient when you are interested in working with the subset of
     * entities having a particular secondary key value.
     *
     * <p>When using a {@link Relationship#MANY_TO_ONE MANY_TO_ONE} or {@link
     * Relationship#MANY_TO_MANY MANY_TO_MANY} secondary key, the sub-index
     * represents the left (MANY) side of a relationship.</p>
     *
     * @param key the secondary key that identifies the entities in the
     * sub-index.
     *
     * @return the sub-index.
     */
    public EntityIndex<PK,E> subIndex(SK key)
        throws DatabaseException {

        return new SubIndex(this, entityBinding, key);
    }

    /*
     * Of the EntityIndex methods only get()/map()/sortedMap() are implemented
     * here.  All other methods are implemented by BasicIndex.
     */

    public E get(SK key)
        throws DatabaseException {

        return get(null, key, null);
    }

    public E get(Transaction txn, SK key, LockMode lockMode)
        throws DatabaseException {

        DatabaseEntry keyEntry = new DatabaseEntry();
        DatabaseEntry pkeyEntry = new DatabaseEntry();
        DatabaseEntry dataEntry = new DatabaseEntry();
        keyBinding.objectToEntry(key, keyEntry);

        OperationStatus status =
            secDb.get(txn, keyEntry, pkeyEntry, dataEntry, lockMode);

        if (status == OperationStatus.SUCCESS) {
            return (E) entityBinding.entryToObject(pkeyEntry, dataEntry);
        } else {
            return null;
        }
    }

    public Map<SK,E> map() {
        return sortedMap();
    }

    public synchronized SortedMap<SK,E> sortedMap() {
        if (map == null) {
            map = new StoredSortedMap(db, keyBinding, entityBinding, true);
        }
        return map;
    }
}
