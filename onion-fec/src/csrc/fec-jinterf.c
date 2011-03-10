#include <fcntl.h>
#include <string.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#if defined(__GNUC__) || !defined(_WIN32)
#include <stdint.h>
#endif
#ifndef __FreeBSD__
#include <malloc.h>
#endif

#ifndef GF_BITS
#error GF_BITS NOT DEFINED!
#endif

#if GF_BITS == 8
#include "com_onionnetworks_fec_Native8Code.h"
#define FEC_METHOD(NAME) Java_com_onionnetworks_fec_Native8 ## Code_ ## NAME
#elif GF_BITS == 16
#include "com_onionnetworks_fec_Native16Code.h"
#define FEC_METHOD(NAME) Java_com_onionnetworks_fec_Native16 ## Code_ ## NAME
#else
#error Unsupported GF_BITS
#endif
#include "fec.h"

/*
** Try to malloc to the given pointer. If it fails, set the pending Java
** exception to an OutOfMemoryError.
**
** @param CLEANUP: a label to goto in the case of failure, which should free()
**   unneeded memory (e.g. from an earlier successful malloc()) before the
**   function is exited.
** @param PTR: an already-declared, but uninitialised, pointer.
** @param TYPE: type of PTR
** @param NUM: number of objects to allocate (for an array)
** @param ENV: JNI environment for setting the pending exception
*/
#define malloc_or_oom(CLEANUP, PTR, TYPE, NUM, ENV) \
    PTR = (TYPE *) malloc(sizeof(TYPE) * NUM); \
    if (PTR == NULL) { \
        (*ENV)->ThrowNew(ENV, (*ENV)->FindClass(ENV, "java/lang/OutOfMemoryError"), "malloc failed"); \
        goto CLEANUP; \
    } \

/*
** Test if the given pointer is NULL. If so, assume that Java already has an
** exception pending; this will be true if PTR is the output of one of JNI's
** reference-getting functions.
**
** @param CLEANUP: a label to goto in the case of failure, which should free()
**   unneeded memory (e.g. from an earlier successful malloc()) before the
**   function is exited.
** @param PTR: the pointer to check
*/
#define nonnull_or_oom(CLEANUP, PTR) \
    if (PTR == NULL) { goto CLEANUP; } \

jfieldID codeField;
JNIEXPORT void JNICALL FEC_METHOD(initFEC)
  (JNIEnv * env, jclass clz) {
    codeField = (*env)->GetFieldID(env, clz, "code", "J");
}

/*
 * encode
 *
 * @param code This int is actually stores a memory address that points to
 * an fec_parms struct.
 */
JNIEXPORT void JNICALL FEC_METHOD(nativeEncode)
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
    malloc_or_oom(nativeEncode_cleanup_inArr, inArr, jbyteArray, k, env);
    malloc_or_oom(nativeEncode_cleanup_retArr, retArr, jbyteArray, k, env);
    malloc_or_oom(nativeEncode_cleanup_inarr, inarr, jbyte *, k, env);
    malloc_or_oom(nativeEncode_cleanup_retarr, retarr, jbyte *, k, env);

    numRet = (*env)->GetArrayLength(env, ret);

    /* PushLocalFrame reserves enough space for local variable references
     *
     * - 3 calls to GetIntArrayElements
     * - k calls to GetPrimitiveArrayCritical
     * - numRet calls to GetPrimitiveArrayCritical
     *
     * TODO: these calls might be pointless; see the corresponding comment in
     * decode() for details. Leaving in for now because I'm not a JNI expert.
     */
    if ((*env)->PushLocalFrame(env, 3+k+numRet) < 0) {
        goto nativeEncode_cleanup; /* exception OutOfMemoryError */
    }

    localSrcOff = (*env)->GetIntArrayElements(env, srcOff, NULL);
    nonnull_or_oom(nativeEncode_cleanup, localSrcOff);

    localIndex = (*env)->GetIntArrayElements(env, index, NULL);
    nonnull_or_oom(nativeEncode_cleanup, localIndex);

    localRetOff = (*env)->GetIntArrayElements(env, retOff, NULL);
    nonnull_or_oom(nativeEncode_cleanup, localRetOff);

    for (i=0; i<k; i++) {
        inArr[i] = ((*env)->GetObjectArrayElement(env, src, i));
        nonnull_or_oom(nativeEncode_cleanup, inArr[i]);

        inarr[i] = (*env)->GetPrimitiveArrayCritical(env, inArr[i], 0);
        nonnull_or_oom(nativeEncode_cleanup, inarr[i]);

        inarr[i] += localSrcOff[i];
    }

    for (i=0; i<numRet; i++) {
        retArr[i] = ((*env)->GetObjectArrayElement(env, ret, i));
        nonnull_or_oom(nativeEncode_cleanup, retArr[i]);

        retarr[i] = (*env)->GetPrimitiveArrayCritical(env, retArr[i], 0);
        nonnull_or_oom(nativeEncode_cleanup, retarr[i]);

        retarr[i] += localRetOff[i];
    }

    for (i=0; i<numRet; i++) {
        fec_encode((void *)(uintptr_t)code, (gf **)(uintptr_t)inarr, (void *)(uintptr_t)retarr[i],
                   (int)localIndex[i], (int)packetLength);
    }

    for (i=0; i<k; i++) {
        inarr[i] -= localSrcOff[i];
        (*env)->ReleasePrimitiveArrayCritical(env, inArr[i], inarr[i], 0);
    }

    for (i=0; i<numRet; i++) {
        retarr[i] -= localRetOff[i];
        (*env)->ReleasePrimitiveArrayCritical(env, retArr[i], retarr[i], 0);
    }

    (*env)->ReleaseIntArrayElements(env, srcOff, localSrcOff, 0);
    (*env)->ReleaseIntArrayElements(env, index, localIndex, 0);
    (*env)->ReleaseIntArrayElements(env, retOff, localRetOff, 0);

    /* free the memory reserved by PushLocalFrame() */
    result = (*env)->PopLocalFrame(env, result);

    /* free() complements malloc() */
    nativeEncode_cleanup:
    free(retarr); nativeEncode_cleanup_retarr:
    free(inarr); nativeEncode_cleanup_inarr:
    free(retArr); nativeEncode_cleanup_retArr:
    free(inArr); nativeEncode_cleanup_inArr:
    return;
}

