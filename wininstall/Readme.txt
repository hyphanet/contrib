How to prepare a self installing package of "Freenet for Windows"

1)Get the free Nullsoft installer (NSIS) at http://nullsoft.com/free/NSIS 
  (I won't provide it here for disc space reasons)
2)Use a hex editor to patch makensis.exe so that &Startmenu points to the correct 
  registry entry which contains "Programs" instead of "Startmenu". Otherwise I could not 
  add shortcuts in the Programs and Autostart sections
  (Alternatively change the source and rebuild it)
  (sorry, more detailed description for this step will be inserted here)
3)Take the subdirectory of the NSIS directory called Freenet and add the current freenet.jar.
4)Call makensis freenet

