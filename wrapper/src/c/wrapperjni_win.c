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

#ifndef WIN32
/* For some reason this is not defined sometimes when I build on MFVC 6.0 $%$%$@@!!
 * This causes a compiler error to let me know about the problem.  Anyone with any
 * ideas as to why this sometimes happens or how to fix it, please let me know. */
barf
#endif

#ifdef WIN32

#include <windows.h>
#include <time.h>
#include <tlhelp32.h>
#include "wrapperjni.h"

/* MS Visual Studio 8 went and deprecated the POXIX names for functions.
 *  Fixing them all would be a big headache for UNIX versions. */
#pragma warning(disable : 4996)

static DWORD wrapperProcessId = 0;

HANDLE controlEventQueueMutexHandle = NULL;

FARPROC OptionalProcess32First = NULL;
FARPROC OptionalProcess32Next = NULL;
FARPROC OptionalThread32First = NULL;
FARPROC OptionalThread32Next = NULL;
FARPROC OptionalCreateToolhelp32Snapshot = NULL;

int wrapperLockControlEventQueue() {
    if (!controlEventQueueMutexHandle) {
        /* Not initialized so fail quietly.  A message was shown on startup. */
        return -1;
    }

    /* Only wait for up to 30 seconds to make sure we don't get into a deadlock situation.
     *  This could happen if a signal is encountered while locked. */
    switch (WaitForSingleObject(controlEventQueueMutexHandle, 30000)) {
    case WAIT_ABANDONED:
        printf("WrapperJNI Error: Control Event mutex was abandoned.\n");
        fflush(NULL);
        return -1;
    case WAIT_FAILED:
        printf("WrapperJNI Error: Control Event mutex wait failed.\n");
        fflush(NULL);
        return -1;
    case WAIT_TIMEOUT:
        printf("WrapperJNI Error: Control Event mutex wait timed out.\n");
        fflush(NULL);
        return -1;
    default:
        /* Ok */
        break;
    }

    return 0;
}

int wrapperReleaseControlEventQueue() {
    if (!ReleaseMutex(controlEventQueueMutexHandle)) {
        printf( "WrapperJNI Error: Failed to release Control Event mutex. %s\n", getLastErrorText());
        fflush(NULL);
        return -1;
    }

    return 0;
}

/**
 * Handler to take care of the case where the user hits CTRL-C when the wrapper
 *  is being run as a console.  If this is not done, then the Java process
 *  would exit due to a CTRL_LOGOFF_EVENT when a user logs off even if the
 *  application is installed as a service.
 */
int wrapperConsoleHandler(int key) {
    int event;

    /* Call the control callback in the java code */
    switch(key) {
    case CTRL_C_EVENT:
        event = org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_C_EVENT;
        break;
    case CTRL_BREAK_EVENT:
        /* This is a request to do a thread dump. Let the JVM handle this. */
        return FALSE;
    case CTRL_CLOSE_EVENT:
        event = org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_CLOSE_EVENT;
        break;
    case CTRL_LOGOFF_EVENT:
        event = org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_LOGOFF_EVENT;
        break;
    case CTRL_SHUTDOWN_EVENT:
        event = org_tanukisoftware_wrapper_WrapperManager_WRAPPER_CTRL_SHUTDOWN_EVENT;
        break;
    default:
        event = key;
    }
    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Got Control Signal %d->%d\n", key, event);
        flushall();
    }

    wrapperJNIHandleSignal(event);

    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Handled signal\n");
        flushall();
    }

    return TRUE; /* We handled the event. */
}

/**
 * Looks up the name of the explorer.exe file in the registry.  It may change
 *  in a future version of windows, so this is the safe thing to do.
 */
char explorerExe[1024];
void
initExplorerExeName() {
    /* Location: "\\HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\Shell" */
    sprintf(explorerExe, "Explorer.exe");
}

void throwException(JNIEnv *env, const char *className, const char *message) {
    jclass exceptionClass;
    jmethodID constructor;
    jbyteArray jMessage;
    jobject exception;
    
    if (exceptionClass = (*env)->FindClass(env, className)) {
        /* Look for the constructor. Ignore failures. */
        if (constructor = (*env)->GetMethodID(env, exceptionClass, "<init>", "([B)V")) {
            jMessage = (*env)->NewByteArray(env, (jsize)strlen(message));
            /* The 1.3.1 jni.h file does not specify the message as const.  The cast is to
             *  avoid compiler warnings trying to pass a (const char *) as a (char *). */
            (*env)->SetByteArrayRegion(env, jMessage, 0, (jsize)strlen(message), (char *)message);
            
            exception = (*env)->NewObject(env, exceptionClass, constructor, jMessage);
            
            if ((*env)->Throw(env, exception)){
                printf("WrapperJNI Error: Unable to throw exception of class '%s' with message: %s",
                    className, message);
                flushall();
            }

            (*env)->DeleteLocalRef(env, jMessage);
            (*env)->DeleteLocalRef(env, exception);
        }

        (*env)->DeleteLocalRef(env, exceptionClass);
    } else {
        printf("WrapperJNI Error: Unable to load class, '%s' to report exception: %s",
            className, message);
        flushall();
    }
}

