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
  Windows,
  ComObj,
  ComServ,
  URLMon,
  FreenetThread;

const
  CLSID_FreeNetProtocol : TGUID = '{CDDCA3BE-697E-4BEB-BCE4-5650C1580BCE}';

Type
  TFreeNetProtocol = Class(TComObject,IInternetProtocol, IInternetProtocolInfo)
    fnt: TFreeNetThread;
    FID: Integer;
    destructor Destroy; override;
    procedure Initialize; override;
    function ObjQueryInterface(Const IID : TGUID; out Obj) : HResult; override;

    { IInternetProtocolRoot }
    function Start(szUrl : LPCWStr; OIProtSink : IInternetProtocolSink;
                   OIBindInfo : IInternetBindInfo;
                   grfPI,dwReserved : DWord) : HResult; StdCall;
    function Continue(Const ProtocolData : TProtocolData) : HResult; StdCall;
    function Abort(hrReason : HResult; dwOptions : DWord) : HResult; StdCall;
    function Terminate(dwOptions : DWord) : HResult; StdCall;
    function Suspend : HResult; StdCall;
    function Resume : HResult; StdCall;
    { IInternetProtocol }
    function Read(pv : Pointer; cb : ULONG; out cbRead : ULONG) : HResult; StdCall;
    function Seek(dlibMove : Large_Integer; dwOrigin : DWord;
                  out libNewPosition : ULarge_Integer) : HResult; StdCall;
    function LockRequest(dwOptions : DWord) : HResult; StdCall;
    function UnlockRequest : HResult; StdCall;
    {IInternetProtocolInfo}
    function ParseUrl(pwzUrl: LPCWSTR; ParseAction: TParseAction; dwParseFlags: DWORD;
      pwzResult: LPWSTR; cchResult: DWORD; pcchResult: DWORD;
      dwReserved: DWORD): HResult; stdcall;
    function CombineUrl(pwzBaseUrl, pwzRelativeUrl: LPCWSTR; dwCombineFlags: DWORD;
      pwzResult: LPWSTR; cchResult: DWORD; out pcchResult: DWORD;
      dwReserved: DWORD): HResult; stdcall;
    function CompareUrl(pwzUrl1, pwzUrl2: LPCWSTR; dwCompareFlags: DWORD): HResult; stdcall;
    function QueryInfo(pwzUrl: LPCWSTR; QueryOption: TQueryOption; dwQueryFlags: DWORD;
      pBuffer: Pointer; cbBuffer: DWORD; var cbBuf: DWORD; dwReserved: DWORD): HResult; stdcall;

  end;

implementation

uses
  sysUtils,
  classes,
  FreenetStuff;

var
  NumInstances: Integer = 0;

function TFreeNetProtocol.ObjQueryInterface(Const IID : TGUID; out Obj) : HResult;
var S : String;
begin
  if GetInterface(IID,Obj) Then begin
    S := 'OK';
    Result := S_OK;
  end else
  begin
    S := 'Not implemented';
    Result := E_NOINTERFACE;
  end;
end;

function TFreeNetProtocol.Start(szUrl : LPCWStr;
                                       OIProtSink : IInternetProtocolSink;
                                       OIBindInfo : IInternetBindInfo;
                                       grfPI,dwReserved : DWord) : HREsult;
var sReq : String;
begin
  sReq := WideCharToString(szURL);
  LogMessage('Start URL="'+sReq+'" grPI='+IntToStr(grfPI),FID);
  Delete(sReq,1,length (fnet)); { freenet: }
  sReq := HTTPDecode (sReq);
  LogMessage('Start Request Thread URI="'+sReq+'"',FID);
  fnt := TFreenetThread.Create (OIProtSink);
  fnt.Request := sReq;
  fnt.ID := FID;
  fnt.Resume;
  Result := HResult (E_PENDING);
end;

function TFreeNetProtocol.Continue(Const ProtocolData : TProtocolData) : HResult;
begin
  LogMessage('Continue',FID);
  Result := INET_E_INVALID_REQUEST;
end;

function TFreeNetProtocol.Abort(hrReason : HResult; dwOptions : DWord) : HResult;
begin
  LogMessage('Abort',FID);
  if assigned (fnt) then
  begin
    fnt.Terminate;
  end;
  Result := HRESULT (E_PENDING);
end;

function TFreeNetProtocol.Terminate(dwOptions : DWord) : HResult;
begin
  LogMessage('Terminate',FID);
  if assigned (fnt) then
  begin
    fnt.Terminate;
    fnt.WaitFor;
    fnt.Free;
    fnt := nil;
  end;
  Result := S_OK;
end;

function TFreeNetProtocol.Suspend : HResult;
begin
  LogMessage('Suspend',FID);
  Result := INET_E_INVALID_REQUEST;
end;

function TFreeNetProtocol.Resume : HResult;
begin
  LogMessage('Resume',FID);
  Result := INET_E_INVALID_REQUEST;
end;

function TFreeNetProtocol.Read(pv : Pointer; cb : ULONG; out cbRead : ULONG) : HResult;
var
  s: String;
begin
  LogMessage('Read',FID);
  Result := HResult (fnt.Read (pv,cb,cbRead));
  case Result of
    HRESULT(E_PENDING): s := 'E_PENDING';
    HRESULT(S_OK): s := 'S_OK';
    HRESULT(S_FALSE): s := 'S_FALSE';
    HRESULT(INET_E_DOWNLOAD_FAILURE): s := 'INET_E_DOWNLOAD_FAILURE';
    else s := inttostr (Result);
  end;

  LogMessage('Read ended. Returned:'+s+' '+inttostr(cbRead)+ ' bytes read',FID);
