param([switch]$Offline)
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $mavenArgs = @('-B', 'clean', 'verify')
    if ($Offline) { $mavenArgs = @('-o') + $mavenArgs }
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a full JDK 21 or newer' }
    $jdkBin = Join-Path $env:JAVA_HOME 'bin'
    $artifact = 'target/notification-library-0.1.0-SNAPSHOT.jar'
    $entries = & (Join-Path $jdkBin 'jar.exe') tf $artifact
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect JAR' }
    $runtimeEntries = $entries | Where-Object { $_ -notlike 'META-INF/maven/*' }
    if ($runtimeEntries -match '(^consumer/|^quality/|Test.class$|Demo.class$|\.ya?ml$|\.properties$)') {
        throw 'Unexpected tests, demos or runtime configuration in library JAR'
    }
    foreach ($example in Get-ChildItem -LiteralPath examples -Filter '*.java') {
        & (Join-Path $jdkBin 'java.exe') --class-path $artifact $example.FullName
        if ($LASTEXITCODE -ne 0) { throw ('Packaged example failed: ' + $example.Name) }
    }
    & (Join-Path $jdkBin 'jdeps.exe') -s $artifact
    if ($LASTEXITCODE -ne 0) { throw 'JAR dependency inspection failed' }
    $sources = Get-ChildItem -LiteralPath src/main/java -Recurse -Filter '*.java' |
        ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
    [System.IO.File]::WriteAllLines((Join-Path (Get-Location) 'target/javadoc-sources.txt'),
        [string[]]$sources, [System.Text.UTF8Encoding]::new($false))
    & (Join-Path $jdkBin 'javadoc.exe') -quiet '-Xdoclint:all,-missing' -encoding UTF-8 -d target/apidocs '@target/javadoc-sources.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Javadoc generation failed' }
    Write-Output 'Verified: build, architecture, examples against JAR, packaging and Javadoc.'
} finally {
    Pop-Location
}
