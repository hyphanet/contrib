/*
 * Copyright (c) 1999, 2009 Tanuki Software, Ltd.
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

#ifndef WIN32

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <grp.h>
#include <pthread.h>
#include <pwd.h>
#include <signal.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#include "wrapperjni.h"

static pid_t wrapperProcessId = -1;

pthread_mutex_t controlEventQueueMutex = PTHREAD_MUTEX_INITIALIZER;

int wrapperLockControlEventQueue() {
    int count = 0;
    struct timespec ts;
    
    /* Only wait for up to 30 seconds to make sure we don't get into a deadlock situation.
     *  This could happen if a signal is encountered while locked. */
    while (pthread_mutex_trylock(&controlEventQueueMutex) == EBUSY) {
        if (count >= 3000) {
            printf("WrapperJNI Error: Timed out waiting for control event queue lock.\n");
            fflush(NULL);
            return -1;
        }

        ts.tv_sec = 0;
        ts.tv_nsec = 10000000; /* 10ms (nanoseconds) */
        nanosleep(&ts, NULL);
        count++;
    }

    if ( count > 0 ) {
        if (wrapperJNIDebugging) {
            /* This is useful for making sure that the JNI call is working. */
            printf("WrapperJNI Debug: wrapperLockControlEventQueue looped %d times before lock.\n", count);
            fflush(NULL);
        }
    }

    return 0;
}

int wrapperReleaseControlEventQueue() {
    if (pthread_mutex_unlock(&controlEventQueueMutex)) {
        printf("WrapperJNI Error: Failed to unlock the event queue mutex.\n");
        fflush(NULL);
    }
    return 0;
}

/**
 * Handle interrupt signals (i.e. Crtl-C).
 */
void handleInterrupt(int sig_num) {
    wrapperJNIHandleSignal(org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_C_EVENT);
    signal(SIGINT, handleInterrupt);
}

/**
 * Handle termination signals (i.e. machine is shutting down).
 */
void handleTermination(int sig_num) {
    wrapperJNIHandleSignal(org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_TERM_EVENT);
    signal(SIGTERM, handleTermination); 
}

/**
 * Handle hangup signals.
 */
void handleHangup(int sig_num) {
    wrapperJNIHandleSignal(org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_HUP_EVENT);
    signal(SIGHUP, handleHangup); 
}

/**
 * Handle usr1 signals.
 *
 * SIGUSR1 & SIGUSR2 are used by the JVM for internal garbage collection sweeps.
 *  These signals MUST be passed on to the JVM or the JVM will hang.
 */
/*
void handleUsr1(int sig_num) {
    wrapperJNIHandleSignal(org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_USR1_EVENT);
    signal(SIGUSR1, handleUsr1); 
}
*/

/**
 * Handle usr2 signals.
 *
 * SIGUSR1 & SIGUSR2 are used by the JVM for internal garbage collection sweeps.
 *  These signals MUST be passed on to the JVM or the JVM will hang.
 */