/*
 * The data[] MUST be preshuffled before this call is made or it WILL NOT
 * WORK!  It is very difficult to make Java aware that the pointers have
 * been shuffled in the encode() call, so we must pre-shuffle the data
 * so that encode doesn't move any pointers around.
 */
JNIEXPORT void JNICALL FEC_METHOD(nativeDecode)
    (JNIEnv *env, jobject obj, jobjectArray data, jintArray dataOff,
     jintArray whichdata, jint k, jint packetLength) {

    jint *localWhich, *localDataOff;
    jbyteArray *inArr;
    jbyte **inarr;
    jobject result = NULL;

    int i;
    jlong code = (*env)->GetLongField(env, obj, codeField);

    /* allocate memory for the arrays */
    malloc_or_oom(nativeDecode_cleanup_inArr, inArr, jbyteArray, k, env);
    malloc_or_oom(nativeDecode_cleanup_inarr, inarr, jbyte *, k, env);

    /* PushLocalFrame reserves enough space for local variable references
     *
     * - 2 calls to GetIntArrayElements
     * - k calls to GetPrimitiveArrayCritical
     *
     * TODO: the JNI documentation at
     *
     * "Local Reference Management"
     * - http://www.j2ee.me/j2se/1.4.2/docs/guide/jni/jni-12.html#localrefs
     * "Array Operations"
     * - http://www.j2ee.me/j2se/1.4.2/docs/guide/jni/spec/functions.html#wp17314
     * "Global and Local References"
     * - http://www.j2ee.me/j2se/1.4.2/docs/guide/jni/spec/design.html#wp1242
     *
     * seems to me to suggest that the calls to PushLocalFrame, PopLocalFrame,
     * and Release** below are all redundant (with the possible exception of
     * ReleasePrimitiveArrayCritical). Local references should be released
     * automatically by the JNI as we exit this function; it seems to me to be
     * pointless to manually release these negligibly just before this, and
     * after the hard work has already been done by fec_decode / fec_encode
     * (which requires these references to be held).
     *
     * Leaving in for now because I'm not a JNI expert.
     */
    if ((*env)->PushLocalFrame(env, 2+k) < 0) {
        goto nativeDecode_cleanup; /* exception: OutOfMemoryError */
    }

    localDataOff = (*env)->GetIntArrayElements(env, dataOff, NULL);
    nonnull_or_oom(nativeDecode_cleanup, localDataOff);

    localWhich = (*env)->GetIntArrayElements(env, whichdata, NULL);
    nonnull_or_oom(nativeDecode_cleanup, localWhich);

    for (i=0; i<k; i++) {
        inArr[i] = ((*env)->GetObjectArrayElement(env, data, i));
        nonnull_or_oom(nativeDecode_cleanup, inArr[i]);

        inarr[i] = (*env)->GetPrimitiveArrayCritical(env, inArr[i], 0);
        nonnull_or_oom(nativeDecode_cleanup, inarr[i]);

        inarr[i] += localDataOff[i];
    }

    fec_decode((struct fec_parms *)(intptr_t)code, (gf **)(intptr_t)inarr, (int *)(intptr_t)localWhich, (int)packetLength);

    for (i=0; i<k; i++) {
        inarr[i] -= localDataOff[i];
        (*env)->SetObjectArrayElement(env, data, i, inArr[i]);
        (*env)->ReleasePrimitiveArrayCritical(env, inArr[i], inarr[i], 0);
    }

    (*env)->ReleaseIntArrayElements(env, whichdata, localWhich, 0);
    (*env)->ReleaseIntArrayElements(env, dataOff, localDataOff, 0);

    /* free the memory reserved by PushLocalFrame() */
    result = (*env)->PopLocalFrame(env, result);

    /* free() may not be necessary. complements malloc() */
    nativeDecode_cleanup:
    free(inarr); nativeDecode_cleanup_inarr:
    free(inArr); nativeDecode_cleanup_inArr:
    return;
}

JNIEXPORT jlong JNICALL FEC_METHOD(nativeNewFEC)
    (JNIEnv * env, jobject obj, jint k, jint n) {
    // uintptr_t is needed for systems where sizeof(void*) < sizeof(long)
    return (jlong)(uintptr_t)fec_new(k,n);
}

JNIEXPORT void JNICALL FEC_METHOD(nativeFreeFEC)
    (JNIEnv * env, jobject obj) {
    jlong code = (*env)->GetLongField(env, obj, codeField);
    fec_free((void *)(uintptr_t)code);
}
