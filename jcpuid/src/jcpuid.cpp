#include "jcpuid.h"

//Executes the indicated subfunction of the CPUID operation
JNIEXPORT jobject JNICALL Java_freenet_support_CPUInformation_CPUID_doCPUID
  (JNIEnv * env, jclass cls, jint iFunction)
{
	int a,b,c,d;
	jclass clsResult = env->FindClass ("freenet/support/CPUInformation/CPUID$CPUIDResult");
	jmethodID constructor = env->GetMethodID(clsResult,"<init>","(IIII)V" );
	
	asm 
	(
		"mov %eax, iFunction;"
		"cpuid;"
		"mov a, %eax;"
		"mov b, %ebx;"
		"mov c, %ecx;"
		"mov d, %edx;"
	);
	return env->NewObject(clsResult,constructor,a,b,c,d);
}
