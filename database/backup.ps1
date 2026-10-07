# =====================================================================================
# DP DrukBazaars: backup on Windows (the development computer, or a Windows server).
#
# Saves the database and the uploaded files into D:\dkphar-backups (change $BackupDir below)
# and keeps the last 14 days. Run it by hand, or every night with Task Scheduler:
#   Program: powershell.exe
#   Arguments: -ExecutionPolicy Bypass -File D:\Inventory\database\backup.ps1
#
# The password is read from Inventory_System\secrets.properties (spring.datasource.password=...),
# never written in this file and never shown on screen.
#
# Restore:
#   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p inventorydb < inventorydb-2026-10-02.sql
#   Expand-Archive files-2026-10-02.zip -DestinationPath D:\Inventory
# =====================================================================================
$ErrorActionPreference = 'Stop'

$Database  = 'inventorydb'
$MySqlUser = 'root'
$MySqlBin  = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$BackupDir = 'D:\dkphar-backups'
$KeepDays  = 14
$Secrets   = Join-Path $PSScriptRoot '..\Inventory_System\secrets.properties'

# IntelliJ runs the app from the Inventory folder, so uploads\ and private-uploads\ are there.
# Older copies may also be in Inventory_System: both places are saved.
$AppDirs = @((Join-Path $PSScriptRoot '..'), (Join-Path $PSScriptRoot '..\Inventory_System'))

$line = Get-Content $Secrets | Where-Object { $_ -like 'spring.datasource.password=*' } | Select-Object -First 1
if (-not $line) { throw "No spring.datasource.password in $Secrets" }
$env:MYSQL_PWD = $line.Substring('spring.datasource.password='.Length)

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd'
$sqlFile = Join-Path $BackupDir "$Database-$stamp.sql"

try {
    & (Join-Path $MySqlBin 'mysqldump.exe') -u $MySqlUser --single-transaction --routines --triggers --events `
        --set-gtid-purged=OFF --result-file="$sqlFile" $Database
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed ($LASTEXITCODE)" }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

# one zip per place (both have an "uploads" folder, so they cannot share one zip)
$suffixes = @('', '-inventory-system')
for ($i = 0; $i -lt $AppDirs.Count; $i++) {
    $folders = foreach ($name in 'uploads', 'private-uploads') {
        $p = Join-Path $AppDirs[$i] $name
        if (Test-Path $p) { (Resolve-Path $p).Path }
    }
    if ($folders) {
        Compress-Archive -Path $folders -DestinationPath (Join-Path $BackupDir "files$($suffixes[$i])-$stamp.zip") -Force
    }
}

Get-ChildItem $BackupDir -File | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } | Remove-Item

Write-Output "Backup done: $sqlFile"
