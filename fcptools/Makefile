
#
# Makefile for Linux and Cygwin versions of the Freenet FCP tools and library
#
# Written May 2001
# David McNab, david@rebirthing.co.nz, http://freeweb.sf.new
#

INSTDIR=/usr/local/bin

default:
	@echo "Target must be one of:"
	@echo " linux    Builds linux version"
	@echo " cygwin   Builds cygwin version"
	@echo " clean    Cleans out all objects and binaries"
	@echo " install  puts binaries into $(INSTDIR)"
	@echo "          (requires root access)"

linux:
	cd ezFCPlib; make ezFCPlib.a
	cd fwproxy; make linux
	cd fcpget; make linux
	cd fcpput; make linux
	cd fcpputsite; make linux
	cd fcpputsplit; make linux
	cd fcpdiag; make linux
	cd fcpproxy; make linux

cygwin:
	cd ezFCPlib; make cygwin
	cd fwproxy; make cygwin
	cd fcpget; make cygwin
	cd fcpput; make cygwin
	cd fcpputsite; make cygwin
	cd fcpputsplit; make cygwin
	cd fcpdiag; make cygwin
	cd fcpproxy; make cygwin

clean:
	cd ezFCPlib; make clean
	cd fwproxy; make clean
	cd fcpget; make clean
	cd fcpput; make clean
	cd fcpputsite; make clean
	cd fcpputsplit; make clean
	cd fcpdiag; make clean
	cd fcpproxy; make clean

install:
	cd ezFCPlib; make linux 
	cd fwproxy; make linux; cp -f fwproxy $(INSTDIR)
	cd fcpget; make linux; cp -f fcpget $(INSTDIR)
	cd fcpput; make linux; cp -f fcpput $(INSTDIR)
	cd fcpputsite; make linux; cp -f fcpputsite $(INSTDIR)
	cd fcpputsplit; make linux; cp -f fcpputsplit $(INSTDIR)
	cd fcpdiag; make linux; cp -f fcpdiag $(INSTDIR)
	cd fcpproxy; make linux; cp -f fcpproxy $(INSTDIR)

install-cygwin:
	cd ezFCPlib; make cygwin 
	cd fwproxy; make cygwin; cp -f fwproxy.exe $(INSTDIR)
	cd fcpget; make cygwin; cp -f fcpget.exe $(INSTDIR)
	cd fcpput; make cygwin; cp -f fcpput.exe $(INSTDIR)
	cd fcpputsite; make cygwin; cp -f fcpputsite.exe $(INSTDIR)
	cd fcpputsplit; make cygwin; cp -f fcpputsplit.exe $(INSTDIR)
	cd fcpdiag; make cygwin; cp -f fcpdiag.exe $(INSTDIR)
	cd fcpproxy; make cygwin; cp -f fcpproxy.exe $(INSTDIR)

# this is temporary stuff

ptest: ptest.c ezFCPlib
	gcc -g -o ptest ptest.c ezFCPlib/ezFCPlib.a

ezFCPlib:
	cd ezFCPlib; make linux


