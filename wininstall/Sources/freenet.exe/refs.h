#include "windows.h"
extern HINSTANCE hInstance;
extern HWND hWnd;

void ImportRefs(void);
void ExportRefs(void);

void ImportFileWithProgressPump(const TCHAR * szFilename);
void ImportFile(const TCHAR * szFilename);
HANDLE ImportFileAsync(const TCHAR * szFilename);
void ExportFile(const TCHAR * szFilename);
