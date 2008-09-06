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
package com.db4o.internal;

import java.net.*;
import java.util.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.internal.query.processor.*;
import com.db4o.query.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;
import com.db4o.types.*;

/**
 * @exclude
 * @sharpen.ignore
 */
public final class Platform4 {
    

    private static final String	JDK_PACKAGE	= "com.db4o.internal.";

	static private TernaryBool collectionCheck=TernaryBool.UNSPECIFIED;

    static private JDK jdkWrapper;
    static private TernaryBool nioCheck=TernaryBool.UNSPECIFIED;

    static private TernaryBool setAccessibleCheck=TernaryBool.UNSPECIFIED;
    static private TernaryBool shutDownHookCheck=TernaryBool.UNSPECIFIED;
    static TernaryBool callConstructorCheck=TernaryBool.UNSPECIFIED;
    static ShutDownRunnable shutDownRunnable;

    static Thread shutDownThread;
    
    static final String ACCESSIBLEOBJECT = "java.lang.reflect.AccessibleObject";
    static final String GETCONSTRUCTOR = "newConstructorForSerialization";
    static final String REFERENCEQUEUE = "java.lang.ref.ReferenceQueue"; 
    static final String REFLECTIONFACTORY = "sun.reflect.ReflectionFactory";
    static final String RUNFINALIZERSONEXIT = "runFinalizersOnExit";
    
    static final String UTIL = "java.util.";
    static final String DB4O_PACKAGE = "com.db4o.";
    static final String DB4O_CONFIG = DB4O_PACKAGE + "config.";
    static final String DB4O_ASSEMBLY = ", db4o";
    
    
    // static private int cCreateNewFile;
    static private TernaryBool weakReferenceCheck=TernaryBool.UNSPECIFIED;
    
    private static final Class[] SIMPLE_CLASSES = {
		Integer.class,
		Long.class,
		Float.class,
		Boolean.class,
		Double.class,
		Byte.class,
		Character.class,
		Short.class,
		String.class,
		java.util.Date.class
	};
    
    synchronized static final void addShutDownHook(PartialObjectContainer container) {
        if (!hasShutDownHook()) {
        	return;
        }
        
        if (shutDownThread == null) {
            shutDownRunnable = new ShutDownRunnable();
            shutDownThread = jdk().addShutdownHook(shutDownRunnable);
        }
        shutDownRunnable.ensure(container);
    }

    /**
     * @deprecated
     */
    public static final boolean canSetAccessible() {
        if (setAccessibleCheck.isUnspecified()) {
            if (jdk().ver() >= 2) {
                setAccessibleCheck = TernaryBool.YES;
            } else {
                setAccessibleCheck = TernaryBool.NO;
                if (((Config4Impl)Db4o.configure()).messageLevel() >= 0) {
                    Messages.logErr(Db4o.configure(), 47, null, null);
                }
            }
        }
        return setAccessibleCheck.definiteYes();
    }
    
    /**
     * use for system classes only, since not ClassLoader
     * or Reflector-aware
     */
    static final boolean classIsAvailable(String className) {
    	return ReflectPlatform.forName(className) != null;
    }
    
    /**
     * @deprecated
     */
    static Db4oCollections collections(Transaction transaction){
        return jdk().collections(transaction);
    }
    
    static final Reflector createReflector(Object classLoader){
        return jdk().createReflector(classLoader);
    }

    static final Object createReferenceQueue() {
        return jdk().createReferenceQueue();
    }
    
    public static Object createWeakReference(Object obj){
        return jdk().createWeakReference(obj);
    }

    static final Object createActiveObjectReference(Object a_queue, Object a_yapObject, Object a_object) {
        return jdk().createActivateObjectReference(a_queue, (ObjectReference) a_yapObject, a_object);
    }
    
    public static Object deserialize(byte[] bytes) {
    	return jdk().deserialize(bytes);
    }
    
    public static final long doubleToLong(double a_double) {
        return Double.doubleToLongBits(a_double);
    }

    public static final QConEvaluation evaluationCreate(Transaction a_trans, Object example){
        if(example instanceof Evaluation){
            return new QConEvaluation(a_trans, example);
        }
        return null;
    }
    
    public static final void evaluationEvaluate(Object a_evaluation, Candidate a_candidate){
        ((Evaluation)a_evaluation).evaluate(a_candidate);
    }

    /** may be needed for YapConfig processID() at a later date */
    /*
    static boolean createNewFile(File file) throws IOException{
    	return file.createNewFile();
    }
    */
	
