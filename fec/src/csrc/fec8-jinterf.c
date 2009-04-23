#include <fcntl.h>
#include <string.h>
#include "jni.h"
#include <stdio.h>
#include <stdlib.h>
#if defined(__GNUC__) || !defined(_WIN32)
#include <stdint.h>
#endif
#include <malloc.h>
#include "com_onionnetworks_fec_Native8Code.h"
#include "fec.h"

jfieldID codeField;
JNIEXPORT void JNICALL Java_com_onionnetworks_fec_Native8Code_initFEC
  (JNIEnv * env, jclass clz) {
	codeField = (*env)->GetFieldID(env, clz, "code", "J");
}

/*
 * encode
 *
 * @param code This int is actually stores a memory address that points to
 * an fec_parms struct.
 */
JNIEXPORT void JNICALL Java_com_onionnetworks_fec_Native8Code_nativeEncode
  (JNIEnv *env, jobject obj, jobjectArray src, jintArray srcOff,
	jintArray index, jobjectArray ret, jintArray retOff, jint k,
	jint packetLength) {

    jint *localSrcOff, *localIndex, *localRetOff;
	jbyteArray *inArr, *retArr;
    jbyte **inarr, **retarr;
	jobject result = NULL;

	int i, numRet;
	jlong code = (*env)->GetLongField(env, obj, codeField);

	/* allocate memory for the arrays */
    inArr  = (jbyteArray *) malloc(sizeof(jbyteArray) * k);
    retArr = (jbyteArray *) malloc(sizeof(jbyteArray) * k);

    inarr  = (jbyte **) malloc(sizeof(jbyte *) * k);
    retarr = (jbyte **) malloc(sizeof(jbyte *) * k);

    numRet = (*env)->GetArrayLength(env, ret);

	/* PushLocalFrame reserves enough space for local variable references */
	if ((*env)->PushLocalFrame(env, k*2+numRet+3) < 0) {
		return; /* exception OutOfMemoryError */
	}

    localSrcOff = (*env)->GetIntArrayElements(env, srcOff, NULL);
    if (localSrcOff == NULL) {
        return; /* exception occured */
    }

    localIndex = (*env)->GetIntArrayElements(env, index, NULL);
    if (localIndex == NULL) {
        return; /* exception occured */
    }

    localRetOff = (*env)->GetIntArrayElements(env, retOff, NULL);
    if (localRetOff == NULL) {
        return; /* exception occured */
    }

    for (i=0;i<k;i++) {
		inArr[i] = ((*env)->GetObjectArrayElement(env, src, i));
			if (inArr[i] == NULL) {
				return; /* exception occured */
			}

		inarr[i] = (*env)->GetPrimitiveArrayCritical(env, inArr[i], 0); 
        if (inarr[i] == NULL) {
            return; /* exception occured */
        }
        inarr[i] += localSrcOff[i];
    }

    for (i=0;i<numRet;i++) {
		retArr[i] = ((*env)->GetObjectArrayElement(env, ret, i));
        if (retArr[i] == NULL) {
            return; /* exception occured */
        }

		retarr[i] = (*env)->GetPrimitiveArrayCritical(env, retArr[i], 0); 
        if (retarr[i] == NULL) {
            return; /* exception occured */
        }
        retarr[i] += localRetOff[i];
    }

    for (i=0;i<numRet;i++) {
        fec_encode((void *)(uintptr_t)code, (gf **)(uintptr_t)inarr, (void *)(uintptr_t)retarr[i],
                   (int)localIndex[i], (int)packetLength);
    }

    for (i=0;i<k;i++) {
        inarr[i] -= localSrcOff[i]; 
		(*env)->ReleasePrimitiveArrayCritical(env, inArr[i], inarr[i], 0);
    } 
 
    for (i=0;i<numRet;i++) {
        retarr[i] -= localRetOff[i];
		(*env)->ReleasePrimitiveArrayCritical(env, retArr[i], retarr[i], 0); 
    }

    (*env)->ReleaseIntArrayElements(env, srcOff, localSrcOff, 0);
    (*env)->ReleaseIntArrayElements(env, index, localIndex, 0);
    (*env)->ReleaseIntArrayElements(env, retOff, localRetOff, 0);

	/* free the memory reserved by PushLocalFrame() */
	result = (*env)->PopLocalFrame(env, result);

	/* free() complements malloc() */
	free(inArr);
	free(retArr);
	free(inarr);
	free(retarr);
}


JNIEXPORT void JNICALL Java_com_onionnetworks_fec_Native8Code_nativeDecode
    (JNIEnv *env, jobject obj, jobjectArray data, jintArray dataOff,
     jintArray whichdata, jint k, jint packetLength) {

    jint *localWhich, *localDataOff;
	jbyteArray *inArr;
	jbyte **inarr;
	jobject result = NULL;
	
	int i;
	jlong code = (*env)->GetLongField(env, obj, codeField);

	/* allocate memory for the arrays */
	inArr = (jbyteArray *) malloc(sizeof(jbyteArray) * k);
	inarr = (jbyte **) malloc(sizeof(jbyte *) * k);

    localDataOff = (*env)->GetIntArrayElements(env, dataOff, NULL);
    if (localDataOff == NULL) {
        return;  /* exception occured */
    }

    localWhich = (*env)->GetIntArrayElements(env, whichdata, NULL);
    if (localWhich == NULL) {
        return;  /* exception occured */
    }

	/* PushLocalFrame reserves enough space for local variable references */
	if ((*env)->PushLocalFrame(env, k) < 0) {
		return; /* exception: OutOfMemoryError */
	}

    for (i=0;i<k;i++) {
	inArr[i] = ((*env)->GetObjectArrayElement(env, data, i));
        if (inArr[i] == NULL) {
            return;  /* exception occured */
        }
	inarr[i] = (*env)->GetPrimitiveArrayCritical(env, inArr[i], 0); 
        if (inarr[i] == NULL) {
            return;  /* exception occured */
        }
        inarr[i] += localDataOff[i];
    }

	fec_decode((struct fec_parms *)(intptr_t)code, (gf **)(intptr_t)inarr, (int *)(intptr_t)localWhich, (int)packetLength);

    for (i = 0; i < k; i++) {
        inarr[i] -= localDataOff[i];
        (*env)->SetObjectArrayElement(env, data, i, inArr[i]);
    }

    for (i = 0; i < k; i++) {
		(*env)->ReleasePrimitiveArrayCritical(env, inArr[i], inarr[i], 0); 
    }

    (*env)->ReleaseIntArrayElements(env, whichdata, localWhich, 0);
    (*env)->ReleaseIntArrayElements(env, dataOff, localDataOff, 0);

	/* free the memory reserved by PushLocalFrame() */
	result = (*env)->PopLocalFrame(env, result);

	/* free() may not be necessary. complements malloc() */
	free(inArr);
	free(inarr);
}

JNIEXPORT void JNICALL Java_com_onionnetworks_fec_Native8Code_nativeNewFEC
    (JNIEnv * env, jobject obj, jint k, jint n) {
    // uintptr_t is needed for systems where sizeof(void*) < sizeof(long)
    jlong code = (jlong)(uintptr_t)fec_new(k,n);

	(*env)->SetLongField(env, obj, codeField, code);
}

JNIEXPORT void JNICALL Java_com_onionnetworks_fec_Native8Code_nativeFreeFEC
    (JNIEnv * env, jobject obj) {
	jlong code = (*env)->GetLongField(env, obj, codeField);
    fec_free((void *)(uintptr_t)code);
	(*env)->SetLongField(env, obj, codeField, 0);
}
