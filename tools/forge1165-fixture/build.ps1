<#
  Builds the TEST-ONLY MCphone 1.16.5 / Forge 36 phone-slot bridge fixture.

  Compiles with the Zulu JDK 8 bundled in .build-tools against the server SRG jar, the Forge jars
  and the addon mod jars, then packages a plain mod jar (no reobf step is needed: the sources are
  written in SRG names already).

  Usage:
    powershell -ExecutionPolicy Bypass -File .\build.ps1
    powershell -ExecutionPolicy Bypass -File .\build.ps1 -Deploy

  -Deploy copies the jar into <addons>/mods so the next server start picks it up.
#>
param(
    [switch]$Deploy,
    [string]$AddonsRoot = '',
    [string]$JavaHome = '',
    [string]$ModsDir = ''
)

$ErrorActionPreference = 'Stop'

# javac emits its diagnostics in the OS ANSI codepage; decode native output the same way so the log
# stays readable on a Chinese Windows install.
try { [Console]::OutputEncoding = [System.Text.Encoding]::Default } catch { }

$here = $PSScriptRoot
$buildTools = Split-Path -Parent $here
if ([string]::IsNullOrWhiteSpace($AddonsRoot)) {
    $AddonsRoot = Join-Path $buildTools 'forge1165-addons'
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = Join-Path $buildTools 'zulu8.98.0.201-ca-jdk8.0.514-win_x64'
}
if ([string]::IsNullOrWhiteSpace($ModsDir)) {
    $ModsDir = Join-Path $AddonsRoot 'mods'
}

$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$java = Join-Path $JavaHome 'bin\java.exe'

foreach ($tool in @($javac, $jar)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "Missing Java 8 tool: $tool"
    }
}
if (-not (Test-Path -LiteralPath $AddonsRoot)) {
    throw "Missing server root: $AddonsRoot"
}

$src = Join-Path $here 'main\java'
$res = Join-Path $here 'main\resources'
$classes = Join-Path $here 'build\classes'
$dist = Join-Path $here 'dist'
$logs = Join-Path $here 'logs'
$log = Join-Path $logs 'build.log'
$jarName = 'mcphone-1165-fixture-1.0.0.jar'
$jarPath = Join-Path $dist $jarName

