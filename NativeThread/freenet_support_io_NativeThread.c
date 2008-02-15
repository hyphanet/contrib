#include<sys/resource.h>
#include<sys/time.h>
#include<stdio.h>
#include <errno.h>

#include"freenet_support_io_NativeThread.h"

JNIEXPORT jint JNICALL Java_sandbox_NativeThread_getLinuxPriority
  (JNIEnv * env, jobject jobj) {
	return getpriority(PRIO_PROCESS, 0);
}

JNIEXPORT jboolean JNICALL Java_sandbox_NativeThread_setLinuxPriority
(JNIEnv * env, jobject jobj, jint prio) {
	int ret;
	errno = 0;
	ret = setpriority(PRIO_PROCESS, 0, prio);
	if (ret == -1 && errno != 0) {
		printf("Setting the thread priority failed!! %d %d\n",ret,errno);
		return JNI_FALSE;
	}
	return JNI_TRUE;
}
