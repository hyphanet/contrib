README - Freenet 0.3.9

IMPORTANT WARNING
-----------------

This is a beta release of the Freenet code.  This means that while it
will work most of the time, it probably won't work all of the time.  
See the section "reporting bugs" below for more information.


SYSTEM REQUIREMENTS
-------------------

This release should run on any operating system that has Java version 1.1
or higher.  The following Java implementations should work:

  * Any Sun JRE (Java Runtime Environment) or JDK (also known by these
    names: Java SDK, J2SE), version 1.1 or higher.

You do need Java, even the installation will fail if you don't have it installed as it is 
used for the Freenet configuration on install. If you don't have it, grab it from
http://jsp2.java.sun.com/j2se/1.3/jre/download-windows.html and reinstall.

Memory requirements are modest (for Java).  You will need at least a few hundred megabytes 
of disk storage for Freenet data storage (the more the better).


BINARY INSTALLATION
-------------------

Note that Java must have been installed prior to Freenet being installed, otherwise
the configuration of Freenet will break.

CONFIGURATION
-------------

* To change the configuration of the node use "Configure" in the system tray utility.

  NOTE: Do not change the node port once you installed Freenet, all the documents on your HD
  won't be found and your node won't be known by others anymore.
  If you do change it, you'll have to modify FLaunch.ini (the finsert/frequest entries) 
  to use the new port if you want to be able to use the console clients

* Browse through freenet.ini if you like to modify .ini files manually

* For help on configuration and usage you can send a mail to: support@freenetproject.org


USAGE
-----

* Start freenet.exe to start the node. The system tray icon should appear and the node
  is running then. If you suspect it not to work properly check the Troubleshooting FAQ 
  below.
  It will also start FProxy, a web proxy that allows to use a normal browser to retrieve &
  insert documents in Freenet. See FProxy.txt for more information about how to use FProxy.

* Type 'finsert' or 'frequest' in a DOS console to get usage information for the console 
  client.

* Check freenet.log for any error messages once the server is running.


KEY INDEX
---------

Because searching is not yet implemented, or even designed, there are
key index servers where you can register keys that exist on Freenet.

A list of known public key index servers is available at
http://www.thalassocracy.org/keyindex/. Another work in progress for an web and In-Freenet index is http://freegle.com.


FOR MORE INFORMATION
--------------------

Freenet web site: <http://freenetproject.org/>.
Freenet FAQ:      <http://freenetproject.org/index.php?page=faq>.
Please read the FAQ before posting any questions to the mailing lists.


REPORTING BUGS
--------------

Feedback from users is an essential part of the open source model of
software development.  If you would like to report a bug, send a mail to 
<Freenet-dev@lists.sourceforge.net>

---------------------------------------------------------------------------

SOURCE DISTRIBUTION
-------------------

To build the software, first get a current checkout of the code base. On
the Freenet website 'cvs snapshots' are provided, just download and
extract the newest one and you should be set. Ofcourse you can also
checkout the cvs tree manually.

  Go into the Freenet\scripts directory and type "build".  Type "install"
  to install Freenet.

-----------------------------------------------------------------------

Credits for the Win32 version
=============================

 - Nullsoft.com for providing the free installer
 - Lee Benjamin Burhans (LBurhans@hotmail.com) for providing 
   the original Javafind utility
 - Tim Buchalka <buchalka@hotmail.com> for improving JavaFind
 - Sebastian@SSpaeth.de for putting all the crap together and make it work 
 - All the people that I forced to install it over and over again to 
   test it and make it work
 - Philipp Hug for writing the IE plugin and providing useful installer tools