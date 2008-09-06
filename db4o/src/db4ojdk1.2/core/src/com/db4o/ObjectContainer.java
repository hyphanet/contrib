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
package  com.db4o;

import java.util.*;

import com.db4o.ext.*;
import com.db4o.query.*;


/**
 * the interface to a db4o database, stand-alone or client/server.
 * <br><br>The ObjectContainer interface provides methods
 * to store, query and delete objects and to commit and rollback
 * transactions.<br><br>
 * An ObjectContainer can either represent a stand-alone database
 * or a connection to a {@link Db4o#openServer(String, int) db4o server}.
 * <br><br>An ObjectContainer also represents a transaction. All work
 * with db4o always is transactional. Both {@link #commit()} and
 * {@link #rollback()} start new transactions immediately. For working 
 * against the same database with multiple transactions, open a db4o server
 * with {@link Db4o#openServer(String, int)} and 
 * {@link ObjectServer#openClient() connect locally} or
 * {@link Db4o#openClient(String, int, String, String) over TCP}.
 * @see ExtObjectContainer ExtObjectContainer for extended functionality.
 * @sharpen.partial
 */
public interface ObjectContainer {
	
    /**
     * activates all members on a stored object to the specified depth.
	 * <br><br>
     * See {@link com.db4o.config.Configuration#activationDepth(int) "Why activation"}
     * for an explanation why activation is necessary.<br><br>
     * The activate method activates a graph of persistent objects in memory.
     * Only deactivated objects in the graph will be touched: their
     * fields will be loaded from the database. 
     * The activate methods starts from a
     * root object and traverses all member objects to the depth specified by the
     * depth parameter. The depth parameter is the distance in "field hops" 
     * (object.field.field) away from the root object. The nodes at 'depth' level
     * away from the root (for a depth of 3: object.member.member) will be instantiated
     * but deactivated, their fields will be null.
     * The activation depth of individual classes can be overruled
     * with the methods
     * {@link com.db4o.config.ObjectClass#maximumActivationDepth maximumActivationDepth()} and
     * {@link com.db4o.config.ObjectClass#minimumActivationDepth minimumActivationDepth()} in the
     * {@link com.db4o.config.ObjectClass ObjectClass interface}.<br><br>
     * A successful call to activate triggers the callback method
     * {@link com.db4o.ext.ObjectCallbacks#objectOnActivate objectOnActivate}
     * which can be used for cascaded activation.<br><br>
	 * @see com.db4o.config.Configuration#activationDepth Why activation?
	 * @see ObjectCallbacks Using callbacks
     * @param obj the object to be activated.
	 * @param depth the member {@link com.db4o.config.Configuration#activationDepth depth}
	 *  to which activate is to cascade.
	 *  @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
	 *  @throws DatabaseClosedException db4o database file was closed or failed to open.
     */
    public void activate (Object obj, int depth) throws Db4oIOException, DatabaseClosedException;
    
    /**
     * closes this ObjectContainer.
     * <br><br>A call to close() automatically performs a 
     * {@link #commit commit()}.
     * <br><br>Note that every session opened with Db4o.openFile() requires one
     * close()call, even if the same filename was used multiple times.<br><br>
     * Use <code>while(!close()){}</code> to kill all sessions using this container.<br><br>
     * @return success - true denotes that the last used instance of this container
     * and the database file were closed.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     */
	public boolean close() throws Db4oIOException;

    /**
     * commits the running transaction.
     * <br><br>Transactions are back-to-back. A call to commit will starts
     * a new transaction immedidately.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     * @throws DatabaseReadOnlyException database was configured as read-only.
     */
    public void commit () throws Db4oIOException, DatabaseClosedException, DatabaseReadOnlyException;
    

    /**
     * deactivates a stored object by setting all members to <code>NULL</code>.
     * <br>Primitive types will be set to their default values.
     * <br><br><b>Examples: ../com/db4o/samples/activate.</b><br><br>
     * Calls to this method save memory.
     * The method has no effect, if the passed object is not stored in the
     * <code>ObjectContainer</code>.<br><br>
     * <code>deactivate()</code> triggers the callback method
     * {@link com.db4o.ext.ObjectCallbacks#objectOnDeactivate objectOnDeactivate}.
     * <br><br>
     * Be aware that calling this method with a depth parameter greater than 
     * 1 sets members on member objects to null. This may have side effects 
     * in other places of the application.<br><br>
	 * @see ObjectCallbacks Using callbacks
  	 * @see com.db4o.config.Configuration#activationDepth Why activation?
     * @param obj the object to be deactivated.
	 * @param depth the member {@link com.db4o.config.Configuration#activationDepth depth} 
	 * to which deactivate is to cascade.
	 * @throws DatabaseClosedException db4o database file was closed or failed to open.
	*/
    public void deactivate (Object obj, int depth) throws DatabaseClosedException;