void throwServiceException(JNIEnv *env, const char *message) {
    throwException(env, "org/tanukisoftware/wrapper/WrapperServiceException", message);
}

/**
 * Generates a text representation of an SID.
 *
 * Code in this function is based on public domain example code on the Microsoft site:
 * http://msdn.microsoft.com/library/default.asp?url=/library/en-us/secauthz/security/converting_a_binary_sid_to_string_format_in_c__.asp
 * Use of this code has no affect on the license of this source file.
 */
BOOL GetTextualSid(
    PSID pSid,            /* binary Sid */
    LPTSTR TextualSid,    /* buffer for Textual representation of Sid */
    LPDWORD lpdwBufferLen /* required/provided TextualSid buffersize */
    )
{
    PSID_IDENTIFIER_AUTHORITY psia;
    DWORD dwSubAuthorities;
    DWORD dwSidRev=SID_REVISION;
    DWORD dwCounter;
    DWORD dwSidSize;

    /* Validate the binary SID. */

    if(!IsValidSid(pSid)) return FALSE;

    /* Get the identifier authority value from the SID. */

    psia = GetSidIdentifierAuthority(pSid);

    /* Get the number of subauthorities in the SID. */

    dwSubAuthorities = *GetSidSubAuthorityCount(pSid);

    /* Compute the buffer length. */
    /* S-SID_REVISION- + IdentifierAuthority- + subauthorities- + NULL */

    dwSidSize=(15 + 12 + (12 * dwSubAuthorities) + 1) * sizeof(TCHAR);

    /* Check input buffer length. */
    /* If too small, indicate the proper size and set last error. */

    if (*lpdwBufferLen < dwSidSize)
    {
        *lpdwBufferLen = dwSidSize;
        SetLastError(ERROR_INSUFFICIENT_BUFFER);
        return FALSE;
    }

    /* Add 'S' prefix and revision number to the string. */

    dwSidSize=wsprintf(TextualSid, TEXT("S-%lu-"), dwSidRev );

    /* Add SID identifier authority to the string. */

    if ( (psia->Value[0] != 0) || (psia->Value[1] != 0) )
    {
        dwSidSize+=wsprintf(TextualSid + lstrlen(TextualSid),
                    TEXT("0x%02hx%02hx%02hx%02hx%02hx%02hx"),
                    (USHORT)psia->Value[0],
                    (USHORT)psia->Value[1],
                    (USHORT)psia->Value[2],
                    (USHORT)psia->Value[3],
                    (USHORT)psia->Value[4],
                    (USHORT)psia->Value[5]);
    }
    else
    {
        dwSidSize+=wsprintf(TextualSid + lstrlen(TextualSid),
                    TEXT("%lu"),
                    (ULONG)(psia->Value[5]      )   +
                    (ULONG)(psia->Value[4] <<  8)   +
                    (ULONG)(psia->Value[3] << 16)   +
                    (ULONG)(psia->Value[2] << 24)   );
    }

    /* Add SID subauthorities to the string. */
    for (dwCounter=0 ; dwCounter < dwSubAuthorities ; dwCounter++)
    {
        dwSidSize+=wsprintf(TextualSid + dwSidSize, TEXT("-%lu"),
                    *GetSidSubAuthority(pSid, dwCounter) );
    }

    return TRUE;
}

/**
 * Converts a FILETIME to a time_t structure.
 */
time_t
fileTimeToTimeT(FILETIME *filetime) {
    SYSTEMTIME utc;
    SYSTEMTIME local;
    TIME_ZONE_INFORMATION timeZoneInfo;
    struct tm tm;

    FileTimeToSystemTime(filetime, &utc);
    GetTimeZoneInformation(&timeZoneInfo);
    SystemTimeToTzSpecificLocalTime(&timeZoneInfo, &utc, &local);

    tm.tm_sec = local.wSecond;
    tm.tm_min = local.wMinute;
    tm.tm_hour = local.wHour;
    tm.tm_mday = local.wDay;
    tm.tm_mon = local.wMonth - 1;
    tm.tm_year = local.wYear - 1900;
    tm.tm_wday = local.wDayOfWeek;
    tm.tm_yday = -1;
    tm.tm_isdst = -1;
    return mktime(&tm);
}

/**
 * Looks for the login time given a user SID.  The login time is found by looking
 *  up the SID in the registry.
 */
time_t
getUserLoginTime(TCHAR *sidText) {
    HKEY     userKey;
    int      i;
    TCHAR    userKeyName[MAX_PATH];
    DWORD    userKeyNameSize;
    FILETIME lastTime;
    time_t   loginTime;

    loginTime = 0;

    /* Open a key to the HKRY_USERS registry. */
    if (RegOpenKey(HKEY_USERS, NULL, &userKey) != ERROR_SUCCESS) {
        printf("WrapperJNI Error: Error opening registry for HKEY_USERS: %s\n", getLastErrorText());
        flushall();
        return loginTime;
    }

    /* Loop over the users */
    i = 0;
    userKeyNameSize = sizeof(userKeyName);
    while (RegEnumKeyEx(userKey, i, userKeyName, &userKeyNameSize, NULL, NULL, NULL, &lastTime) == ERROR_SUCCESS) {
        if (stricmp(sidText, userKeyName) == 0) {
            /* We found the SID! */

            /* Convert the FILETIME to UNIX time. */
            loginTime = fileTimeToTimeT(&lastTime);

            break;
        }

        userKeyNameSize = sizeof(userKeyName);
        i++;
    }

    /* Always close the userKey. */
    RegCloseKey(userKey);

    return loginTime;
}

