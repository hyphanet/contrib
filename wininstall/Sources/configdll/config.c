#include <windows.h>
#include "rsrc.h"
#define	NUMPSPAGES 2
//#define MAXSTRLEN 512
#define PREFLEN	64  /* max length of pref strinf (incl. 0) */

void Config (HWND); /* Prototype for Config*/
int PSdlgFunc1(HWND, DWORD uMsg, WPARAM wParam, LPARAM lParam);
int PSdlgFunc2(HWND, DWORD uMsg, WPARAM wParam, LPARAM lParam);
int PSdlgFunc3(HWND, DWORD uMsg, WPARAM wParam, LPARAM lParam);

LPVOID 		lpprefvalues;	/* pointer to the prefvalues Mem buffer*/
HPROPSHEETPAGE hPs[NUMPSPAGES];	/* handler of the Property sheets*/
HINSTANCE	hInstance;		/* Instance handle of the dll*/
HGLOBAL		hPropSheet;		/* handle of the active propsheet*/


typedef struct {
	const char *name;
	BOOL state;
	DWORD ID;} cb_s;

						/* number of checkboxen*/
#define NUMCB 3			/*Name and Status of Checkbox n*/
cb_s cb[NUMCB]={{"transient",0,ID_CB_TRANSIENT},{"InformRead",1,ID_CB_INFREAD},{"InformWrite",1,ID_CB_INFWRITE}};

#define CB_TRANSIENT 0
#define CB_INFREAD 1
#define CB_INFWRITE 2

typedef struct {
	const char *name;
	char state[PREFLEN];
	DWORD ID;} eb_s;

#define NUMEB 2  	/* number of string prefs (edit boxes) */
eb_s eb[NUMEB]={{"ListenPort","19114",ID_EB_LISTENPORT},{"BandwidthLimit","50000",ID_EB_BANDWIDTH}};

#define EB_LISTENPORT 0
#define EB_BANDWIDTH 1



const char *inifile = ".//freenet.ini";
const char *inisec = "Freenet node";
const char *szTitle= "Freenet configurator";

char i;		/* multi purpose char*/