    /**
     * deletes a stored object permanently.
     * <br><br>Note that this method has to be called <b>for every single object
     * individually</b>. Delete does not recurse to object members. Simple
     * and array member types are destroyed.
     * <br><br>Object members of the passed object remain untouched, unless
     * cascaded deletes are  
     * {@link com.db4o.config.ObjectClass#cascadeOnDelete configured for the class}
     * or for {@link com.db4o.config.ObjectField#cascadeOnDelete one of the member fields}.
     * <br><br>The method has no effect, if
     * the passed object is not stored in the <code>ObjectContainer</code>.
     * <br><br>A subsequent call to
     * <code>set()</code> with the same object newly stores the object
     * to the <code>ObjectContainer</code>.<br><br>
     * <code>delete()</code> triggers the callback method
     * {@link com.db4o.ext.ObjectCallbacks#objectOnDelete objectOnDelete}
     * which can be also used for cascaded deletes.<br><br>
	 * @see com.db4o.config.ObjectClass#cascadeOnDelete
	 * @see com.db4o.config.ObjectField#cascadeOnDelete
	 * @see ObjectCallbacks Using callbacks
     * @param obj the object to be deleted from the
     * <code>ObjectContainer</code>.<br>
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     * @throws DatabaseReadOnlyException database was configured as read-only.
     */
    public void delete (Object obj) throws Db4oIOException, DatabaseClosedException, DatabaseReadOnlyException;
    
    /**
     * returns an ObjectContainer with extended functionality.
     * <br><br>Every ObjectContainer that db4o provides can be casted to
     * an ExtObjectContainer. This method is supplied for your convenience
     * to work without a cast.
     * <br><br>The ObjectContainer functionality is split to two interfaces
     * to allow newcomers to focus on the essential methods.<br><br>
     * @return this, casted to ExtObjectContainer
     */
    public ExtObjectContainer ext();
	
    /**
	 * Query-By-Example interface to retrieve objects.
	 * <br><br><code>get()</code> creates an
	 * {@link ObjectSet ObjectSet} containing
	 * all objects in the <code>ObjectContainer</code> that match the passed
	 * template object.<br><br>
	 * Calling <code>get(NULL)</code> returns all objects stored in the
	 * <code>ObjectContainer</code>.<br><br><br>
	 * <b>Query Evaluation</b>
	 * <br>All non-null members of the template object are compared against
	 * all stored objects of the same class.
	 * Primitive type members are ignored if they are 0 or false respectively.
	 * <br><br>Arrays and all supported <code>Collection</code> classes are
	 * evaluated for containment. Differences in <code>length/size()</code> are
	 * ignored.
	 * <br><br>Consult the documentation of the Configuration package to
	 * configure class-specific behaviour.<br><br><br>
	 * <b>Returned Objects</b><br>
	 * The objects returned in the
	 * {@link ObjectSet ObjectSet} are instantiated
	 * and activated to the preconfigured depth of 5. The
	 * {@link com.db4o.config.Configuration#activationDepth activation depth}
	 * may be configured {@link com.db4o.config.Configuration#activationDepth globally} or
	 * {@link com.db4o.config.ObjectClass individually for classes}.
	 * <br><br>
	 * db4o keeps track of all instantiatied objects. Queries will return
	 * references to these objects instead of instantiating them a second time.
	 * <br><br>
	 * Objects newly activated by <code>get()</code> can respond to the callback
	 * method {@link com.db4o.ext.ObjectCallbacks#objectOnActivate objectOnActivate}.
	 * <br><br>
	 * @param template object to be used as an example to find all matching objects.<br><br>
	 * @return {@link ObjectSet ObjectSet} containing all found objects.<br><br>
	 * @see com.db4o.config.Configuration#activationDepth Why activation?
	 * @see ObjectCallbacks Using callbacks
	 * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
	 * @throws DatabaseClosedException db4o database file was closed or failed to open.
	 * @deprecated Use {@link #queryByExample(Object)} instead
	 */
	public ObjectSet get (Object template) throws Db4oIOException, DatabaseClosedException;