	public static Object[] collectionToArray(ObjectContainerBase stream, Object obj){
		Collection4 col = flattenCollection(stream, obj);
		Object[] ret = new Object[col.size()];
		col.toArray(ret);
		return ret;
	}

    static final Collection4 flattenCollection(ObjectContainerBase stream, Object obj) {
        Collection4 col = new Collection4();
        flattenCollection1(stream, obj, col);
        return col;
    }

    /**
     * Should create additional configuration, for example through reflection
     * on annotations.
     * 
     * - If a valid configuration is passed as classConfig, any additional
     *   configuration, if available, should be applied to this object, and
     *   this object should be returned.
     * - If classConfig is null and there is no additional configuration,
     *   null should be returned.
     * - If classConfig is null and there is additional configuration, this code
     *   should create and register a new configuration via config.objectClass(),
     *   apply additional configuration there and return this new instance.
     * 
     * The reason for this dispatch is to avoid creation of a configuration
     * for a class that doesn't need configuration at all.
     * 
     * @param clazz The class to be searched for additional configuration information
     * @param config The global database configuration
     * @param classConfig A class configuration, if one already exists
     * @return classConfig, if not null, a newly created ObjectClass otherwise.
     */
    public static Config4Class extendConfiguration(ReflectClass clazz,Configuration config,Config4Class classConfig) {
    	return jdk().extendConfiguration(clazz, config, classConfig);
    }
    
    static final void flattenCollection1(ObjectContainerBase stream, Object obj, Collection4 col) {
        if (obj == null) {
            col.add(null);
        } else {
            ReflectClass claxx = stream.reflector().forObject(obj);
            if (claxx.isArray()) {
                Iterator4 objects = ArrayHandler.iterator(claxx, obj);
                while (objects.moveNext()) {
                    flattenCollection1(stream, objects.current(), col);
                }
            } else {
                flattenCollection2(stream, obj, col);
            }
        }
    }

    static final void flattenCollection2(final ObjectContainerBase container, Object obj, final Collection4 col) {
        if (container.reflector().forObject(obj).isCollection()) {
            forEachCollectionElement(obj, new Visitor4() {
                public void visit(Object element) {
                    flattenCollection1(container, element, col);
                }
            });
        } else {
            col.add(obj);
        }
    }

    public static final void forEachCollectionElement(Object obj, Visitor4 visitor) {
        jdk().forEachCollectionElement(obj, visitor);
    }

    public static final String format(Date date, boolean showTime) {
    	return jdk().format(date, showTime);
    }

    public static Object getClassForType(Object obj) {
        return obj;
    }

    public static final void getDefaultConfiguration(Config4Impl config) {
		
    	// Initialize all JDK stuff first, before doing ClassLoader stuff
    	jdk();
    	hasWeakReferences();
    	hasNio();
    	hasCollections();
    	hasShutDownHook();
        
    	if(config.reflector()==null) {
    		config.reflectWith(jdk().createReflector(null));
    	}
    	
        configStringBufferCompare(config);
        
        translate(config.objectClass("java.lang.Class"), "TClass");
        translateCollection(config, "Hashtable", "THashtable", true);
        if (jdk().ver() >= 2) {
			try {
				translateCollection(config, "AbstractCollection", "TCollection", false);
				translateUtilNull(config, "AbstractList");
				translateUtilNull(config, "AbstractSequentialList");
				translateUtilNull(config, "LinkedList");
				translateUtilNull(config, "ArrayList");
				translateUtilNull(config, "Vector");
				translateUtilNull(config, "Stack");
				translateUtilNull(config, "AbstractSet");
				translateUtilNull(config, "HashSet");
				translate(config, UTIL + "TreeSet", "TTreeSet");
				translateCollection(config, "AbstractMap", "TMap", true);
				translateUtilNull(config, "HashMap");
				translateUtilNull(config, "WeakHashMap");
				translate(config, UTIL + "TreeMap", "TTreeMap");
			} catch (Exception e) {
			}
        } else {
			translateCollection(config, "Vector", "TVector", false);
        }
        netReadAsJava(config, "ext.Db4oDatabase");
        netReadAsJava(config, "P1Object");
        netReadAsJava(config, "P1Collection");
        netReadAsJava(config, "P1HashElement");
        netReadAsJava(config, "P1ListElement");
        netReadAsJava(config, "P2HashMap");
        netReadAsJava(config, "P2LinkedList");
        netReadAsJava(config, "StaticClass");
        netReadAsJava(config, "StaticField");
        
        maximumActivationDepth(config, "P1ListElement", 1);
        activationDepth(config, "P2LinkedList", 1);
        
        activationDepth(config, "P2HashMap", 2);
        activationDepth(config, "P1HashElement", 1);
        
        jdk().extendConfiguration(config);
    }
    

