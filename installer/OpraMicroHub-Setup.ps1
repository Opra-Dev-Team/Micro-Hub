# Micro Hub setup window.
# Toggles install or remove plugins. Outdated installed jars update automatically.

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ErrorActionPreference = 'Stop'

try {
    [System.Windows.Forms.Application]::SetCompatibleTextRenderingDefault($false)
} catch {
}
[System.Windows.Forms.Application]::EnableVisualStyles()

if (-not ([System.Management.Automation.PSTypeName]'MicroHubWindow').Type) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class MicroHubWindow {
    [DllImport("kernel32.dll")]
    public static extern IntPtr GetConsoleWindow();
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$DistDir = Join-Path $RepoRoot 'dist'
$LibraryFile = Join-Path $DistDir 'library.txt'

$script:PluginDirs = @(
    (Join-Path $env:USERPROFILE '.runelite\microbot-plugins'),
    (Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins')
)
$script:InstallDir = $script:PluginDirs[0]
$script:SkipClientCheck = $false

$script:ColorBg = [System.Drawing.Color]::FromArgb(12, 12, 12)
$script:ColorCard = [System.Drawing.Color]::FromArgb(22, 22, 22)
$script:ColorCardEdge = [System.Drawing.Color]::FromArgb(46, 46, 46)
$script:ColorText = [System.Drawing.Color]::FromArgb(232, 232, 232)
$script:ColorMuted = [System.Drawing.Color]::FromArgb(154, 154, 154)
$script:ColorAccent = [System.Drawing.Color]::FromArgb(255, 255, 0)
$script:ColorAccentDark = [System.Drawing.Color]::FromArgb(214, 214, 0)
$script:ColorInstalled = [System.Drawing.Color]::FromArgb(52, 211, 153)
$script:ColorWarning = [System.Drawing.Color]::FromArgb(255, 255, 0)
$script:ColorLog = [System.Drawing.Color]::FromArgb(8, 8, 8)
$script:ColorTrackOff = [System.Drawing.Color]::FromArgb(58, 58, 58)
$script:TitleFontCollection = $null
$script:UpdateWaitNoted = $false

$script:HashCache = @{}
$script:SourceMetaCache = @{}
$script:JarVersionCache = @{}
$script:ShortcutFileName = 'Micro Hub.lnk'
$script:LauncherDir = Join-Path $env:LOCALAPPDATA 'OpraMicroHub'
$script:UpdateNoticeShown = $false
$script:PendingUpdateApplied = $false
$script:PendingToggles = @{}
$script:RunScriptUri = 'https://raw.githubusercontent.com/Opra-Dev-Team/Micro-Hub/dev/installer/run.ps1'
$script:TeamName = 'Opra Dev Team'
$script:TeamGitHubUri = 'https://github.com/Opra-Dev-Team'
$script:MicrobotDownloadUri = 'https://microbot.cloud/'
$script:LegacyJars = @{
    'BankSorterPlugin.jar' = 'OpiesBankSorterPlugin.jar'
    'SandBuyerPlugin.jar' = 'OpiesSandBuyerPlugin.jar'
    'MoltenGlassPlugin.jar' = 'OpiesMoltenGlassPlugin.jar'
    'EclipseRedPlugin.jar' = 'OpiesEclipseRedPlugin.jar'
    'FlaxPickerPlugin.jar' = 'OpiesFlaxPickerPlugin.jar'
    'OpraMotherlodePlugin.jar' = 'MotherloadMinePlugin.jar'
}

$script:PluginLabels = @{
    'OpraHouseThievingPlugin.jar' = 'House Thieving'
    'OpraMotherlodePlugin.jar' = 'Motherlode Mine'
    'OpraBlastFurnacePlugin.jar' = 'Blast Furnace'
}

$script:PluginBlurbs = @{
    'BankSorterPlugin.jar' = 'Sorts the bank into an iron 8-tab layout.'
    'SandBuyerPlugin.jar' = 'Buys sand and soda ash in Catherby, then hops.'
    'EclipseRedPlugin.jar' = 'Collects Eclipse red at the Hunter Guild.'
    'MoltenGlassPlugin.jar' = 'Smelts molten glass at the Edgeville furnace.'
    'FlaxPickerPlugin.jar' = 'Picks flax at Nemus Retreat and can spin it.'
    'OpraMotherlodePlugin.jar' = 'Mines paydirt in the Motherlode Mine.'
    'OpraHouseThievingPlugin.jar' = 'Pickpockets wealthy citizens and thieves houses in Varlamore.'
    'OpraBlastFurnacePlugin.jar' = 'Smelts bars at the Blast Furnace.'
    'SweetcornChefPlugin.jar' = 'Picks, cooks, and drops sweetcorn at Kastori.'
}

function Get-LibraryJarNames {
    if (-not (Test-Path -LiteralPath $LibraryFile)) {
        return @()
    }
    $names = @()
    foreach ($line in (Get-Content -LiteralPath $LibraryFile)) {
        $name = $line.Trim()
        if (-not $name -or $name.StartsWith('#')) { continue }
        if ($name -match '[\\/]' -or $name.Contains('..')) {
            throw "library.txt has an unsafe jar name: $name"
        }
        $names += $name
    }
    return @($names | Select-Object -Unique)
}

function Get-PluginLabel([string] $jarName) {
    if ($script:PluginLabels.ContainsKey($jarName)) {
        return $script:PluginLabels[$jarName]
    }
    $name = [System.IO.Path]::GetFileNameWithoutExtension($jarName)
    if ($name.StartsWith('Opies')) { $name = $name.Substring(5) }
    if ($name.StartsWith('Opra')) { $name = $name.Substring(4) }
    if ($name.EndsWith('Plugin')) { $name = $name.Substring(0, $name.Length - 6) }
    $spaced = [regex]::Replace($name, '(?<!^)([A-Z])', ' $1')
    return $spaced.Trim()
}

function Test-ClientRunning {
    if ($script:SkipClientCheck) { return $false }
    $names = @('RuneLite', 'Microbot')
    if ($null -ne (Get-Process -Name $names -ErrorAction SilentlyContinue | Select-Object -First 1)) {
        return $true
    }
    $procs = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" -ErrorAction SilentlyContinue)
    foreach ($proc in $procs) {
        $cmd = [string] $proc.CommandLine
        if ($cmd -match 'microbot-' -and $cmd -match '\.jar') {
            return $true
        }
    }
    return $false
}

function Get-InstalledCopy([string] $jarName) {
    $found = New-Object System.Collections.Generic.List[object]
    foreach ($path in (Get-InstalledJarPaths $jarName)) {
        [void] $found.Add((Get-Item -LiteralPath $path))
    }
    Write-Output -NoEnumerate $found
}

function Get-InstalledJarPaths([string] $jarName) {
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($jarName)
    $paths = New-Object System.Collections.Generic.List[string]
    foreach ($dir in $script:PluginDirs) {
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        foreach ($file in @(Get-ChildItem -LiteralPath $dir -File -Filter ($stem + '*.jar') -ErrorAction SilentlyContinue)) {
            if ($file.Name -eq $jarName -or $file.Name.StartsWith($stem + '-')) {
                [void] $paths.Add($file.FullName)
            }
        }
    }
    Write-Output -NoEnumerate $paths
}

function Get-CachedFileHash([string] $path) {
    $item = Get-Item -LiteralPath $path
    $key = '{0}|{1}|{2}' -f $item.FullName, $item.Length, $item.LastWriteTimeUtc.Ticks
    if ($script:HashCache.ContainsKey($key)) {
        return $script:HashCache[$key]
    }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    $script:HashCache[$key] = $hash
    return $hash
}

function Get-PluginSourceMeta([string] $jarName) {
    if ($script:SourceMetaCache.ContainsKey($jarName)) {
        return $script:SourceMetaCache[$jarName]
    }
    $meta = @{
        Version = $null
        MinClient = $null
    }
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($jarName)
    $srcRoot = Join-Path $RepoRoot 'src'
    if (Test-Path -LiteralPath $srcRoot) {
        $file = Get-ChildItem -LiteralPath $srcRoot -Filter ($stem + '.java') -Recurse -File -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $file) {
            $text = Get-Content -LiteralPath $file.FullName -Raw
            if ($text -match 'public static final String version = "([^"]+)"') {
                $meta.Version = $Matches[1]
            }
            if ($text -match 'minClientVersion = "([^"]+)"') {
                $meta.MinClient = $Matches[1]
            }
        }
    }
    $script:SourceMetaCache[$jarName] = $meta
    return $meta
}

function Get-JarPluginVersion([string] $jarPath, [string] $jarName) {
    if (-not $jarPath -or -not (Test-Path -LiteralPath $jarPath)) { return $null }
    $minClient = (Get-PluginSourceMeta $jarName).MinClient
    foreach ($value in @(Get-JarVersionStrings $jarPath)) {
        $text = [string] $value
        if ($minClient -and ($text -eq $minClient)) { continue }
        return $text
    }
    return $null
}

function Get-AvailablePluginVersion([string] $jarName) {
    $source = Join-Path $DistDir $jarName
    return Get-JarPluginVersion $source $jarName
}

function Get-JarVersionStrings([string] $jarPath) {
    $item = Get-Item -LiteralPath $jarPath
    $key = '{0}|{1}|{2}' -f $item.FullName, $item.Length, $item.LastWriteTimeUtc.Ticks
    if ($script:JarVersionCache.ContainsKey($key)) {
        return @($script:JarVersionCache[$key])
    }
    $found = New-Object System.Collections.Generic.List[string]
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        try {
            $encoding = [System.Text.Encoding]::GetEncoding(28591)
            foreach ($entry in $zip.Entries) {
                if (-not $entry.FullName.EndsWith('Plugin.class')) { continue }
                if ($entry.FullName.Contains('$')) { continue }
                $stream = $entry.Open()
                try {
                    $memory = New-Object System.IO.MemoryStream
                    $stream.CopyTo($memory)
                    $bytes = $memory.ToArray()
                    $memory.Dispose()
                } finally {
                    $stream.Dispose()
                }
                $text = $encoding.GetString($bytes)
                foreach ($match in [regex]::Matches($text, '\d+\.\d+\.\d+')) {
                    $value = $match.Value
                    if (-not $found.Contains($value)) {
                        [void] $found.Add($value)
                    }
                }
            }
        } finally {
            $zip.Dispose()
        }
    } catch {
        $script:JarVersionCache[$key] = @()
        return @()
    }
    $script:JarVersionCache[$key] = $found.ToArray()
    return @($found.ToArray())
}

