These are the sources for the Windows system tray utility
========================================================

This is a rewrite in C of Sebastian Spaeth's freenet systray utility
It incorporates numerous bugfixes which I couldn't make directly to the
original assembler project as my intel assembly knowledge is very poor.

The original requirement that Sebastian used was to create as small an
executable file size as possible - I've tried to keep to this and this
version compiles to 11.5Kb using both Microsoft Visual C++ and Cygwin.
The reasons for my rewrite are two-fold - bug fixes and maintainability.
I have added some new functionality too though and additional features
should be easier to add from now on.

Included is an nmake (i.e. Microsoft) compatible makefile, 'freenet.mak';
an MSVC project definition file, 'freenet.dsp'; and a Cygwin/GNU Make
compatible makefile, 'Makefile'.  Building should therefore be a doddle.

mailto:no-brain@mindless.com

Below is Sebastian's original comments for the assembler source

--------------------------------------------------------

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