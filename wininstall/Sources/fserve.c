/* A wrapper for the Freenet client/node */
/* Originally hacked together by Sebastian Späth (Sebastian@SSpaeth.de)*/
/* License: Feel free to do whatever you want with it (Public Domain)  */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <windows.h>
#define MAXSTRLEN 256
#define CMDLINE_LEN 1024


/* the following are the parameters of the profileString array, containing the exe filename, the Javapath and the */
enum {EXE_EXEC,JAVA_PATH,EXE_FILE,AMT_OPTIONS};

// uncomment next line to turn debug messages on
//#define DEBUGGING


void GetExePath(char *exePath, char *argv){
	//Isn't there a easier function for this?

	char s[MAXSTRLEN]="";
	char i;

	strcpy(s,argv);
	if (s[strlen(s)-4]=='.') {s[strlen(s)-4]='\0';};
	for (i=strlen(s);i>0;--i) {
		if (s[i]=='\\') {
			s[i]='\0';
			strcpy(exePath,s);
			return;
		}
	}
}
/*-----------------------------------------------------------------*/
void GetExeName(char *exeFilename, char *argv){
	char *p_char;
	char i;
	char s[MAXSTRLEN]="";
	BOOL finished=FALSE;

	strcpy(s,argv);
	#ifdef DEBUGGING
	 printf("Trying to extract EXE Filename from: %s\n",s);
	#endif
	if (s[strlen(s)-4]=='.') s[strlen(s)-4]='\0';
	#ifdef DEBUGGING
	 printf("Trying to extract EXE Filename from (after stripping away the extension): %s\n",s);
	#endif
	/* stripping leading path information, so that only the pure exe file name is left */
	p_char=&s[0];
	for (i=strlen(s);i>0;--i) {
			if (s[i-1]=='\\') {
			p_char=&s[i];
			finished=TRUE;
		} //if (exeFilename...
		if (finished || s[i-1]=='\0') break;
	} //for (i=strle...
	strcpy(exeFilename,p_char);
	#ifdef DEBUGGING
	 printf("EXE Filename seems to be: %s\n",exeFilename);
	#endif
}
/*-------------------------------------------------------------------*/
void parseJavaPath (char *s) {
/* This function parses the Javapath and adds "s to the path if there are blanks in the path */

  BOOL blanks = FALSE;
  char j,i;

	for (j=(strlen(s)-1);j>0;--j) if (s[j] == ' ') blanks=TRUE;
    if (blanks) {
		for (j=strlen(s);j>=0;--j) s[j+1]=s[j];
		s[0]='\"'; //inserting the " at the right place.
		strcat(s,"\""); //closing parenthesis after Javaexec bin and go on
		  #ifdef DEBUGGING
    	   printf("Blanks found. Setting to: %s\n",s);
  		  #endif
	}
}
/*-------------------------------------------------------------------*/
int main(int argc,char *argv[]){
	char i,j;
    char javapath[MAXSTRLEN] = "";
	char cmdline[CMDLINE_LEN] = "";
	char s[MAXSTRLEN] = "";
 char profileString[AMT_OPTIONS][25]={"","",""}; /* fields are:[0] Param to Java binary [1]Javaexecutable [2]ThisExefilename */
	const char *cfgSection = "Freenet Launcher";
	const char *cfgFilename = ".\\FLaunch.ini";

	/*	SetConsoleTitle("Freenet Launcher"); */
	GetExeName(profileString[EXE_FILE],argv[0]);	/* Fill this Exe filename in profileString[3]*/
	strcpy(profileString[EXE_EXEC],profileString[EXE_FILE]);		/* File Exefilename+_exec in profileString[0]*/
	strcat(profileString[EXE_EXEC],"_exec");

	GetExePath(s,argv[0]);
	SetCurrentDirectory(s);
	#ifdef DEBUGGING
	 printf("ExeFileName= %s\nSetting directory to: %s\n",profileString[EXE_FILE],s);
	#endif
	/*now parsing the configfile */
	for (i=0;i<=AMT_OPTIONS-1;++i) {
#ifdef DEBUGGING
printf("parsing %d\n",i);
#endif
		s[0]='\0'; /*initialising s so we never append shit when nothing was found */
		if(GetPrivateProfileString(cfgSection,profileString[i],"",s,MAXSTRLEN,cfgFilename) == 0 && i>1){
			printf("Couldn't find %s in section %s in %s\n",profileString[i],cfgSection,cfgFilename);
    		  #ifdef DEBUGGING
		   	  } else {printf("Setting %s to %s\n",profileString[i],s);
	  		  #endif
		};
		switch (i) {
			case EXE_EXEC : {
						strcpy(profileString[JAVA_PATH], (strlen(s)==0) ? "javaexec": s);
						break;
						}
			case JAVA_PATH : { 
						parseJavaPath(s);
						strcat (cmdline,s);
                        strcat (cmdline," ");
						break;
						}
			case EXE_FILE :{
#ifdef DEBUGGING
				        printf("start cmdline");
#endif
					   	strcat(cmdline,s);
						break;
						}
		};
	};
	/* adding additional command line options */
	for (i=1;i < argc;i++) {
		strcat(cmdline," ");
		strcat(cmdline,argv[i]);
		}

	/* set the CLASSPATH env var to the location of the jar. */
	/* Is there any need to request the value from the config file? */
	SetEnvironmentVariable("CLASSPATH","freenet.jar");

	/*finally executing external programm and returning error code on exit*/
	printf("Executing: %s\n",cmdline);
    //ShellExecute(NULL,"open",javapath,cmdline,NULL,SW_SHOW);
	return system(cmdline);
}