New-Item -ItemType Directory -Force -Path $classes, $dist, $logs | Out-Null
$resolvedClasses = [IO.Path]::GetFullPath($classes)
$resolvedFixture = [IO.Path]::GetFullPath($here).TrimEnd('\') + '\'
if (-not $resolvedClasses.StartsWith($resolvedFixture, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean outside fixture directory: $resolvedClasses"
}
Remove-Item -Recurse -Force -LiteralPath $classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes | Out-Null

# Compile against Forge's PATCHED Minecraft (forge-*-server.jar). The -srg and -slim splits are
# unpatched/partial: their Item does not extend ForgeRegistryEntry and their MinecraftServer lacks
# func_241755_D_, so letting either win on the classpath breaks the build. netty/authlib come from
# the -extra split.
$cpJars = New-Object System.Collections.Generic.List[string]
$libraries = Join-Path $AddonsRoot 'libraries'
$patched = Get-ChildItem -Recurse -File -LiteralPath $libraries -Filter 'forge-*-server.jar' |
    Select-Object -First 1
if ($null -eq $patched) {
    throw "Missing patched Minecraft jar (libraries/**/forge-*-server.jar) under $AddonsRoot"
}
$cpJars.Add($patched.FullName)
# The vanilla SRG split supplies classes the patched jar does not carry (for example
# net.minecraft.profiler.ISnooperInfo). It must come after the patched jar so its unpatched Item
# cannot shadow the Forge-patched one.
$vanilla = Get-ChildItem -Recurse -File -LiteralPath $libraries -Filter 'server-*-srg.jar' |
    Select-Object -First 1
if ($null -ne $vanilla) {
    $cpJars.Add($vanilla.FullName)
}
Get-ChildItem -Recurse -File -LiteralPath $libraries -Filter *.jar |
    Where-Object {
        $_.Name -notlike '*-slim.jar' -and
        $_.Name -notlike 'minecraft_server*.jar'
    } |
    Sort-Object FullName | ForEach-Object { $cpJars.Add($_.FullName) }
Get-ChildItem -File -LiteralPath $AddonsRoot -Filter 'forge-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object FullName | ForEach-Object { $cpJars.Add($_.FullName) }
if (Test-Path -LiteralPath $ModsDir) {
    Get-ChildItem -File -LiteralPath $ModsDir -Filter *.jar |
        Sort-Object FullName | ForEach-Object { $cpJars.Add($_.FullName) }
}
$classpath = ($cpJars | Select-Object -Unique) -join ';'

$sources = Get-ChildItem -Recurse -File -LiteralPath $src -Filter *.java | Sort-Object FullName
if ($sources.Count -eq 0) {
    throw "No Java sources under $src"
}

# The paths contain non-ASCII characters, so the source list is passed as real argv entries rather
# than through a @argfile (which javac reads with the platform charset and would mangle).
$sourcePaths = $sources | ForEach-Object { $_.FullName }

"== javac ==" | Set-Content -LiteralPath $log -Encoding UTF8
$javacOutput = & $javac -encoding UTF-8 -nowarn -source 8 -target 8 `
    -cp $classpath -d $classes $sourcePaths 2>&1
$javacExit = $LASTEXITCODE
$javacOutput | Out-String | Write-Host
$javacOutput | Out-File -FilePath $log -Encoding UTF8 -Append
if ($javacExit -ne 0) {
    throw "javac failed with exit code $javacExit; see $log"
}

Copy-Item -Recurse -Force -Path (Join-Path $res '*') -Destination $classes

if (Test-Path -LiteralPath $jarPath) {
    Remove-Item -Force -LiteralPath $jarPath
}
"== jar ==" | Add-Content -LiteralPath $log -Encoding ASCII
& $jar cf $jarPath -C $classes . 2>&1 | Tee-Object -FilePath $log -Append
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE; see $log"
}

$entryCount = (& $jar tf $jarPath | Measure-Object).Count
$size = (Get-Item -LiteralPath $jarPath).Length

# Read the JDK version from the release file rather than probing javac/java: both print their banner
# on stderr, which PowerShell turns into a NativeCommandError record.
$javaVersion = 'unknown'
$releaseFile = Join-Path $JavaHome 'release'
if (Test-Path -LiteralPath $releaseFile) {
    $match = Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="(.*)"$'
    if ($match) { $javaVersion = $match.Matches[0].Groups[1].Value }
}

$verify = @()
$verify += "artifact : $jarPath"
$verify += "bytes    : $size"
$verify += "entries  : $entryCount"
$verify += "jdk      : $javaVersion ($JavaHome)"
$verify += ""
$verify += "META-INF/mods.toml present : $((& $jar tf $jarPath | Select-String -SimpleMatch 'META-INF/mods.toml').Count -gt 0)"
$verify += "FixtureMod.class present   : $((& $jar tf $jarPath | Select-String -SimpleMatch 'mcphone1165fixture/FixtureMod.class').Count -gt 0)"
$verify | Tee-Object -FilePath $log -Append

if ($Deploy) {
    if (-not (Test-Path -LiteralPath $ModsDir)) {
        throw "Missing mods directory: $ModsDir"
    }
    Copy-Item -Force -LiteralPath $jarPath -Destination (Join-Path $ModsDir $jarName)
    "deployed : $(Join-Path $ModsDir $jarName)" | Tee-Object -FilePath $log -Append
}

Write-Host ''
Write-Host "Built $jarPath"
if (-not $Deploy) {
    Write-Host "Not deployed. Re-run with -Deploy, or copy the jar into $ModsDir yourself."
}
