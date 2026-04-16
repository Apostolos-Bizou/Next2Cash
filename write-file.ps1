# Usage: .\write-file.ps1 -Path "file.vue" -Content $content
param([string]$Path, [string]$Content)
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
Write-Host "OK - Written: $Path"