#include "windows.h"

extern HINSTANCE hInstance;
extern HWND hWnd;

void ImportRefs(void);
void ExportRefs(void);
void ImportFile(const TCHAR * szFilename);
void ImportFileWithProgress(const TCHAR * szFilename);