	/**
     * Query-By-Example interface to retrieve objects.
     * <br><br><code>get()</code> creates an
     * {@link ObjectSet ObjectSet} containing
     * all objects in the <code>ObjectContainer</code> that match the passed
     * template object.<br><br>
	 * Calling <code>get(NULL)</code> returns all objects stored in the
     * <code>ObjectContainer</code>.<br><br><br>
     * <b>Query Evaluation</b>
     * <br>All non-null members of the template object are compared against
     * all stored objects of the same class.
     * Primitive type members are ignored if they are 0 or false respectively.
     * <br><br>Arrays and all supported <code>Collection</code> classes are
     * evaluated for containment. Differences in <code>length/size()</code> are
     * ignored.
     * <br><br>Consult the documentation of the Configuration package to
     * configure class-specific behaviour.<br><br><br>
     * <b>Returned Objects</b><br>
     * The objects returned in the
     * {@link ObjectSet ObjectSet} are instantiated
     * and activated to the preconfigured depth of 5. The
	 * {@link com.db4o.config.Configuration#activationDepth activation depth}
	 * may be configured {@link com.db4o.config.Configuration#activationDepth globally} or
     * {@link com.db4o.config.ObjectClass individually for classes}.
	 * <br><br>
     * db4o keeps track of all instantiatied objects. Queries will return
     * references to these objects instead of instantiating them a second time.
     * <br><br>
     * Objects newly activated by <code>get()</code> can respond to the callback
     * method {@link com.db4o.ext.ObjectCallbacks#objectOnActivate objectOnActivate}.
     * <br><br>
     * @param template object to be used as an example to find all matching objects.<br><br>
     * @return {@link ObjectSet ObjectSet} containing all found objects.<br><br>
	 * @see com.db4o.config.Configuration#activationDepth Why activation?
	 * @see ObjectCallbacks Using callbacks
	 * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
	 * @throws DatabaseClosedException db4o database file was closed or failed to open.
	 */
    public ObjectSet queryByExample (Object template) throws Db4oIOException, DatabaseClosedException;
    
    /**
     * creates a new SODA {@link Query Query}.
     * <br><br>
     * Use {@link #get get(Object template)} for simple Query-By-Example.<br><br>
     * {@link #query(Predicate) Native queries } are the recommended main db4o query
     * interface. 
     * <br><br>
     * @return a new Query object
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     */
    public Query query ()  throws DatabaseClosedException;
    
