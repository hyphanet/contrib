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

import com.db4o.foundation.*;
import com.db4o.internal.diagnostic.*;
import com.db4o.internal.fieldhandlers.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.replication.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 * 
 * TODO: This class was written to make ObjectContainerBase 
 * leaner, so TransportObjectContainer has less members.
 * 
 * All funcionality of this class should become part of 
 * ObjectContainerBase and the functionality in 
 * ObjectContainerBase should delegate to independant
 * modules without circular references.
 * 
 */
public final class HandlerRegistry {
    
    public static final byte HANDLER_VERSION = (byte)3;
    
    private final ObjectContainerBase _container;  // this is the master container and not valid
	                                   // for TransportObjectContainer

    private static final Db4oTypeImpl[]   _db4oTypes     = { new BlobImpl()};

    private ClassMetadata                _untypedArrayHandler;
    
    private ClassMetadata                _untypedMultiDimensionalArrayHandler;
    
    private FieldHandler _untypedFieldHandler;

    public StringHandler          _stringHandler;
    
    private Hashtable4 _mapIdToTypeInfo = newHashtable();
    
    private Hashtable4 _mapFieldHandlerToId = newHashtable();
    
    private Hashtable4 _mapTypeHandlerToId = newHashtable();
    
    private Hashtable4 _mapReflectorToClassMetadata = newHashtable();
    

    private int                     _highestBuiltinTypeID     = Handlers4.ANY_ARRAY_N_ID + 1;

    private final VirtualFieldMetadata[]         _virtualFields = new VirtualFieldMetadata[2]; 

    private final Hashtable4        _mapReflectorToFieldHandler  = newHashtable();
    
    private final Hashtable4        _mapReflectorToTypeHandler  = newHashtable();
    
    // see comment in classReflectorForHandler
    private final Hashtable4        _mapFieldHandlerToReflector  = newHashtable();
    
    private SharedIndexedFields              		_indexes;
    
    MigrationConnection             i_migration;
    
    Db4oReplicationReferenceProvider _replicationReferenceProvider;
    
    public final DiagnosticProcessor      _diagnosticProcessor;
    
    public boolean                 i_encrypt;
    byte[]                  i_encryptor;
    int                     i_lastEncryptorByte;
    
    final GenericReflector                _reflector;
    
    private final HandlerVersionRegistry _handlerVersions;
    
    private LatinStringIO _stringIO;
    
    public ReflectClass ICLASS_COMPARE;
    ReflectClass ICLASS_DB4OTYPE;
    ReflectClass ICLASS_DB4OTYPEIMPL;
	public ReflectClass ICLASS_INTERNAL;
    ReflectClass ICLASS_UNVERSIONED;
    public ReflectClass ICLASS_OBJECT;
    ReflectClass ICLASS_OBJECTCONTAINER;
	public ReflectClass ICLASS_STATICCLASS;
	public ReflectClass ICLASS_STRING;
    ReflectClass ICLASS_TRANSIENTCLASS;


    HandlerRegistry(final ObjectContainerBase container, byte stringEncoding, GenericReflector reflector) {
        
        _handlerVersions = new HandlerVersionRegistry(this);
        
        _stringIO = LatinStringIO.forEncoding(stringEncoding);
    	
    	_container = container;
    	container._handlers = this;
        
        _reflector = reflector;
        _diagnosticProcessor = container.configImpl().diagnosticProcessor();
    	
    	initClassReflectors(reflector);
        
        _indexes = new SharedIndexedFields();
        
        _virtualFields[0] = _indexes._version;
        _virtualFields[1] = _indexes._uUID;

        registerBuiltinHandlers();
        
        registerPlatformTypes();
        
        initArrayHandlers();
        
        Platform4.registerPlatformHandlers(container);
    }

    private void initArrayHandlers() {
        TypeHandler4 handler = (TypeHandler4) fieldHandlerForId(Handlers4.UNTYPED_ID);
        _untypedArrayHandler = new PrimitiveFieldHandler(
            container(), 
            new ArrayHandler(handler, false), 
            Handlers4.ANY_ARRAY_ID,
            ICLASS_OBJECT);
        mapTypeInfo(
            Handlers4.ANY_ARRAY_ID, 
            _untypedArrayHandler, 
            new UntypedArrayFieldHandler(), 
            _untypedArrayHandler,
            null );

        _untypedMultiDimensionalArrayHandler = new PrimitiveFieldHandler(
            container(), 
            new MultidimensionalArrayHandler(handler, false), 
            Handlers4.ANY_ARRAY_N_ID,
            ICLASS_OBJECT);
        mapTypeInfo(
            Handlers4.ANY_ARRAY_N_ID, 
            _untypedMultiDimensionalArrayHandler, 
            new UntypedMultidimensionalArrayFieldHandler(), 
            _untypedMultiDimensionalArrayHandler,
            null );

    }
    
