/* --- The following code comes from d:\programme\lcc\lib\wizard\textmode.tpl. */

#include <stdio.h>

#include <stdlib.h>

//#include <string.h>

#include <windows.h>

#define MAXSTRLEN 256

#define CMDLINE_LEN 1024

// uncomment next line to turn debug messages on

//#define DEBUGGING

#define FLAUNCHER ".\\FLaunch.ini"

#define FLaunchSec "Freenet Launcher"



/*----------------------------------------------------------------*/

void SetCurrentExePath(char *argv){

	char s[MAXSTRLEN]="";

	char i;



	strcpy(s,argv);

	for (i=strlen(s);i>0;--i) {

		if (s[i]=='\\') {

			s[i]='\0';

			SetCurrentDirectory(s);

			return;

		}

	}

}

/*----------------------------------------------------------------*/

char *GetParam (char *string,char *param,char *section,char *cfgFilename) {

 FILE inifile;



 if(GetPrivateProfileString(section,param,"",string,MAXSTRLEN,cfgFilename) == 0) {

			printf("Couldn't find %s in %s (nothing serious)\n",param,cfgFilename);

			return NULL;

		}

 /* printf("Found %s as parameter %s\n",string,param); */

 return string;

}

/*----------------------------------------------------------------*/

BOOL WriteParam (char *string,char *param,char *section,char *cfgFilename) {

 FILE inifile;



 if(WritePrivateProfileString(section,param,string,cfgFilename) == 0) {

			printf("Couldn't write %s in %s\n",param,cfgFilename);

			return FALSE;

		}

 /* printf("Wrote %s as parameter %s\n",string,param); */

 return TRUE;

}

/*---------------------------------------------------------------------*/

void SetJavawToJavaPath(){

  char s[MAXSTRLEN];

  char t[MAXSTRLEN];

  char j;

  FILE *file;



  GetParam(s,"javaexec",FLaunchSec,FLAUNCHER);

  if (strlen(s)<1) return;

  j = strlen(s)-1;

  while (s[j-1]!='\\' && j>0) --j;

  s[j]='\0';

  strcpy(t,s);

  strcat(t,"javaw.exe");

  if ((file=fopen(t,"r"))!=NULL) {

    WriteParam(t,"javaw",FLaunchSec,FLAUNCHER);



  } else {

	strcpy(t,s);

	strcat(t,"wjview.exe");

	if ((file=fopen(t,"r"))!=NULL)

      WriteParam(t,"javaw",FLaunchSec,FLAUNCHER);

	}

  return;

}

/*---------------------------------------------------------------------*/

int main(int argc,char *argv[])

{

/*As a temporary measure (as long as JavaSearch can't handle this, just set Javaw to the same directory as Java*/

    SetJavawToJavaPath();



	printf("Successfully configured clients\n");

	return 0;

}

