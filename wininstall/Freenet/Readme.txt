How to prepare a self installing package of "Freenet for Windows"

1)Get the free Nullsoft installer (NSIS) at http://nullsoft.com/free/NSIS 
  (I won't provide it here for disc space reasons)
2)Use a hex editor to patch makensis.exe so that &Startmenu points to 
  "Programs" instead of "Startmenu". Otherwise I could add shortcuts in the Programs 
  and Autostart sections
  (Alternatively change the source and rebuild it)
  (sorry, more detailed description for this step will be inserted here)
3)Create a subdirectory of the NSIS directpry called Freenet in which you put all the files 
  that usually belong in a 
  Win binary snapshot.
5)Copy the Javasearch.exe in the Freenet directory as well
4)Call makensis freenet

