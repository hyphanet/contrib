Freenet plugin for Microsoft Internet Explorer
----------------------------------------------

Version: 0.06
Any questions? mailto:freenet@philipphug.ch

Installation
============
Automatic installation
Included in freenet-installer

Manual installation
1) adapt ie path in FreenetPlugin.reg
2) doubleclick on FreenetPlugin.reg
3) start "regsvr32 FreenetProtocol.dll" (without the "s)
4) launch ie and enter "freenet:"

Features
========
1)allows read-only access to freenet
2)supports ie security manager (like https)
  ie warns if there are insecure items (eg. images on a we server) within a freenet page
3)plugin reads fproxy port from freenet.ini

Known Bugs
==========
1)downloads limited to text and html
2)uploads do not work
the following bugs/limitations are known:
->fproxy must be running on localhost on port 8081
->downloads do not work
->only get function does work (no insert)
