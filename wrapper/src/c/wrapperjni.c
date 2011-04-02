/*
 * Copyright (c) 1999, 2008 Tanuki Software, Inc.
 * http://www.tanukisoftware.com
 * All rights reserved.
 *
 * This software is the proprietary information of Tanuki Software.
 * You shall use it only in accordance with the terms of the
 * license agreement you entered into with Tanuki Software.
 * http://wrapper.tanukisoftware.org/doc/english/licenseOverview.html
 * 
 * 
 * Portions of the Software have been derived from source code
 * developed by Silver Egg Technology under the following license:
 * 
 * Copyright (c) 2001 Silver Egg Technology
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without 
 * restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sub-license, and/or 
 * sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 */

#include <stdio.h>
#include <string.h>
#include <errno.h>
#ifdef WIN32
#include <windows.h>
#include <tchar.h>
#endif

#include "wrapperinfo.h"
#include "wrapperjni.h"

int wrapperJNIDebugging = JNI_FALSE;

#define CONTROL_EVENT_QUEUE_SIZE 10
int controlEventQueue[CONTROL_EVENT_QUEUE_SIZE];
int controlEventQueueLastReadIndex = 0;
int controlEventQueueLastWriteIndex = 0;

/**
 * Create an error message from GetLastError() using the
 *  FormatMessage API Call...
 */
#ifdef WIN32
TCHAR lastErrBuf[1024];
char* getLastErrorText() {
    DWORD dwRet;
    LPTSTR lpszTemp = NULL;

    dwRet = FormatMessage( FORMAT_MESSAGE_ALLOCATE_BUFFER | 
                           FORMAT_MESSAGE_FROM_SYSTEM |FORMAT_MESSAGE_ARGUMENT_ARRAY,
                           NULL,
                           GetLastError(),
                           LANG_NEUTRAL,
                           (LPTSTR)&lpszTemp,
                           0,
                           NULL);

    /* supplied buffer is not long enough */
    if (!dwRet || ((long)1023 < (long)dwRet+14)) {
        lastErrBuf[0] = TEXT('\0');
    } else {
        lpszTemp[lstrlen(lpszTemp)-2] = TEXT('\0');  /*remove cr and newline character */
        _stprintf( lastErrBuf, TEXT("%s (0x%x)"), lpszTemp, GetLastError());
    }

    if (lpszTemp) {
        GlobalFree((HGLOBAL) lpszTemp);
    }

    return lastErrBuf;
}
#else
char* getLastErrorText() {
    return strerror(errno);
}
#endif

void wrapperJNIHandleSignal(int signal) {
    if (wrapperLockControlEventQueue()) {
        /* Failed.  Should have been reported. */
        printf("WrapperJNI Error: Signal %d trapped, but ignored.\n", signal);
        fflush(NULL);
        return;
    }

#ifdef _DEBUG
    printf(" Queue Write 1 R:%d W:%d E:%d\n", controlEventQueueLastReadIndex, controlEventQueueLastWriteIndex, signal);
    fflush(NULL);
#endif
    controlEventQueueLastWriteIndex++;
    if (controlEventQueueLastWriteIndex >= CONTROL_EVENT_QUEUE_SIZE) {
        controlEventQueueLastWriteIndex = 0;
    }
    controlEventQueue[controlEventQueueLastWriteIndex] = signal;
#ifdef _DEBUG
    printf(" Queue Write 2 R:%d W:%d\n", controlEventQueueLastReadIndex, controlEventQueueLastWriteIndex);
    fflush(NULL);
#endif

    if (wrapperReleaseControlEventQueue()) {
        /* Failed.  Should have been reported. */
        return;
    }
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetLibraryVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetLibraryVersion(JNIEnv *env, jclass clazz) {
    jstring version;

    version = (*env)->NewStringUTF(env, wrapperVersion);

    return version;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeIsProfessionalEdition
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeIsProfessionalEdition(JNIEnv *env, jclass clazz) {
    return JNI_FALSE;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeIsStandardEdition
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeIsStandardEdition(JNIEnv *env, jclass clazz) {
    return JNI_FALSE;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetControlEvent
 * Signature: (V)I
 */
JNIEXPORT jint JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetControlEvent(JNIEnv *env, jclass clazz) {
    int event = 0;

    if (wrapperLockControlEventQueue()) {
        /* Failed.  Should have been reported. */
        return 0;
    }

    if (controlEventQueueLastWriteIndex != controlEventQueueLastReadIndex) {
#ifdef _DEBUG
        printf(" Queue Read 1 R:%d W:%d\n", controlEventQueueLastReadIndex, controlEventQueueLastWriteIndex);
        fflush(NULL);
#endif
        controlEventQueueLastReadIndex++;
        if (controlEventQueueLastReadIndex >= CONTROL_EVENT_QUEUE_SIZE) {
            controlEventQueueLastReadIndex = 0;
        }
        event = controlEventQueue[controlEventQueueLastReadIndex];
#ifdef _DEBUG
        printf(" Queue Read 2 R:%d W:%d E:%d\n", controlEventQueueLastReadIndex, controlEventQueueLastWriteIndex, event);
        fflush(NULL);
#endif
    }

    if (wrapperReleaseControlEventQueue()) {
        /* Failed.  Should have been reported. */
        return event;
    }

    return event;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    accessViolationInner
 * Signature: (V)V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_accessViolationInner(JNIEnv *env, jclass clazz) {
    char *ptr;

    /* Cause access violation */
    ptr = NULL;
    ptr[0] = '\n';
}