/*
void handleUsr2(int sig_num) {
    wrapperJNIHandleSignal(org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_USR2_EVENT);
    signal(SIGUSR2, handleUsr2); 
}
*/

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeInit
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeInit(JNIEnv *env, jclass clazz, jboolean debugging) {
    wrapperJNIDebugging = debugging;

    if (wrapperJNIDebugging) {
        /* This is useful for making sure that the JNI call is working. */
        printf("WrapperJNI Debug: Inside native WrapperManager initialization method\n");
        fflush(NULL);
    }

    /* Set handlers for signals */
    signal(SIGINT,  handleInterrupt);
    signal(SIGTERM, handleTermination);
    signal(SIGHUP,  handleHangup);
    /*
    signal(SIGUSR1, handleUsr1);
    signal(SIGUSR2, handleUsr2);
    */

    /* Store the current process Id */
    wrapperProcessId = getpid();
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetJavaPID
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetJavaPID(JNIEnv *env, jclass clazz) {
    return (int)getpid();
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeRequestThreadGroup
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeRequestThreadDump(JNIEnv *env, jclass clazz) {
    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Sending SIGQUIT event to process group %d.\n",
            (int)wrapperProcessId);
        fflush(NULL);
    }
    if (kill(wrapperProcessId, SIGQUIT) < 0) {
        printf("WrapperJNI Error: Unable to send SIGQUIT to JVM process: %s\n",
            getLastErrorText());
        fflush(NULL);
    }
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeSetConsoleTitle
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeSetConsoleTitle(JNIEnv *env, jclass clazz, jbyteArray jTitleBytes) {
    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Setting the console title not supported on UNIX platforms.\n");
        fflush(NULL);
    }
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetUser
 * Signature: (Z)Lorg/tanukisoftware/wrapper/WrapperUser;
 */
/*#define UVERBOSE*/
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetUser(JNIEnv *env, jclass clazz, jboolean groups) {
    jclass wrapperUserClass;
    jmethodID constructor;
    jmethodID setGroup;
    jmethodID addGroup;
    uid_t uid;
    struct passwd *pw;
    gid_t ugid;
    jbyteArray jUser;
    jbyteArray jRealName;
    jbyteArray jHome;
    jbyteArray jShell;
    jobject wrapperUser = NULL;
    struct group *aGroup;
    int member;
    int i;
    gid_t ggid;
    jbyteArray jGroupName;

    /* Look for the WrapperUser class. Ignore failures as JNI throws an exception. */
    if ((wrapperUserClass = (*env)->FindClass(env, "org/tanukisoftware/wrapper/WrapperUNIXUser")) != NULL) {

        /* Look for the constructor. Ignore failures. */
        if ((constructor = (*env)->GetMethodID(env, wrapperUserClass, "<init>", "(II[B[B[B[B)V")) != NULL) {

            uid = geteuid();
            pw = getpwuid(uid);
            ugid = pw->pw_gid;

            /* Create the arguments to the constructor as java objects */

            /* User byte array */
            jUser = (*env)->NewByteArray(env, strlen(pw->pw_name));
            (*env)->SetByteArrayRegion(env, jUser, 0, strlen(pw->pw_name), (jbyte*)pw->pw_name);

            /* Real Name byte array */
            jRealName = (*env)->NewByteArray(env, strlen(pw->pw_gecos));
            (*env)->SetByteArrayRegion(env, jRealName, 0, strlen(pw->pw_gecos), (jbyte*)pw->pw_gecos);

            /* Home byte array */
            jHome = (*env)->NewByteArray(env, strlen(pw->pw_dir));
            (*env)->SetByteArrayRegion(env, jHome, 0, strlen(pw->pw_dir), (jbyte*)pw->pw_dir);

            /* Shell byte array */
            jShell = (*env)->NewByteArray(env, strlen(pw->pw_shell));
            (*env)->SetByteArrayRegion(env, jShell, 0, strlen(pw->pw_shell), (jbyte*)pw->pw_shell);

            /* Now create the new wrapperUser using the constructor arguments collected above. */
            wrapperUser = (*env)->NewObject(env, wrapperUserClass, constructor, uid, ugid, jUser, jRealName, jHome, jShell);

            (*env)->DeleteLocalRef(env, jUser);
            (*env)->DeleteLocalRef(env, jRealName);
            (*env)->DeleteLocalRef(env, jHome);
            (*env)->DeleteLocalRef(env, jShell);

            /* If the caller requested the user's groups then look them up. */
            if (groups) {
                /* Set the user group. */
                if ((setGroup = (*env)->GetMethodID(env, wrapperUserClass, "setGroup", "(I[B)V")) != NULL) {
                    if ((aGroup = getgrgid(ugid)) != NULL) {
                        ggid = aGroup->gr_gid;

                        /* Group name byte array */
                        jGroupName = (*env)->NewByteArray(env, strlen(aGroup->gr_name));
                        (*env)->SetByteArrayRegion(env, jGroupName, 0, strlen(aGroup->gr_name), (jbyte*)aGroup->gr_name);

                        /* Add the group to the user. */
                        (*env)->CallVoidMethod(env, wrapperUser, setGroup, ggid, jGroupName);

                        (*env)->DeleteLocalRef(env, jGroupName);
                    }
                }

                /* Look for the addGroup method. Ignore failures. */
                if ((addGroup = (*env)->GetMethodID(env, wrapperUserClass, "addGroup", "(I[B)V")) != NULL) {
                    setgrent();
                    while ((aGroup = getgrent()) != NULL) {
                        /* Search the member list to decide whether or not the user is a member. */
                        member = 0;
                        i = 0;
                        while ((member == 0) && aGroup->gr_mem[i]) {
                            if (strcmp(aGroup->gr_mem[i], pw->pw_name) == 0) {
                               member = 1;
                            }
                            i++;
                        }

                        if (member) {
                            ggid = aGroup->gr_gid;
                            
                            /* Group name byte array */
                            jGroupName = (*env)->NewByteArray(env, strlen(aGroup->gr_name));
                            (*env)->SetByteArrayRegion(env, jGroupName, 0, strlen(aGroup->gr_name), (jbyte*)aGroup->gr_name);

                            /* Add the group to the user. */
                            (*env)->CallVoidMethod(env, wrapperUser, addGroup, ggid, jGroupName);

                            (*env)->DeleteLocalRef(env, jGroupName);
                        }
                    }
                    endgrent();
                }
            }
        }

        (*env)->DeleteLocalRef(env, wrapperUserClass);
    }

    return wrapperUser;
}


/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetInteractiveUser
 * Signature: (Z)Lorg/tanukisoftware/wrapper/WrapperUser;
 */
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetInteractiveUser(JNIEnv *env, jclass clazz, jboolean groups) {
    /* If the DISPLAY environment variable is set then assume that this user
     *  has access to an X display, in which case we will return the same thing
     *  as nativeGetUser. */
    if (getenv("DISPLAY")) {
        /* This is an interactive JVM since it has access to a display. */
        return Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetUser(env, clazz, groups);
    } else {
        /* There is no DISPLAY variable, so assume that this JVM is non-interactive. */
        return NULL;
    }
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeListServices
 * Signature: ()[Lorg/tanukisoftware/wrapper/WrapperWin32Service;
 */
JNIEXPORT jobjectArray JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeListServices(JNIEnv *env, jclass clazz) {
    /** Not supported on UNIX platforms. */
    return NULL;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeSendServiceControlCode
 * Signature: ([BI)Lorg/tanukisoftware/wrapper/WrapperWin32Service;
 */
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeSendServiceControlCode(JNIEnv *env, jclass clazz, jbyteArray serviceName, jint controlCode) {
    /** Not supported on UNIX platforms. */
    return NULL;
}

#endif
