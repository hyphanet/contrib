
// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the EZFCPDLL_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// EZFCPDLL_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef EZFCPDLL_EXPORTS
#define EZFCPDLL_API __declspec(dllexport)
#else
#define EZFCPDLL_API __declspec(dllimport)
#endif

// This class is exported from the ezFCPdll.dll
class EZFCPDLL_API CEzFCPdll {
public:
	CEzFCPdll(void);
	// TODO: add your methods here.
};

extern EZFCPDLL_API int nEzFCPdll;

EZFCPDLL_API int fnEzFCPdll(void);

