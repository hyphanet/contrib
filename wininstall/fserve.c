//A wrapper for the Freenet client/node under the GNU general public license
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <windows.h>
#define MAXSTRLEN 256
#define CMDLINE_LEN 1024
#define DEFJAVAEXEC "javaexec"

#define EXE_MIN 0
#define EXE_EXEC 1
#define JAVA_PATH 2
#define EXE_FILE 3
#define AMT_OPTIONS 4

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
int main(int argc,char *argv[]){
	char i,j;
	char cmdline[CMDLINE_LEN] = "";
	char s[MAXSTRLEN] = "";
	char profileString[AMT_OPTIONS][25]={"","","",""}; /* fields are:[0]ThisExeFilename+ "_min" [1] Param to Java binary [2]Javaexecutable [3]ThisExefilename */
	const char *minimizeStr = "start /min ";
	const char *cfgSection = "Freenet Launcher";
	const char *cfgFilename = ".\\FLaunch.ini";

	/*	SetConsoleTitle("Freenet Launcher"); */
	GetExeName(profileString[EXE_FILE],argv[0]);	/* Fill this Exe filename in profileString[3]*/
	strcpy(profileString[EXE_MIN],profileString[EXE_FILE]);		/* File Exefilename+_min in profileString[0]*/
	strcat(profileString[EXE_MIN],"_min");
	strcpy(profileString[EXE_EXEC],profileString[EXE_FILE]);		/* File Exefilename+_exec in profileString[0]*/
	strcat(profileString[EXE_EXEC],"_exec");

	GetExePath(s,argv[0]);
	SetCurrentDirectory(s);
	#ifdef DEBUGGING
	 printf("ExeFileName= %s\nSetting directory to: %s\n",profileString[EXE_FILE],s);
	#endif

	/*now parsing the configfile */
	for (i=0;i<=AMT_OPTIONS-1;++i) {
		s[0]='\0'; /*initialising s so we never append shit when nothing was found */
		if(GetPrivateProfileString(cfgSection,profileString[i],"",s,MAXSTRLEN,cfgFilename) == 0 && i>1){
			printf("Couldn't find %s in section %s in %s\n",profileString[i],cfgSection,cfgFilename);
    		  #ifdef DEBUGGING
		   	  } else {printf("Setting %s to %s\n",profileString[i],s);
	  		  #endif
		};
		switch (i) {
			case EXE_MIN :{
					strlwr(s);
					if (strstr(s,"1") == &s[0] || strstr(s,"true") ==&s[0]) {
						strcat(cmdline,minimizeStr);
	      					#ifdef DEBUGGING
	   						printf("Minimizing application!\n");
	  	  					#endif
						};
					break;
					}
			case EXE_EXEC : {
						strcpy(profileString[JAVA_PATH], (strlen(s)==0) ? "javaexec": s);
					  	#ifdef DEBUGGING
	   					printf("Found explicit Java binary instruction %s\n",s);
	  	  			  	#endif
						break;
						}
			case JAVA_PATH : {
						// Now follows an ugly hack to insert a parenthesis after the C:\"path\java.exe"
						// this is due to differences in W98 W2K which allows/doesn't allow a title in Parenthesis
						for (j=strlen(s);j>=0;--j) s[j+1]=s[j];
						j=0;
						while (j<(strlen(s)) && (s[j]!='\\')) {
							s[j]=s[j+1];
							++j;
							}
						s[j]='\"'; //inserting the " at the right place, I know it's ugly
						strcat(cmdline,s);
					    strcat(cmdline,"\" "); //closing parenthesis after Javaexec bin and go on
						break;
						}
			case EXE_FILE :{
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

	/*finally executing external programm and returning error code on exit*/
	printf("Executing: %s\n",cmdline);
	return system(cmdline);
}
