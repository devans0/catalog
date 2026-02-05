# title: Catalog Client Test Harness
# author: Dominic Evans
# date: February 4, 2026
# version: 1.0
# copyright: 2026 Dominic Evans

$SANDBOX = "test\sandbox"
$BIN_DIR = "bin"
$LIB_DIR = "lib\*"

# Clean up and create sandbox directories
if (Test-Path $SANDBOX) { Remove-Item -Recurse -Force $SANDBOX }
New-Item -ItemType Directory -Path "$SANDBOX\share1"
New-Item -ItemType Directory -Path "$SANDBOX\down1"
New-Item -ItemType Directory -Path "$SANDBOX\share2"
New-Item -ItemType Directory -Path "$SANDBOX\down2"

Start-Process java -ArgumentList "-cp `"$BIN_DIR;$LIB_DIR`" catalog_application.server.CatalogServer"
Start-Sleep -s 3

# Start Client 1 on port 2051
Start-Process java -ArgumentList "-cp `"$BIN_DIR;$LIB_DIR`" catalog_application.client.CatalogClient 2051 $SANDBOX\share1 $SANDBOX\down1"

# Start Client 2 on port 2052
Start-Process java -ArgumentList "-cp `"$BIN_DIR;$LIB_DIR`" catalog_application.client.CatalogClient 2052 $SANDBOX\share2 $SANDBOX\down2"

Write-Host "-------------------------------------------------------"
Write-Host "[TEST] Server and clients are live"
Write-Host "[TEST] Client 1 share directory: $SANDBOX\share1"
Write-Host "[TEST] Client 1 download directory: $SANDBOX\download1"
Write-Host "[TEST] Client 2 share directory: $SANDBOX\share2"
Write-Host "[TEST] Client 2 download directory: $SANDBOX\download2"
Write-Host "[TEST] Close the Java windows  to terminate processes"
Write-Host "-------------------------------------------------------"