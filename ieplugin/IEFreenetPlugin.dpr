library IEFreenetPlugin;
{
  This code is part of the freenet-IE plugin by Philipp Hug
  It is distributed under the GNU General Public Licence (GPL)
  version 2. See http://www.gnu.org/ for further details of the GPL.
}

{
  this is the dll project

  @author <a href="mailto:freenet@philipphug.ch">Philipp Hug</a>
  @author <a href="mailto:author2@universe">Author 2</a>
}

uses
  ComServ,
  FreenetProtcolImpl in 'FreenetProtcolImpl.pas',
  FreenetStuff in 'FreenetStuff.pas',
  FreenetThread in 'FreenetThread.pas';

{$R *.RES}

exports
  DllGetClassObject,
  DllCanUnloadNow,
  DllRegisterServer,
  DllUnregisterServer;

begin
end.
