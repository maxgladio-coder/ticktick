param([string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @(
    $env:JAVA_HOME,
    'C:\Program Files\Android\Android Studio\jbr',
    'C:\Program Files\JetBrains\PyCharm 2025.3.3\jbr'
)
$javaRoot = $javaCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
if (-not $javaRoot) { throw '未找到 JDK；请安装 Android Studio 或设置 JAVA_HOME。' }
$sdkRoot = Join-Path $projectRoot '.tools\android-sdk'
if (-not (Test-Path (Join-Path $sdkRoot 'platforms\android-36\android.jar'))) {
    throw '未找到项目内 Android SDK 36。请使用 Android Studio 配置 SDK，然后直接运行 gradlew.bat。'
}
$previousValues = @{}
foreach ($name in @('JAVA_HOME', 'GRADLE_USER_HOME', 'ANDROID_HOME', 'ANDROID_USER_HOME', 'JAVA_TOOL_OPTIONS')) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $javaRoot
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.android'
    $socketDirectory = Join-Path $projectRoot '.tools\tmp'
    New-Item -ItemType Directory -Force $socketDirectory | Out-Null
    # Avoid Windows packaged-app temporary directory remapping breaking JDK AF_UNIX sockets.
    $env:JAVA_TOOL_OPTIONS = "$($previousValues['JAVA_TOOL_OPTIONS']) `"-Djdk.net.unixdomain.tmpdir=$socketDirectory`" `"-Djava.io.tmpdir=$socketDirectory`""
    & .\gradlew.bat @Tasks --no-daemon --console=plain
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -eq 0 -and ($Tasks -contains ':app:assembleDebug' -or $Tasks -contains 'assembleDebug')) {
        & (Join-Path $PSScriptRoot 'package-apk.ps1')
    }
} finally {
    Pop-Location
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
}
exit $buildExitCode