    /**
     * @deprecated uses deprecated API
     */
	private static void configStringBufferCompare(Config4Impl config) {
		config.objectClass("java.lang.StringBuffer").compare(new ObjectAttribute() {
            public Object attribute(Object original) {
                if (original instanceof StringBuffer) {
                    return ((StringBuffer) original).toString();
                }
                return original;
            }
        });
	}

	private static void activationDepth(Config4Impl config, String className, int depth) {
		final ObjectClass classConfig = db4oClassConfig(config, className);
		classConfig.minimumActivationDepth(depth);
		classConfig.maximumActivationDepth(depth);
	}

	private static void maximumActivationDepth(Config4Impl config,
			final String className, int depth) {
		db4oClassConfig(config, className).maximumActivationDepth(depth);
	}

	private static ObjectClass db4oClassConfig(Config4Impl config,
			final String className) {
		return config.objectClass(db4oClass(className));
	}
    
    public static Object getTypeForClass(Object obj){
        return obj;
    }

    static final Object getYapRefObject(Object a_object) {
        return jdk().getYapRefObject(a_object);
    }

    static final synchronized boolean hasCollections() {
        if (collectionCheck.isUnspecified()) {
        	if (classIsAvailable(UTIL + "Collection")) {
        		collectionCheck = TernaryBool.YES;
        		return true;
        	}
            collectionCheck = TernaryBool.NO;
        }
        return collectionCheck.definiteYes();
    }

    public static final boolean hasLockFileThread() {
        return true;
    }

    public static final boolean hasNio() {
        if (!Debug.nio) {
            return false;
        }
        if (nioCheck.isUnspecified()) {
            if ((jdk().ver() >= 4)
                && (!noNIO())) {
                nioCheck = TernaryBool.YES;
                return true;
            }
            nioCheck = TernaryBool.NO;
        }
        return nioCheck.definiteYes();

    }

    static final boolean hasShutDownHook() {
        if (shutDownHookCheck.isUnspecified()) {            
            if (jdk().ver() >= 3){
                shutDownHookCheck = TernaryBool.YES;
                return true;
            } 
            Reflection4.invoke(System.class, RUNFINALIZERSONEXIT, new Class[] {boolean.class}, new Object[]{new Boolean(true)});
            shutDownHookCheck = TernaryBool.NO;
        }
        return shutDownHookCheck.definiteYes();
    }

    static final boolean hasWeakReferences() {
        if (!Debug.weakReferences) {
            return false;
        }
        if (weakReferenceCheck.isUnspecified()) {
            if (classIsAvailable(ACCESSIBLEOBJECT)
                && classIsAvailable(REFERENCEQUEUE)
                && jdk().ver() >= 2) {
                weakReferenceCheck = TernaryBool.YES;
                return true;
            }
            weakReferenceCheck = TernaryBool.NO;
        }
        return weakReferenceCheck.definiteYes();
    }
    
    /** @param obj */
    static final boolean ignoreAsConstraint(Object obj){
        return false;
    }

    static final boolean isCollectionTranslator(Config4Class a_config) {
        return jdk().isCollectionTranslator(a_config); 
    }
    
    public static boolean isConnected(Socket socket) {
        return jdk().isConnected(socket);
    }   
    
    /** @param claxx */
    public static final boolean isValueType(ReflectClass claxx){
    	return false;
    }
    
    public static JDK jdk() {
        if (jdkWrapper == null) {
            createJdk();
        }
        return jdkWrapper;
    }
    
    private static void createJdk() {
    	
        if (classIsAvailable("java.lang.reflect.Method")){
            jdkWrapper = (JDK)ReflectPlatform.createInstance(JDK_PACKAGE + "JDKReflect");
        }

        if (classIsAvailable(Platform4.ACCESSIBLEOBJECT)){
        	jdkWrapper = createJDKWrapper("1_2");
        }
        
        if (jdk().methodIsAvailable("java.lang.Runtime","addShutdownHook",
                new Class[] { Thread.class })){
        	jdkWrapper = createJDKWrapper("1_3");
        }

        if(classIsAvailable("java.nio.channels.FileLock")){
        	jdkWrapper = createJDKWrapper("1_4");
        }
        
        if(classIsAvailable("java.lang.Enum")){
        	jdkWrapper = createJDKWrapper("5");
        }
        
    }
    
