#include "windows.h"
#include "types.h"

#define WM_SHELLNOTIFY WM_USER+5 
#define IDI_TRAY 0 
#define IDM_STARTSTOP 1001
#define IDM_GATEWAY 1004 
#define IDM_CONFIGURE 1005
#define IDM_SHOWLOG 1006
#define IDM_EXIT 1010

#define GATEWLEN 32

/* prototype of the WndProc - standard */
LRESULT CALLBACK WndProc( HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);

/* startup code: */
bool OnlyOneInstance(void);
void Initialise(void);

/* message-pump (i.e. WindowProc)-level functions to start and stop freenet node */
/* note - ASYNCHRONOUS - fire-and-forget messages - for true multithreaded interface */
void StartFserve(void);
void ExitFserve(void);

/* bit of a kludge - synchronous - used to load configurator dll */
bool StartConfig(void);
bool StartConfigOrig(void);


/* helper functions to synchronise multithreaded locking requests on a object by its handler */
void LockObject(HANDLE pObject);
void UnlockObject(HANDLE pObject);
/* helper functions to parse command line */
void GetFirstToken(LPSTR szCurrentPointer, LPSTR *pszEndPointer);
LPSTR GetNextToken(LPSTR szCurrentPointer, const LPSTR szEndPointer);
void GetToken(LPSTR szCurrentPointer);
LPSTR SkipSpace(LPSTR szString);
 