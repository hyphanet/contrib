// @@header: d:\dokumente\source\portcfg\portcfgres.h
// @@resources: d:\dokumente\source\portcfg\portcfg.rc
/*<---------------------------------------------------------------------->*/
#include <windows.h>
#include <stdlib.h>
//#include <windowsx.h>
//#include <commctrl.h>
//#include <string.h>
#include "portcfgres.h"
#include "winsock2.h"
/*<---------------------------------------------------------------------->*/
// #define DEBUG //uncomment line to turn off debug output
#define JAVAEXEC 	"listenPort"
#define FLAUNCHSEC 	"Freenet Node"
#define FLAUNCHFILE ".\\freenet.ini"
#define MAXSTRLEN 256

static int freeport=0;
char str[256];


/* prototype for the dialog box function. */
static BOOL CALLBACK DialogFunc(HWND hwndDlg, UINT msg, WPARAM wParam, LPARAM lParam);

/*----------------------------------------------------------------*/
char *GetParam (char *string,char *param,char *section,char *cfgFilename) {
 if(GetPrivateProfileString(section,param,"",string,MAXSTRLEN,cfgFilename) == 0) {return NULL;}
 return string;
}
/*----------------------------------------------------------------*/
BOOL WriteParam (char *string,char *param,char *section,char *cfgFilename) {

 if(WritePrivateProfileString(section,param,string,cfgFilename) == 0) {return FALSE;}
 return TRUE;
}
/*---------------------------------------------------------------------*/

int APIENTRY WinMain(HINSTANCE hinst, HINSTANCE hinstPrev, LPSTR lpCmdLine, int nCmdShow)
{
int retval;
WNDCLASS wc;

	memset(&wc,0,sizeof(wc));
	wc.lpfnWndProc = DefDlgProc;
	wc.cbWndExtra = DLGWINDOWEXTRA;
	wc.hInstance = hinst;
	wc.hCursor = LoadCursor(NULL, IDC_ARROW);
	wc.hbrBackground = (HBRUSH) (COLOR_WINDOW + 1);
	wc.lpszClassName = "portcfg";
	RegisterClass(&wc);

	retval = DialogBox(hinst, MAKEINTRESOURCE(IDD_MAINDIALOG), NULL, (DLGPROC) DialogFunc);

	WSACleanup();   //release WinSock DLL

	return retval;
}
/*----------------------------------------------------------------------------------*/


int SearchPort (HWND hDlg) {
/* Looks for a free random port >5000
   Returns: 0 for error, Port number otherwise*/
   //registered ports:5001-65535
   SOCKET hSock;
   struct sockaddr_in sa;           /* Local address and port number */
   struct hostent *hp;
   u_short port = 1024;
   int namelen;
   char s[256];
   int i;

    hSock = socket (PF_INET,SOCK_STREAM,0); //returns INVALID_SOCKET on errror
	if (hSock == INVALID_SOCKET) {MessageBox(hDlg,itoa(WSAGetLastError(),s,10),"Socket error",MB_OK);return 0;}

	/* Name the local socket with bind() */

    memset(&sa, 0, sizeof(struct sockaddr_in)); 	/* clear our address */
    gethostname(s, sizeof(s));        		/* who are we? */
    hp = gethostbyname(s);   	            /* get our address info */
    if (hp == NULL)                             /* we don't exist !? */
      return(0);
    sa.sin_family = hp->h_addrtype;         /* this is our host address */
    sa.sin_port = htons(port);    	        /* this is our port number */
    hSock = socket(AF_INET, SOCK_STREAM, 0);/* create the socket */
    if (hSock == INVALID_SOCKET)
      return 0;

	srand((unsigned int) (GetTickCount()));	/* Get random seed*/

	for (i=0;i<1000;++i) {
    	/* bind the socket to the internet address */
		do {port=rand();} while (port<5001 || port>0xFFFF);
		#ifdef DEBUG
		  MessageBox(hDlg,itoa(port,s,10),"Port chosen",MB_OK);
		#endif
  		if (bind(hSock, (struct sockaddr *)&sa, sizeof(struct sockaddr_in))!= SOCKET_ERROR)
			{break;}
		#ifdef DEBUG
		else
			{MessageBox(hDlg,itoa(WSAGetLastError(),s,10),"Bind error",MB_OK);}
		#endif
	};
	if (i==500) return 0; /* didn't find a free port, returning 0*/

	#ifdef DEBUG
	  MessageBox(hDlg,itoa(port,s,10),"Returning port",MB_OK);
	#endif

	closesocket(hSock);

   return port;
}
/*----------------------------------------------------------------------------------*/
static int InitializeApp(HWND hDlg,WPARAM wParam, LPARAM lParam)
{
WORD wVersionRequested;
WSADATA wsaData;

	/*initialize WinSock DLL*/
	wVersionRequested = MAKEWORD( 2, 0 );
	/* WSAStartup() returns error value if failed (0 on success) */
	if (WSAStartup(wVersionRequested, &wsaData) != 0) {return 0; /* No sense continuing if we can't use WinSock */}

	/* Does a ListenPort exist?*/
	if (GetParam (str,JAVAEXEC,FLAUNCHSEC,FLAUNCHFILE) != NULL) {
		strcat(str, " is already configured as freenet port. Should we use it?");
		if (MessageBox(NULL,str,"Port already defined",MB_YESNO) == IDYES) {freeport=atoi(str);EndDialog(hDlg,1);};
	}
	freeport = SearchPort(hDlg);
	return 1;
}

/*----------------------------------------------------------------------------------*/
/*
This is the main function for the dialog. It handles all messages. Do what your
application needs to do here.
*/
static BOOL CALLBACK DialogFunc(HWND hwndDlg, UINT msg, WPARAM wParam, LPARAM lParam)
{
	switch (msg) {
	/* This message means the dialog is started but not yet visible.
	   Do All initializations here
        */
	case WM_INITDIALOG:
		InitializeApp(hwndDlg,wParam,lParam);
		SetDlgItemText(hwndDlg,102,itoa(freeport,str,10));
		return TRUE;
	/* By default, IDOK means close this dialog returning 1, IDCANCEL means
           close this dialog returning zero
        */
	case WM_COMMAND:
		switch (LOWORD(wParam)) {
			case IDOK:
				WriteParam (itoa(freeport,str,10),JAVAEXEC,FLAUNCHSEC,FLAUNCHFILE);
				EndDialog(hwndDlg,1);
				return 1;
			case IDCANCEL:
				EndDialog(hwndDlg,0);
				return 1;
		}
		break;
        /* By default, WM_CLOSE is equivalent to CANCEL */
	case WM_CLOSE:
		EndDialog(hwndDlg,0);
		return TRUE;

	}
	return FALSE;
}