    private static JDK createJDKWrapper(String name){
        JDK newWrapper = (JDK)ReflectPlatform.createInstance(JDK_PACKAGE + "JDK_" + name);
        if(newWrapper != null){
            return newWrapper;
        }
        return jdkWrapper;
    }
    
	public static boolean isSimple(Class a_class){
		for (int i = 0; i < SIMPLE_CLASSES.length; i++) {
			if(a_class == SIMPLE_CLASSES[i]){
				return true;
			}
		}
		return false;
	}
    
    static final void killYapRef(Object a_object){
    	jdk().killYapRef(a_object);
    }
    
    public static void link(){
        // link standard translators, so they won't get deleted
        // by deployment
        
        new TClass();
        new TVector();
        new THashtable();
        new TNull();
    }

    // FIXME: functionality should really be in IoAdapter
    public static final void lockFile(String path,Object file) {
        if (!hasNio()) {
            return;
        }

        // FIXME: libgcj 3.x isn't able to properly lock the database file
        String fullversion = System.getProperty("java.fullversion");
        if (fullversion != null && fullversion.indexOf("GNU libgcj") >= 0) {
            System.err.println("Warning: Running in libgcj 3.x--not locking database file!");
            return;
        }
        
        jdk().lockFile(path,file);
    }
    
    public static final void unlockFile(String path,Object file) {
        if (hasNio()) {
            jdk().unlockFile(path,file);
        }
    }

    public static final double longToDouble(long a_long) {
        return Double.longBitsToDouble(a_long);
    }

    /** @param marker */
    static void markTransient(String marker) {
        // do nothing
    }

    public static boolean callConstructor() {
        if (callConstructorCheck.isUnspecified()) {
            
            if(jdk().methodIsAvailable(
                REFLECTIONFACTORY,
                GETCONSTRUCTOR,
                new Class[]{Class.class, jdk().constructorClass()}
                )){
                
                callConstructorCheck = TernaryBool.NO;
                return false;
            }
            callConstructorCheck = TernaryBool.YES;
        }
        return callConstructorCheck.definiteYes();
    }
    
    /**
     * @deprecated
     */
    private static final void netReadAsJava(Config4Impl config, String className){
        Config4Class classConfig = (Config4Class)config.objectClass(db4oClass(className) + DB4O_ASSEMBLY);
        if(classConfig == null){
            return;
        }
        classConfig.maintainMetaClass(false);
        classConfig.readAs(db4oClass(className));
    }

	private static String db4oClass(String className) {
		return DB4O_PACKAGE + className;
	}

