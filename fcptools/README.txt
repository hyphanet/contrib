README.txt

This source package builds four Freenet FCP components, fwproxy, fcpget, fcpput
and ezFCPlib.

CopyLeft (c) 2001 by David McNab,
david@rebirthing.co.nz

- fcpputsite is a command-line freesite insertion client - fast and easy.

- fwproxy is a handy http proxy server and forms an ideal fproxy replacement.

- fcpget is a command-line request client, completely based on FCP.

- fcpput is a command-line insert client, completely based on FCP.

- ezFCPlib is an easy FCP library. It is unique in that it doesn't require
  any third party libraries (as do many of the other Freenet client libs).

This is a work in progress.

The aim of FCPtools is to offer a truly multi-platform set of FCP tools,
to assist with writing Freenet client programs in C/C++.

While still in development, the tools have successfully compiled and run on:
* Windows native (compiling with MS Visual C++)
* Windows with Cygwin
* Linux (Mandrake 8.0)


-------------------------------------------------
Building the tools
-------------------------------------------------

On Linux or Cygwin, simply type 'make linux' or 'make cygwin'.

On Windows MSVC, simply open the fcptools.dsw workspace file,
and build each of the projects.

-------------------------------------------------
ezFCPlib - the easiest multi-platform FCP library
-------------------------------------------------

1. Features:

Writing Freenet client programs in C/C++ has never been easier.
This API offers two levels of Freenet access - file/memory level, and
stream level.
Read the spec in ezfcplib/doc/spec.html for more info.


-------------------------------------
'fwproxy' - the FreeWeb Proxy Server.
-------------------------------------

1. Features:

* FProxy replacement
* generic http proxy
* FreeWeb gateway

This proxy is evolving towards a complete FCP-based replacement of
fproxy, with the FreeWeb browsing features built-in.

You can point your browser to this proxy permanently.
Any normal web requests go out to the mainstream web, while
fproxy-style and freeweb-style requests go to Freenet.


2. Building:

On Linux, this should build ok without any problems.
Just 'cd' to fwproxy, and type 'make linux'.

Same for cygwin, except you need 'make cygwin'.


3. Running:

As you can see in main.c, this proxy listens on port 8888.

If you want, you can edit your .freenetrc file, change the
fproxy listening port to something else, and set fwproxy to
listen on 8081. That way, any links explicitly using
'http://127.0.0.1:8081/whatever' will work ok.

Also, you'll need to make a directory /usr/share/fwproxy, and
copy there the file gateway.html


-------------------------------------------------------
fcpget - a simple FCP-based command line request client
-------------------------------------------------------

While originally intended as a demo of the ezFCPlib library, this
is rapidly evolving into a full-featured request client.