    private void registerPlatformTypes() {
        NetTypeHandler[] handlers = Platform4.types(_container.reflector());
        for (int i = 0; i < handlers.length; i++) {
        	registerNetTypeHandler(handlers[i]);
        }
    }

	public void registerNetTypeHandler(NetTypeHandler handler) {
		handler.registerReflector(_reflector);
		GenericConverter converter = (handler instanceof GenericConverter) ? (GenericConverter)handler : null;
		registerBuiltinHandler(handler.getID(), handler, true, handler.getName(), converter);
	}
    
    private void registerBuiltinHandlers(){
        
        IntHandler intHandler = new IntHandler();
        registerBuiltinHandler(Handlers4.INT_ID, intHandler);
        registerHandlerVersion(intHandler, 0, new IntHandler0());
        
        LongHandler longHandler = new LongHandler();
        registerBuiltinHandler(Handlers4.LONG_ID, longHandler);
        registerHandlerVersion(longHandler, 0, new LongHandler0());
        
        FloatHandler floatHandler = new FloatHandler();
        registerBuiltinHandler(Handlers4.FLOAT_ID, floatHandler);
        registerHandlerVersion(floatHandler, 0, new FloatHandler0());
        
        BooleanHandler booleanHandler = new BooleanHandler();
        registerBuiltinHandler(Handlers4.BOOLEAN_ID, booleanHandler);
        // TODO: Are we missing a boolean handler version?
        
        DoubleHandler doubleHandler = new DoubleHandler();
        registerBuiltinHandler(Handlers4.DOUBLE_ID, doubleHandler);
        registerHandlerVersion(doubleHandler, 0, new DoubleHandler0());
        
        ByteHandler byteHandler = new ByteHandler();
        registerBuiltinHandler(Handlers4.BYTE_ID, byteHandler);
        // TODO: Are we missing a byte handler version?

        CharHandler charHandler = new CharHandler();
        registerBuiltinHandler(Handlers4.CHAR_ID, charHandler);
        // TODO: Are we missing a char handler version?
        
        ShortHandler shortHandler = new ShortHandler();
        registerBuiltinHandler(Handlers4.SHORT_ID, shortHandler);
        registerHandlerVersion(shortHandler, 0, new ShortHandler0());
        
        _stringHandler = new StringHandler();
        registerBuiltinHandler(Handlers4.STRING_ID, _stringHandler);
        registerHandlerVersion(_stringHandler, 0, new StringHandler0());

        DateHandler dateHandler = new DateHandler();
        registerBuiltinHandler(Handlers4.DATE_ID, dateHandler);
        registerHandlerVersion(dateHandler, 0, new DateHandler0());
        
        registerUntypedHandlers();
        
        registerCompositeHandlerVersions();
    }

    private void registerUntypedHandlers() {
        int id = Handlers4.UNTYPED_ID;
        _untypedFieldHandler = new UntypedFieldHandler(container());
        PrimitiveFieldHandler classMetadata = new PrimitiveFieldHandler(container(), (TypeHandler4)_untypedFieldHandler, id, ICLASS_OBJECT);
        map(id, classMetadata, _untypedFieldHandler, new PlainObjectHandler(), ICLASS_OBJECT);
        registerHandlerVersion(_untypedFieldHandler, 0, new UntypedFieldHandler0(container()));
        registerHandlerVersion(_untypedFieldHandler, 2, new UntypedFieldHandler2(container()));

    }
    
    private void registerCompositeHandlerVersions(){
        
        FirstClassObjectHandler firstClassObjectHandler = new FirstClassObjectHandler();
        registerHandlerVersion(firstClassObjectHandler, 0, new FirstClassObjectHandler0());
        
        ArrayHandler arrayHandler = new ArrayHandler();
        registerHandlerVersion(arrayHandler, 0, new ArrayHandler0());
        registerHandlerVersion(arrayHandler, 2, new ArrayHandler2());
        
        MultidimensionalArrayHandler multidimensionalArrayHandler = new MultidimensionalArrayHandler();
        registerHandlerVersion(multidimensionalArrayHandler, 0, new MultidimensionalArrayHandler0());
        
        PrimitiveFieldHandler primitiveFieldHandler = new PrimitiveFieldHandler();
        registerHandlerVersion(primitiveFieldHandler, 0, primitiveFieldHandler);  // same handler, but making sure versions get cascaded
        registerHandlerVersion(primitiveFieldHandler, 2, primitiveFieldHandler);  // same handler, but making sure versions get cascaded
    }
    