    /**
     * queries for all instances of a class.
     * @param clazz the class to query for.
     * @return the {@link ObjectSet} returned by the query.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     */
    public ObjectSet query(Class clazz) throws Db4oIOException, DatabaseClosedException;

    
    /**
     * Native Query Interface.
     * <br><br>Native Queries allow typesafe, compile-time checked and refactorable 
     * querying, following object-oriented principles. Native Queries expressions
     * are written as if one or more lines of code would be run against all
     * instances of a class. A Native Query expression should return true to mark 
     * specific instances as part of the result set. 
     * db4o will  attempt to optimize native query expressions and execute them 
     * against indexes and without instantiating actual objects, where this is 
     * possible.<br><br>
     * The syntax of the enclosing object for the native query expression varies,
     * depending on the language version used. Here are some examples,
     * how a simple native query will look like in some of the programming languages 
     * and dialects that db4o supports:<br><br>
     * 
     * <code>
     * <b>// C# .NET 2.0</b><br>
     * IList &lt;Cat&gt; cats = db.Query &lt;Cat&gt; (delegate(Cat cat) {<br>
     * &#160;&#160;&#160;return cat.Name == "Occam";<br>
     * });<br>
     * <br>
     *<br>
     * <b>// Java JDK 5</b><br>
     * List &lt;Cat&gt; cats = db.query(new Predicate&lt;Cat&gt;() {<br>
     * &#160;&#160;&#160;public boolean match(Cat cat) {<br>
     * &#160;&#160;&#160;&#160;&#160;&#160;return cat.getName().equals("Occam");<br>
     * &#160;&#160;&#160;}<br>
     * });<br>
     * <br>
     * <br>
     * <b>// Java JDK 1.2 to 1.4</b><br>
     * List cats = db.query(new Predicate() {<br>
     * &#160;&#160;&#160;public boolean match(Cat cat) {<br>
     * &#160;&#160;&#160;&#160;&#160;&#160;return cat.getName().equals("Occam");<br>
     * &#160;&#160;&#160;}<br>
     * });<br>
     * <br>
     * <br>
     * <b>// Java JDK 1.1</b><br>
     * ObjectSet cats = db.query(new CatOccam());<br>
     * <br>
     * public static class CatOccam extends Predicate {<br>
     * &#160;&#160;&#160;public boolean match(Cat cat) {<br>
     * &#160;&#160;&#160;&#160;&#160;&#160;return cat.getName().equals("Occam");<br>
     * &#160;&#160;&#160;}<br>
     * });<br>
     * <br>
     * <br>     
     * <b>// C# .NET 1.1</b><br>
     * IList cats = db.Query(new CatOccam());<br>
     * <br>
     * public class CatOccam : Predicate {<br>
     * &#160;&#160;&#160;public boolean Match(Cat cat) {<br>
     * &#160;&#160;&#160;&#160;&#160;&#160;return cat.Name == "Occam";<br>
     * &#160;&#160;&#160;}<br>
     * });<br>
     * </code>
     * <br>
     * Summing up the above:<br>
     * In order to run a Native Query, you can<br>
     * - use the delegate notation for .NET 2.0.<br>
     * - extend the Predicate class for all other language dialects<br><br>
     * A class that extends Predicate is required to 
     * implement the #match() / #Match() method, following the native query
     * conventions:<br>
     * - The name of the method is "#match()" (Java) / "#Match()" (.NET).<br>
     * - The method must be public public.<br>
     * - The method returns a boolean.<br>
     * - The method takes one parameter.<br>
     * - The Type (.NET) / Class (Java) of the parameter specifies the extent.<br>
     * - For all instances of the extent that are to be included into the
     * resultset of the query, the match method should return true. For all
     * instances that are not to be included, the match method should return
     * false.<br><br>  
     *   
     * @param predicate the {@link Predicate} containing the native query expression.
     * @return the {@link ObjectSet} returned by the query.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     */
    public ObjectSet query(Predicate predicate) throws Db4oIOException, DatabaseClosedException;

    /**
     * Native Query Interface. Queries as with {@link com.db4o.ObjectContainer#query(com.db4o.query.Predicate)},
     * but will sort the resulting {@link com.db4o.ObjectSet} according to the given {@link com.db4o.query.QueryComparator}.
     * 
     * @param predicate the {@link Predicate} containing the native query expression.
     * @param comparator the {@link QueryComparator} specifiying the sort order of the result
     * @return the {@link ObjectSet} returned by the query.
     */
    public ObjectSet query(Predicate predicate,QueryComparator comparator);

    /**
     * Native Query Interface. Queries as with {@link com.db4o.ObjectContainer#query(com.db4o.query.Predicate)},
     * but will sort the resulting {@link com.db4o.ObjectSet} according to the given java.util.Comparator.
     * 
     * @param predicate the {@link Predicate} containing the native query expression.
     * @param comparator the java.util.Comparator specifying the sort order of the result
     * @return the {@link ObjectSet} returned by the query.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     * @decaf.ignore

     */
    public ObjectSet query(Predicate predicate,Comparator comparator) throws Db4oIOException, DatabaseClosedException;

    /**
     * rolls back the running transaction.
     * <br><br>Transactions are back-to-back. A call to rollback will starts
     * a new transaction immedidately.
     * <br><br>rollback will not restore modified objects in memory. They
     * can be refreshed from the database by calling 
     * {@link ExtObjectContainer#refresh(Object, int)}.
     * @throws Db4oIOException I/O operation failed or was unexpectedly interrupted.
     * @throws DatabaseClosedException db4o database file was closed or failed to open.
     * @throws DatabaseReadOnlyException database was configured as read-only.
     */
    public void rollback() throws Db4oIOException, DatabaseClosedException, DatabaseReadOnlyException;
    
