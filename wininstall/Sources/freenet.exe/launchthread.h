#ifndef FREENET_TRAY_LAUNCHTHREAD_H_INCLUDED
#define FREENET_TRAY_LAUNCHTHREAD_H_INCLUDED

/*	thread to take care of actually loading and running the freenet node, looking after it once
	it's started, and handle requests to stop and restart it */
DWORD WINAPI _stdcall MonitorThread(LPVOID null);

/****************************************************************************************/
/* Functions used by the above                                                          */
/* note - ONLY TO BE CALLED FROM ASYNCHRONOUS THREAD LEVEL - i.e. by the Monitor Thread */
/* as they are VERY blocking ... calling them from message-pump-level code will result  */
/* in perceived loss of response from the systray icon --- would be a bad thing         */
/****************************************************************************************/
void MonitorThreadRunFserve(void);
void MonitorThreadKillFserve(void);
void ClearTempDirectories(void);
/* following function is a callback used to kill off (cleanly!) the freenet node */
BOOL CALLBACK KillWindowByProcessId(HWND hWnd, LPARAM lParam);

#endif /*FREENET_TRAY_LAUNCHTHREAD_H_INCLUDED*/