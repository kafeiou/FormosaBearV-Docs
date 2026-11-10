# FormosaBearV Portable Distribution Packaging Script (PowerShell)
param(
    [string]$JettyZipPath = "$PSScriptRoot\..\tools\jetty\jetty-home-12.1.14.zip",
    [string]$WarFilePath = "$PSScriptRoot\..\docs-web\target\docs-web-2.04.war",
    [string]$OutputDirectory = "$PSScriptRoot\..\target",
    [string]$OutputZipName = "FormosaBearV-docs-2.04-portable.zip"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$JettyZip = (Resolve-Path $JettyZipPath -ErrorAction SilentlyContinue)
if (-not $JettyZip) {
    $JettyZip = [System.IO.Path]::GetFullPath("$ProjectRoot\tools\jetty\jetty-home-12.1.14.zip")
} else {
    $JettyZip = $JettyZip.Path
}
$WarFile = (Resolve-Path $WarFilePath -ErrorAction SilentlyContinue)
if (-not $WarFile) {
    $WarFile = [System.IO.Path]::GetFullPath("$ProjectRoot\docs-web\target\docs-web-2.04.war")
} else {
    $WarFile = $WarFile.Path
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  FormosaBearV (v2.04) Portable Package Builder" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Project Root: $ProjectRoot"

# 1. Check or Download Base Jetty ZIP
$JettyDir = Split-Path $JettyZip -Parent
if (-not (Test-Path $JettyDir)) {
    New-Item -ItemType Directory -Path $JettyDir -Force | Out-Null
}

if (-not (Test-Path $JettyZip)) {
    Write-Host "[1/5] Base Jetty ZIP not found at $JettyZip. Downloading from Maven Central..." -ForegroundColor Yellow
    & mvn dependency:get "-Dartifact=org.eclipse.jetty:jetty-home:12.1.14:zip" -Dtransitive=false
    $M2Zip = "$env:USERPROFILE\.m2\repository\org\eclipse\jetty\jetty-home\12.1.14\jetty-home-12.1.14.zip"
    if (Test-Path $M2Zip) {
        Copy-Item -Path $M2Zip -Destination $JettyZip -Force
        Write-Host "       Successfully cached Jetty ZIP to $JettyZip" -ForegroundColor Green
    } else {
        throw "Failed to acquire jetty-home-12.1.14.zip from Maven repository."
    }
} else {
    Write-Host "[1/5] Using base Jetty ZIP: $JettyZip" -ForegroundColor Green
}

# 2. Check or Build docs-web WAR
if (-not (Test-Path $WarFile)) {
    Write-Host "[2/5] docs-web-2.04.war not found. Building project with Maven..." -ForegroundColor Yellow
    Push-Location $ProjectRoot
    try {
        & mvn clean package -DskipTests
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $WarFile)) {
        throw "Maven build finished but $WarFile was not generated."
    }
} else {
    Write-Host "[2/5] Using existing WAR: $WarFile" -ForegroundColor Green
}

# 3. Assemble Portable Staging Directory
$StagingRoot = "$OutputDirectory\portable-staging"
$BundleDir = "$StagingRoot\FormosaBearV-docs-2.04-portable"

if (Test-Path $StagingRoot) {
    Remove-Item -Path $StagingRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $BundleDir -Force | Out-Null

Write-Host "[3/5] Extracting base Jetty 12 server..." -ForegroundColor Yellow
$TempExtract = "$StagingRoot\temp-jetty"
New-Item -ItemType Directory -Path $TempExtract -Force | Out-Null
Expand-Archive -Path $JettyZip -DestinationPath $TempExtract -Force

$ExtractedHome = Get-ChildItem -Path $TempExtract -Directory | Select-Object -First 1
if (-not $ExtractedHome) {
    throw "Invalid Jetty ZIP: no root directory found."
}
Move-Item -Path $ExtractedHome.FullName -Destination "$BundleDir\jetty-home"
Remove-Item -Path $TempExtract -Recurse -Force

Write-Host "[4/5] Initializing Jetty base modules & deploying application..." -ForegroundColor Yellow
Push-Location $BundleDir
try {
    # Find Java 21+ for start.d initialization
    $CandidateJdks = @()
    if ($env:JAVA_HOME) { $CandidateJdks += "$env:JAVA_HOME\bin\java.exe" }
    $CandidateJdks += @(
        "W:\developer\jdk-21.0.9\bin\java.exe",
        "W:\developer\jdk-21\bin\java.exe",
        "$ProjectRoot\jdk-21.0.9\bin\java.exe",
        "$ProjectRoot\jdk-21\bin\java.exe",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-21*\bin\java.exe",
        "$env:ProgramFiles\Java\jdk-21*\bin\java.exe"
    )
    $JavaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($JavaCmd) { $CandidateJdks += $JavaCmd.Source }

    $JavaBin = $null
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        foreach ($cand in $CandidateJdks) {
            $resolvedList = Resolve-Path $cand -ErrorAction SilentlyContinue
            foreach ($r in $resolvedList) {
                if (Test-Path $r.Path) {
                    $verOutput = cmd /c "`"$($r.Path)`" -version 2>&1"
                    if ($verOutput -match 'version "(2[1-9]|[3-9][0-9])\.') {
                        $JavaBin = $r.Path
                        break
                    }
                }
            }
            if ($JavaBin) { break }
        }
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if (-not $JavaBin) {
        throw "Could not find a valid Java 21+ runtime for Jetty 12 initialization. Please set JAVA_HOME to JDK 21+."
    }
    Write-Host "       Using Java runtime for initialization: $JavaBin" -ForegroundColor Gray
    & $JavaBin -jar "jetty-home\start.jar" --create-startd --add-modules=ee10-deploy,http
    if (-not (Test-Path "$BundleDir\start.d")) {
        throw "Failed to initialize start.d directory in $BundleDir"
    }
} finally {
    Pop-Location
}

# Copy ROOT.war
if (-not (Test-Path "$BundleDir\webapps")) {
    New-Item -ItemType Directory -Path "$BundleDir\webapps" -Force | Out-Null
}
Copy-Item -Path $WarFile -Destination "$BundleDir\webapps\ROOT.war" -Force

# Create data directory
New-Item -ItemType Directory -Path "$BundleDir\data" -Force | Out-Null

# Copy runtime scripts and README from templates
$TemplatesDir = "$ProjectRoot\scripts\templates"
if (Test-Path $TemplatesDir) {
    Copy-Item -Path "$TemplatesDir\*" -Destination $BundleDir -Force
}

# 5. Compress to Final Output ZIP
Write-Host "[5/5] Compressing portable distribution archive..." -ForegroundColor Yellow
$FinalZip = "$OutputDirectory\$OutputZipName"
if (Test-Path $FinalZip) {
    Remove-Item -Path $FinalZip -Force
}

Compress-Archive -Path "$BundleDir\*" -DestinationPath $FinalZip -Force

# Also sync to unzipped directory in output directory for immediate local use
$UnzippedTarget = "$OutputDirectory\FormosaBearV-docs-2.04-portable"
Write-Host "       Updating extracted folder: $UnzippedTarget..." -ForegroundColor Gray
if (Test-Path $UnzippedTarget) {
    Remove-Item -Path $UnzippedTarget -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $UnzippedTarget -Force | Out-Null
Copy-Item -Path "$BundleDir\*" -Destination $UnzippedTarget -Recurse -Force

# Clean up staging directory
Remove-Item -Path $StagingRoot -Recurse -Force

$ZipSizeMB = [math]::Round((Get-Item $FinalZip).Length / 1MB, 2)
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  打包成功 (SUCCESS) !" -ForegroundColor Green
Write-Host "  輸出檔案: $FinalZip" -ForegroundColor Green
Write-Host "  檔案大小: $ZipSizeMB MB" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