    /**
	 * newly stores objects or updates stored objects.
	 * <br><br>An object not yet stored in the <code>ObjectContainer</code> will be
	 * stored when it is passed to <code>set()</code>. An object already stored
	 * in the <code>ObjectContainer</code> will be updated.
	 * <br><br><b>Updates</b><br>
	 * - will affect all simple type object members.<br>
	 * - links to object members that are already stored will be updated.<br>
	 * - new object members will be newly stored. The algorithm traverses down
	 * new members, as long as further new members are found.<br>
	 * - object members that are already stored will <b>not</b> be updated
	 * themselves.<br>Every object member needs to be updated individually with a
	 * call to <code>set()</code> unless a deep
	 * {@link com.db4o.config.Configuration#updateDepth global} or 
	 * {@link com.db4o.config.ObjectClass#updateDepth class-specific}
	 * update depth was configured or cascaded updates were 
	 * {@link com.db4o.config.ObjectClass#cascadeOnUpdate defined in the class}
	 * or in {@link com.db4o.config.ObjectField#cascadeOnUpdate one of the member fields}.
	 * <br><br><b>Examples: ../com/db4o/samples/update.</b><br><br>
	 * Depending if the passed object is newly stored or updated, the
	 * callback method
	 * {@link com.db4o.ext.ObjectCallbacks#objectOnNew objectOnNew} or
	 * {@link com.db4o.ext.ObjectCallbacks#objectOnUpdate objectOnUpdate} is triggered.
	 * {@link com.db4o.ext.ObjectCallbacks#objectOnUpdate objectOnUpdate}
	 * might also be used for cascaded updates.<br><br>
	 * @param obj the object to be stored or updated.
	 * @see ExtObjectContainer#store(java.lang.Object, int) ExtObjectContainer#set(object, depth)
	 * @see com.db4o.config.Configuration#updateDepth
	 * @see com.db4o.config.ObjectClass#updateDepth
	 * @see com.db4o.config.ObjectClass#cascadeOnUpdate
	 * @see com.db4o.config.ObjectField#cascadeOnUpdate
	 * @see ObjectCallbacks Using callbacks
	 * @throws DatabaseClosedException db4o database file was closed or failed to open.
	 * @throws DatabaseReadOnlyException database was configured as read-only.
	 * @deprecated Use {@link #store(Object)} instead
	 */
	public void set (Object obj) throws DatabaseClosedException, DatabaseReadOnlyException;

	/**
     * newly stores objects or updates stored objects.
     * <br><br>An object not yet stored in the <code>ObjectContainer</code> will be
     * stored when it is passed to <code>set()</code>. An object already stored
     * in the <code>ObjectContainer</code> will be updated.
     * <br><br><b>Updates</b><br>
	 * - will affect all simple type object members.<br>
     * - links to object members that are already stored will be updated.<br>
	 * - new object members will be newly stored. The algorithm traverses down
	 * new members, as long as further new members are found.<br>
     * - object members that are already stored will <b>not</b> be updated
     * themselves.<br>Every object member needs to be updated individually with a
	 * call to <code>set()</code> unless a deep
	 * {@link com.db4o.config.Configuration#updateDepth global} or 
     * {@link com.db4o.config.ObjectClass#updateDepth class-specific}
     * update depth was configured or cascaded updates were 
     * {@link com.db4o.config.ObjectClass#cascadeOnUpdate defined in the class}
     * or in {@link com.db4o.config.ObjectField#cascadeOnUpdate one of the member fields}.
     * <br><br><b>Examples: ../com/db4o/samples/update.</b><br><br>
     * Depending if the passed object is newly stored or updated, the
     * callback method
     * {@link com.db4o.ext.ObjectCallbacks#objectOnNew objectOnNew} or
     * {@link com.db4o.ext.ObjectCallbacks#objectOnUpdate objectOnUpdate} is triggered.
     * {@link com.db4o.ext.ObjectCallbacks#objectOnUpdate objectOnUpdate}
     * might also be used for cascaded updates.<br><br>
     * @param obj the object to be stored or updated.
	 * @see ExtObjectContainer#store(java.lang.Object, int) ExtObjectContainer#set(object, depth)
	 * @see com.db4o.config.Configuration#updateDepth
	 * @see com.db4o.config.ObjectClass#updateDepth
	 * @see com.db4o.config.ObjectClass#cascadeOnUpdate
	 * @see com.db4o.config.ObjectField#cascadeOnUpdate
	 * @see ObjectCallbacks Using callbacks
	 * @throws DatabaseClosedException db4o database file was closed or failed to open.
	 * @throws DatabaseReadOnlyException database was configured as read-only.
     */
    public void store (Object obj) throws DatabaseClosedException, DatabaseReadOnlyException;
    
    
    
}



