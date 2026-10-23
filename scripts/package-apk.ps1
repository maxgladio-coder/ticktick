param([string]$MetadataPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $MetadataPath) {
    $MetadataPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\output-metadata.json'
}
$metadataFile = Get-Item -LiteralPath $MetadataPath
$metadata = Get-Content -LiteralPath $metadataFile.FullName -Raw | ConvertFrom-Json
$outputs = @($metadata.elements)
if ($outputs.Count -ne 1 -or @($outputs[0].filters).Count -ne 0) {
    throw '发布需要唯一的通用 APK，不能混用多个分包。'
}
$version = $outputs[0].versionName
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'APK 版本名必须为三段数字。' }
$sourceApk = Join-Path $metadataFile.DirectoryName $outputs[0].outputFile
$outputDirectory = Join-Path $projectRoot 'app\build\outputs\publish'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$destinationApk = Join-Path $outputDirectory "ticktick-$version.apk"
Copy-Item -LiteralPath $sourceApk -Destination $destinationApk -Force
$sourceHash = (Get-FileHash -LiteralPath $sourceApk -Algorithm SHA256).Hash
if ((Get-FileHash -LiteralPath $destinationApk -Algorithm SHA256).Hash -ne $sourceHash) {
    throw '发布 APK 的文件校验失败。'
}
Write-Output "发布安装包：$destinationApk"
