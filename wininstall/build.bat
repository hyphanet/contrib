@Echo Off

Echo - cleaning local
del license.txt
del freenet-webinstall.exe
del freenet-java-webinstall.exe
del freenet-install.exe

Echo Building Freenet for Windows installers
Echo - set up license.txt by combining the various license docs
copy "Freenet License.txt" + SunBCLA.txt + div.txt + GNU.txt License.txt


Echo - build freenet-webinstall.exe
makensis freenet-modern.nsi


Echo - finishing up
REM - lower casing the filename
rename freenet-Webinstall.exe t.exe
rename t.exe freenet-webinstall.exe