    private static final boolean noNIO() {
        try {
            if (propertyIs("java.vendor", "Sun")
                && propertyIs("java.version", "1.4.0")
                && (propertyIs("os.name", "Linux")
                    || propertyIs("os.name", "Windows 95")
                    || propertyIs("os.name", "Windows 98"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static final void pollReferenceQueue(Object a_stream, Object a_referenceQueue) {
        jdk().pollReferenceQueue((ObjectContainerBase) a_stream, a_referenceQueue);
    }

    private static final boolean propertyIs(String propertyName, String propertyValue) {
        String property = System.getProperty(propertyName);
        return (property != null) && (property.indexOf(propertyValue) == 0);
    }
    
	public static void registerCollections(GenericReflector reflector) {
		registerDeprecatedCollection(reflector);
		jdk().registerCollections(reflector);
	}

	/**
	 * @deprecated
	 */
	private static void registerDeprecatedCollection(GenericReflector reflector) {
		reflector.registerCollection(P1Collection.class);
	}

    synchronized static final void removeShutDownHook(PartialObjectContainer container) {
        if (!hasShutDownHook() || shutDownRunnable == null) {
        	return;
        }
        
        shutDownRunnable.remove(container);
        if (shutDownRunnable.size() == 0) {
            if (!shutDownRunnable.dontRemove) {
                try {
                    jdk().removeShutdownHook(shutDownThread);
                } catch (Exception e) {
                    // this is safer than attempting perfect
                    // synchronisation
                }
            }
            shutDownThread = null;
            shutDownRunnable = null;
        }
    }
    
    public static final byte[] serialize(Object obj) throws Exception{
    	return jdk().serialize(obj);
    }

    public static final void setAccessible(Object a_accessible) {
        if (setAccessibleCheck == TernaryBool.UNSPECIFIED) {
            canSetAccessible();
        }
        if (setAccessibleCheck == TernaryBool.YES) {
            jdk().setAccessible(a_accessible);
        }
    }
    
    public static boolean storeStaticFieldValues(Reflector reflector, ReflectClass claxx) {
        return isEnum(reflector, claxx);
    }

	public static boolean isEnum(Reflector reflector, ReflectClass claxx) {
		return jdk().isEnum(reflector, claxx);
	}

    private static final void translate(ObjectClass oc, String to) {
        ((Config4Class)oc).translateOnDemand(DB4O_CONFIG + to);
    }

    private static final void translate(Config4Impl config, String from, String to) {
        translate(config.objectClass(from), to);
    }

    private static final void translateCollection(
        Config4Impl config,
        String from,
        String to,
        boolean cascadeOnDelete) {
        ObjectClass oc = config.objectClass(UTIL + from);
        oc.updateDepth(3);
        if (cascadeOnDelete) {
            oc.cascadeOnDelete(true);
        }
        translate(oc, to);
    }

    private static final void translateUtilNull(Config4Impl config, String className) {
        translate(config, UTIL + className, "TNull");
    }

    static final NetTypeHandler[] types(Reflector reflector) {
        return jdk().types(reflector);
    }
    
    public static byte[] updateClassName(byte[] bytes) {
        // needed for .NET only: update assembly names if necessary
        return bytes;
    }
    
    public static Object weakReferenceTarget(Object weakRef){
        return jdk().weakReferenceTarget(weakRef);
    }

	public static Object wrapEvaluation(Object evaluation) {
		return evaluation;
	}

	public static boolean isDb4oClass(String className) {
        if (className.indexOf(".test.") > 0) {
            return false;
        }
        if (className.indexOf(".db4ounit.") > 0) {
            return false;
        }
		return className.indexOf("com.db4o") == 0;
	}

    /** @param claxx */
    public static boolean isTransient(ReflectClass claxx) {
        return false;
    }
    
    public static boolean isTransient(Class claxx) {
    	return false;
    }

	public static Reflector reflectorForType(Class clazz) {
		return jdk().reflectorForType(clazz);
	}
	
	public static String stackTrace(){
		return StackTracer.stackTrace();
	}
	
	public static Date now(){
		return new Date();
	}

	public static boolean useNativeSerialization() {
		return jdk().useNativeSerialization();
	}

    public static void registerPlatformHandlers(ObjectContainerBase container) {
        ReflectClass claxx = container.reflector().forClass(java.lang.Number.class);
        container.handlers().mapFieldHandler(claxx, container.handlers().untypedFieldHandler());
    }

    public static Class nullableTypeFor(Class primitiveJavaClass) {
    	if(_primitive2Wrapper == null)
    		initPrimitive2Wrapper();
    	Class wrapperClazz = (Class)_primitive2Wrapper.get(primitiveJavaClass);
    	if(wrapperClazz==null)        
    		throw new NotImplementedException("No nullableTypeFor : " + primitiveJavaClass.getName());
    	return wrapperClazz;
    }
    
    private static void initPrimitive2Wrapper(){
    	_primitive2Wrapper = new Hashtable4();
    	_primitive2Wrapper.put(int.class, Integer.class);
    	_primitive2Wrapper.put(byte.class, Byte.class);
    	_primitive2Wrapper.put(short.class, Short.class);
    	_primitive2Wrapper.put(float.class, Float.class);
    	_primitive2Wrapper.put(double.class, Double.class);
    	_primitive2Wrapper.put(long.class, Long.class);
    	_primitive2Wrapper.put(boolean.class, Boolean.class);
    	_primitive2Wrapper.put(char.class, Character.class);
    	
    }
	
    private static Hashtable4 _primitive2Wrapper;
    
    public static Object nullValue(Class clazz)
    {
    	if(_nullValues == null) {
    		initNullValues();
    	}
    	return _nullValues.get(clazz);
    	
    }
    
    private static void initNullValues() {
    	_nullValues = new Hashtable4();
    	_nullValues.put(boolean.class, Boolean.FALSE);
    	_nullValues.put(byte.class, new Byte((byte)0));
    	_nullValues.put(short.class, new Short((short)0));
    	_nullValues.put(char.class, new Character((char)0));
    	_nullValues.put(int.class, new Integer(0));
    	_nullValues.put(float.class, new Float(0.0));
    	_nullValues.put(long.class, new Long(0));
    	_nullValues.put(double.class, new Double(0.0));    	
	}

	private static Hashtable4 _nullValues;

}