/*------------------------------------------------------------------------
 Procedure:     LibMain ID:1
 Purpose:       Dll entry point.Called when a dll is loaded or
                unloaded by a process, and when new threads are
                created or destroyed.
 Input:         hDllInst: Instance handle of the dll
                fdwReason: event: attach/detach
                lpvReserved: not used
 Output:        The return value is used only when the fdwReason is
                DLL_PROCESS_ATTACH. True means that the dll has
                sucesfully loaded, False means that the dll is unable
                to initialize and should be unloaded immediately.
 Errors:
------------------------------------------------------------------------*/
BOOL WINAPI __declspec(dllexport) LibMain(HINSTANCE hDLLInst, DWORD fdwReason, LPVOID lpvReserved)
{
    switch (fdwReason)
    {
        case DLL_PROCESS_ATTACH:
            // The DLL is being loaded for the first time by a given process.
            // Perform per-process initialization here.  If the initialization
            // is successful, return TRUE; if unsuccessful, return FALSE.

			hInstance = hDLLInst;
				  Config(NULL);
            break;

        case DLL_PROCESS_DETACH:
            // The DLL is being unloaded by a given process.  Do any
            // per-process clean up here, such as undoing what was done in
            // DLL_PROCESS_ATTACH.  The return value is ignored.


            break;

        //case DLL_THREAD_ATTACH:
            // A thread is being created in a process that has already loaded
            // this DLL.  Perform any per-thread initialization here.  The
            // return value is ignored.

          //  break;
        //case DLL_THREAD_DETACH:
            // A thread is exiting cleanly in a process that has already
            // loaded this DLL.  Perform any per-thread clean up here.  The
            // return value is ignored.

          //  break;
		  default:
		  break;

    }

    return TRUE;	/* Load dll*/
}
/*----------------------------------------------------------------------------------------*/
void main(){Config(NULL);return;}
/*----------------------------------------------------------------------------------------*/
char *ReadPref (char *param,char *string) {
 if(GetPrivateProfileString(inisec,param,"",string,PREFLEN,inifile) == 0) {return NULL;}
 return string;
}
/*----------------------------------------------------------------------------------------*/
BOOL WritePref (char *param, char *string) {
  if(WritePrivateProfileString(inisec,param,string,inifile) == 0) {return FALSE;}
  return TRUE;
}
/*----------------------------------------------------------------------------------------*/
void SavePrefs () {
const char *sbi[2]={"no","yes"};
char i;
	/* First Writing the BOOL prefs*/
  for (i=0;i<NUMCB;++i) WritePref(cb[i].name,sbi[cb[i].state]);

  	/* Writing String prefs*/
  for (i=0;i<NUMEB;++i) WritePref(eb[i].name,eb[i].state);
  return;
}
/*----------------------------------------------------------------------------------------*/
void LoadPrefs(){
  char s[PREFLEN];
  char i;
	/* First Reading the BOOL prefs */
  for (i=0; i<NUMCB;++i)
    cb[i].state= (CompareString(0,NORM_IGNORECASE,ReadPref(cb[i].name,s),-1,"yes",-1)==2) ? 1 : 0;

  	/* Reading String prefs*/
  for (i=0;i<NUMEB;++i) ReadPref(eb[i].name,eb[i].state);

  return;
}
/*----------------------------------------------------------------------------------------*/
/*----------------------------------------------------------------------------------------*/
/*----------------------------------------------------------------------------------------*/
/*----------------------------------------------------------------------------------------*/
/*----------------------------------------------------------------------------------------*/
/*----------------------------------------------------------------------------------------*/
void __declspec(dllexport) Config (HWND hwndParent) {

	const char *psztitle = "b";
	const char *myPS[NUMPSPAGES] = {"PSHEET1","PSHEET2"};		/* Names of the PropSheet dialogs*/
	PROPSHEETPAGE PropSheet[NUMPSPAGES];  	/* Information about the single Propsheets*/
	PROPSHEETHEADER PropHdr;	/* Information about the PropertsheetHeader*/
	LPVOID lpdlgFunc[NUMPSPAGES]={(LPVOID)&PSdlgFunc1,(LPVOID)&PSdlgFunc2};	/* address of the property sheet dialog functions */
	char i;

	LoadPrefs();			/* Load prefs from file into mem*/

	for (i=0;i<NUMPSPAGES;++i) {			/* Set up the property sheets*/
		/*	Set up the 1st property sheet*/
	    PropSheet[i].dwSize = sizeof(PROPSHEETPAGE);
		PropSheet[i].dwFlags = PSP_DEFAULT;
		PropSheet[i].hInstance = hInstance;
		PropSheet[i].pszTemplate = myPS[i];
		PropSheet[i].pszIcon = 0;
		PropSheet[i].pfnDlgProc = lpdlgFunc[i];
		PropSheet[i].pszTitle = psztitle;
		PropSheet[i].lParam = 0;
		PropSheet[i].pfnCallback = 0;

		hPs[i] = CreatePropertySheetPage(&PropSheet[i]);
	}

        /* Set up the property sheet header */

		PropHdr.dwSize = sizeof(PROPSHEETHEADER);
		PropHdr.dwFlags = PSH_DEFAULT;
		PropHdr.hwndParent = hwndParent;
		PropHdr.hInstance = hInstance;
		PropHdr.pszIcon = 0;
		PropHdr.pszCaption = szTitle;
		PropHdr.nPages = NUMPSPAGES;
		PropHdr.nStartPage = 0;
		PropHdr.phpage = &hPs[0];
		PropHdr.pfnCallback = 0;

        /* Display the property sheet control*/
		PropertySheet(&PropHdr);


	return;
}

