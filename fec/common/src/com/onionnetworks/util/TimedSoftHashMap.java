package com.onionnetworks.util;

import java.util.*;
import java.lang.ref.*;

public class TimedSoftHashMap extends HashMap {

    public static final int DEFAULT_TTL = 2*60*1000;

    TreeSet timings = new TreeSet();

    public TimedSoftHashMap() {
        super();
    }

    public TimedSoftHashMap(Map t) {
        throw new UnsupportedOperationException("this(Map t)");
    }

    public TimedSoftHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public TimedSoftHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public boolean containsValue(Object value) {
        return super.containsValue(new HashableSoftReference(value,0));
    }

    public Set entrySet() {
        throw new UnsupportedOperationException("entrySet()");
    }

    /**
     * We renew the timer every time get() is called.
     */
    public Object get(Object key) {
        HashableSoftReference ref = (HashableSoftReference) super.get(key);
        if (ref != null) {
            ref.renew();
        }
        checkTimings();
        return ref == null ? null : ref.get();
    }

    public boolean isEmpty() {
        // In order to implement this method you need to clear out the
        // garbage collected entries.  Shouldn't be hard but I don't have time
        throw new UnsupportedOperationException("isEmpty()");
    }

    public Set keySet() {
        throw new UnsupportedOperationException("entrySet()");
    }

    public Object put(Object key, Object value) {
        return this.put(key,value,DEFAULT_TTL);
    }

    public Object put(Object key, Object value, int ttl) {
        checkTimings();
        HashableSoftReference hsr = new HashableSoftReference(value,ttl);
        HashableSoftReference hsr2 = (HashableSoftReference)super.put(key,hsr);
        timings.add(hsr);
        if (hsr2 == null) {
            return null;
        } else {
            timings.remove(hsr2);
            return hsr2.get();
        }
    }

    public void putAll(Map t) {
        throw new UnsupportedOperationException("putAll(Map t)");
    }

    public Object remove(Object key) {
        checkTimings();
        Reference ref = (Reference) super.remove(key);
        timings.remove(ref);
        return ref == null ? null : ref.get();
    }

    public int size() {
        // In order to implement this method you need to clear out the
        // garbage collected entries.  Shouldn't be hard but I don't have time
        throw new UnsupportedOperationException("size()");
    }

    public Collection values() {
        throw new UnsupportedOperationException("values()");
    }

    public Object clone() {
        throw new UnsupportedOperationException("clone()");
    }

    protected void checkTimings() {
        long time = System.currentTimeMillis();
        for (Iterator it=timings.iterator();it.hasNext();) {
            HashableSoftReference hsr = (HashableSoftReference) it.next();
            if (hsr.deathTime < time) {
                for (Iterator it2=super.keySet().iterator();it2.hasNext();) {
                    Object key = it2.next();
                    if (super.get(key) == hsr) {
                        it2.remove();
                        it.remove();
                        break;
                    }
                }
            } else {
                break;
            }
        }
    }


    /**
     * This class is only necessary for the containsValue calls, as the
     * keys in the TimedSoftHashMap should not be SoftReferences and there
     * for should already have equals() and hashCode() that works.
     */
    public class HashableSoftReference extends SoftReference implements
        Comparable {

        public long deathTime;
        public int ttl;

        public HashableSoftReference(Object ref, int ttl) {
            super(ref);
            this.ttl = ttl;
            renew();
        }

        public void renew() {
            this.deathTime = System.currentTimeMillis()+ttl;
        }

        public int compareTo(Object obj) {
            HashableSoftReference hsr = (HashableSoftReference) obj;
            if (hsr.deathTime == deathTime) {
                return 0;
            }
            return hsr.deathTime < deathTime ? 1 : -1;
        }

        public boolean equals(Object obj) {
            if (obj == null) {
                throw new NullPointerException();
            }

            Object thisObj = this.get();
            if (thisObj == null) {
                return false;
            } else {
                return thisObj.equals(obj);
            }
        }

        public int hashCode() {
            Object thisObj = this.get();
            if (thisObj == null) {
                return 0;
            } else {
                return thisObj.hashCode();
            }
        }
    }
}