    private void registerBuiltinHandler(int id, BuiltinTypeHandler handler) {
        registerBuiltinHandler(id, handler, true, null, null);
    }

    private void registerBuiltinHandler(int id, BuiltinTypeHandler typeHandler, boolean registerPrimitiveClass, String primitiveName, GenericConverter converter) {

        typeHandler.registerReflector(_reflector);
        if(primitiveName == null) {
        	primitiveName = typeHandler.classReflector().getName();
        }

        if(registerPrimitiveClass){
            _reflector.registerPrimitiveClass(id, primitiveName, converter);
        }
        
        ReflectClass classReflector = typeHandler.classReflector();
        
        PrimitiveFieldHandler classMetadata = new PrimitiveFieldHandler(container(), typeHandler, id, classReflector);
        
        map(id, classMetadata, typeHandler, typeHandler, classReflector);
        
        if (NullableArrayHandling.useJavaHandling()) {
            if(typeHandler instanceof PrimitiveHandler){
                ReflectClass primitiveClassReflector = 
                    ((PrimitiveHandler) typeHandler).primitiveClassReflector();
                if(primitiveClassReflector != null){
                    mapPrimitive(0, classMetadata, typeHandler, typeHandler, primitiveClassReflector);
                }
            }
        }
    }
    
    private void map(
        int id,
        ClassMetadata classMetadata,  // TODO: remove when _mapIdToClassMetadata is gone 
        FieldHandler fieldHandler, 
        TypeHandler4 typeHandler, 
        ReflectClass classReflector) {
        
        mapTypeInfo(id, classMetadata, fieldHandler, typeHandler, classReflector);
        
        mapPrimitive(id, classMetadata, fieldHandler, typeHandler, classReflector);
        if (id > _highestBuiltinTypeID) {
            _highestBuiltinTypeID = id;
        }
    }
    
    private void mapTypeInfo(
        int id,
        ClassMetadata classMetadata, 
        FieldHandler fieldHandler,
        TypeHandler4 typeHandler, 
        ReflectClass classReflector) {
        _mapIdToTypeInfo.put(id, new TypeInfo(classMetadata,fieldHandler, typeHandler, classReflector));
    }
    
    private void mapPrimitive(int id, ClassMetadata classMetadata, FieldHandler fieldHandler, TypeHandler4 typeHandler, ReflectClass classReflector) {
        _mapFieldHandlerToReflector.put(fieldHandler, classReflector);
        mapFieldHandler(classReflector, fieldHandler);
        _mapReflectorToTypeHandler.put(classReflector, typeHandler);
        if(classReflector != null){
            _mapReflectorToClassMetadata.put(classReflector, classMetadata);
        }
        if(id != 0){
            Integer wrappedID = new Integer(id);
            _mapFieldHandlerToId.put(fieldHandler, wrappedID);
            _mapTypeHandlerToId.put(typeHandler, wrappedID);
        }
    }

    public void mapFieldHandler(ReflectClass classReflector, FieldHandler fieldHandler) {
        _mapReflectorToFieldHandler.put(classReflector, fieldHandler);
    }

	private void registerHandlerVersion(FieldHandler handler, int version, TypeHandler4 replacement) {
		if(replacement instanceof BuiltinTypeHandler) {
			((BuiltinTypeHandler)replacement).registerReflector(_reflector);
		}
	    _handlerVersions.put(handler, version, replacement);
    }

    public TypeHandler4 correctHandlerVersion(TypeHandler4 handler, int version){
        return _handlerVersions.correctHandlerVersion(handler, version);
    }

    int arrayType(Object obj) {
    	ReflectClass claxx = reflector().forObject(obj);
        if (! claxx.isArray()) {
            return 0;
        }
        if (reflector().array().isNDimensional(claxx)) {
            return Const4.TYPE_NARRAY;
        } 
        return Const4.TYPE_ARRAY;
    }
	