/*----------------------------------------------------------------------------------------*/
int PSdlgFunc1 (HWND hdwnd, DWORD uMsg, WPARAM wParam, LPARAM lParam) {
NMHDR * lnmhdr;/* pointer to notification header */
DWORD i;

	switch (uMsg) {

	  case WM_NOTIFY:
		lnmhdr=(NMHDR*)lParam;
		switch (lnmhdr->code) {
			case PSN_SETACTIVE:			/* page gaining focus */
				hPropSheet=lnmhdr->hwndFrom;

				SetWindowLong(hdwnd, DWL_MSGRESULT, 0);
				return TRUE;
				break;

			case PSN_KILLACTIVE:		/* page gaining focus or user pressed a button*/

				/* Getting all values of the page (actually all)and save them*/
				for (i=0;i<NUMCB;++i)
			   		cb[i].state= SendDlgItemMessage(hdwnd,cb[i].ID, BM_GETCHECK,0,0);

				/* Getting all string values of the page (actually all)and save them*/
				for (i=0;i<NUMEB;++i)
			   		GetDlgItemText(hdwnd, eb[i].ID, eb[i].state, PREFLEN);

				SetWindowLong(hdwnd, DWL_MSGRESULT, 0);
				return TRUE;
				break;

			case PSN_APPLY:				/* User chose OK or apply, save all values now */
				SavePrefs();
				//TODO:MessageBox(hdwnd,"Do you want to restart you node now to use the new values?(nonfunctional yet)",szTitle,MB_OK);
				break;


			case PSN_RESET:				/* Add own cancel code here */
				break;
		}


	  case (WM_COMMAND):
		switch (LOWORD(wParam)) {

			case (ID_CB_TRANSIENT||ID_CB_INFWRITE): //||ID_CB_INFREAD):
			 SendMessage(hPropSheet, PSM_CHANGED, (WPARAM)hdwnd, 0);
				break;

		}	/* end LOWORD of WM_COMMAND*/


		//switch (HIWORD(wParam)) {

			//case (BLAH):
			//	break:
		//}
		break; /* end WM_COMMAND*/

	  case (WM_INITDIALOG):
			/* Setting all CB values of the page (actually all) of them*/
			for (i=0;i<NUMCB;++i)
			   	SendDlgItemMessage(hdwnd,cb[i].ID, BM_SETCHECK, cb[i].state, 0);

			/* Setting all string values (EB) of the page (actually all) of them*/
			for (i=0;i<NUMEB;++i)
			   	SetDlgItemText(hdwnd,eb[i].ID, eb[i].state);

		  break;
	}
	return FALSE;		/* We did not handle the event -> default handler*/
}
/*----------------------------------------------------------------------------------------*/
int PSdlgFunc2 (HWND hdwnd, DWORD uMsg, WPARAM wParam, LPARAM lParam) {
NMHDR * lnmhdr;/* pointer to notification header */

	switch (uMsg) {

	  case WM_NOTIFY:
		lnmhdr=(NMHDR*)lParam;
		switch ((long)(NMHDR*)lnmhdr->code) {
			case PSN_SETACTIVE:			/* page gaining focus */


				SetWindowLong(hdwnd, DWL_MSGRESULT, 0);
				return TRUE;
				break;

			case PSN_KILLACTIVE:		/* page gaining focus */

				SetWindowLong(hdwnd, DWL_MSGRESULT, 0);
				return TRUE;
				break;

			case PSN_APPLY:				/* User chose OK or apply, save all values now */
				break;


			case PSN_RESET:				/* Add own cancel code here */
				break;
		}


	  case (WM_COMMAND):
		//switch (LOWORD(wParam)) {

			//case (BLAH):
			//	break;
		//}	/* end LOWORD of WM_COMMAND*/


		//switch (HIWORD(wParam)) {

			//case (BLAH):
			//	break:
		//}
		break; /* end WM_COMMAND*/

	  case (WM_INITDIALOG):
		  break;

	}
	return FALSE;		/* We did not handle the event -> default handler*/
}
/*----------------------------------------------------------------------------------------*/
int PSdlgFunc3(HWND hdwnd, DWORD uMsg, WPARAM wParam, LPARAM lParam) {
	return FALSE;
	}
/*----------------------------------------------------------------------------------------*/
