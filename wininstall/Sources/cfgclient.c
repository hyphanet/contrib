/* Originally hacked together by Sebastian Späth (Sebastian@SSpaeth.de)*/
/* License: Feel free to do whatever you want with it (Public Domain)  */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#define MAXSTRLEN 256
#define CMDLINE_LEN 1024
// uncomment next line to turn debug messages on
//#define DEBUGGING

#define CFGFILE ".\\freenet.ini"
#define FLAUNCHER ".\\FLaunch.ini"
#define FPROXYRC ".\\.fproxyrc"
#define OPTIONSTR " -serverAddress "
#define DEFAULT_IP "tcp/127.0.0.1"

  const char FNetSec[] = "Freenet node";
  const char FLaunchSec[] = "Freenet Launcher";

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
BOOL changeAddress(char *address, char *key, char *section, char *filename) {
	char i,j;
	char *p_char;
	char s[MAXSTRLEN] = "";

	/*strip -serveraddres off */
	if(GetParam(s, key, section, filename) != NULL) {
		/* Stripping existing -serverAddress values */
		if ((p_char = strstr(strlwr(s),"-serveraddress")) != NULL) {
		   GetParam(s, key, section, filename);
		   i = p_char - &s[0] - 1; /* Set i to the space before -serverAddress (1.th char=0) */
		   j = i + 17;
		   while (s[j]!='\0' && s[j]!=' ') ++j;
		   for (;s[i-1]!='\0';j++,i++) s[i]=s[j];
		} else { GetParam(s, key, section, filename);}
	}
	strcat(s,address);
	return WriteParam(s, key, section, filename);
}

/*---------------------------------------------------------------------*/
int main(int argc,char *argv[])
{
	char appendString[MAXSTRLEN] = "";
	char s[MAXSTRLEN];


 	 /* Try to get another machinename to connect to, otherwise the default 127.0.0.1 will be used */
	if(GetParam(s,"nodeAddress",FNetSec, CFGFILE) == NULL) {
		strcat (appendString,DEFAULT_IP);
	} else {
		strcat (appendString,s);
	}

	strcat(appendString,":");

	 /* append port to appendString */
	 /* If Listenport is not in Freenet.ini exit without doing anything */
	if(GetParam(s,"ListenPort",FNetSec, CFGFILE) == NULL) {
		printf("Error, no port specified, what should I configure then?\n");
	}
	strcat(appendString,s);

	/*ok, here appendstring is the fully qualified Address string we want to append */

	strcpy(s,OPTIONSTR);	//setting s to OPTIONSTR " -serverAddress "
	strcat(s,appendString);	//and adding the actual address
							// so s will be " -serverAddress 127... "
							// and appendstring only 127.0.0.1:1234

	changeAddress(s,"finsert",FLaunchSec,FLAUNCHER);
	changeAddress(s,"frequest",FLaunchSec,FLAUNCHER);
	changeAddress(s,"fproxy",FLaunchSec,FLAUNCHER);
	changeAddress(appendString,"serverAddress","FProxy",".\\.fproxyrc");

/*As a temporary measure (as long as JavaSearch can't handle this, just set Javaw to the same directory as Java*/
    SetJavawToJavaPath();

	printf("Successfully configured clients\n");
	return 0;
}