function Get-InstalledPluginVersion([string] $jarName) {
    foreach ($file in (Get-InstalledCopy $jarName)) {
        $version = Get-JarPluginVersion $file.FullName $jarName
        if ($version) { return $version }
    }
    return $null
}

function Test-PluginNeedsUpdate([string] $jarName) {
    $source = Join-Path $DistDir $jarName
    if (-not (Test-Path -LiteralPath $source)) { return $false }
    $installed = Get-InstalledCopy $jarName
    if ($installed.Count -eq 0) { return $false }
    try {
        $sourceHash = Get-CachedFileHash $source
        foreach ($file in $installed) {
            if ((Get-CachedFileHash $file.FullName) -ne $sourceHash) {
                return $true
            }
        }
    } catch {
        return $true
    }
    return $installed.Count -gt 1
}

function Get-DesktopShortcutPath {
    return (Join-Path ([Environment]::GetFolderPath('Desktop')) $script:ShortcutFileName)
}

function Test-DesktopShortcut {
    return (Test-Path -LiteralPath (Get-DesktopShortcutPath))
}

function Get-InstallerIconSource {
    return (Join-Path $ScriptDir 'logo.ico')
}

function Get-HubTitleFont([float] $pixelSize) {
    $path = Join-Path $ScriptDir 'fonts\Jersey10-Regular.ttf'
    if ($null -eq $script:TitleFontCollection -and (Test-Path -LiteralPath $path)) {
        $script:TitleFontCollection = New-Object System.Drawing.Text.PrivateFontCollection
        $script:TitleFontCollection.AddFontFile($path)
    }
    if ($null -ne $script:TitleFontCollection -and $script:TitleFontCollection.Families.Length -gt 0) {
        return New-Object System.Drawing.Font(
            $script:TitleFontCollection.Families[0],
            $pixelSize,
            [System.Drawing.FontStyle]::Regular,
            [System.Drawing.GraphicsUnit]::Pixel)
    }
    return New-Object System.Drawing.Font('Segoe UI Semibold', 20)
}

function Start-BrandUri([string] $uri) {
    if ([string]::IsNullOrWhiteSpace($uri)) { return }
    Start-Process $uri | Out-Null
}

function New-BrandLink([string] $text, [string] $uri) {
    $link = New-Object System.Windows.Forms.Label
    $link.Text = $text
    $link.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $link.ForeColor = $script:ColorAccent
    $link.BackColor = $script:ColorBg
    $link.AutoSize = $true
    $link.Cursor = [System.Windows.Forms.Cursors]::Hand
    $target = $uri
    $link.Add_Click({ Start-BrandUri $target }.GetNewClosure())
    return $link
}

function Open-UnlockedStream([string] $path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $stream = New-Object System.IO.MemoryStream
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Position = 0
    return $stream
}

function Install-ShortcutIcon {
    $source = Get-InstallerIconSource
    if (-not (Test-Path -LiteralPath $source)) {
        return $null
    }
    if (-not (Test-Path -LiteralPath $script:LauncherDir)) {
        New-Item -ItemType Directory -Path $script:LauncherDir | Out-Null
    }
    $dest = Join-Path $script:LauncherDir 'logo.ico'
    Copy-Item -LiteralPath $source -Destination $dest -Force
    return $dest
}