	public final void decrypt(ByteArrayBuffer reader) {
	    if(i_encrypt){
			int encryptorOffSet = i_lastEncryptorByte;
			byte[] bytes = reader._buffer;
			for (int i = reader.length() - 1; i >= 0; i--) {
				bytes[i] += i_encryptor[encryptorOffSet];
				if (encryptorOffSet == 0) {
					encryptorOffSet = i_lastEncryptorByte;
				} else {
					encryptorOffSet--;
				}
			}
	    }
	}
	
    public final void encrypt(ByteArrayBuffer reader) {
        if(i_encrypt){
	        byte[] bytes = reader._buffer;
	        int encryptorOffSet = i_lastEncryptorByte;
	        for (int i = reader.length() - 1; i >= 0; i--) {
	            bytes[i] -= i_encryptor[encryptorOffSet];
	            if (encryptorOffSet == 0) {
	                encryptorOffSet = i_lastEncryptorByte;
	            } else {
	                encryptorOffSet--;
	            }
	        }
        }
    }
    
    public void oldEncryptionOff() {
        i_encrypt = false;
        i_encryptor = null;
        i_lastEncryptorByte = 0;
        container().configImpl().oldEncryptionOff();
    }
    
    public final ReflectClass classForID(int id) {
        TypeInfo typeInfo = typeInfoForID(id);
        if(typeInfo == null){
            return null;
        }
        return typeInfo.classReflector;
    }

    public final TypeHandler4 typeHandlerForID(int id) {
        TypeInfo typeInfo = typeInfoForID(id);
        if(typeInfo == null){
            return null;
        }
        return typeInfo.typeHandler;
    }
    
    private TypeInfo typeInfoForID(int id){
        return (TypeInfo)_mapIdToTypeInfo.get(id);
    }
    
    public final int typeHandlerID(TypeHandler4 handler){
        if(handler instanceof ClassMetadata){
            return ((ClassMetadata)handler).getID();
        }
        Object idAsInt = _mapTypeHandlerToId.get(handler);
        if(idAsInt == null){
            return 0;
        }
        return ((Integer)idAsInt).intValue();
    }

	private void initClassReflectors(GenericReflector reflector){
		ICLASS_COMPARE = reflector.forClass(Const4.CLASS_COMPARE);
		ICLASS_DB4OTYPE = reflector.forClass(Const4.CLASS_DB4OTYPE);
		ICLASS_DB4OTYPEIMPL = reflector.forClass(Const4.CLASS_DB4OTYPEIMPL);
        ICLASS_INTERNAL = reflector.forClass(Const4.CLASS_INTERNAL);
        ICLASS_UNVERSIONED = reflector.forClass(Const4.CLASS_UNVERSIONED);
		ICLASS_OBJECT = reflector.forClass(Const4.CLASS_OBJECT);
		ICLASS_OBJECTCONTAINER = reflector
				.forClass(Const4.CLASS_OBJECTCONTAINER);
		ICLASS_STATICCLASS = reflector.forClass(Const4.CLASS_STATICCLASS);
		ICLASS_STRING = reflector.forClass(String.class);
		ICLASS_TRANSIENTCLASS = reflector
				.forClass(Const4.CLASS_TRANSIENTCLASS);
		
		Platform4.registerCollections(reflector);
    }
    
    void initEncryption(Config4Impl a_config){
        if (a_config.encrypt() && a_config.password() != null
            && a_config.password().length() > 0) {
            i_encrypt = true;
            i_encryptor = new byte[a_config.password().length()];
            for (int i = 0; i < i_encryptor.length; i++) {
                i_encryptor[i] = (byte) (a_config.password().charAt(i) & 0xff);
            }
            i_lastEncryptorByte = a_config.password().length() - 1;
            return;
        }
        
        oldEncryptionOff();
    }
    
    static Db4oTypeImpl getDb4oType(ReflectClass clazz) {
        for (int i = 0; i < _db4oTypes.length; i++) {
            if (clazz.isInstance(_db4oTypes[i])) {
                return _db4oTypes[i];
            }
        }
        return null;
    }

    public ClassMetadata classMetadataForId(int id) {
        TypeInfo typeInfo = typeInfoForID(id);
        if(typeInfo == null){
            return null;
        }
        return typeInfo.classMetadata;
    }
    
    public FieldHandler fieldHandlerForId(int id){
        TypeInfo typeInfo = typeInfoForID(id);
        if(typeInfo == null){
            return null;
        }
        return typeInfo.fieldHandler;
    }
    
