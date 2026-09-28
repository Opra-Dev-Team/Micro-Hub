# [OPIE] Plugin Library setup window.
# Install copies the checked plugins. Uninstall removes only the checked plugins.

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ErrorActionPreference = 'Stop'

try {
    [System.Windows.Forms.Application]::SetCompatibleTextRenderingDefault($false)
} catch {
}
[System.Windows.Forms.Application]::EnableVisualStyles()

if (-not ([System.Management.Automation.PSTypeName]'OpieSetupWindow').Type) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class OpieSetupWindow {
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

$script:ColorBg = [System.Drawing.Color]::FromArgb(18, 18, 20)
$script:ColorCard = [System.Drawing.Color]::FromArgb(32, 33, 38)
$script:ColorCardEdge = [System.Drawing.Color]::FromArgb(58, 60, 68)
$script:ColorText = [System.Drawing.Color]::FromArgb(245, 245, 247)
$script:ColorMuted = [System.Drawing.Color]::FromArgb(161, 161, 170)
$script:ColorAccent = [System.Drawing.Color]::FromArgb(255, 59, 48)
$script:ColorAccentDark = [System.Drawing.Color]::FromArgb(214, 40, 32)
$script:ColorInstalled = [System.Drawing.Color]::FromArgb(52, 211, 153)
$script:ColorWarning = [System.Drawing.Color]::FromArgb(251, 191, 36)
$script:ColorLog = [System.Drawing.Color]::FromArgb(14, 14, 16)

$script:HashCache = @{}
$script:SourceMetaCache = @{}
$script:JarVersionCache = @{}
$script:ShortcutFileName = '[OPIE] Plugin Library.lnk'
$script:LauncherDir = Join-Path $env:LOCALAPPDATA 'OpiesPluginLibrary'
$script:UpdateNoticeShown = $false
$script:PendingUpdateApplied = $false
$script:RunScriptUri = 'https://raw.githubusercontent.com/opesoid/opies-plugin-hut/dev/installer/run.ps1'
$script:TeamName = 'Opra Dev Team'
$script:TeamGitHubUri = 'https://github.com/Opra-Dev-Team'
$script:MicrobotDownloadUri = 'https://microbot.cloud/'

$script:PluginBlurbs = @{
    'OpiesBankSorterPlugin.jar' = 'Sorts the bank into an iron 8-tab layout.'
    'OpiesSandBuyerPlugin.jar' = 'Buys sand and soda ash in Catherby, then hops.'
    'OpiesEclipseRedPlugin.jar' = 'Collects Eclipse red at the Hunter Guild.'
    'OpiesMoltenGlassPlugin.jar' = 'Smelts molten glass at the Edgeville furnace.'
    'OpiesFlaxPickerPlugin.jar' = 'Picks flax at Nemus Retreat and can spin it.'
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
    $name = [System.IO.Path]::GetFileNameWithoutExtension($jarName)
    if ($name.StartsWith('Opies')) { $name = $name.Substring(5) }
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
    return (Join-Path $ScriptDir 'opes-plugin-hut.ico')
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
    $dest = Join-Path $script:LauncherDir 'opes-plugin-hut.ico'
    Copy-Item -LiteralPath $source -Destination $dest -Force
    return $dest
}

function Add-DesktopShortcut {
    if (-not (Test-Path -LiteralPath $script:LauncherDir)) {
        New-Item -ItemType Directory -Path $script:LauncherDir | Out-Null
    }
    $vbs = Join-Path $script:LauncherDir 'Open-Installer.vbs'
    $launcher = @(
        "' Opens the latest [OPIE] Plugin Library installer.",
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
    $shortcut.Description = 'Open the [OPIE] Plugin Library installer'
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

function Remove-LibraryJars([string[]] $jarNames) {
    $removed = @()
    foreach ($jarName in $jarNames) {
        foreach ($path in (Get-InstalledJarPaths $jarName)) {
            [System.IO.File]::Delete($path)
            if (Test-Path -LiteralPath $path) {
                throw "Could not delete $path. Close the client and try again."
            }
            $removed += $path
        }
    }
    return @($removed)
}

function Install-Library([string[]] $jarNames) {
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
    foreach ($jarName in $jarNames) {
        $source = Join-Path $DistDir $jarName
        $dest = Join-Path $script:InstallDir $jarName
        [System.IO.File]::Copy($source, $dest, $true)
        $installed += $dest
    }
    return @{
        Removed = $removed
        Installed = $installed
    }
}

function Uninstall-Library([string[]] $jarNames) {
    if (Test-ClientRunning) {
        throw 'Close the game client first so the plugin files can be deleted.'
    }
    if ($jarNames.Count -eq 0) {
        throw 'Select at least one plugin.'
    }
    return @(Remove-LibraryJars $jarNames)
}

function Get-CheckedJarNames($form) {
    $names = New-Object System.Collections.Generic.List[string]
    $cards = $form.Tag['Cards']
    if ($null -eq $cards) { return @() }
    foreach ($card in $cards) {
        if ($card.Tag.Checked -and $card.Tag.Available) {
            [void] $names.Add($card.Tag.JarName)
        }
    }
    return $names.ToArray()
}

function Write-Log($box, [string] $message) {
    if ($null -eq $box) { return }
    $box.AppendText(("[{0}]  {1}{2}" -f (Get-Date -Format 'HH:mm:ss'), $message, [Environment]::NewLine))
    $owner = $box.FindForm()
    if ($null -ne $owner -and $null -ne $owner.Tag -and $null -ne $owner.Tag.StatusLine) {
        $owner.Tag.StatusLine.Text = $message
    }
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
        $button.ForeColor = [System.Drawing.Color]::White
        $button.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(255, 84, 74)
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
        $card.Tag.Checked = -not $card.Tag.Checked
        $card.Invalidate()
        $owner = $card.FindForm()
        if ($null -ne $owner) {
            Update-PluginCardStatus $owner
        }
    }.GetNewClosure())
}

function Build-PluginCards($form) {
    $list = $form.Tag.List
    $cards = $form.Tag.Cards
    $list.Controls.Clear()
    $cards.Clear()
    $names = @(Get-LibraryJarNames)
    $y = 4
    $scrollBar = [System.Windows.Forms.SystemInformation]::VerticalScrollBarWidth
    $cardWidth = $list.ClientSize.Width - 16 - $scrollBar
    if ($cardWidth -lt 480) { $cardWidth = 640 }
    foreach ($jarName in $names) {
        $source = Join-Path $DistDir $jarName
        $available = Test-Path -LiteralPath $source
        $card = New-Object System.Windows.Forms.Panel
        $card.Location = New-Object System.Drawing.Point(8, $y)
        $card.Size = New-Object System.Drawing.Size($cardWidth, 72)
        $card.BackColor = $script:ColorBg
        $card.Cursor = [System.Windows.Forms.Cursors]::Hand
        $card.Tag = @{
            JarName = $jarName
            Checked = [bool] $available
            Available = [bool] $available
            AvailableVersion = (Get-AvailablePluginVersion $jarName)
            HasUpdate = $false
            PillText = $(if ($available) { 'Not installed' } else { 'Missing' })
            PillKind = $(if ($available) { 'off' } else { 'missing' })
        }
        Set-ControlBuffered $card
        $card.Add_Paint({
            param($sender, $e)
            $g = $e.Graphics
            $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
            $bounds = New-Object System.Drawing.Rectangle(0, 0, ($sender.Width - 1), ($sender.Height - 1))
            $path = New-RoundedPath $bounds 12
            $fill = New-Object System.Drawing.SolidBrush $script:ColorCard
            $g.FillPath($fill, $path)
            $pen = New-Object System.Drawing.Pen $script:ColorCardEdge
            $g.DrawPath($pen, $path)
            if ($sender.Tag.Checked) {
                $bar = New-Object System.Drawing.Rectangle(10, 22, 4, 28)
                $barPath = New-RoundedPath $bar 2
                $barBrush = New-Object System.Drawing.SolidBrush $script:ColorAccent
                $g.FillPath($barBrush, $barPath)
                $barBrush.Dispose()
                $barPath.Dispose()
            }
            $box = New-Object System.Drawing.Rectangle(26, 25, 22, 22)
            $boxPath = New-RoundedPath $box 6
            if ($sender.Tag.Checked) {
                $checkBrush = New-Object System.Drawing.SolidBrush $script:ColorAccent
                $g.FillPath($checkBrush, $boxPath)
                $checkBrush.Dispose()
                $mark = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), 2
                $g.DrawLine($mark, 31, 36, 36, 41)
                $g.DrawLine($mark, 36, 41, 44, 30)
                $mark.Dispose()
            } else {
                $empty = New-Object System.Drawing.Pen $script:ColorMuted
                $g.DrawPath($empty, $boxPath)
                $empty.Dispose()
            }
            $pillText = [string] $sender.Tag.PillText
            if ($pillText) {
                $pillFont = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
                $pillSize = $g.MeasureString($pillText, $pillFont)
                $pillH = 26
                $pillW = [Math]::Max($pillH, ([int] [Math]::Ceiling($pillSize.Width) + 18))
                $pillX = $sender.Width - $pillW - 16
                $pillY = [int] (($sender.Height - $pillH) / 2)
                $pillBounds = New-Object System.Drawing.Rectangle($pillX, $pillY, $pillW, $pillH)
                $pillPath = New-RoundedPath $pillBounds 13
                $kind = [string] $sender.Tag.PillKind
                if ($kind -eq 'installed') {
                    $pillBack = [System.Drawing.Color]::FromArgb(16, 48, 36)
                    $pillFore = $script:ColorInstalled
                } elseif ($kind -eq 'update' -or $kind -eq 'missing') {
                    $pillBack = [System.Drawing.Color]::FromArgb(48, 36, 12)
                    $pillFore = $script:ColorWarning
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
            $boxPath.Dispose()
        })
        $card.Add_Click({
            param($sender, $e)
            if (-not $sender.Tag.Available) { return }
            $sender.Tag.Checked = -not $sender.Tag.Checked
            $sender.Invalidate()
            $owner = $sender.FindForm()
            if ($null -ne $owner) {
                Update-PluginCardStatus $owner
            }
        })

        $name = New-Object System.Windows.Forms.Label
        $name.Text = Get-PluginLabel $jarName
        $name.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 12)
        $name.ForeColor = $script:ColorText
        $name.BackColor = $script:ColorCard
        $name.AutoSize = $true
        $name.Location = New-Object System.Drawing.Point(60, 14)
        Add-CardToggle $name $card

        $detail = New-Object System.Windows.Forms.Label
        $detail.Name = 'detail'
        $blurb = $script:PluginBlurbs[$jarName]
        if (-not $blurb) { $blurb = 'Library plugin' }
        $detail.Text = $blurb
        if (-not $available) { $name.ForeColor = $script:ColorMuted }
        $detail.Font = New-Object System.Drawing.Font('Segoe UI', 9)
        $detail.ForeColor = $script:ColorMuted
        $detail.BackColor = $script:ColorCard
        $detail.AutoSize = $false
        $detail.Size = New-Object System.Drawing.Size(($cardWidth - 230), 22)
        $detail.Location = New-Object System.Drawing.Point(60, 38)
        Add-CardToggle $detail $card

        $card.Controls.AddRange(@($name, $detail))
        $list.Controls.Add($card)
        [void] $cards.Add($card)
        $y += 84
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
        } elseif (Test-PluginNeedsUpdate $jarName) {
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
        $card.Invalidate()
    }
    $checked = @(Get-CheckedJarNames $form)
    $form.Tag.InstallButton.Enabled = $checked.Count -gt 0
    if ($null -ne $form.Tag.UninstallButton) {
        $form.Tag.UninstallButton.Enabled = $checked.Count -gt 0
        if ($checked.Count -gt 0) {
            $form.Tag.UninstallButton.ForeColor = $script:ColorMuted
        }
    }
    $running = Test-ClientRunning
    if ($null -ne $form.Tag.Warning -and ($form.Tag.Warning.Visible -ne $running)) {
        $form.Tag.Warning.Visible = $running
        Update-LibraryLayout $form
    }
}

function Invoke-InstallPlugins($form, $log, [string[]] $names) {
    if ($names.Count -eq 0) { return $false }
    if (Test-ClientRunning) {
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            'Close the game client first so the old plugin files can be deleted.',
            'Client is running',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
        return $false
    }
    try {
        $hadUpdates = @($names | Where-Object { Test-PluginNeedsUpdate $_ }).Count -gt 0
        $result = Install-Library $names
        foreach ($path in $result.Removed) { Write-Log $log "Removed old copy: $path" }
        foreach ($path in $result.Installed) { Write-Log $log "Installed: $path" }
        Write-Log $log 'Restart the client, then enable the [OPIE] plugins you want.'
        $title = if ($hadUpdates) { 'Installed / updated' } else { 'Installed' }
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            "Installed or updated $($result.Installed.Count) plugin(s).`r`n`r`nRestart the client before using them.",
            $title,
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
        return $true
    } catch {
        Write-Log $log $_.Exception.Message
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            $_.Exception.Message,
            'Install failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
        return $false
    } finally {
        Update-PluginCardStatus $form
    }
}

function Get-OutdatedLibraryJars {
    foreach ($jarName in @(Get-LibraryJarNames)) {
        if (Test-PluginNeedsUpdate $jarName) {
            $jarName
        }
    }
}

function Invoke-AutomaticUpdates($form, $log) {
    if ($script:PendingUpdateApplied) { return }
    $outdated = @(Get-OutdatedLibraryJars)
    if ($outdated.Count -eq 0) { return }
    $labels = @($outdated | ForEach-Object { Get-PluginLabel $_ })
    $listText = $labels -join ', '
    $clientRunning = Test-ClientRunning
    if (-not $script:UpdateNoticeShown) {
        $script:UpdateNoticeShown = $true
        if ($clientRunning) {
            $message = if ($labels.Count -eq 1) {
                "$listText has an update.`r`n`r`nClose the game client. The old jar will be replaced when the client is closed."
            } else {
                "These plugins have updates: $listText.`r`n`r`nClose the game client. The old jars will be replaced when the client is closed."
            }
        } else {
            $message = if ($labels.Count -eq 1) {
                "$listText has an update. The old jar will be replaced with the latest version."
            } else {
                "These plugins have updates: $listText.`r`n`r`nThe old jars will be replaced with the latest versions."
            }
        }
        Write-Log $log (($message -replace "`r`n", ' ').Trim())
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            $message,
            'Update available',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
        if ($clientRunning) { return }
    }
    if (Test-ClientRunning) { return }
    $script:PendingUpdateApplied = $true
    try {
        $result = Install-Library $outdated
        foreach ($path in $result.Removed) { Write-Log $log "Removed old copy: $path" }
        foreach ($path in $result.Installed) { Write-Log $log "Updated: $path" }
        $done = if ($labels.Count -eq 1) {
            "$listText was updated to the latest version. The old jar was removed.`r`n`r`nRestart the client before using it."
        } else {
            "Updated $($labels.Count) plugins: $listText. The old jars were removed.`r`n`r`nRestart the client before using them."
        }
        Write-Log $log 'Updated installed plugins. Restart the client before using them.'
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            $done,
            'Updated',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
    } catch {
        $script:PendingUpdateApplied = $false
        Write-Log $log $_.Exception.Message
        [System.Windows.Forms.MessageBox]::Show(
            $form,
            $_.Exception.Message,
            'Update failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    } finally {
        Update-PluginCardStatus $form
    }
}

function Update-LibraryLayout($form) {
    if ($null -eq $form -or $null -eq $form.Tag -or $null -eq $form.Tag.List) { return }
    $w = $form.ClientSize.Width
    $h = $form.ClientSize.Height
    $headerH = 120
    $bannerH = 0
    if ($form.Tag.Warning.Visible) { $bannerH = 40 }
    $toolbarH = 32
    $statusH = 28
    $logH = 0
    if ($form.Tag.DetailsOpen) { $logH = 100 }
    $footerH = 64

    $form.Tag.Header.SetBounds(0, 0, $w, $headerH)
    if ($null -ne $form.Tag.GitHubLink) {
        $form.Tag.GitHubLink.Location = New-Object System.Drawing.Point(210, 86)
        $form.Tag.MicrobotLink.Location = New-Object System.Drawing.Point(($form.Tag.GitHubLink.Right + 16), 86)
    }
    $form.Tag.Warning.SetBounds(0, $headerH, $w, $bannerH)
    $y = $headerH + $bannerH + 8
    $form.Tag.SelectAll.Location = New-Object System.Drawing.Point(28, ($y + 2))
    $form.Tag.ClearSelection.Location = New-Object System.Drawing.Point(108, ($y + 2))

    $listTop = $y + $toolbarH
    $listBottom = $h - $footerH - $statusH - $logH - 4
    $listH = [Math]::Max(180, ($listBottom - $listTop))
    $form.Tag.List.SetBounds(16, $listTop, ($w - 32), $listH)

    $statusY = $form.Tag.List.Bottom + 8
    $form.Tag.StatusLine.SetBounds(28, $statusY, ($w - 150), 22)
    $form.Tag.DetailsLink.Location = New-Object System.Drawing.Point(($w - 108), $statusY)
    $form.Tag.Log.Visible = [bool] $form.Tag.DetailsOpen
    if ($form.Tag.DetailsOpen) {
        $form.Tag.Log.SetBounds(28, ($statusY + $statusH), ($w - 56), $logH)
    }

    $btnY = $h - 52
    $form.Tag.ShortcutLink.Location = New-Object System.Drawing.Point(28, ($btnY + 10))
    $install = $form.Tag.InstallButton
    $install.Location = New-Object System.Drawing.Point(($w - 28 - $install.Width), $btnY)
    $close = $form.Tag.CloseButton
    $close.Location = New-Object System.Drawing.Point(($install.Left - $close.Width - 20), ($btnY + 10))
    $uninstall = $form.Tag.UninstallButton
    $uninstall.Location = New-Object System.Drawing.Point(($close.Left - $uninstall.Width - 18), ($btnY + 10))
}

function New-LibraryForm {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '[OPIE] Plugin Library'
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedSingle'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $form.ClientSize = New-Object System.Drawing.Size(760, 620)
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
    $mark.Size = New-Object System.Drawing.Size(40, 40)
    $mark.Location = New-Object System.Drawing.Point(28, 32)
    $mark.SizeMode = [System.Windows.Forms.PictureBoxSizeMode]::Zoom
    $mark.BackColor = $script:ColorBg
    $markImage = $null
    $markStream = $null
    $markPath = Join-Path $ScriptDir 'opes-plugin-hut.png'
    if (Test-Path -LiteralPath $markPath) {
        $markStream = Open-UnlockedStream $markPath
        $markImage = [System.Drawing.Image]::FromStream($markStream)
        $mark.Image = $markImage
    }

    $title = New-Object System.Windows.Forms.Label
    $title.Text = 'Plugin Library'
    $title.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 20)
    $title.ForeColor = $script:ColorText
    $title.BackColor = $script:ColorBg
    $title.AutoSize = $true
    $title.Location = New-Object System.Drawing.Point(80, 26)

    $subtitle = New-Object System.Windows.Forms.Label
    $subtitle.Text = 'Choose the plugins to install. Updates replace the old jar when the client is closed.'
    $subtitle.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $subtitle.ForeColor = $script:ColorMuted
    $subtitle.BackColor = $script:ColorBg
    $subtitle.AutoSize = $false
    $subtitle.Size = New-Object System.Drawing.Size(640, 22)
    $subtitle.Location = New-Object System.Drawing.Point(82, 62)

    $teamLabel = New-Object System.Windows.Forms.Label
    $teamLabel.Text = $script:TeamName
    $teamLabel.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $teamLabel.ForeColor = $script:ColorMuted
    $teamLabel.BackColor = $script:ColorBg
    $teamLabel.AutoSize = $true
    $teamLabel.Location = New-Object System.Drawing.Point(82, 86)

    $githubLink = New-BrandLink 'GitHub' $script:TeamGitHubUri
    $githubLink.Location = New-Object System.Drawing.Point(210, 86)
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
    $warning.Text = 'Close the game client before installing or removing plugins.'
    $warning.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
    $warning.Padding = New-Object System.Windows.Forms.Padding(28, 0, 16, 0)
    $warning.ForeColor = $script:ColorWarning
    $warning.BackColor = [System.Drawing.Color]::FromArgb(42, 32, 12)
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

    $shortcutLink = New-Object System.Windows.Forms.Label
    $shortcutLink.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $shortcutLink.ForeColor = $script:ColorMuted
    $shortcutLink.BackColor = $script:ColorBg
    $shortcutLink.AutoSize = $true
    $shortcutLink.Cursor = [System.Windows.Forms.Cursors]::Hand
    Update-ShortcutLink $shortcutLink

    $installButton = New-SetupButton 'Install selected' 'primary'
    $installButton.Size = New-Object System.Drawing.Size(168, 40)
    $installButton.Add_EnabledChanged({
        param($sender, $e)
        if ($sender.Enabled) {
            $sender.BackColor = $script:ColorAccent
            $sender.ForeColor = [System.Drawing.Color]::White
        } else {
            $sender.BackColor = $script:ColorCardEdge
            $sender.ForeColor = $script:ColorMuted
        }
    })

    $uninstallButton = New-Object System.Windows.Forms.Label
    $uninstallButton.Text = 'Uninstall'
    $uninstallButton.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $uninstallButton.ForeColor = $script:ColorMuted
    $uninstallButton.BackColor = $script:ColorBg
    $uninstallButton.AutoSize = $true
    $uninstallButton.Cursor = [System.Windows.Forms.Cursors]::Hand

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
        SelectAll = $selectAll
        ClearSelection = $clearSelection
        List = $list
        InstallButton = $installButton
        UninstallButton = $uninstallButton
        CloseButton = $closeButton
        ShortcutLink = $shortcutLink
        Warning = $warning
        StatusLine = $statusLine
        DetailsLink = $detailsLink
        Log = $log
        DetailsOpen = $false
        MarkImage = $markImage
        MarkStream = $markStream
        IconStream = $iconStream
        TeamLabel = $teamLabel
        GitHubLink = $githubLink
        MicrobotLink = $microbotLink
    }

    $selectAll.Add_Click({
        foreach ($card in $form.Tag['Cards']) {
            if ($card.Tag.Available) { $card.Tag.Checked = $true }
            $card.Invalidate()
        }
        Update-PluginCardStatus $form
    }.GetNewClosure())

    $clearSelection.Add_Click({
        foreach ($card in $form.Tag['Cards']) {
            $card.Tag.Checked = $false
            $card.Invalidate()
        }
        Update-PluginCardStatus $form
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
        } catch {
            Write-Log $log $_.Exception.Message
            [System.Windows.Forms.MessageBox]::Show(
                $form,
                $_.Exception.Message,
                'Desktop shortcut failed',
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
        }
        Update-ShortcutLink $shortcutLink
        Update-LibraryLayout $form
    }.GetNewClosure())

    $installButton.Add_Click({
        Update-PluginCardStatus $form
        $names = @(Get-CheckedJarNames $form)
        if ($names.Count -eq 0) { return }
        [void] (Invoke-InstallPlugins $form $log $names)
    }.GetNewClosure())

    $uninstallButton.Add_Click({
        Update-PluginCardStatus $form
        if (Test-ClientRunning) {
            [System.Windows.Forms.MessageBox]::Show(
                $form,
                'Close the game client first so the plugin files can be deleted.',
                'Client is running',
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
            return
        }
        try {
            $names = @(Get-CheckedJarNames $form)
            $present = @($names | Where-Object { (Get-InstalledCopy $_).Count -gt 0 })
            if ($present.Count -eq 0) {
                Write-Log $log 'None of the selected plugins are installed.'
                [System.Windows.Forms.MessageBox]::Show(
                    $form,
                    'None of the selected plugins are installed.',
                    'Already uninstalled',
                    [System.Windows.Forms.MessageBoxButtons]::OK,
                    [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
                return
            }
            $removed = @(Uninstall-Library $names)
            foreach ($path in $removed) { Write-Log $log "Uninstalled: $path" }
            Write-Log $log 'Other plugins were left in place.'
            [System.Windows.Forms.MessageBox]::Show(
                $form,
                "Uninstalled $($removed.Count) file(s). Other plugins were left in place.",
                'Uninstalled',
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
        } catch {
            Write-Log $log $_.Exception.Message
            [System.Windows.Forms.MessageBox]::Show(
                $form,
                $_.Exception.Message,
                'Uninstall failed',
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
        }
        Update-PluginCardStatus $form
    }.GetNewClosure())

    $header.Controls.AddRange(@($mark, $title, $subtitle, $teamLabel, $githubLink, $microbotLink))
    $form.Controls.AddRange(@(
        $header, $warning, $selectAll, $clearSelection, $list, $statusLine, $detailsLink, $log,
        $shortcutLink, $uninstallButton, $closeButton, $installButton
    ))
    Update-LibraryLayout $form

    $timer = New-Object System.Windows.Forms.Timer
    $timer.Interval = 1500
    $timer.Add_Tick({
        Update-PluginCardStatus $form
        Invoke-AutomaticUpdates $form $log
    }.GetNewClosure())
    $form.Add_Shown({
        Update-LibraryLayout $form
        Build-PluginCards $form
        Update-PluginCardStatus $form
        if (Test-DesktopShortcut) {
            Update-ExistingShortcutIcon
        }
        Update-ShortcutLink $shortcutLink
        Write-Log $log 'Choose the plugins you want. Unchecked plugins are left alone.'
        if (Test-DesktopShortcut) {
            Write-Log $log 'Desktop shortcut is on the desktop.'
        } else {
            Write-Log $log 'Add a desktop shortcut if you want to open this installer later.'
        }
        Invoke-AutomaticUpdates $form $log
        $timer.Start()
    }.GetNewClosure())
    $null = $form.Add_FormClosed({
        $timer.Stop()
        $timer.Dispose()
        if ($null -ne $form.Icon) { $form.Icon.Dispose() }
        if ($null -ne $form.Tag.IconStream) { $form.Tag.IconStream.Dispose() }
        if ($null -ne $form.Tag.MarkImage) { $form.Tag.MarkImage.Dispose() }
        if ($null -ne $form.Tag.MarkStream) { $form.Tag.MarkStream.Dispose() }
    }.GetNewClosure())

    return $form
}

function Hide-SetupConsole {
    $hwnd = [OpieSetupWindow]::GetConsoleWindow()
    if ($hwnd -ne [IntPtr]::Zero) {
        [void] [OpieSetupWindow]::ShowWindow($hwnd, 0)
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
            'Plugin Library setup failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    })
    try {
        $form = New-LibraryForm
        [void] $form.ShowDialog()
        $form.Dispose()
    } catch {
        [System.Windows.Forms.MessageBox]::Show(
            $_.Exception.Message,
            'Plugin Library setup failed',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    Show-LibrarySetup
}
