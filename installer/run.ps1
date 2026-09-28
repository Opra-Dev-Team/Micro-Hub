$ErrorActionPreference = 'Stop'
$zip = Join-Path $env:TEMP 'opies-plugin-hut.zip'
$parent = Join-Path $env:TEMP 'opies-plugin-hut'
$uri = 'https://github.com/opesoid/opies-plugin-hut/archive/refs/heads/dev.zip'
Invoke-WebRequest -Uri $uri -OutFile $zip -UseBasicParsing
if (Test-Path -LiteralPath $parent) {
    Get-ChildItem -LiteralPath $parent -Force -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$dest = Join-Path $parent ([guid]::NewGuid().ToString('n'))
New-Item -ItemType Directory -Path $dest -Force | Out-Null
Expand-Archive -Path $zip -DestinationPath $dest -Force
$root = Get-ChildItem -LiteralPath $dest -Directory | Select-Object -First 1
if ($null -eq $root) {
    throw 'Could not unpack the plugin hut.'
}
$setup = Join-Path $root.FullName 'installer\OpiesPluginLibrary-Setup.vbs'
if (-not (Test-Path -LiteralPath $setup)) {
    throw "Missing setup file: $setup"
}
Start-Process -FilePath 'wscript.exe' -ArgumentList @('//nologo', $setup)
