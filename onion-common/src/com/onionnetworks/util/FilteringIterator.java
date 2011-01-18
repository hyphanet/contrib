package com.onionnetworks.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** FilteredIterators wrap Iterators and return only elements that pass
 * a provided test.  No more than one value is buffered up.
 * ConcurrentModificationExceptions and other exceptions filter up correctly.
 * remove() is not supported.  This implemention isn't synchronized.  The
 * easiest way to synchronize is shown in the example.
 *
 * <p>Example:
 * <pre>
    Iterator i = Arrays.asList(new String[] {"a",null,"was",null}).iterator();
    Iterator f = new FilteringIterator(i) {
	public boolean accept(Object o) {
	    return (o != null);
	}
	// uncomment the next to lines if you want it synchronized
	// public synchronized Object next() { return super.next(); }
	// public synchronized boolean hasNext() { return super.hasNext(); }
    });
    for (; f.hasNext(); ) {
	Sytem.out.println("non-null: " + f.next());
    }
   </pre>
 * @author Ry4an
 */
public abstract class FilteringIterator implements Iterator {

    private Iterator parent;
    private Object next;
    private boolean removeOkay = true;

    /** Create a FilteringIterator and provide the parent Iterator
     * @param Iterator the iterator to wrap w/ the filter
     */
    public FilteringIterator(Iterator p) {
	parent = p;
    }

    /** Unsupported.
     */
    public void remove() {
	throw new UnsupportedOperationException();
    }

    /** Checks if the parent iterator has another element that will
     * pass the filter defined in <code>accept</code>.
     * @return true = another passing object available, false otherwise
     * @see accept
     */
    public boolean hasNext() {
	while ((next == null) && (parent.hasNext())) {
	    Object o = parent.next();
	    if (accept(o)) {
		next = o;
		return true;
	    }
	}
	return (next != null);
    }
    
    /** Fill in this method with a test that will be applied to 
     * each object which is a candidate for passing through the filter.
     * @param o the object which may be passed through the filter
     * @return true indciated the object should be returned. else false.
     */
    protected abstract boolean accept(Object o);
    
    /** Returns the next object from the parent iterator which passes the
     * filter defined by <code>accept</code>.
     * @return Object an object which passes <code>accept</code>
     * @see accept
     */
    public Object next() {
	if (! hasNext()) {
	    throw new NoSuchElementException();
	}
	Object retval = next;
	next = null;
	return retval;
    }

    /** Test and example. */
    public static void main(String[] args) {
	java.util.List l = new java.util.LinkedList(java.util.Arrays.asList
	    (new String[] {"a",null,"was",null})); // the test array
	Iterator i = l.iterator();
	Iterator f = new FilteringIterator(i) {
	    public boolean accept(Object o) {
		return (o != null);
	    }
	};
	System.out.println("--[ Unfiltered list: ]--");
	for(Iterator j=l.iterator(); j.hasNext(); ) {
	    System.out.println("Item: " + j.next());
	}
	System.out.println("--[ List with null filter: ]--");
	try { // note: this test code is dependent on the test array
	    Object o;
	    if (! f.hasNext()) { throw new Exception(); }
	    if (! (o=f.next()).equals("a")) { throw new Exception(); }
	    System.out.println("Item: " + o);
	    if (! (o=f.next()).equals("was")) { throw new Exception(); }
	    System.out.println("Item: " + o);
	    if (f.hasNext()) { throw new Exception(); }
	} catch (Throwable t) {
	    System.err.println("Something unexpected happened:");
	    t.printStackTrace();
	}
    }
}


