This are the sources for the Windows system tray utility
========================================================

It is written in assembler and compiled and linked with the free
Microsoft assembler and linker (I forgot the weblink where to get
them right now though).

As an development IDE I used the free MASM32, which can be e.g. found here: http://www.pbq.com.au/home/hutch/masm.htm.

The assembler source is quite MASM specific as it uses heavily the
shortcut invoke <function>,arg1,arg2,... instead of the asm typical
push arg2, push arg1, call <function>, but that can be easily 
fixed if needed.

Building should be pretty straightforward as there are only three files
involved: the assembler source code, the resource file, and a header file.

Any questions? mailto:Sebastian@SSpaeth.de