# Standard Makefile for freenet.exe
# For use with GNU make and compatibile toolchain

PROGRAMS = freenet
RM       = rm

# Core source file names

SRCS =  freenet.c launchthread.c
OBJS =  freenet.o launchthread.o rsrc.res

#  Change compiler to suit (gcc is recommended)
CC = gcc
#  I use windres as a GNU resource compiler
RC = windres

CCOPTIONS = 
CFLAGS = $(CDEBUGFLAGS) $(CCOPTIONS) $(DEFINES)

# Pick a suitable CDEBUGFLAGS:

# Fully optimised
CDEBUGFLAGS = -O6 -fomit-frame-pointer -finline-functions -Wall -Wwrite-strings

# Linker flags
LFLAGS = -Wl,-O9,--strip-all,--discard-all,-Bdynamic,-N

# RC flags:
RCFLAGS = --input-format rc --output-format coff


all : freenet

freenet : $(OBJS)
	$(CC) $(CFLAGS) $(LFLAGS) -o freenet.exe $(OBJS)

	


# dependencies
freenet.o:		freenet.c types.h rsrc.h freenet_tray.h launchthread.h shared_data.h Makefile
launchthread.o:	launchthread.c types.h launchthread.h shared_data.h Makefile


rsrc.res:	rsrc.rc rsrc.h Makefile
	$(RC) $(RCFLAGS) -o rsrc.res -i rsrc.rc 

clean :: 
	$(RM) *.o 
	$(RM) *.res
	$(RM) freenet.exe 
	$(RM) *.opt
	$(RM) *.plg
	$(RM) *.aps
	$(RM) *.ncb