void
setUserGroups(JNIEnv *env, jclass wrapperUserClass, jobject wrapperUser, HANDLE hProcessToken) {
    jmethodID addGroup;

    TOKEN_GROUPS *tokenGroups;
    DWORD tokenGroupsSize;
    DWORD i;

    DWORD sidTextSize;
    TCHAR *sidText;
    TCHAR *groupName;
    DWORD groupNameSize;
    TCHAR *domainName;
    DWORD domainNameSize;
    SID_NAME_USE sidType;

    jbyteArray jSID;
    jbyteArray jGroupName;
    jbyteArray jDomainName;

    /* Look for the method used to add groups to the user. */
    if (addGroup = (*env)->GetMethodID(env, wrapperUserClass, "addGroup", "([B[B[B)V")) {
        /* Get the TokenGroups info from the token. */
        GetTokenInformation(hProcessToken, TokenGroups, NULL, 0, &tokenGroupsSize);
        tokenGroups = (TOKEN_GROUPS *)malloc(tokenGroupsSize);
        if (GetTokenInformation(hProcessToken, TokenGroups, tokenGroups, tokenGroupsSize, &tokenGroupsSize)) {
            /* Loop over each of the groups and add each one to the user. */
            for (i = 0; i < tokenGroups->GroupCount; i++) {
                /* Get the text representation of the sid. */
                sidTextSize = 0;
                GetTextualSid(tokenGroups->Groups[i].Sid, NULL, &sidTextSize);
                sidText = (TCHAR*)malloc(sizeof(TCHAR) * sidTextSize);
                GetTextualSid(tokenGroups->Groups[i].Sid, sidText, &sidTextSize);
                
                /* We now have an SID, use it to lookup the account. */
                groupNameSize = 0;
                domainNameSize = 0;
                LookupAccountSid(NULL, tokenGroups->Groups[i].Sid, NULL, &groupNameSize, NULL, &domainNameSize, &sidType);
                groupName = (TCHAR*)malloc(sizeof(TCHAR) * groupNameSize);
                domainName = (TCHAR*)malloc(sizeof(TCHAR) * domainNameSize);
                if (LookupAccountSid(NULL, tokenGroups->Groups[i].Sid, groupName, &groupNameSize, domainName, &domainNameSize, &sidType)) {
                    /*printf("WrapperJNI Debug: SID=%s, group=%s/%s\n", sidText, domainName, groupName);*/

                    /* Create the arguments to the constructor as java objects */

                    /* SID byte array */
                    jSID = (*env)->NewByteArray(env, (jsize)strlen(sidText));
                    (*env)->SetByteArrayRegion(env, jSID, 0, (jsize)strlen(sidText), sidText);

                    /* GroupName byte array */
                    jGroupName = (*env)->NewByteArray(env, (jsize)strlen(groupName));
                    (*env)->SetByteArrayRegion(env, jGroupName, 0, (jsize)strlen(groupName), groupName);

                    /* DomainName byte array */
                    jDomainName = (*env)->NewByteArray(env, (jsize)strlen(domainName));
                    (*env)->SetByteArrayRegion(env, jDomainName, 0, (jsize)strlen(domainName), domainName);

                    /* Now actually add the group to the user. */
                    (*env)->CallVoidMethod(env, wrapperUser, addGroup, jSID, jGroupName, jDomainName);

                    (*env)->DeleteLocalRef(env, jSID);
                    (*env)->DeleteLocalRef(env, jGroupName);
                    (*env)->DeleteLocalRef(env, jDomainName);
                } else {
                    /* This is normal as some accounts do not seem to be mappable. */
                    /*
                    printf("WrapperJNI Debug: Unable to locate account for Sid, %s: %s\n", sidText, getLastErrorText());
                    flushall();
                    */
                }
                free(sidText);
                free(groupName);
                free(domainName);
            }
        } else {
            printf("WrapperJNI Error: Unable to get token information: %s\n", getLastErrorText());
            flushall();
        }

        free(tokenGroups);
    }
}

/**
 * Creates and returns a WrapperUser instance to represent the user who owns
 *  the specified process Id.
 */
