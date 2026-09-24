param(
    [ValidateSet('client','server','test')][string]$Mode = 'client',
    [string]$Maven = 'mvn.cmd'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Get-Command $Maven -ErrorAction SilentlyContinue)) {
    throw 'Maven not found. Install Maven or pass -Maven with the full path to mvn.cmd.'
}
switch ($Mode) {
    'client' { & $Maven '-B' 'compile' 'javafx:run' }
    'server' { & $Maven '-B' 'compile' 'javafx:run' '-Djavafx.mainClass=vn.edu.ut.udm08.server.core.ServerApplication' }
    'test' { & $Maven '-B' 'test' }
}
exit $LASTEXITCODE
