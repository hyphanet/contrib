
/*

*/

#include "compat.h"

int LaunchThread(FP f, void *parms)
{
#ifdef WINDOWS
  return _beginthread(f, 0, parms) == -1 ? -1 : 0;

#else
  pthread_t pth;
  pthread_attr_t attr;
  
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  return pthread_create(&pth, &attr, (void *)f, parms);
#endif
}

void QuitThread(char *s)
{
#ifdef WINDOWS
  _endthread();

#else
  pthread_exit(s);
#endif
}

int _fcpSleep(unsigned int seconds, unsigned int nanoseconds)
{
#ifdef WINDOWS
  Sleep(seconds * 1000);
	return 0;

#else
  struct timespec delay;
  struct timespec remain;

  delay.tv_sec = seconds;
  delay.tv_nsec = nanoseconds;

  return nanosleep( &delay, &remain );
#endif
}