end;

function TFreeNetProtocol.Seek(dlibMove : Large_Integer; dwOrigin : DWord;
                                      out libNewPosition : ULarge_Integer) : HResult;
begin
  LogMessage('Seek',FID);
  Result := E_FAIL;
end;

function TFreeNetProtocol.LockRequest(dwOptions : DWord) : HResult;
begin
  LogMessage('LockRequest',FID);
  Result := S_OK;
end;

function TFreeNetProtocol.UnlockRequest : HResult; StdCall;
begin
  LogMessage('UnlockRequest',FID);
  Result := S_OK;
end;

function TFreeNetProtocol.CombineUrl(pwzBaseUrl, pwzRelativeUrl: LPCWSTR;
  dwCombineFlags: DWORD; pwzResult: LPWSTR; cchResult: DWORD;
  out pcchResult: DWORD; dwReserved: DWORD): HResult;
var
  sBase, sRel, sResult: String;
const
  httproot='http:';
begin
  sBase:= CleanUpURL (WideCharToString(pwzBaseUrl));
  sRel := WideCharToString(pwzRelativeUrl);
  LogMessage('CombineURL: Base:'+sBase+' Rel:'+sRel,FID);
  if copy (sRel,1,length (httproot))=httproot then
  begin
    sResult := sRel;
  end else
  begin
    while (length (sBase)>0) and (copy (sBase,length (sBase),1)<>'/') do sBase := Copy (sBase, 1, length (sBase)-1);
    if copy (sRel,1,2)='./' then
      sRel := copy (sRel, 3, length(sRel));

    if copy (sRel,1,1)='/' then
    begin
      sResult := CleanupURL('freenet:'+sRel)
    end else
    begin
      sResult := CleanUpURL (sBase + sRel);
    end;
  end;
  LogMessage('CombineURL.end: Result:'+sResult,FID);

  StringToWideChar (sResult, pwzResult, cchResult);
  pcchResult := 2*length (sResult)+1;

  Result := S_OK;
  LogMessage('CombineURL.end',FID);
end;

function TFreeNetProtocol.CompareUrl(pwzUrl1, pwzUrl2: LPCWSTR;
  dwCompareFlags: DWORD): HResult;
var
  sURL1, sURL2: String;
begin
  sURL1:= WideCharToString(pwzUrl1);
  sURL2:= WideCharToString(pwzUrl2);
  LogMessage('CompareUrl: URL1:'+sURL1+' URL2:'+sURL2,FID);

  if CleanUpURL (sURL1) = CleanUpURL (sURL2) then
    Result := S_OK
  else
    Result := S_FALSE;
end;

function TFreeNetProtocol.ParseUrl(pwzUrl: LPCWSTR;
  ParseAction: TParseAction; dwParseFlags: DWORD; pwzResult: LPWSTR;
  cchResult, pcchResult, dwReserved: DWORD): HResult;
var
  sURL, sResult: String;
begin
  sURL := WideCharToString (pwzUrl);
  LogMessage('ParseUrl: ParseAction:'+inttostr (ParseAction)+ ' URL:'+sURL,FID);

  Result := S_OK;
  case ParseAction of
    PARSE_CANONICALIZE:
    begin
      sResult := sURL;
    end;
    PARSE_SECURITY_URL:
    begin
      sResult := sURL;
    end;
    PARSE_SECURITY_DOMAIN:
    begin
      sResult := sURL;
    end;
    PARSE_ENCODE:
    begin
      sResult := CleanUpURL (sURL);
    end;
  else
    Result := INET_E_DEFAULT_ACTION;
  end;
  if Result = S_OK then
  begin
    LogMessage('ParseUrl.end: Result:'+sResult,FID);
    StringToWideChar (sResult, pwzResult, cchResult);
    LogMessage('ParseUrl.end: PInt:'+inttostr (pcchResult),FID);
    PInteger (pcchResult)^ := 2*length (sResult)+1;
  end;
  LogMessage('ParseUrl.end',FID);
end;

function TFreeNetProtocol.QueryInfo(pwzUrl: LPCWSTR;
  QueryOption: TQueryOption; dwQueryFlags: DWORD; pBuffer: Pointer;
  cbBuffer: DWORD; var cbBuf: DWORD; dwReserved: DWORD): HResult;
begin
  LogMessage('QueryInfo QueryOption:'+inttostr(ord(QueryOption)),FID);

  Result := S_OK;
  case QueryOption of
    QUERY_USES_NETWORK:
    begin
      PInteger(pBuffer)^:=1;
      cbBuf := 4;
    end;
    13: // QUERY_IS_SECURE
    begin
      PInteger(pBuffer)^:=1;
      cbBuf := 4;
    end;
    99:;
    else
    Result := INET_E_DEFAULT_ACTION;
  end;
end;

destructor TFreeNetProtocol.Destroy;
begin
  dec (NumInstances);
  LogMessage('Destroy. '+inttostr (NumInstances)+' FIP instances and '+inttostr(RunningThreads)+' dl threads left.',FID);
  inherited;
end;

procedure TFreeNetProtocol.Initialize;
begin
  inc (NumInstances);
  FID := random (100000);
  inherited;
  LogMessage('Initialize',FID);
end;

Initialization
  randomize;
  TComObjectFactory.Create(ComServer,TFreeNetProtocol,
                           CLSID_FreeNetProtocol,
                           '',
                           'freenet: Asychronous Pluggable Protocol Handler',
                           ciMultiInstance,tmApartment);
end.
