/* --- The following code comes from d:\programme\lcc\lib\wizard\textmode.tpl. */
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
#define APPENDSTR " -serverAddress "
#define DEFAULT_IP "127.0.0.1"

  const char FNetSec[] = "Freenet node";
  const char FLaunchSec[] = "Freenet Launcher";

/*----------------------------------------------------------------*/
void SetCurrentExePath(char *argv){
	char s[MAXSTRLEN]="";
	char i;

	strcpy(s,argv);
	if (s[strlen(s)-4]=='.') {s[strlen(s)-4]='\0';};
	for (i=strlen(s);i>0;--i) {
		if (s[i]=='\\') {
			s[i]='\0';
			return;
		}
	}
	SetCurrentDirectory(s);
}


char *GetParam (char *string,char *param,char *section,char *cfgFilename) {
 FILE inifile;

 if(GetPrivateProfileString(section,param,"",string,MAXSTRLEN,cfgFilename) == 0) {
			printf("Couldn't find %s in %s\n",param,cfgFilename);
			return NULL;
		}
 /* printf("Found %s as parameter %s\n",string,param); */
 return string;
}

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
  char j;

  GetParam(s,"javaexec",FLaunchSec,FLAUNCHER);
  j = strlen(s)-1;
  while (s[j-1]!='\\') --j;
  s[j]='\0';
  strcat(s,"javaw.exe");
  WriteParam(s,"javaw",FLaunchSec,FLAUNCHER);
}
/*---------------------------------------------------------------------*/
int main(int argc,char *argv[])
{
	char i,j;
	char *p_char;
	char *cfgFile = CFGFILE;
	char appendString[MAXSTRLEN] = APPENDSTR;
	char s[MAXSTRLEN];

     /* Change to current exe directory */
	SetCurrentExePath(argv[0]);

	 /* Try to get another machinename to connect to, otherwise the default 127.0.0.1 will be used */
	if(GetParam(s,"nodeAddress",FNetSec,cfgFile) == NULL) {
		strcat (appendString,DEFAULT_IP);
	} else {
		strcat (appendString,s);
	}

	strcat(appendString,":");

	 /* append port to appendString */
	 /* If Listenport is not in Freenet.ini exit without doing anything */
	if(GetParam(s,"ListenPort",FNetSec,cfgFile) == NULL) {
		printf("Exiting without changing ini files\n");
		return 0;
	}
	strcat(appendString,s);

	/*ok, here appendstring is the fully qualified -serverAddress string we want to append */

	/*Processing finsert entry, strip -serveraddres off */
	if(GetParam(s,"finsert",FLaunchSec,FLAUNCHER) != NULL) {
		/* Stripping existing -serverAddress values */
		if ((p_char = strstr(strlwr(s),"-serveraddress")) != NULL) {
		   GetParam(s,"finsert",FLaunchSec,FLAUNCHER);
		   i = p_char - &s[0] - 1; /* Set i to the space before -serverAddress (1.th char=0) */
		   j = i + 17;
		   while (s[j]!='\0' && s[j]!=' ') ++j;
		   for (;s[i-1]!='\0';j++,i++) s[i]=s[j];
		}
	}
	strcat(s,appendString);
	WriteParam(s,"finsert",FLaunchSec,FLAUNCHER);

	/*Processing frequest entry, strip -serveraddres off */
	if(GetParam(s,"frequest",FLaunchSec,FLAUNCHER) != NULL) {
		/* Stripping existing -serverAddress values */
		if ((p_char = strstr(strlwr(s),"-serveraddress")) != NULL) {
		   GetParam(s,"frequest",FLaunchSec,FLAUNCHER);
		   i = p_char - &s[0] - 1; /* Set i to the space before -serverAddress (1.th char=0) */
		   j = i + 17;
		   while (s[j]!='\0' && s[j]!=' ') ++j;
		   for (;s[i-1]!='\0';j++,i++) s[i]=s[j];
		}
	}
	strcat(s,appendString);
	WriteParam(s,"frequest",FLaunchSec,FLAUNCHER);

/*As a temporary measure (as long as JavaSearch can't handle this, just set Javaw to the same directory as Java*/
    SetJavawToJavaPath();

	printf("Successfully configured clients\n");
	return 0;
}
