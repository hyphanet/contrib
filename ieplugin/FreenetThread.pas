library FreenetProtocol;
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

interface

uses
  Classes,
  URLMon,
  wininet;

var
  RunningThreads: integer = 0;

type
  TFreenetThread = class(TThread)
  private
    FRequest: String;
    FOnline: Boolean;
    FFailed: Boolean;
    FDone: Boolean;
    FinURL: HINTERNET;
    FProtSink: IInternetProtocolSink;
    FID: Integer;
  protected
    procedure Execute; override;
  public
    constructor Create (ProtSink: IInternetProtocolSink); virtual;
    destructor Destroy; override;
    function Read (buf: PChar; bsize: cardinal; out bRead: cardinal): integer;
    property Request: String read FRequest write FRequest;
    property ID: Integer read FID write FID;
  end;

implementation
uses
  windows,
  sysutils,
  FreeNetStuff;
{ TFreenetThread }

constructor TFreenetThread.Create(ProtSink: IInternetProtocolSink);
begin
  FOnline := False;
  FDone := False;
  FFailed := False;
  FreeOnTerminate := False;
  inherited Create (True);
  FProtSink:= ProtSink;
end;

destructor TFreenetThread.Destroy;
begin
  inherited;
end;

procedure TFreenetThread.Execute;
var
  inSession: HINTERNET;
  Request: String;
  x: integer;
  wbuf: array [0..1000] of char;
const
  FHost = 'http://localhost:8081/';
begin
  inc (RunningThreads);
  FRequest := CleanUpURL (Frequest);
  Delete (FRequest, 1, 8);

  Request := FHost+FRequest;

  LogMessage ('Requesting :'+Request, FID);

  inSession := InternetOpen ('freenet',0, nil, nil, 0);
  if assigned (inSession) then
  begin
    StringToWideChar ('freenet',@wbuf, sizeof(wbuf));
    FProtSink.ReportProgress (BINDSTATUS_CONNECTING, @wbuf);
    FinURL := InternetOpenURL (inSession, PChar (Request), nil, 0,INTERNET_FLAG_DONT_CACHE, 0);
    if assigned (FinURL) then
    begin
//      FProtSink.ReportProgress (BINDSTATUS_MIMETYPEAVAILABLE, nil);
      LogMessage ('Requesting. OK we''re online. Done:'+ inttostr (ord(FDone)) + ' Failed:'+inttostr (ord(FFailed)) + ' Terminated:' +inttostr(ord(Terminated)), FID);
      x := 0;
      FProtSink.ReportData (BSCF_FIRSTDATANOTIFICATION or BSCF_AVAILABLEDATASIZEUNKNOWN, 0, 0);
      FOnline := True;
      repeat
        inc (x);
        LogMessage ('Downloading... Done:'+ inttostr (ord(FDone)) + ' Failed:'+inttostr (ord(FFailed)) + ' Terminated:' +inttostr(ord(Terminated)), FID);
        if not fDone then
          FProtSink.ReportData (BSCF_INTERMEDIATEDATANOTIFICATION or BSCF_AVAILABLEDATASIZEUNKNOWN, x, 0);
        if not fDone then
          Sleep(1000);
      until FDone or Terminated;
      FProtSink.ReportData (BSCF_LASTDATANOTIFICATION or BSCF_AVAILABLEDATASIZEUNKNOWN, x, 0);
      FProtSink.ReportData (BSCF_DATAFULLYAVAILABLE or BSCF_AVAILABLEDATASIZEUNKNOWN, x, 0);
      FProtSink.ReportResult(S_OK,0,nil);
      LogMessage ('Downloading finished... Done:'+ inttostr (ord(FDone)) + ' Failed:'+inttostr (ord(FFailed)) + ' Terminated:' +inttostr(ord(Terminated)), FID);
    end else
      FFailed := True;
  end else
    FFailed := True;
  if FFailed then
  begin
    LogMessage ('Request failed. Sorry.', FID);
    FProtSink.ReportResult(S_FALSE,0,nil);
  end;
  dec (RunningThreads);
end;

function TFreenetThread.Read(buf: PChar; bsize: cardinal;
  out bRead: cardinal): integer;
begin
  bRead := 0;
  Result := HRESULT (E_PENDING);
  if FOnline and not FFailed then
  begin
    if InternetReadFile (FinURL, buf, bsize, bRead) then
    begin
      if bRead = 0 then
      begin
        Result := S_FALSE;
        FDone := True;
      end else
      begin
        Result := S_OK;
      end;
    end else
    begin
      FFailed := True;
    end;
  end;
  if FFailed then
  begin
    LogMessage ('TFreenetThread.Read: Request failed. Sorry.', FID);
    FDone := True;
    Result := HRESULT (INET_E_DOWNLOAD_FAILURE);
  end;
end;

end.