function Add-DesktopShortcut {
    if (-not (Test-Path -LiteralPath $script:LauncherDir)) {
        New-Item -ItemType Directory -Path $script:LauncherDir | Out-Null
    }
    $vbs = Join-Path $script:LauncherDir 'Open-Installer.vbs'
    $launcher = @(
        "' Opens the latest Micro Hub installer.",
        'Set shell = CreateObject("Wscript.Shell")',
        ('command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command ""irm {0} | iex"""' -f $script:RunScriptUri),
        'shell.Run command, 0, False'
    ) -join "`r`n"
    Set-Content -LiteralPath $vbs -Value $launcher -Encoding ASCII
    $wsh = New-Object -ComObject WScript.Shell
    $shortcut = $wsh.CreateShortcut((Get-DesktopShortcutPath))
    $shortcut.TargetPath = Join-Path $env:SystemRoot 'System32\wscript.exe'
    $shortcut.Arguments = "//nologo `"$vbs`""
    $shortcut.WorkingDirectory = $script:LauncherDir
    $shortcut.WindowStyle = 7
    $shortcut.Description = 'Open the Micro Hub installer'
    $icon = Install-ShortcutIcon
    if ($icon) {
        $shortcut.IconLocation = "$icon,0"
    } else {
        $shortcut.IconLocation = ((Join-Path $env:SystemRoot 'System32\imageres.dll') + ',109')
    }
    $shortcut.Save()
}

function Update-ExistingShortcutIcon {
    if (-not (Test-DesktopShortcut)) {
        return
    }
    $icon = Install-ShortcutIcon
    if (-not $icon) {
        return
    }
    $wsh = New-Object -ComObject WScript.Shell
    $shortcut = $wsh.CreateShortcut((Get-DesktopShortcutPath))
    $shortcut.IconLocation = "$icon,0"
    $shortcut.Save()
}

function Remove-DesktopShortcut {
    $path = Get-DesktopShortcutPath
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

function Update-ShortcutLink($link) {
    if ($null -eq $link) { return }
    if (Test-DesktopShortcut) {
        $link.Text = 'Remove desktop shortcut'
        $link.ForeColor = $script:ColorMuted
    } else {
        $link.Text = 'Add desktop shortcut'
        $link.ForeColor = $script:ColorAccent
    }
}

function Get-LegacyJarName([string] $jarName) {
    if ($script:LegacyJars.ContainsKey($jarName)) {
        return [string] $script:LegacyJars[$jarName]
    }
    return $null
}

function Test-LegacyJarInstalled([string] $jarName) {
    $legacy = Get-LegacyJarName $jarName
    if (-not $legacy) { return $false }
    return (Get-InstalledCopy $legacy).Count -gt 0
}

function Remove-LibraryJars([string[]] $jarNames, [scriptblock] $onStep) {
    $removed = @()
    $step = 0
    foreach ($jarName in $jarNames) {
        $targets = @($jarName)
        $legacy = Get-LegacyJarName $jarName
        if ($legacy) { $targets += $legacy }
        foreach ($target in $targets) {
            foreach ($path in (Get-InstalledJarPaths $target)) {
                [System.IO.File]::Delete($path)
                if (Test-Path -LiteralPath $path) {
                    throw "Could not delete $path. Close the client and try again."
                }
                $removed += $path
            }
        }
        $step++
        if ($null -ne $onStep) {
            & $onStep $step $jarNames.Count $jarName
        }
    }
    return @($removed)
}

function Install-Library([string[]] $jarNames, [scriptblock] $onStep) {
    if (Test-ClientRunning) {
        throw 'Close the game client first so the old plugin files can be deleted.'
    }
    if ($jarNames.Count -eq 0) {
        throw 'Select at least one plugin.'
    }
    $missing = @()
    foreach ($jarName in $jarNames) {
        $source = Join-Path $DistDir $jarName
        if (-not (Test-Path -LiteralPath $source)) { $missing += $jarName }
    }
    if ($missing.Count -gt 0) {
        throw ("Missing jar(s) in dist: " + ($missing -join ', '))
    }

    $removed = @(Remove-LibraryJars $jarNames)
    if (-not (Test-Path -LiteralPath $script:InstallDir)) {
        New-Item -ItemType Directory -Path $script:InstallDir | Out-Null
    }
    $installed = @()
    $step = 0
    foreach ($jarName in $jarNames) {
        $source = Join-Path $DistDir $jarName
        $dest = Join-Path $script:InstallDir $jarName
        [System.IO.File]::Copy($source, $dest, $true)
        $installed += $dest
        $step++
        if ($null -ne $onStep) {
            & $onStep $step $jarNames.Count $jarName
        }
    }
    return @{
        Removed = $removed
        Installed = $installed
    }
}

function Uninstall-Library([string[]] $jarNames, [scriptblock] $onStep) {
    if (Test-ClientRunning) {
        throw 'Close the game client first so the plugin files can be deleted.'
    }
    if ($jarNames.Count -eq 0) {
        throw 'Select at least one plugin.'
    }
    return @(Remove-LibraryJars $jarNames $onStep)
}

function Test-PluginInstalled([string] $jarName) {
    return ((Get-InstalledCopy $jarName).Count -gt 0) -or (Test-LegacyJarInstalled $jarName)
}

function Get-DesiredPluginOn([string] $jarName) {
    if ($script:PendingToggles.ContainsKey($jarName)) {
        return [bool] $script:PendingToggles[$jarName]
    }
    return Test-PluginInstalled $jarName
}

function Sync-CardCheckedStates($form) {
    $cards = $form.Tag['Cards']
    if ($null -eq $cards) { return }
    foreach ($card in $cards) {
        if (-not $card.Tag.Available) {
            $card.Tag.Checked = $false
            continue
        }
        $card.Tag.Checked = Get-DesiredPluginOn $card.Tag.JarName
    }
}

function Invoke-TogglePlugin($form, $log, [string] $jarName, [bool] $desiredOn) {
    if ($null -eq $form -or $form.Tag.Busy) { return }
    $script:PendingToggles[$jarName] = $desiredOn
    Sync-CardCheckedStates $form
    Update-PluginCardStatus $form
    if (Test-ClientRunning) {
        $form.Tag.Warning.Visible = $true
        Update-LibraryLayout $form
        $label = Get-PluginLabel $jarName
        $message = if ($desiredOn) {
            "$label will install when the client closes."
        } else {
            "$label will uninstall when the client closes."
        }
        Set-HubStatus $form $message 'wait'
        Write-Log $log $message
        return
    }
    Invoke-PendingToggles $form $log
}

function Set-BulkPluginDesired($form, $log, [bool] $desiredOn) {
    if ($null -eq $form -or $form.Tag.Busy) { return }
    $cards = $form.Tag['Cards']
    if ($null -eq $cards) { return }
    $queued = 0
    foreach ($card in $cards) {
        if (-not $card.Tag.Available) { continue }
        $jarName = $card.Tag.JarName
        if ((Get-DesiredPluginOn $jarName) -eq $desiredOn) { continue }
        $script:PendingToggles[$jarName] = $desiredOn
        $queued++
    }
    if ($queued -eq 0) { return }
    Sync-CardCheckedStates $form
    Update-PluginCardStatus $form
    if (Test-ClientRunning) {
        $form.Tag.Warning.Visible = $true
        Update-LibraryLayout $form
        $message = if ($desiredOn) {
            'Selected plugins will install when the client closes.'
        } else {
            'Selected plugins will uninstall when the client closes.'
        }
        Set-HubStatus $form $message 'wait'
        Write-Log $log $message
        return
    }
    Invoke-PendingToggles $form $log
}

function Invoke-PendingToggles($form, $log) {
    if ($null -eq $form -or $form.Tag.Busy) { return }
    if ($script:PendingToggles.Count -eq 0) { return }
    if (Test-ClientRunning) { return }

    $toInstall = New-Object System.Collections.Generic.List[string]
    $toRemove = New-Object System.Collections.Generic.List[string]
    foreach ($jarName in @($script:PendingToggles.Keys)) {
        $want = [bool] $script:PendingToggles[$jarName]
        $have = Test-PluginInstalled $jarName
        if ($want -and -not $have) {
            [void] $toInstall.Add($jarName)
        } elseif (-not $want -and $have) {
            [void] $toRemove.Add($jarName)
        } else {
            $script:PendingToggles.Remove($jarName)
        }
    }
    if ($toInstall.Count -eq 0 -and $toRemove.Count -eq 0) {
        Sync-CardCheckedStates $form
        Update-PluginCardStatus $form
        return
    }

    $form.Tag.Busy = $true
    $script:ToggleTotal = [Math]::Max(1, ($toInstall.Count + $toRemove.Count))
    $script:ToggleDone = 0
    try {
        Show-HubProgress $form $script:ToggleTotal
        if ($toRemove.Count -gt 0) {
            $removed = @(Uninstall-Library $toRemove.ToArray() {
                param($done, $count, $jarName)
                $script:ToggleDone = $done
                Set-HubStatus $form ("Removing $(Get-PluginLabel $jarName)") 'busy'
                Advance-HubProgress $form ($script:ToggleDone / $script:ToggleTotal)
            })
            foreach ($path in $removed) { Write-Log $log "Uninstalled: $path" }
            $script:ToggleDone = $toRemove.Count
        }
        if ($toInstall.Count -gt 0) {
            $removeBase = $toRemove.Count
            $result = Install-Library $toInstall.ToArray() {
                param($done, $count, $jarName)
                Set-HubStatus $form ("Installing $(Get-PluginLabel $jarName)") 'busy'
                Advance-HubProgress $form (($removeBase + $done) / $script:ToggleTotal)
            }
            foreach ($path in $result.Removed) { Write-Log $log "Removed old copy: $path" }
            foreach ($path in $result.Installed) { Write-Log $log "Installed: $path" }
        }
        foreach ($jarName in @($toInstall.ToArray() + $toRemove.ToArray())) {
            $script:PendingToggles.Remove($jarName)
        }
        Write-Log $log 'Restart the client, then enable the plugins you want.'
        Complete-HubProgress $form
        Set-HubStatus $form 'Plugin changes applied. Restart the client before using them.' 'ok'
    } catch {
        Hide-HubProgress $form
        Write-Log $log $_.Exception.Message
        Set-HubStatus $form $_.Exception.Message 'error'
        $script:PendingToggles.Clear()
    } finally {
        $form.Tag.Busy = $false
        Sync-CardCheckedStates $form
        Update-PluginCardStatus $form
    }
}

function Write-Log($box, [string] $message) {
    if ($null -eq $box) { return }
    $box.AppendText(("[{0}]  {1}{2}" -f (Get-Date -Format 'HH:mm:ss'), $message, [Environment]::NewLine))
}

function Set-HubStatus($form, [string] $message, [string] $kind) {
    if ($null -eq $form -or $null -eq $form.Tag -or $null -eq $form.Tag.StatusLine) { return }
    $form.Tag.StatusLine.Text = $message
    switch ($kind) {
        'ok' { $form.Tag.StatusLine.ForeColor = $script:ColorInstalled }
        'wait' { $form.Tag.StatusLine.ForeColor = $script:ColorAccent }
        'error' { $form.Tag.StatusLine.ForeColor = $script:ColorAccent }
        'busy' { $form.Tag.StatusLine.ForeColor = $script:ColorText }
        default { $form.Tag.StatusLine.ForeColor = $script:ColorMuted }
    }
}

function Show-HubProgress($form, [int] $steps) {
    $form.Tag.ProgressSteps = [Math]::Max(1, $steps)
    $form.Tag.ProgressShown = 0
    $form.Tag.ProgressTarget = 0
    $form.Tag.Progress.Visible = $true
    Update-LibraryLayout $form
    Advance-HubProgress $form 0.08
}

function Advance-HubProgress($form, [double] $target) {
    if ($null -eq $form -or $null -eq $form.Tag.Progress) { return }
    $form.Tag.ProgressTarget = [Math]::Max(0, [Math]::Min(1, $target))
    $form.Tag.Progress.Visible = $true
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 240) {
        $shown = [double] $form.Tag.ProgressShown
        $goal = [double] $form.Tag.ProgressTarget
        if ([Math]::Abs($shown - $goal) -le 0.012) {
            $form.Tag.ProgressShown = $goal
            $form.Tag.Progress.Invalidate()
            [System.Windows.Forms.Application]::DoEvents()
            break
        }
        $form.Tag.ProgressShown = $shown + (($goal - $shown) * 0.38)
        $form.Tag.Progress.Invalidate()
        [System.Windows.Forms.Application]::DoEvents()
        Start-Sleep -Milliseconds 16
    }
}

function Complete-HubProgress($form) {
    Advance-HubProgress $form 1
    if ($null -ne $form.Tag.Progress) {
        $form.Tag.Progress.Visible = $false
    }
    Update-LibraryLayout $form
}

function Hide-HubProgress($form) {
    if ($null -eq $form -or $null -eq $form.Tag.Progress) { return }
    $form.Tag.Progress.Visible = $false
    $form.Tag.ProgressShown = 0
    $form.Tag.ProgressTarget = 0
    Update-LibraryLayout $form
}

function Set-ControlBuffered($control) {
    [void] $control.GetType().InvokeMember(
        'DoubleBuffered',
        [System.Reflection.BindingFlags]'NonPublic, Instance, SetProperty',
        $null,
        $control,
        @($true))
}

function New-RoundedPath([System.Drawing.Rectangle] $bounds, [int] $radius) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $radius * 2
    $path.AddArc($bounds.X, $bounds.Y, $diameter, $diameter, 180, 90)
    $path.AddArc(($bounds.Right - $diameter), $bounds.Y, $diameter, $diameter, 270, 90)
    $path.AddArc(($bounds.Right - $diameter), ($bounds.Bottom - $diameter), $diameter, $diameter, 0, 90)
    $path.AddArc($bounds.X, ($bounds.Bottom - $diameter), $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-SetupButton([string] $text, [string] $kind) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $text
    $button.Size = New-Object System.Drawing.Size(148, 40)
    $button.FlatStyle = 'Flat'
    $button.Cursor = [System.Windows.Forms.Cursors]::Hand
    $button.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10)
    $button.FlatAppearance.BorderSize = 0
    if ($kind -eq 'primary') {
        $button.BackColor = $script:ColorAccent
        $button.ForeColor = [System.Drawing.Color]::Black
        $button.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(255, 255, 140)
        $button.FlatAppearance.MouseDownBackColor = $script:ColorAccentDark
    } elseif ($kind -eq 'danger') {
        $button.BackColor = $script:ColorCard
        $button.ForeColor = $script:ColorText
        $button.FlatAppearance.BorderSize = 1
        $button.FlatAppearance.BorderColor = $script:ColorCardEdge
        $button.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(44, 46, 54)
        $button.FlatAppearance.MouseDownBackColor = [System.Drawing.Color]::FromArgb(28, 29, 34)
    } else {
        $button.Size = New-Object System.Drawing.Size(96, 40)
        $button.BackColor = $script:ColorBg
        $button.ForeColor = $script:ColorMuted
        $button.FlatAppearance.MouseOverBackColor = $script:ColorCard
        $button.FlatAppearance.MouseDownBackColor = $script:ColorCard
    }
    return $button
}

function Add-CardToggle($control, $card) {
    $control.Cursor = [System.Windows.Forms.Cursors]::Hand
    $control.Add_Click({
        if (-not $card.Tag.Available) { return }
        $owner = $card.FindForm()
        if ($null -eq $owner -or $owner.Tag.Busy) { return }
        $desired = -not (Get-DesiredPluginOn $card.Tag.JarName)
        Invoke-TogglePlugin $owner $owner.Tag.Log $card.Tag.JarName $desired
    }.GetNewClosure())
}

function Get-PluginCardGridMetrics($list) {
    $gap = 8
    $pad = 8
    $scrollBar = [System.Windows.Forms.SystemInformation]::VerticalScrollBarWidth
    $inner = [Math]::Max(200, $list.ClientSize.Width - $pad - $scrollBar)
    $cols = 1
    if ($inner -ge ((340 * 2) + $gap)) { $cols = 2 }
    $cardWidth = [int] (($inner - (($cols - 1) * $gap)) / $cols)
    if ($cardWidth -lt 200) { $cardWidth = 200 }
    return @{
        Gap = $gap
        Pad = $pad
        Cols = $cols
        CardWidth = $cardWidth
        CardHeight = 46
        RowStride = 52
    }
}

function Update-PluginCardLayout($form) {
    if ($null -eq $form -or $null -eq $form.Tag -or $null -eq $form.Tag.List) { return }
    $list = $form.Tag.List
    $cards = $form.Tag.Cards
    if ($null -eq $cards -or $cards.Count -eq 0) { return }
    $metrics = Get-PluginCardGridMetrics $list
    $cols = [int] $metrics.Cols
    $cardWidth = [int] $metrics.CardWidth
    $gap = [int] $metrics.Gap
    $pad = [int] $metrics.Pad
    $cardHeight = [int] $metrics.CardHeight
    $rowStride = [int] $metrics.RowStride
    $index = 0
    foreach ($card in $cards) {
        $col = $index % $cols
        $row = [int] [Math]::Floor($index / $cols)
        $x = $pad + ($col * ($cardWidth + $gap))
        $y = 4 + ($row * $rowStride)
        $card.Location = New-Object System.Drawing.Point($x, $y)
        $card.Size = New-Object System.Drawing.Size($cardWidth, $cardHeight)
        $detail = $card.Controls['detail']
        if ($null -ne $detail) {
            $detailWidth = [Math]::Max(60, $cardWidth - 220)
            $detail.Size = New-Object System.Drawing.Size($detailWidth, 16)
        }
        $card.Invalidate()
        $index++
    }
}

function Build-PluginCards($form) {
    $list = $form.Tag.List
    $cards = $form.Tag.Cards
    $list.Controls.Clear()
    $cards.Clear()
    $names = @(Get-LibraryJarNames)
    $metrics = Get-PluginCardGridMetrics $list
    $cardWidth = [int] $metrics.CardWidth
    foreach ($jarName in $names) {
        $source = Join-Path $DistDir $jarName
        $available = Test-Path -LiteralPath $source
        $installed = $available -and (Test-PluginInstalled $jarName)
        $card = New-Object System.Windows.Forms.Panel
        $card.Location = New-Object System.Drawing.Point(8, 4)
        $card.Size = New-Object System.Drawing.Size($cardWidth, 46)
        $card.BackColor = $script:ColorBg
        $card.Cursor = [System.Windows.Forms.Cursors]::Hand
        $card.Tag = @{
            JarName = $jarName
            Checked = [bool] $installed
            Knob = $(if ($installed) { 1.0 } else { 0.0 })
            Available = [bool] $available
            AvailableVersion = (Get-AvailablePluginVersion $jarName)
            HasUpdate = $false
            PillText = $(if (-not $available) { 'Missing' } elseif ($installed) { 'Installed' } else { 'Not installed' })
            PillKind = $(if (-not $available) { 'missing' } elseif ($installed) { 'installed' } else { 'off' })
        }
        Set-ControlBuffered $card
        $card.Add_Paint({
            param($sender, $e)
            $g = $e.Graphics
            $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
            $bounds = New-Object System.Drawing.Rectangle(0, 0, ($sender.Width - 1), ($sender.Height - 1))
            $path = New-RoundedPath $bounds 8
            $fill = New-Object System.Drawing.SolidBrush $script:ColorCard
            $g.FillPath($fill, $path)
            $pen = New-Object System.Drawing.Pen $script:ColorCardEdge
            $g.DrawPath($pen, $path)
            $knobT = [double] $sender.Tag.Knob
            if ($knobT -lt 0) { $knobT = 0 }
            if ($knobT -gt 1) { $knobT = 1 }
            $trackH = 16
            $trackW = 34
            $trackY = [int] (($sender.Height - $trackH) / 2)
            $track = New-Object System.Drawing.Rectangle(12, $trackY, $trackW, $trackH)
            $trackPath = New-RoundedPath $track 8
            $trackOff = $script:ColorTrackOff
            $trackOn = $script:ColorAccent
            if (-not $sender.Tag.Available) { $trackOn = $script:ColorCardEdge }
            $trackColor = [System.Drawing.Color]::FromArgb(
                [int] ($trackOff.R + (($trackOn.R - $trackOff.R) * $knobT)),
                [int] ($trackOff.G + (($trackOn.G - $trackOff.G) * $knobT)),
                [int] ($trackOff.B + (($trackOn.B - $trackOff.B) * $knobT)))
            $trackBrush = New-Object System.Drawing.SolidBrush $trackColor
            $g.FillPath($trackBrush, $trackPath)
            $knobSize = 12
            $knobX = $track.X + 2 + [int] (($track.Width - $knobSize - 4) * $knobT)
            $knobY = $track.Y + 2
            $knobOff = [System.Drawing.Color]::FromArgb(232, 232, 232)
            $knobOn = [System.Drawing.Color]::FromArgb(12, 12, 12)
            $knobColor = [System.Drawing.Color]::FromArgb(
                [int] ($knobOff.R + (($knobOn.R - $knobOff.R) * $knobT)),
                [int] ($knobOff.G + (($knobOn.G - $knobOff.G) * $knobT)),
                [int] ($knobOff.B + (($knobOn.B - $knobOff.B) * $knobT)))
            $knobBrush = New-Object System.Drawing.SolidBrush $knobColor
            $g.FillEllipse($knobBrush, $knobX, $knobY, $knobSize, $knobSize)
            $knobBrush.Dispose()
            $trackBrush.Dispose()
            $trackPath.Dispose()
            $pillText = [string] $sender.Tag.PillText
            if ($pillText) {
                $pillFont = New-Object System.Drawing.Font('Segoe UI Semibold', 8)
                $pillSize = $g.MeasureString($pillText, $pillFont)
                $pillH = 20
                $pillW = [Math]::Max($pillH, ([int] [Math]::Ceiling($pillSize.Width) + 14))
                $pillX = $sender.Width - $pillW - 16
                $pillY = [int] (($sender.Height - $pillH) / 2)
                $pillBounds = New-Object System.Drawing.Rectangle($pillX, $pillY, $pillW, $pillH)
                $pillPath = New-RoundedPath $pillBounds 10
                $kind = [string] $sender.Tag.PillKind
                if ($kind -eq 'installed') {
                    $pillBack = [System.Drawing.Color]::FromArgb(16, 48, 36)
                    $pillFore = $script:ColorInstalled
                } elseif ($kind -eq 'update' -or $kind -eq 'missing') {
                    $pillBack = [System.Drawing.Color]::FromArgb(48, 48, 0)
                    $pillFore = $script:ColorAccent
                } else {
                    $pillBack = [System.Drawing.Color]::FromArgb(44, 45, 52)
                    $pillFore = $script:ColorMuted
                }
                $pillBrush = New-Object System.Drawing.SolidBrush $pillBack
                $g.FillPath($pillBrush, $pillPath)
                $textBrush = New-Object System.Drawing.SolidBrush $pillFore
                $textY = $pillY + (($pillH - $pillSize.Height) / 2)
                $g.DrawString($pillText, $pillFont, $textBrush, ($pillX + 9), $textY)
                $textBrush.Dispose()
                $pillBrush.Dispose()
                $pillPath.Dispose()
                $pillFont.Dispose()
            }
            $path.Dispose()
            $fill.Dispose()
            $pen.Dispose()
        })
        $card.Add_Click({
            param($sender, $e)
            if (-not $sender.Tag.Available) { return }
            $owner = $sender.FindForm()
            if ($null -eq $owner -or $owner.Tag.Busy) { return }
            $desired = -not (Get-DesiredPluginOn $sender.Tag.JarName)
            Invoke-TogglePlugin $owner $owner.Tag.Log $sender.Tag.JarName $desired
        })

        $name = New-Object System.Windows.Forms.Label
        $name.Text = Get-PluginLabel $jarName
        $name.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10)
        $name.ForeColor = $script:ColorText
        $name.BackColor = $script:ColorCard
        $name.AutoSize = $true
        $name.Location = New-Object System.Drawing.Point(54, 4)
        Add-CardToggle $name $card

        $detail = New-Object System.Windows.Forms.Label
        $detail.Name = 'detail'
        $blurb = $script:PluginBlurbs[$jarName]
        if (-not $blurb) { $blurb = 'Library plugin' }
        $detail.Text = $blurb
        if (-not $available) { $name.ForeColor = $script:ColorMuted }
        $detail.Font = New-Object System.Drawing.Font('Segoe UI', 8)
        $detail.ForeColor = $script:ColorMuted
        $detail.BackColor = $script:ColorCard
        $detail.AutoSize = $false
        $detail.Size = New-Object System.Drawing.Size(($cardWidth - 220), 16)
        $detail.Location = New-Object System.Drawing.Point(54, 24)
        Add-CardToggle $detail $card

        $card.Controls.AddRange(@($name, $detail))
        $list.Controls.Add($card)
        [void] $cards.Add($card)
    }
    if ($names.Count -eq 0) {
        $empty = New-Object System.Windows.Forms.Label
        $empty.Text = "No plugins listed.`r`n$LibraryFile"
        $empty.ForeColor = $script:ColorMuted
        $empty.BackColor = $script:ColorBg
        $empty.AutoSize = $false
        $empty.Size = New-Object System.Drawing.Size(640, 48)
        $empty.Location = New-Object System.Drawing.Point(12, 12)
        $list.Controls.Add($empty)
    } else {
        Update-PluginCardLayout $form
    }
}

function Update-PluginCardStatus($form) {
    $cards = $form.Tag['Cards']
    if ($null -eq $cards) { return }
    foreach ($card in $cards) {
        $detail = $card.Controls['detail']
        $jarName = $card.Tag.JarName
        $blurb = $script:PluginBlurbs[$jarName]
        if (-not $blurb) { $blurb = 'Library plugin' }
        if ($null -ne $detail) { $detail.Text = $blurb }
        $availableVersion = $card.Tag.AvailableVersion
        $needsUpdate = $false
        if (-not $card.Tag.Available) {
            $card.Tag.PillText = 'Missing'
            $card.Tag.PillKind = 'missing'
        } elseif ((Test-PluginNeedsUpdate $jarName) -or (Test-LegacyJarInstalled $jarName)) {
            $needsUpdate = $true
            if ($availableVersion) {
                $card.Tag.PillText = "Update $availableVersion"
            } else {
                $card.Tag.PillText = 'Update'
            }
            $card.Tag.PillKind = 'update'
        } elseif ((Get-InstalledCopy $jarName).Count -gt 0) {
            $installedVersion = Get-InstalledPluginVersion $jarName
            if ($installedVersion) {
                $card.Tag.PillText = "Installed $installedVersion"
            } else {
                $card.Tag.PillText = 'Installed'
            }
            $card.Tag.PillKind = 'installed'
        } else {
            $card.Tag.PillText = 'Not installed'
            $card.Tag.PillKind = 'off'
        }
        $card.Tag.HasUpdate = $needsUpdate
        $card.Tag.Checked = Get-DesiredPluginOn $jarName
        $card.Invalidate()
    }
    $running = Test-ClientRunning
    if ($null -ne $form.Tag.Warning -and ($form.Tag.Warning.Visible -ne $running)) {
        $form.Tag.Warning.Visible = $running
        Update-LibraryLayout $form
    }
}

function Get-OutdatedLibraryJars {
    foreach ($jarName in @(Get-LibraryJarNames)) {
        if ((Test-PluginNeedsUpdate $jarName) -or (Test-LegacyJarInstalled $jarName)) {
            $jarName
        }
    }
}

function Invoke-AutomaticUpdates($form, $log) {
    if ($script:PendingUpdateApplied -or $form.Tag.Busy) { return }
    $outdated = @(Get-OutdatedLibraryJars)
    if ($outdated.Count -eq 0) {
        $script:UpdateWaitNoted = $false
        return
    }
    $labels = @($outdated | ForEach-Object { Get-PluginLabel $_ })
    $listText = $labels -join ', '
    if (Test-ClientRunning) {
        if (-not $script:UpdateWaitNoted) {
            $script:UpdateWaitNoted = $true
            $message = if ($labels.Count -eq 1) {
                "$listText has an update. It will install when the client closes."
            } else {
                "Updates ready for $listText. They will install when the client closes."
            }
            Write-Log $log $message
            Set-HubStatus $form $message 'wait'
        }
        return
    }
    $script:UpdateWaitNoted = $false
    $script:PendingUpdateApplied = $true
    $form.Tag.Busy = $true
    try {
        Show-HubProgress $form $outdated.Count
        $result = Install-Library $outdated {
            param($step, $total, $jarName)
            Set-HubStatus $form ("Updating $(Get-PluginLabel $jarName)") 'busy'
            Advance-HubProgress $form ($step / [Math]::Max(1, $total))
        }
        foreach ($path in $result.Removed) { Write-Log $log "Removed old copy: $path" }
        foreach ($path in $result.Installed) { Write-Log $log "Updated: $path" }
        $done = if ($labels.Count -eq 1) {
            "$listText was updated. Restart the client before using it."
        } else {
            "Updated $($labels.Count) plugins. Restart the client before using them."
        }
        Write-Log $log $done
        Complete-HubProgress $form
        Set-HubStatus $form $done 'ok'
    } catch {
        $script:PendingUpdateApplied = $false
        Hide-HubProgress $form
        Write-Log $log $_.Exception.Message
        Set-HubStatus $form $_.Exception.Message 'error'
    } finally {
        $form.Tag.Busy = $false
        Update-PluginCardStatus $form
    }
}

function Update-LibraryLayout($form) {
    if ($null -eq $form -or $null -eq $form.Tag -or $null -eq $form.Tag.List) { return }
    $w = $form.ClientSize.Width
    $h = $form.ClientSize.Height
    $headerH = 96
    $bannerH = 0
    if ($form.Tag.Warning.Visible) { $bannerH = 40 }
    $toolbarH = 32
    $statusH = 28
    $progressH = 0
    if ($null -ne $form.Tag.Progress -and $form.Tag.Progress.Visible) { $progressH = 14 }
    $logH = 0
    if ($form.Tag.DetailsOpen) { $logH = 100 }
    $footerH = 64

    $form.Tag.Header.SetBounds(0, 0, $w, $headerH)
    if ($null -ne $form.Tag.Subtitle) {
        $form.Tag.Subtitle.Width = [Math]::Max(280, ($w - 100))
    }
    if ($null -ne $form.Tag.GitHubLink) {
        $form.Tag.GitHubLink.Location = New-Object System.Drawing.Point(200, 70)
        $form.Tag.MicrobotLink.Location = New-Object System.Drawing.Point(($form.Tag.GitHubLink.Right + 16), 70)
    }
    $form.Tag.Warning.SetBounds(0, $headerH, $w, $bannerH)
    $y = $headerH + $bannerH + 8
    $form.Tag.SelectAll.Location = New-Object System.Drawing.Point(28, ($y + 2))
    $form.Tag.ClearSelection.Location = New-Object System.Drawing.Point(108, ($y + 2))

    $listTop = $y + $toolbarH
    $listBottom = $h - $footerH - $statusH - $progressH - $logH - 4
    $listH = [Math]::Max(180, ($listBottom - $listTop))
    $form.Tag.List.SetBounds(16, $listTop, ($w - 32), $listH)

    $statusY = $form.Tag.List.Bottom + 8
    $form.Tag.StatusLine.SetBounds(28, $statusY, ($w - 150), 22)
    $form.Tag.DetailsLink.Location = New-Object System.Drawing.Point(($w - 108), $statusY)
    if ($null -ne $form.Tag.Progress) {
        $form.Tag.Progress.SetBounds(28, ($statusY + 22), ($w - 56), 8)
    }
    $form.Tag.Log.Visible = [bool] $form.Tag.DetailsOpen
    if ($form.Tag.DetailsOpen) {
        $form.Tag.Log.SetBounds(28, ($statusY + $statusH + $progressH), ($w - 56), $logH)
    }

    $btnY = $h - 52
    $form.Tag.ShortcutLink.Location = New-Object System.Drawing.Point(28, ($btnY + 10))
    $close = $form.Tag.CloseButton
    $close.Location = New-Object System.Drawing.Point(($w - 28 - $close.Width), ($btnY + 10))
    Update-PluginCardLayout $form
}

function New-LibraryForm {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'Micro Hub'
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'Sizable'
    $form.MaximizeBox = $true
    $form.MinimizeBox = $true
    $form.MinimumSize = New-Object System.Drawing.Size(900, 560)
    $form.ClientSize = New-Object System.Drawing.Size(1100, 720)
    $form.BackColor = $script:ColorBg
    $form.ForeColor = $script:ColorText
    $form.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $form.ShowInTaskbar = $true
    $iconStream = $null
    $iconPath = Get-InstallerIconSource
    if (Test-Path -LiteralPath $iconPath) {
        $iconStream = Open-UnlockedStream $iconPath
        $form.Icon = New-Object System.Drawing.Icon $iconStream
    }
    Set-ControlBuffered $form

    $header = New-Object System.Windows.Forms.Panel
    $header.BackColor = $script:ColorBg

    $mark = New-Object System.Windows.Forms.PictureBox
    $mark.Size = New-Object System.Drawing.Size(44, 44)
    $mark.Location = New-Object System.Drawing.Point(24, 18)
    $mark.SizeMode = [System.Windows.Forms.PictureBoxSizeMode]::Zoom
    $mark.BackColor = $script:ColorBg
    $markImage = $null
    $markStream = $null
    $markPath = Join-Path $ScriptDir 'logo.png'
    if (Test-Path -LiteralPath $markPath) {
        $markStream = Open-UnlockedStream $markPath
        $markImage = [System.Drawing.Image]::FromStream($markStream)
        $mark.Image = $markImage
    }

    $title = New-Object System.Windows.Forms.Label
    $title.Text = 'Micro Hub'
    $title.Font = Get-HubTitleFont 30
    $title.ForeColor = $script:ColorAccent
    $title.BackColor = $script:ColorBg
    $title.AutoSize = $true
    $title.Location = New-Object System.Drawing.Point(78, 14)

    $subtitle = New-Object System.Windows.Forms.Label
    $subtitle.Text = 'Toggle plugins on to install, off to remove. Updates apply when the client closes.'
    $subtitle.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $subtitle.ForeColor = $script:ColorMuted
    $subtitle.BackColor = $script:ColorBg
    $subtitle.AutoSize = $false
    $subtitle.Size = New-Object System.Drawing.Size(640, 18)
    $subtitle.Location = New-Object System.Drawing.Point(80, 46)

    $teamLabel = New-Object System.Windows.Forms.Label
    $teamLabel.Text = $script:TeamName
    $teamLabel.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $teamLabel.ForeColor = $script:ColorMuted
    $teamLabel.BackColor = $script:ColorBg
    $teamLabel.AutoSize = $true
    $teamLabel.Location = New-Object System.Drawing.Point(80, 70)

    $githubLink = New-BrandLink 'GitHub' $script:TeamGitHubUri
    $githubLink.Location = New-Object System.Drawing.Point(200, 70)
    $microbotLink = New-BrandLink 'Get Microbot' $script:MicrobotDownloadUri

    $selectAll = New-Object System.Windows.Forms.Label
    $selectAll.Text = 'Select all'
    $selectAll.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $selectAll.ForeColor = $script:ColorText
    $selectAll.BackColor = $script:ColorBg
    $selectAll.AutoSize = $true
    $selectAll.Cursor = [System.Windows.Forms.Cursors]::Hand

    $clearSelection = New-Object System.Windows.Forms.Label
    $clearSelection.Text = 'Clear'
    $clearSelection.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $clearSelection.ForeColor = $script:ColorMuted
    $clearSelection.BackColor = $script:ColorBg
    $clearSelection.AutoSize = $true
    $clearSelection.Cursor = [System.Windows.Forms.Cursors]::Hand

    $list = New-Object System.Windows.Forms.Panel
    $list.BackColor = $script:ColorBg
    $list.AutoScroll = $true
    $list.AutoScrollMargin = New-Object System.Drawing.Size(0, 0)
    Set-ControlBuffered $list

    $warning = New-Object System.Windows.Forms.Label
    $warning.Text = 'The game client is open. Install, uninstall, and updates wait until it closes.'
    $warning.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
    $warning.Padding = New-Object System.Windows.Forms.Padding(28, 0, 16, 0)
    $warning.ForeColor = $script:ColorWarning
    $warning.BackColor = [System.Drawing.Color]::FromArgb(32, 32, 0)
    $warning.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $warning.Visible = $false

    $statusLine = New-Object System.Windows.Forms.Label
    $statusLine.Text = 'Ready'
    $statusLine.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $statusLine.ForeColor = $script:ColorMuted
    $statusLine.BackColor = $script:ColorBg
    $statusLine.AutoSize = $false
    $statusLine.AutoEllipsis = $true

    $detailsLink = New-Object System.Windows.Forms.Label
    $detailsLink.Text = 'Details'
    $detailsLink.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $detailsLink.ForeColor = $script:ColorText
    $detailsLink.BackColor = $script:ColorBg
    $detailsLink.AutoSize = $true
    $detailsLink.Cursor = [System.Windows.Forms.Cursors]::Hand

    $log = New-Object System.Windows.Forms.TextBox
    $log.Multiline = $true
    $log.ReadOnly = $true
    $log.ScrollBars = 'Vertical'
    $log.BorderStyle = 'None'
    $log.BackColor = $script:ColorLog
    $log.ForeColor = [System.Drawing.Color]::FromArgb(212, 212, 216)
    $log.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $log.Visible = $false

    $progress = New-Object System.Windows.Forms.Panel
    $progress.BackColor = $script:ColorBg
    $progress.Visible = $false
    Set-ControlBuffered $progress
    $progress.Add_Paint({
        param($sender, $e)
        $g = $e.Graphics
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $owner = $sender.FindForm()
        $shown = 0.0
        if ($null -ne $owner -and $null -ne $owner.Tag) {
            $shown = [double] $owner.Tag.ProgressShown
        }
        if ($shown -lt 0) { $shown = 0 }
        if ($shown -gt 1) { $shown = 1 }
        $trackBounds = New-Object System.Drawing.Rectangle(0, 0, [Math]::Max(1, $sender.Width - 1), [Math]::Max(1, $sender.Height - 1))
        $trackPath = New-RoundedPath $trackBounds 3
        $trackBrush = New-Object System.Drawing.SolidBrush $script:ColorCard
        $g.FillPath($trackBrush, $trackPath)
        $fillW = [int] ($sender.Width * $shown)
        if ($fillW -gt 2) {
            $fillBounds = New-Object System.Drawing.Rectangle(0, 0, $fillW, [Math]::Max(1, $sender.Height - 1))
            $fillPath = New-RoundedPath $fillBounds 3
            $fillBrush = New-Object System.Drawing.SolidBrush $script:ColorAccent
            $g.FillPath($fillBrush, $fillPath)
            $fillBrush.Dispose()
            $fillPath.Dispose()
        }
        $trackBrush.Dispose()
        $trackPath.Dispose()
    })

    $shortcutLink = New-Object System.Windows.Forms.Label
    $shortcutLink.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $shortcutLink.ForeColor = $script:ColorMuted
    $shortcutLink.BackColor = $script:ColorBg
    $shortcutLink.AutoSize = $true
    $shortcutLink.Cursor = [System.Windows.Forms.Cursors]::Hand
    Update-ShortcutLink $shortcutLink

    $closeButton = New-Object System.Windows.Forms.Label
    $closeButton.Text = 'Close'
    $closeButton.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $closeButton.ForeColor = $script:ColorMuted
    $closeButton.BackColor = $script:ColorBg
    $closeButton.AutoSize = $true
    $closeButton.Cursor = [System.Windows.Forms.Cursors]::Hand
    $closeButton.Add_Click({ $form.Close() }.GetNewClosure())

    $cards = New-Object System.Collections.Generic.List[object]
    $form.Tag = @{
        Cards = $cards
        Header = $header
        Subtitle = $subtitle
        SelectAll = $selectAll
        ClearSelection = $clearSelection
        List = $list
        CloseButton = $closeButton
        ShortcutLink = $shortcutLink
        Warning = $warning
        StatusLine = $statusLine
        DetailsLink = $detailsLink
        Log = $log
        Progress = $progress
        ProgressShown = 0.0
        ProgressTarget = 0.0
        ProgressSteps = 1
        Busy = $false
        DetailsOpen = $false
        MarkImage = $markImage
        MarkStream = $markStream
        IconStream = $iconStream
        TeamLabel = $teamLabel
        GitHubLink = $githubLink
        MicrobotLink = $microbotLink
    }

    $selectAll.Add_Click({
        Set-BulkPluginDesired $form $log $true
    }.GetNewClosure())

    $clearSelection.Add_Click({
        Set-BulkPluginDesired $form $log $false
    }.GetNewClosure())

    $detailsLink.Add_Click({
        $form.Tag.DetailsOpen = -not [bool] $form.Tag.DetailsOpen
        if ($form.Tag.DetailsOpen) {
            $detailsLink.Text = 'Hide'
        } else {
            $detailsLink.Text = 'Details'
        }
        Update-LibraryLayout $form
    }.GetNewClosure())

    $shortcutLink.Add_Click({
        try {
            if (Test-DesktopShortcut) {
                Remove-DesktopShortcut
                Write-Log $log 'Removed the desktop shortcut.'
            } else {
                Add-DesktopShortcut
                Write-Log $log 'Added a desktop shortcut. It opens the latest installer.'
            }
            if (Test-DesktopShortcut) {
                Set-HubStatus $form 'Desktop shortcut added.' 'ok'
            } else {
                Set-HubStatus $form 'Desktop shortcut removed.' 'ok'
            }
        } catch {
            Write-Log $log $_.Exception.Message
            Set-HubStatus $form $_.Exception.Message 'error'
        }
        Update-ShortcutLink $shortcutLink
        Update-LibraryLayout $form
    }.GetNewClosure())

    $header.Controls.AddRange(@($mark, $title, $subtitle, $teamLabel, $githubLink, $microbotLink))
    $form.Controls.AddRange(@(
        $header, $warning, $selectAll, $clearSelection, $list, $statusLine, $detailsLink, $progress, $log,
        $shortcutLink, $closeButton
    ))
    $form.Add_Resize({
        Update-LibraryLayout $form
    }.GetNewClosure())
    Update-LibraryLayout $form

    $timer = New-Object System.Windows.Forms.Timer
    $timer.Interval = 1500
    $timer.Add_Tick({
        if ($form.Tag.Busy) { return }
        Update-PluginCardStatus $form
        Invoke-PendingToggles $form $log
        Invoke-AutomaticUpdates $form $log
    }.GetNewClosure())
    $motion = New-Object System.Windows.Forms.Timer
    $motion.Interval = 16
    $motion.Add_Tick({
        $cards = $form.Tag.Cards
        if ($null -ne $cards) {
            foreach ($card in $cards) {
                $target = if ($card.Tag.Checked) { 1.0 } else { 0.0 }
                $current = [double] $card.Tag.Knob
                if ([Math]::Abs($current - $target) -gt 0.015) {
                    $next = $current + (($target - $current) * 0.28)
                    if ([Math]::Abs($next - $target) -lt 0.02) { $next = $target }
                    $card.Tag.Knob = $next
                    $card.Invalidate()
                }
            }
        }
    }.GetNewClosure())
    $form.Add_Shown({
        Update-LibraryLayout $form
        Build-PluginCards $form
        Update-PluginCardStatus $form
        if (Test-DesktopShortcut) {
            Update-ExistingShortcutIcon
        }
        Update-ShortcutLink $shortcutLink
        Write-Log $log 'Toggle a plugin on to install it, or off to remove it.'
        if (Test-DesktopShortcut) {
            Write-Log $log 'Desktop shortcut is on the desktop.'
        } else {
            Write-Log $log 'Add a desktop shortcut if you want to open this installer later.'
        }
        Set-HubStatus $form 'Toggle plugins on or off.' 'idle'
        Invoke-PendingToggles $form $log
        Invoke-AutomaticUpdates $form $log
        $timer.Start()
        $motion.Start()
    }.GetNewClosure())
    $null = $form.Add_FormClosed({
        $timer.Stop()
        $timer.Dispose()
        $motion.Stop()
        $motion.Dispose()
        if ($null -ne $form.Icon) { $form.Icon.Dispose() }
        if ($null -ne $form.Tag.IconStream) { $form.Tag.IconStream.Dispose() }
        if ($null -ne $form.Tag.MarkImage) { $form.Tag.MarkImage.Dispose() }
        if ($null -ne $form.Tag.MarkStream) { $form.Tag.MarkStream.Dispose() }
    }.GetNewClosure())

    return $form
}

function Hide-SetupConsole {
    $hwnd = [MicroHubWindow]::GetConsoleWindow()
    if ($hwnd -ne [IntPtr]::Zero) {
        [void] [MicroHubWindow]::ShowWindow($hwnd, 0)
    }
}

function Show-LibrarySetup {
    Hide-SetupConsole
    [System.Windows.Forms.Application]::SetUnhandledExceptionMode(
        [System.Windows.Forms.UnhandledExceptionMode]::CatchException)
    [System.Windows.Forms.Application]::add_ThreadException({
        param($sender, $e)
        [System.Windows.Forms.MessageBox]::Show(
            $e.Exception.Message,
            'Micro Hub setup failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    })
    try {
        $form = New-LibraryForm
        [void] $form.ShowDialog()
        $form.Dispose()
        if ($null -ne $script:TitleFontCollection) {
            $script:TitleFontCollection.Dispose()
            $script:TitleFontCollection = $null
        }
    } catch {
        [System.Windows.Forms.MessageBox]::Show(
            $_.Exception.Message,
            'Micro Hub setup failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    Show-LibrarySetup
}