jobject
createWrapperUserForProcess(JNIEnv *env, DWORD processId, jboolean groups) {
    HANDLE hProcess;
    HANDLE hProcessToken;
    TOKEN_USER *tokenUser;
    DWORD tokenUserSize;

    DWORD sidTextSize;
    TCHAR *sidText;
    TCHAR *userName;
    DWORD userNameSize;
    TCHAR *domainName;
    DWORD domainNameSize;
    SID_NAME_USE sidType;
    time_t loginTime;

    jclass wrapperUserClass;
    jmethodID constructor;
    jbyteArray jSID;
    jbyteArray jUserName;
    jbyteArray jDomainName;
    jobject wrapperUser = NULL;

    if (hProcess = OpenProcess(PROCESS_ALL_ACCESS, FALSE, processId)) {
        if (OpenProcessToken(hProcess, TOKEN_ALL_ACCESS, &hProcessToken)) {
            GetTokenInformation(hProcessToken, TokenUser, NULL, 0, &tokenUserSize);
            tokenUser = (TOKEN_USER *)malloc(tokenUserSize);
            if (GetTokenInformation(hProcessToken, TokenUser, tokenUser, tokenUserSize, &tokenUserSize)) {

                /* Get the text representation of the sid. */
                sidTextSize = 0;
                GetTextualSid(tokenUser->User.Sid, NULL, &sidTextSize);
                sidText = (TCHAR*)malloc(sizeof(TCHAR) * sidTextSize);
                GetTextualSid(tokenUser->User.Sid, sidText, &sidTextSize);

                /* We now have an SID, use it to lookup the account. */
                userNameSize = 0;
                domainNameSize = 0;
                LookupAccountSid(NULL, tokenUser->User.Sid, NULL, &userNameSize, NULL, &domainNameSize, &sidType);
                userName = (TCHAR*)malloc(sizeof(TCHAR) * userNameSize);
                domainName = (TCHAR*)malloc(sizeof(TCHAR) * domainNameSize);
                if (LookupAccountSid(NULL, tokenUser->User.Sid, userName, &userNameSize, domainName, &domainNameSize, &sidType)) {

                    /* Get the time that this user logged in. */
                    loginTime = getUserLoginTime(sidText);

                    /* Look for the WrapperUser class. Ignore failures as JNI throws an exception. */
                    if (wrapperUserClass = (*env)->FindClass(env, "org/tanukisoftware/wrapper/WrapperWin32User")) {

                        /* Look for the constructor. Ignore failures. */
                        if (constructor = (*env)->GetMethodID(env, wrapperUserClass, "<init>", "([B[B[BI)V")) {

                            /* Create the arguments to the constructor as java objects */

                            /* SID byte array */
                            jSID = (*env)->NewByteArray(env, (jsize)strlen(sidText));
                            (*env)->SetByteArrayRegion(env, jSID, 0, (jsize)strlen(sidText), sidText);

                            /* UserName byte array */
                            jUserName = (*env)->NewByteArray(env, (jsize)strlen(userName));
                            (*env)->SetByteArrayRegion(env, jUserName, 0, (jsize)strlen(userName), userName);

                            /* DomainName byte array */
                            jDomainName = (*env)->NewByteArray(env, (jsize)strlen(domainName));
                            (*env)->SetByteArrayRegion(env, jDomainName, 0, (jsize)strlen(domainName), domainName);

                            /* Now create the new wrapperUser using the constructor arguments collected above. */
                            wrapperUser = (*env)->NewObject(env, wrapperUserClass, constructor, jSID, jUserName, jDomainName, loginTime);
                            
                            /* If the caller requested the user's groups then look them up. */
                            if (groups) {
                                setUserGroups(env, wrapperUserClass, wrapperUser, hProcessToken);
                            }

                            (*env)->DeleteLocalRef(env, jSID);
                            (*env)->DeleteLocalRef(env, jUserName);
                            (*env)->DeleteLocalRef(env, jDomainName);
                        }

                        (*env)->DeleteLocalRef(env, wrapperUserClass);
                    }
                } else {
                    /* This is normal as some accounts do not seem to be mappable. */
                    /*
                    printf("WrapperJNI Debug: Unable to locate account for Sid, %s: %s\n", sidText, getLastErrorText());
                    flushall();
                    */
                }
                free(sidText);
                free(userName);
                free(domainName);
            } else {
                printf("WrapperJNI Error: Unable to get token information: %s\n", getLastErrorText());
                flushall();
            }
            free(tokenUser);

            CloseHandle(hProcessToken);
        } else {
            printf("WrapperJNI Error: Unable to open process token: %s\n", getLastErrorText());
            flushall();
        }

        CloseHandle(hProcess);
    } else {
        printf("WrapperJNI Error: Unable to open process: %s\n", getLastErrorText());
        flushall();
    }

    return wrapperUser;
}

void loadDLLProcs() {
    HMODULE kernel32Mod;

    if ((kernel32Mod = GetModuleHandle("KERNEL32.DLL")) == NULL) {
        printf("WrapperJNI Error: Unable to load KERNEL32.DLL: %s\n", getLastErrorText());
        flushall();
        return;
    }
    if ((OptionalProcess32First = GetProcAddress(kernel32Mod, "Process32First")) == NULL) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: The Process32First function is not available on this version of Windows.\n");
            flushall();
        }
    }
    if ((OptionalProcess32Next = GetProcAddress(kernel32Mod, "Process32Next")) == NULL) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: The Process32Next function is not available on this version of Windows.\n");
            flushall();
        }
    }
    if ((OptionalThread32First = GetProcAddress(kernel32Mod, "Thread32First")) == NULL) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: The Thread32First function is not available on this version of Windows.\n");
            flushall();
        }
    }
    if ((OptionalThread32Next = GetProcAddress(kernel32Mod, "Thread32Next")) == NULL) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: The Thread32Next function is not available on this version of Windows.\n");
            flushall();
        }
    }
    if ((OptionalCreateToolhelp32Snapshot = GetProcAddress(kernel32Mod, "CreateToolhelp32Snapshot")) == NULL) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: The CreateToolhelp32Snapshot function is not available on this version of Windows.\n");
            flushall();
        }
    }
}


