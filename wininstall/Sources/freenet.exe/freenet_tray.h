#include "windows.h"
#include "types.h"

#define WM_SHELLNOTIFY WM_USER+5 
#define IDI_TRAY 0 

#define GATEWLEN 32

/* prototype of the WndProc - standard */
LRESULT CALLBACK WndProc( HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);

/* startup code: */
bool OnlyOneInstance(void);
BOOL Initialise(void);  /* reads basic settings, also acts on command line */
void GetAppDirectory(char * szbuffer);

/* Reloading settings from flaunch.ini, etc.  */
void ReloadSettings(void);


/* message-pump (i.e. WindowProc)-level functions to start and stop freenet node */
/* note - ASYNCHRONOUS - fire-and-forget messages - for true multithreaded interface */
void StartFserve(void);
void ExitFserve(void);

/*	The first loads the configurator dll - synchronous but because of a bug which I cannot
	track down the main message pump still runs ...
	The second loads the original Java Configurator in a separate thread i.e. asynchronous
*/
void StartConfig(void);
void StartConfigOrig(void);
DWORD WINAPI WaitForJavaConfigurator(LPVOID hThread);
/* following function is a callback used to set focus to the Java Configurator window */
BOOL CALLBACK SetFocusByProcId(HWND hWnd, LPARAM lParam);

/* helper functions to synchronise multithreaded locking requests on a object by its handler */
void LockObject(HANDLE pObject);
void UnlockObject(HANDLE pObject);
/* helper functions to parse command line */
void GetFirstToken(LPSTR szCurrentPointer, LPSTR *pszEndPointer);
LPSTR GetNextToken(LPSTR szCurrentPointer, const LPSTR szEndPointer);
void GetToken(LPSTR szCurrentPointer);
LPSTR SkipSpace(LPSTR szString);


void CreateConfig(LPCSTR szConfigFile);
void MergeConfig(LPCSTR szConfigFile, LPCSTR szConfigDefaultsFile);
