                                README

           Java(TM) 2 Runtime Environment, Standard Edition
                           Version 1.3.0_02
                          

The Java(TM) 2 Runtime Environment contains the Java virtual machine, 
runtime class libraries, and Java application launcher that are 
necessary to run programs written in the Java progamming language. 
It is not a development environment and does not contain development 
tools such as compilers or debuggers. If you need development tools, 
use the Java 2 SDK (Software Development Kit) rather than the 
Java 2 Runtime Environment. 

The Java 2 Runtime Environment includes the Java Plug-in product 
which enables support for the Java 2 platform on recent releases of 
Netscape Navigator and Microsoft Internet Explorer. For more 
information, see the Plug-in web page at 
http://java.sun.com/products/plugin/ .


=======================================================================
     Deploying Applications with the Java 2 Runtime Environment
=======================================================================

A Java-language application, unlike an applet, cannot rely on a web 
browser for installation and runtime services. When you deploy an  
application written in the Java programming language, your software 
bundle will probably consist of the following parts: 

            Your own class, resource, and data files. 
            A runtime environment. 
            An installation procedure or program. 

You already have the first part, of course. The remainder of this
document covers the other two parts. See also the Notes for Developers 
page on the Java Software website:

     http://java.sun.com/j2se/1.3/runtime.html

-----------------------------------------------------------------------
Runtime Environment
-----------------------------------------------------------------------

To run your application, a user needs a Java virtual machine, the Java 
platform core classes, and various support programs and files. This 
collection of software is known as a runtime environment. 

The Java 2 SDK software can serve as a runtime environment. However, 
you probably can't assume your users have the Java 2 SDK installed, 
and your Java 2 SDK license doesn't allow you to redistribute SDK 
files. 

To solve this problem, Sun provides the Java 2 Runtime Environment 
as a free, redistributable runtime environment. 

The final step in the deployment process occurs when the software is 
installed on individual user system. Installation consists of copying 
software onto the user's system, then configuring the user's system to 
support that software. 

This step includes installing and configuring the runtime environment. 
If you use the Java 2 Runtime Environment, you must make sure that your 
installation procedure never overwrites an existing installation, unless 
the existing runtime environment is an older version. 

The Win32 version of the Java 2 Runtime Environment is distributed as a 
self-installing executable. A simple way to redistribute the Java 2 
Runtime Environment is to include this executable in your software 
bundle. You can then have your installation program run the executable 
to install the Java 2 Runtime Environment, or simply instruct the user 
to install the Java 2 Runtime Environment before installing the rest of 
your bundle. In this installation model, the end-user will have a 
"public" copy of the Java 2 Runtime Environment just as if it had been 
downloaded from Sun's website and installed separately. 

The Runtime Environment's installation program records program 
information in the Windows Registry. This registry information 
includes the software version, which you should compare with the Java 2 
Runtime Environment version in your software bundle. For more information, 
refer to the Notes for Developers on the Java Software web site:

     http://java.sun.com/j2se/1.3/runtime.html 

Another approach is to install the Java 2 Runtime Environment on your 
own system, then copy the files you need into your application's own 
installation set for redistribution. If you choose this approach, 
you must include all files except those described as "optional" in the 
"Redistrubition of the Java 2 runtime environment" section of this 
README. The Java 2 Runtime Environment software can only be 
redistributed if all "required" files are included. See the license 
file for specifics. 

If you use this approach, do not try to emulate the installation steps 
performed by the Java 2 Runtime Environment installer. You might "break" 
an existing runtime environment installation by missing a new or 
undocumented installation step. Instead, you should include the Java 2
Runtime Environment files in your own application directory. In 
effect, your application has its own "private" copy of the Java 2 
Runtime Environment. 

The Java 2 Runtime Environment includes Java Plug-in software, 
which enables Netscape Navigator and Microsoft Internet Explorer to 
support the Java 2 platform. To develop applets that use Java Plug-in 
software, see the Java Plug-in product page on the Java Software web 
site:

     http://java.sun.com/products/plugin/

For documentation on new Java Plug-in features in J2SE 1.3.0_01, see 
  
     http://java.sun.com/j2se/1.3.0_01/docs/


-----------------------------------------------------------------------
Winsock Deployment
-----------------------------------------------------------------------

If your application uses the networking classes, it may not run 
reliably under Winsock 1.1. If your networking application must support 
Windows 95, which includes Winsock 1.1, you will want to include a 
Winsock 2.0 install in your installation procedure. (Windows NT 4.0 and 
Windows 98 include Winsock 2.0.) You can download the Winsock 2.0 
from this address: 

http://www.microsoft.com/windows95/downloads/contents/wuadmintools/s_wunetworkingtools/w95sockets2/