/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeInit
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeInit(JNIEnv *env, jclass clazz, jboolean debugging) {
    char szPath[512];
    OSVERSIONINFO osVer;

    wrapperJNIDebugging = debugging;

    if (wrapperJNIDebugging) {
        /* This is useful for making sure that the JNI call is working. */
        printf("WrapperJNI Debug: Initializing WrapperManager native library.\n");
        flushall();

        if (GetModuleFileName(NULL, szPath, 512) == 0){
            printf("WrapperJNI Debug: Unable to retrieve the Java process file name.\n");
            flushall();
        } else {
            printf("WrapperJNI Debug: Java Executable: %s\n", szPath);
            flushall();
        }
    }

    osVer.dwOSVersionInfoSize = sizeof(osVer);
    if (GetVersionEx(&osVer)) {
        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: Windows version: %ld.%ld.%ld\n",
                osVer.dwMajorVersion, osVer.dwMinorVersion, osVer.dwBuildNumber);
            flushall();
        }
    } else {
        printf("WrapperJNI Error: Unable to retrieve the Windows version information.\n");
        flushall();
    }

    loadDLLProcs();

    if (!(controlEventQueueMutexHandle = CreateMutex(NULL, FALSE, NULL))) {
        printf("WrapperJNI Error: Failed to create control event queue mutex. Signals will be ignored. %s\n", getLastErrorText());
        flushall();
        controlEventQueueMutexHandle = NULL;
    }

    /* Make sure that the handling of CTRL-C signals is enabled for this process. */
    SetConsoleCtrlHandler(NULL, FALSE);

    /* Initialize the CTRL-C handler */
    SetConsoleCtrlHandler((PHANDLER_ROUTINE)wrapperConsoleHandler, TRUE);

    /* Store the current process Id */
    wrapperProcessId = GetCurrentProcessId();

    /* Initialize the explorer.exe name. */
    initExplorerExeName();
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetJavaPID
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetJavaPID(JNIEnv *env, jclass clazz) {
    return GetCurrentProcessId();
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeRequestThreadDump
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeRequestThreadDump(JNIEnv *env, jclass clazz) {
    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Sending BREAK event to process group %ld.\n", wrapperProcessId);
        flushall();
    }
    if ( GenerateConsoleCtrlEvent( CTRL_BREAK_EVENT, wrapperProcessId ) == 0 ) {
        printf("WrapperJNI Error: Unable to send BREAK event to JVM process: %s\n",
            getLastErrorText());
        flushall();
    }
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeSetConsoleTitle
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeSetConsoleTitle(JNIEnv *env, jclass clazz, jbyteArray jTitleBytes) {
    int len;
    char *title;
    jbyte *titleBytes;
    
    /* The array we get from JNI is not null terminated so build our own string. */
    len = (*env)->GetArrayLength(env, jTitleBytes);
    title = malloc(len + 1);
    titleBytes = (*env)->GetByteArrayElements(env, jTitleBytes, 0);
    memcpy(title, titleBytes, len);
    title[len] = 0;
    (*env)->ReleaseByteArrayElements(env, jTitleBytes, titleBytes, JNI_ABORT);

    if (wrapperJNIDebugging) {
        printf("WrapperJNI Debug: Setting the console title to: %s\n", title);
        flushall();
    }

    SetConsoleTitle(title);
    
    free(title);
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetUser
 * Signature: (Z)Lorg/tanukisoftware/wrapper/WrapperUser;
 */
/*#define UVERBOSE*/
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetUser(JNIEnv *env, jclass clazz, jboolean groups) {
    DWORD processId;

#ifdef UVERBOSE
    printf("WrapperJNI Debug: nativeGetUser()\n");
    flushall();
#endif

    /* Get the current processId. */
    processId = GetCurrentProcessId();

    return createWrapperUserForProcess(env, processId, groups);
}


/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeGetInteractiveUser
 * Signature: (Z)Lorg/tanukisoftware/wrapper/WrapperUser;
 */
/*#define IUVERBOSE*/
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeGetInteractiveUser(JNIEnv *env, jclass clazz, jboolean groups) {
    HANDLE snapshot;
    PROCESSENTRY32 processEntry;
    THREADENTRY32 threadEntry;
    BOOL foundThread;
    HDESK desktop;

    jobject wrapperUser = NULL;

#ifdef IUVERBOSE
    printf("WrapperJNI Debug: nativeGetInteractiveUser()\n");
    flushall();
#endif

    /* This function will only work if all required optional functions existed. */
    if ((OptionalProcess32First == NULL) || (OptionalProcess32Next == NULL) ||
        (OptionalThread32First == NULL) || (OptionalThread32Next == NULL) ||
        (OptionalCreateToolhelp32Snapshot == NULL)) {

        if (wrapperJNIDebugging) {
            printf("WrapperJNI Debug: getInteractiveUser not supported on this platform.\n");
            flushall();
        }
        return NULL;
    }

    /* In order to be able to return the interactive user, we first need to locate the
     *  logged on user whose desktop we are able to open.  On XP systems, there will be
     *  more than one user with a desktop, but only the first one to log on will allow
     *  us to open its desktop.  On all NT systems, there will be additional logged on
     *  users if there are other services running. */
    if ((snapshot = (HANDLE)OptionalCreateToolhelp32Snapshot(TH32CS_SNAPPROCESS + TH32CS_SNAPTHREAD, 0)) >= 0) {
        processEntry.dwSize = sizeof(processEntry);
        if (OptionalProcess32First(snapshot, &processEntry)) {
            do {
                /* We are only interrested in the Explorer processes. */
                if (stricmp(explorerExe, processEntry.szExeFile) == 0) {
#ifdef IUVERBOSE
                    printf("WrapperJNI Debug: Process size=%ld, cnt=%ld, id=%ld, parentId=%ld, moduleId=%ld, threads=%ld, exe=%s\n",
                        processEntry.dwSize, processEntry.cntUsage, processEntry.th32ProcessID,
                        processEntry.th32ParentProcessID, processEntry.th32ModuleID, processEntry.cntThreads,
                        processEntry.szExeFile);
                    flushall();
#endif

                    /* Now look for a thread which is owned by the explorer process. */
                    threadEntry.dwSize = sizeof(threadEntry);
                    if (OptionalThread32First(snapshot, &threadEntry)) {
                        foundThread = FALSE;
                        do {
                            /* We are only interrested in threads that belong to the current Explorer process. */
                            if (threadEntry.th32OwnerProcessID == processEntry.th32ProcessID) {
#ifdef IUVERBOSE
                                printf("WrapperJNI Debug:   Thread id=%ld\n", threadEntry.th32ThreadID);
                                flushall();
#endif

                                /* We have a thread, now see if we can gain access to its desktop */
                                if (desktop = GetThreadDesktop(threadEntry.th32ThreadID)) {
                                    /* We got the desktop!   We now know that this is the thread and thus
                                     *  process that we have been looking for.   Unfortunately it does not
                                     *  appear that we can get the Sid of the account directly from this
                                     *  desktop.  I tried using GetUserObjectInformation, but the Sid
                                     *  returned does not seem to map to a valid account. */

                                    wrapperUser = createWrapperUserForProcess(env, processEntry.th32ProcessID, groups);
                                } else {
#ifdef IUVERBOSE
                                    printf("WrapperJNI Debug: GetThreadDesktop failed: %s\n", getLastErrorText());
                                    flushall();
#endif
                                }

                                /* We only need the first thread, so break */
                                foundThread = TRUE;
                                break;
                            }
                        } while (OptionalThread32Next(snapshot, &threadEntry));

                        if (!foundThread && (GetLastError() != ERROR_NO_MORE_FILES)) {
#ifdef IUVERBOSE
                            printf("WrapperJNI Debug: Unable to get next thread entry: %s\n", getLastErrorText());
                            flushall();
#endif
                        }
                    } else if (GetLastError() != ERROR_NO_MORE_FILES) {
                        printf("WrapperJNI Debug: Unable to get first thread entry: %s\n", getLastErrorText());
                        flushall();
                    }
                }
            } while (OptionalProcess32Next(snapshot, &processEntry));

#ifdef IUVERBOSE
            if (GetLastError() != ERROR_NO_MORE_FILES) {
                printf("WrapperJNI Debug: Unable to get next process entry: %s\n", getLastErrorText());
                flushall();
            }
#endif
        } else if (GetLastError() != ERROR_NO_MORE_FILES) {
            printf("WrapperJNI Error: Unable to get first process entry: %s\n", getLastErrorText());
            flushall();
        }

        CloseHandle(snapshot);
    } else {
        printf("WrapperJNI Error: Toolhelp snapshot failed: %s\n", getLastErrorText());
        flushall();
    }

    return wrapperUser;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeListServices
 * Signature: ()[Lorg/tanukisoftware/wrapper/WrapperWin32Service;
 */
JNIEXPORT jobjectArray JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeListServices(JNIEnv *env, jclass clazz) {
    char buffer[512];
    SC_HANDLE hSCManager;
    DWORD size, sizeNeeded, servicesReturned, resumeHandle;
    DWORD err;
    ENUM_SERVICE_STATUS *services = NULL;
    BOOL threwError = FALSE;
    DWORD i;
    
    jobjectArray serviceArray = NULL;
    jclass serviceClass;
    jmethodID constructor;
    jbyteArray jName;
    jbyteArray jDisplayName;
    DWORD state;
    DWORD exitCode;
    jobject service;

    hSCManager = OpenSCManager(NULL, NULL, SC_MANAGER_ENUMERATE_SERVICE);
    if (hSCManager) {
        /* Before we can get the list of services, we need to know how much memory it will take. */
        resumeHandle = 0;
        if (!EnumServicesStatus(hSCManager, SERVICE_WIN32, SERVICE_STATE_ALL, NULL, 0, &sizeNeeded, &servicesReturned, &resumeHandle)) {
            err = GetLastError();
            if ((err == ERROR_MORE_DATA) || (err == ERROR_INSUFFICIENT_BUFFER)) {
                /* Allocate the needed memory and call again. */
                size = sizeNeeded;
                services = malloc(size);
                if (!EnumServicesStatus(hSCManager, SERVICE_WIN32, SERVICE_STATE_ALL, services, size, &sizeNeeded, &servicesReturned, &resumeHandle)) {
                    /* Failed to get the services. */
                    sprintf(buffer, "Unable to enumerate the system services: %s",
                        getLastErrorText());
                    throwServiceException(env, buffer);
                    threwError = TRUE;
                } else {
                    /* Success. */
                }
            } else {
                sprintf(buffer, "Unable to enumerate the system services: %s",
                    getLastErrorText());
                throwServiceException(env, buffer);
                threwError = TRUE;
            }
        } else {
            /* Success which means that no services were found. */
        }
        
        if (!threwError) {
            if (serviceClass = (*env)->FindClass(env, "org/tanukisoftware/wrapper/WrapperWin32Service")) {
                /* Look for the constructor. Ignore failures. */
                if (constructor = (*env)->GetMethodID(env, serviceClass, "<init>", "([B[BII)V")) {
                    serviceArray = (*env)->NewObjectArray(env, servicesReturned, serviceClass, NULL);
                    
                    for (i = 0; i < servicesReturned; i++ ) {
                        jName = (*env)->NewByteArray(env, (jsize)strlen(services[i].lpServiceName));
                        (*env)->SetByteArrayRegion(env, jName, 0, (jsize)strlen(services[i].lpServiceName), services[i].lpServiceName);
                        
                        jDisplayName = (*env)->NewByteArray(env, (jsize)strlen(services[i].lpDisplayName));
                        (*env)->SetByteArrayRegion(env, jDisplayName, 0, (jsize)strlen(services[i].lpDisplayName), services[i].lpDisplayName);
                        
                        state = services[i].ServiceStatus.dwCurrentState;
                        
                        exitCode = services[i].ServiceStatus.dwWin32ExitCode;
                        if (exitCode == ERROR_SERVICE_SPECIFIC_ERROR) {
                            exitCode = services[i].ServiceStatus.dwServiceSpecificExitCode;
                        }
                        
                        service = (*env)->NewObject(env, serviceClass, constructor, jName, jDisplayName, state, exitCode);
                        (*env)->SetObjectArrayElement(env, serviceArray, i, service);

                        (*env)->DeleteLocalRef(env, jDisplayName);
                        (*env)->DeleteLocalRef(env, jName);
                        (*env)->DeleteLocalRef(env, service);
                    }
                }

                (*env)->DeleteLocalRef(env, serviceClass);
            } else {
                /* Unable to load the service class. */
                sprintf(buffer, "Unable to locate class org.tanukisoftware.wrapper.WrapperWin32Service");
                throwServiceException(env, buffer );
            }
        }

        if (services != NULL) {
            free(services);
        }

        /* Close the handle to the service control manager database */
        CloseServiceHandle(hSCManager);
    } else {
        /* Unable to open the service manager. */
        sprintf(buffer, "Unable to open the Windows service control manager database: %s",
            getLastErrorText());
        throwServiceException(env, buffer );
    }
    
    return serviceArray;
}

/*
 * Class:     org_tanukisoftware_wrapper_WrapperManager
 * Method:    nativeSendServiceControlCode
 * Signature: ([BI)Lorg/tanukisoftware/wrapper/WrapperWin32Service;
 */
JNIEXPORT jobject JNICALL
Java_org_tanukisoftware_wrapper_WrapperManager_nativeSendServiceControlCode(JNIEnv *env, jclass clazz, jbyteArray serviceName, jint controlCode) {
    char buffer[512];
    int len;
    jbyte *jServiceNameBytes;
    char *serviceNameBytes;
    SC_HANDLE hSCManager;
    SC_HANDLE hService;
    int serviceAccess;
    DWORD wControlCode;
    BOOL threwError = FALSE;
    SERVICE_STATUS serviceStatus;
    jclass serviceClass;
    jmethodID constructor;
    jobject service = NULL;
    char displayBuffer[512];
    DWORD displayBufferSize = 512;
    jbyteArray jDisplayName;
    DWORD state;
    DWORD exitCode;
    
    hSCManager = OpenSCManager(NULL, NULL, GENERIC_READ);
    if (hSCManager) {
        /* Decide on the access needed when opening the service. */
        if (controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_START ) {
            serviceAccess = SERVICE_START | SERVICE_INTERROGATE | SERVICE_QUERY_STATUS;
            wControlCode = SERVICE_CONTROL_INTERROGATE;
        } else if (controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_STOP) {
            serviceAccess = SERVICE_STOP | SERVICE_QUERY_STATUS;
            wControlCode = SERVICE_CONTROL_STOP;
        } else if (controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_INTERROGATE ) {
            serviceAccess = SERVICE_INTERROGATE | SERVICE_QUERY_STATUS;
            wControlCode = SERVICE_CONTROL_INTERROGATE;
        } else if ( controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_PAUSE ) {
            serviceAccess = SERVICE_PAUSE_CONTINUE | SERVICE_QUERY_STATUS;
            wControlCode = SERVICE_CONTROL_PAUSE;
        } else if ( controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_CONTINUE ) {
            serviceAccess = SERVICE_PAUSE_CONTINUE | SERVICE_QUERY_STATUS;
            wControlCode = SERVICE_CONTROL_CONTINUE;
        } else if ( (controlCode >= 128) || (controlCode <= 255) ) {
            serviceAccess = SERVICE_USER_DEFINED_CONTROL | SERVICE_QUERY_STATUS;
            wControlCode = controlCode;
        } else {
            /* Illegal control code. */
            sprintf(buffer, "Illegal Control code specified: %d", controlCode);
            throwServiceException(env, buffer );
            threwError = TRUE;
        }
        
        if (!threwError) {
            /* The array we get from JNI is not null terminated so build our own string. */
            len = (*env)->GetArrayLength(env, serviceName);
            serviceNameBytes = malloc(len + 1);
            jServiceNameBytes = (*env)->GetByteArrayElements(env, serviceName, 0);
            memcpy(serviceNameBytes, jServiceNameBytes, len);
            serviceNameBytes[len] = 0;
            (*env)->ReleaseByteArrayElements(env, serviceName, jServiceNameBytes, JNI_ABORT);
            
            hService = OpenService(hSCManager, serviceNameBytes, serviceAccess);
            if (hService) {
                /* If we are trying to start a service, it needs to be handled specially. */
                if (controlCode == org_tanukisoftware_wrapper_WrapperManager_SERVICE_CONTROL_CODE_START) {
                    if (StartService(hService, 0, NULL)) {
                        /* Started the service. Continue on and interrogate the service. */
                    } else {
                        /* Failed. */
                        sprintf(buffer, "Unable to start service \"%s\": %s", serviceNameBytes,
                            getLastErrorText());
                        throwServiceException(env, buffer );
                        threwError = TRUE;
                    }
                }
                
                if (!threwError) {
                    if (ControlService(hService, wControlCode, &serviceStatus)) {
                        /* Success.  fall through. */
                    } else {
                        /* Failed to send the control code.   See if the service is running. */
                        if (GetLastError() == ERROR_SERVICE_NOT_ACTIVE) {
                            /* Service is not running, so get its status information. */
                            if (QueryServiceStatus(hService, &serviceStatus)) {
                                /* We got the status.  fall through. */
                            } else {
                                /* Actual failure. */
                                sprintf(buffer, "Unable to query status of service \"%s\": %s",
                                    serviceNameBytes, getLastErrorText());
                                throwServiceException(env, buffer );
                                threwError = TRUE;
                            }
                        } else {
                            /* Actual failure. */
                            sprintf(buffer, "Unable to send control code to service \"%s\": %s",
                                serviceNameBytes, getLastErrorText());
                            throwServiceException(env, buffer );
                            threwError = TRUE;
                        }
                    }
                    
                    if (!threwError) {
                        /* Build up a service object to return. */
                        if (serviceClass = (*env)->FindClass(env, "org/tanukisoftware/wrapper/WrapperWin32Service")) {
                            /* Look for the constructor. Ignore failures. */
                            if (constructor = (*env)->GetMethodID(env, serviceClass, "<init>", "([B[BII)V")) {
                                
                                if (!GetServiceDisplayName(hSCManager, serviceNameBytes, displayBuffer, &displayBufferSize)) {
                                    strcpy(displayBuffer, serviceNameBytes);
                                }
                                jDisplayName = (*env)->NewByteArray(env, (jsize)strlen(displayBuffer));
                                (*env)->SetByteArrayRegion(env, jDisplayName, 0, (jsize)strlen(displayBuffer), displayBuffer);
                                
                                state = serviceStatus.dwCurrentState;
                                
                                exitCode = serviceStatus.dwWin32ExitCode;
                                if (exitCode == ERROR_SERVICE_SPECIFIC_ERROR) {
                                    exitCode = serviceStatus.dwServiceSpecificExitCode;
                                }
                                
                                service = (*env)->NewObject(env, serviceClass, constructor, serviceName, jDisplayName, state, exitCode);

                                (*env)->DeleteLocalRef(env, jDisplayName);
                            }

                            (*env)->DeleteLocalRef(env, serviceClass);
                        } else {
                            /* Unable to load the service class. */
                            sprintf(buffer, "Unable to locate class org.tanukisoftware.wrapper.WrapperWin32Service");
                            throwServiceException(env, buffer );
                        }
                    }
                }
                
                CloseServiceHandle(hService);
            } else {
                /* Unable to open service. */
                sprintf(buffer, "Unable to open the service '%s': %s",
                    serviceNameBytes, getLastErrorText());
                throwServiceException(env, buffer );
                threwError = TRUE;
            }
            
            free(serviceNameBytes);
        }

        /* Close the handle to the service control manager database */
        CloseServiceHandle(hSCManager);
    } else {
        /* Unable to open the service manager. */
        sprintf(buffer, "Unable to open the Windows service control manager database: %s",
            getLastErrorText());
        throwServiceException(env, buffer );
        threwError = TRUE;
    }
    
    return service;
}

#endif
