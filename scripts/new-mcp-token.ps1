param(
    [Parameter(Mandatory = $true)] [ValidateNotNullOrEmpty()] [string]$Username,
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')] [string]$ClientId = 'laptop',
    [ValidateRange(1,12)] [int]$Hours = 12,
    [string[]]$Hosts = @('localhost','127.0.0.1'),
    [switch]$AllowWrite
)

$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.mcp-local'))
New-Item -ItemType Directory -Force -Path $destination | Out-Null
if ($env:OS -eq 'Windows_NT') {
    # Build only a DACL; copying Get-Acl can also try to write the SACL/owner,
    # which requires SeSecurityPrivilege on ordinary Windows accounts.
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true,$false)
    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($account,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.SetAccessRule($rule)
    $directory = New-Object IO.DirectoryInfo($destination)
    if ($PSVersionTable.PSEdition -eq 'Core') {
        [IO.FileSystemAclExtensions]::SetAccessControl($directory,$acl)
    } else {
        $directory.SetAccessControl($acl)
    }
}
$tokenFile = Join-Path $destination ($ClientId + '.token')
$serverFile = Join-Path $destination ($ClientId + '.yml')
if ((Test-Path -LiteralPath $tokenFile) -or (Test-Path -LiteralPath $serverFile)) {
    throw 'Files for this ClientId already exist. Use a new ClientId; existing credentials are not overwritten.'
}
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$token = [Convert]::ToBase64String($bytes).Replace('+','-').Replace('/','_').TrimEnd('=')
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($token))).Replace('-','').ToLowerInvariant() }
finally { $sha.Dispose() }
$expires = [DateTimeOffset]::UtcNow.AddHours($Hours).ToString('o')
$config = @('doneit:','  mcp:','    enabled: true','    allowed-hosts:')
foreach ($hostName in $Hosts) {
    if ([string]::IsNullOrWhiteSpace($hostName)) { throw 'Hosts cannot contain an empty value' }
    $config += '      - ' + ($hostName | ConvertTo-Json -Compress)
}
$config += @('    tokens:',
    ('      - id: ' + ($ClientId | ConvertTo-Json -Compress)),
    ('        username: ' + ($Username | ConvertTo-Json -Compress)),
    ('        sha256: ' + ($hash | ConvertTo-Json -Compress)),
    ('        write: ' + $AllowWrite.IsPresent.ToString().ToLowerInvariant()),
    ('        expires-at: ' + ($expires | ConvertTo-Json -Compress)))
$token | Set-Content -Encoding UTF8 -LiteralPath $tokenFile
$config | Set-Content -Encoding UTF8 -LiteralPath $serverFile
Write-Output "Client token file (keep on laptop): $tokenFile"
Write-Output "Server config (contains only hash): $serverFile"
Write-Output "Expires (UTC): $expires"
