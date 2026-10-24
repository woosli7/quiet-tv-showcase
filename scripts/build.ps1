param(
    [string]$AndroidJar = $env:QUIETTV_ANDROID_JAR,
    [string]$BuildTools = $env:QUIETTV_BUILD_TOOLS,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Preview
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (!$AndroidJar -or !$BuildTools -or !$JavaHome) {
    throw 'Set QUIETTV_ANDROID_JAR, QUIETTV_BUILD_TOOLS, and JAVA_HOME, or pass -AndroidJar, -BuildTools, and -JavaHome. See docs/BUILDING.md.'
}
$AndroidJar = (Resolve-Path -LiteralPath $AndroidJar).Path
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
$javaBin = Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin'
foreach ($file in @("$javaBin\java.exe", "$javaBin\javac.exe", "$javaBin\keytool.exe", "$BuildTools\aapt2.exe", "$BuildTools\zipalign.exe", "$BuildTools\lib\d8.jar", "$BuildTools\lib\apksigner.jar")) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Required tool missing: $file" }
}
# A unique build folder avoids mixing old class files into a fresh build.
$out = Join-Path $root ('build\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$out\classes", "$out\dex" -Force | Out-Null
$sourcePaths = @(Get-ChildItem "$root\app\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$javaBin\javac.exe" -source 8 -target 8 -classpath $AndroidJar -d "$out\classes" $sourcePaths
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& "$BuildTools\aapt2.exe" compile --dir "$root\app\res" -o "$out\resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
$manifest = "$root\app\AndroidManifest.xml"
if ($Preview) {
    $previewManifest = [IO.File]::ReadAllText($manifest).Replace('package="com.quiettv.home"','package="com.quiettv.preview"').Replace('android:name=".','android:name="com.quiettv.home.').Replace('com.quiettv.home.permission.', 'com.quiettv.preview.permission.').Replace('android:label="Quiet TV"','android:label="Quiet TV Preview"').Replace('<application ', '<application android:debuggable="true" ')
    $manifest = "$out\AndroidManifest.xml"
    [IO.File]::WriteAllText($manifest, $previewManifest, [Text.UTF8Encoding]::new($false))
}
& "$BuildTools\aapt2.exe" link -o "$out\base.apk" -I $AndroidJar --manifest $manifest "$out\resources.zip" --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'Resource packaging failed' }
$classPaths = @(Get-ChildItem "$out\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$javaBin\java.exe" -cp "$BuildTools\lib\d8.jar" com.android.tools.r8.D8 --lib $AndroidJar --min-api 23 --output "$out\dex" $classPaths
if ($LASTEXITCODE -ne 0) { throw 'Dex compilation failed' }
Copy-Item -LiteralPath "$out\base.apk" -Destination "$out\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$out\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$out\dex\classes.dex", 'classes.dex') | Out-Null }
finally { $archive.Dispose() }
& "$BuildTools\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Alignment failed' }
# This is a local development key, never the original project's signing key.
# The conventional development password below is not a production secret.
$keystore = Join-Path $root 'build\debug.keystore'
if (!(Test-Path -LiteralPath $keystore)) {
    & "$javaBin\keytool.exe" -genkeypair -keystore $keystore -storepass android -keypass android -alias quiettv-debug -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Quiet TV Development'
    if ($LASTEXITCODE -ne 0) { throw 'Development key generation failed' }
}
$apk = Join-Path $root $(if ($Preview) { 'build\QuietTV-preview.apk' } else { 'build\QuietTV-debug.apk' })
& "$javaBin\java.exe" -jar "$BuildTools\lib\apksigner.jar" sign --ks $keystore --ks-key-alias quiettv-debug --ks-pass pass:android --key-pass pass:android --out $apk "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed' }
& "$javaBin\java.exe" -jar "$BuildTools\lib\apksigner.jar" verify $apk
if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed' }
Write-Output "Built and verified: $apk"
