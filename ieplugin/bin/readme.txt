freenet-URL-handler for IE
--------------------------

the following bugs/limitations are known:
->fproxy must be running on localhost on port 8081
->downloads do not work
->only get function does work (no insert)

special features
->supports ie security manager (like https)
  ie warns if there are insecure items (eg. images on a web server) on a freenet page

fixed bugs
->only html can be downloaded
->size/file is limited to 64 kb
->ie is locked up during request
->freenet-url starts with freenet:// instead of freenet:
->multiple browsers work now. log file could not be opened the second time

installation

1) adapt ie path in FreenetPlugin.reg
2) doubleclick on FreenetPlugin.reg
3) start "regsvr32 FreenetProtocol.dll" (without the "s)
4) launch ie and enter "freenet:"

version: alpha 0.05

philipp hug (aka codeshark)
freenet@hug.cx