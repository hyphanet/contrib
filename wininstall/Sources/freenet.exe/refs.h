#ifndef FREENET_REFS_H_INCLUDED
#define FREENET_REFS_H_INCLUDED

#include "stdafx.h"

void ImportRefs(void);
void ExportRefs(void);

void ImportFileWithProgressPump(const TCHAR * szFilename);
void ImportFile(const TCHAR * szFilename);
HANDLE ImportFileAsync(const TCHAR * szFilename);
void ExportFile(const TCHAR * szFilename);

#endif // FREENET_REFS_H_INCLUDED
