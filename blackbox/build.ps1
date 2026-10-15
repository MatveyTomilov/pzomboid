param(
    [Parameter(Mandatory=$true)][string]$GameDir,
    [Parameter(Mandatory=$true)][string]$GradleHome,
    [Parameter(Mandatory=$true)][string]$EcjJar
)
$ErrorActionPreference = 'Stop'
$blackboxGameRoot = (Resolve-Path -LiteralPath $GameDir).Path
$blackboxCompiler = (Resolve-Path -LiteralPath $EcjJar).Path
$blackboxJava = Join-Path $blackboxGameRoot 'jre64\bin\java.exe'
$blackboxGradle = Join-Path $GradleHome 'lib\gradle-gradle-cli-main-9.1.0.jar'
foreach ($blackboxRequired in @($blackboxJava, $blackboxGradle, (Join-Path $blackboxGameRoot 'projectzomboid.jar'), (Join-Path $blackboxGameRoot 'ZombieBuddy.jar'))) {
    if (-not (Test-Path -LiteralPath $blackboxRequired -PathType Leaf)) { throw "Missing build input: $blackboxRequired" }
}
& $blackboxJava -jar $blackboxGradle --no-daemon --console=plain -p $PSScriptRoot build "-PgameDir=$blackboxGameRoot" "-PecjJar=$blackboxCompiler" "-PjavaBin=$blackboxJava"
if ($LASTEXITCODE -ne 0) { throw "BlackBox build/tests failed with exit code $LASTEXITCODE" }
