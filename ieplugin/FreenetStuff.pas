unit FreenetStuff;
{
  This code is part of the freenet-IE plugin by Philipp Hug
  It is distributed under the GNU General Public Licence (GPL)
  version 2. See http://www.gnu.org/ for further details of the GPL.
}

{
  this unit contains some routines needed by the plugin

  @author <a href="mailto:freenet@philipphug.cx">Philipp Hug</a>
  @author <a href="mailto:author2@universe">Author 2</a>
}

interface

function HTTPDecode(const AStr: String): String;
function CleanUpURL (s: String): String;
procedure LogMessage(Msg : String; ID: Integer = 0);

const
  fnet = 'freenet:';

implementation
uses
  windows,
  sysutils,
  wininet;

var
  LogFile : Text;
  LogFileName: ShortString;

function CleanUpURL (s: String): String;
begin
  Result := s;
  if copy (Result, 1,length (fnet)) <> fnet then
    Result := 'freenet:'+s;

  while copy (Result,length (fnet)+1,1)='/' do
    Delete (Result,length (fnet)+1,1);
  while copy (Result,length (Result),1)='/' do
    Delete (Result,length (Result),1);
end;

{ HTTPDecode is copied from HTTPApp unit to keep DLL file size small. }
function HTTPDecode(const AStr: String): String;
var
  Sp, Rp, Cp: PChar;
begin
  SetLength(Result, Length(AStr));
  Sp := PChar(AStr);
  Rp := PChar(Result);
  while Sp^ <> #0 do
  begin
    if not (Sp^ in ['+','%']) then
      Rp^ := Sp^
    else
      if Sp^ = '+' then
        Rp^ := ' '
      else
      begin
        inc(Sp);
        if Sp^ = '%' then
          Rp^ := '%'
        else
        begin
          Cp := Sp;
          Inc(Sp);
          Rp^ := Chr(StrToInt(Format('$%s%s',[Cp^, Sp^])));
        end;
      end;
    Inc(Rp);
    Inc(Sp);
  end;
  SetLength(Result, Rp - PChar(Result));
end;


procedure LogMessage(Msg : String; ID: Integer = 0);
begin
  WriteLn(LogFile,DateTimeToStr(Now),' [',ID,'-',GetCurrentThreadID,']: ',Msg,'.');
  Flush(LogFile);
end;

initialization
  LogFileName := 'c:\IEFreeNetPlugin.'+inttostr(GetCurrentProcessID)+'.log';
  Assign(LogFile,LogFileName);
  Rewrite(LogFile);
  LogMessage('Starting');
finalization
  LogMessage('Exiting');
  Close(LogFile);
  DeleteFile (LogFileName);
end.

