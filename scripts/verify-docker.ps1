param([string]$Image = 'notification-library:demo')
$ErrorActionPreference = 'Stop'
Get-Command docker -ErrorAction Stop | Out-Null
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $dockerOs = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker daemon is unavailable' }
    if ($dockerOs -ne 'linux') { throw 'Select Linux containers for this Dockerfile' }
    & docker build -t $Image .
    if ($LASTEXITCODE -ne 0) { throw 'Docker build failed' }

    # The default command must work without explicitly naming a demo.
    & docker run --rm --network none $Image
    if ($LASTEXITCODE -ne 0) { throw 'Default Docker demo failed' }

    foreach ($demo in @('MultiProviderEmailDemo', 'MobileDemo', 'AsyncBatchDemo', 'TemplateDemo', 'CallbackDemo', 'RetryDemo', 'SlackDemo')) {
        & docker run --rm --network none $Image $demo
        if ($LASTEXITCODE -ne 0) { throw ('Docker demo failed: ' + $demo) }
    }
    Write-Output 'Verified: image build and all eight demos in Linux containers.'
} finally {
    Pop-Location
}