    public FieldHandler fieldHandlerForClass(ReflectClass clazz) {
        
        // TODO: maybe need special handling for arrays here?
        
        if (clazz == null) {
            return null;
        }
        
        if(clazz.isInterface()){
           return untypedFieldHandler();
        }
        
        if (clazz.isArray()) {
            if (reflector().array().isNDimensional(clazz)) {
                return _untypedMultiDimensionalArrayHandler;
            }
            return _untypedArrayHandler;
        }
        
        FieldHandler fieldHandler = (FieldHandler) _mapReflectorToFieldHandler.get(clazz);
        if(fieldHandler != null){
            return fieldHandler;
        }
        TypeHandler4 configuredHandler =
            container().configImpl().typeHandlerForClass(clazz, HandlerRegistry.HANDLER_VERSION);
        if(configuredHandler != null && SlotFormat.isEmbedded(configuredHandler)){
            mapFieldHandler(clazz, configuredHandler);
            return configuredHandler;
        }
        return null;
    }

    ClassMetadata classMetadataForClass(ReflectClass clazz) {
        if (clazz == null) {
            return null;
        }
        if (clazz.isArray()) {
            return (ClassMetadata) untypedArrayHandler(clazz);
        }
        return (ClassMetadata) _mapReflectorToClassMetadata.get(clazz);
    }
    
    public FieldHandler untypedFieldHandler(){
        return _untypedFieldHandler;
    }
    
    public TypeHandler4 untypedObjectHandler(){
        return (TypeHandler4) untypedFieldHandler();
    }
    
    public TypeHandler4 untypedArrayHandler(ReflectClass clazz){
        if (clazz.isArray()) {
            if (reflector().array().isNDimensional(clazz)) {
                return _untypedMultiDimensionalArrayHandler;
            }
            return _untypedArrayHandler;
        }
        return null;
    }
    
    public TypeHandler4 typeHandlerForClass(ReflectClass clazz){
        if(clazz == null){
            return null;
        }
        return (TypeHandler4) _mapReflectorToTypeHandler.get(clazz);
    }
    
    public ReflectClass classReflectorForHandler(TypeHandler4 handler){
        
        // This method never gets called from test cases so far.
        
        // It is written for the usecase of custom Typehandlers and
        // it is only require for arrays.
        
        // The methodology is highly problematic since it implies that 
        // one Typehandler can only be used for one ReflectClass.
        
        
        return (ReflectClass) _mapFieldHandlerToReflector.get(handler);
    }
    
    public boolean isSecondClass(Object a_object){
    	if(a_object != null){
    		ReflectClass claxx = reflector().forObject(a_object);
    		if(_mapReflectorToFieldHandler.get(claxx) != null){
    			return true;
    		}
            return Platform4.isValueType(claxx);
    	}
    	return false;
    }

    public boolean isSystemHandler(int id) {
    	return id <= _highestBuiltinTypeID;
    }

	public void migrationConnection(MigrationConnection mgc) {
		i_migration = mgc;
	}
	
	public MigrationConnection  migrationConnection() {
		return i_migration;
	}

	public VirtualFieldMetadata virtualFieldByName(String name) {
        for (int i = 0; i < _virtualFields.length; i++) {
            if (name.equals(_virtualFields[i].getName())) {
                return _virtualFields[i];
            }
        }
        return null;
	}

    public boolean isVariableLength(TypeHandler4 handler) {
        return handler instanceof VariableLengthTypeHandler;
    }
    
    public SharedIndexedFields indexes(){
        return _indexes;
    }
    
    public LatinStringIO stringIO(){
        return _stringIO;
    }

    public void stringIO(LatinStringIO io) {
        _stringIO = io;
    }
    
    private GenericReflector reflector() {
        return container().reflector();
    }

    private ObjectContainerBase container() {
        return _container;
    }
    
    private static final Hashtable4 newHashtable(){
        return new Hashtable4(32);
    }

    public int fieldHandlerIdForFieldHandler(FieldHandler fieldHandler) {
        Object wrappedIdObj = _mapFieldHandlerToId.get(fieldHandler);
        if(wrappedIdObj != null){
    		Integer wrappedId = (Integer) wrappedIdObj;
            return wrappedId.intValue();
        }
        return 0;
    }

    public TypeHandler4 configuredTypeHandler(ReflectClass claxx) {
        TypeHandler4 typeHandler = container().configImpl().typeHandlerForClass(claxx, HANDLER_VERSION);
        if(typeHandler != null  && typeHandler instanceof EmbeddedTypeHandler){
        	_mapReflectorToTypeHandler.put(claxx, typeHandler);
        }
        return typeHandler;
    }

}