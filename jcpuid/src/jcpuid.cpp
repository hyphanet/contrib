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
		"cpuid"
		: "=a" (a),
		  "=b" (b),
		  "=c"(c),
		  "=d"(d)
		:"a"(iFunction)
	);
	return env->NewObject(clsResult,constructor,a,b,c,d);
}