The following URL contains information about how to determine if 
the Winsock 2.0 components are installed on a Windows 95 platform:

http://support.microsoft.com/support/kb/articles/Q177/7/19.asp


=======================================================================
         Redistribution of the Java 2 Runtime Environment
=======================================================================

The term "vendors" used here refers to licensees, developers, and 
independent software vendors (ISVs) who license and distribute the 
Java 2 Runtime Environment with their programs.

Vendors must follow the terms of the Binary Code License agreement  
which include, among others:

 - Arbitrary subsetting of the Java 2 Runtime Environment is not 
   allowed. See the section below entitled "Required vs. Optional Files" 
   for those files that may be optionally omitted from redistributions 
   of the runtime environment. 

 - You must include in your product's license the provisions called 
   out in the Binary Code license.

-----------------------------------------------------------------------
Required vs. Optional Files
-----------------------------------------------------------------------
Licensees must follow the terms of the Java 2 Runtime Environment 
license.  

The files that make up the Java 2 Runtime Environment are divided into 
two categories: required and optional.  Optional files may be excluded 
from redistributions of the Java 2 Runtime Environment at the 
licensee's discretion.  

The following sections contain lists of the files that may be 
optionally omitted from redistributions with the Java 2 Runtime 
Environment.  All files not in these lists of optional files must be 
included in redistributions of the runtime environment.

The Java 2 Runtime Environment includes the bin and lib directories 
which both must reside in the same directory, called <runtime-dir> in 
the lists below. All paths are relative to the <runtime-dir> directory.

Note that the native code C runtime library, msvcrt.dll, is located 
in the Windows system directory.  This file should be included in 
redistributions of the Win32 version of the Java 2 Runtime Environment.

-----------------------------------------------------------------------
Optional Files and Directories
-----------------------------------------------------------------------
All font properties files in the lib directory other than the default 
lib\font.properties file are optional, and vendors may choose not to 
include them in redistributions of the Java 2 Runtime Environment. In 
addition, the following may be optionally excluded from 
redistributions:

bin\beans.ocx		      
   Plugin ActiveX control
lib\jaws.jar		      
   Plugin classes
lib\i18n.jar                  
   Character conversion classes and all other locale support
lib\ext\                      
   Directory containing extension jar files
bin\rmid.exe
   Java RMI Activation System Daemon
bin\rmiregistry.exe
   Java Remote Object Registry
bin\tnameserv.exe
   Java IDL Name Server
bin\keytool.exe
   Key and Certificate Management Tool
bin\policytool.exe
   Policy File Creation and Management Tool


-----------------------------------------------------------------------
Redistribution of Java 2 SDK Files
-----------------------------------------------------------------------
The Java 2 SDK, Standard Edition, may not be redistributed. However, 
the limited set of files from the Win32 version of the SDK listed below 
may be included in vendor redistributions of the Java 2 Runtime 
Environment.  All paths are relative to the top-level directory of the 
SDK.

 - jre/lib/cmm/PYCC.pf
      Color-management profile. This file is required only if the 
      Java 2D API is used to perform color map conversions.

 - All .ttf font files in the jre/lib/fonts directory. Note that the 
   LucidaSansRegular.ttf font is already contained in the Java 2 
   Runtime Environment, so there is no need to bring that file over 
   from the SDK. 

 - jre/lib/audio/soundbank.gm
      This is a MIDI soundbank used by the software MIDI 
      synthesizer. MIDI synthesis is commmonly available in hardware 
      or as part of the operating system on modern Win32 systems.  
      On these systems, MIDI synthesis may be supported through 
      these native services rather than through use of the software 
      synthesizer, and the soundbank.gm file may not be required.  
      To reduce the size of the Runtime Environment's download 
      bundle, the soundbank has been removed from the Java 2 Runtime 
      Environment.  However, a soundbank file may be included in 
      redistributions of the Runtime Environment at the vendor's 
      discretion.  A minimal soundbank file (soundbank.gm) is 
      included with the SDK.  This minimal soundbank and a selection 
      of enhanced MIDI soundbanks are available from the Java Sound 
      web site:  http://java.sun.com/products/java-media/sound/
      Any of these soundbanks may be included in redistributions 
      of the Java 2 Runtime Environment.  These soundbanks also may 
      be installed to enable software MIDI synthesis in distributions 
      which did not include a soundbank, or to upgrade the quality of 
      software MIDI synthesis in distributions which included the 
      minimal soundbank file.
      

-----------------------------------------------------------------------
This product includes code licensed from RSA Data Security.

Copyright (c) 2001 Sun Microsystems(tm), Inc.
901 San Antonio Road, Palo Alto, CA 94303-4900
All rights reserved.

