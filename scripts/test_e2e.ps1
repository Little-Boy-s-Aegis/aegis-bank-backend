$ErrorActionPreference = "Continue"

# Step 1: Enable secure mode for SQLI
Write-Host "`n=== Step 1: Enable Secure Mode for SQLI ===" -ForegroundColor Cyan
$toggle = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/security/toggle" -Method POST -ContentType "application/json" -Body '{"vulnerability":"SQLI","enabled":false}'
Write-Host "SQLI Enabled: $($toggle.enabled)"

Start-Sleep -Seconds 1

# Step 2: Trigger SQL Injection attack
Write-Host "`n=== Step 2: Trigger SQL Injection Attack ===" -ForegroundColor Cyan
$body = @{ username = "admin' OR '1'='1"; password = "test" } | ConvertTo-Json
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host "Status: $($resp.StatusCode)"
    Write-Host "Body: $($resp.Content)"
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode) (Attack was BLOCKED - this is expected!)"
    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $reader.BaseStream.Position = 0
        Write-Host "Body: $($reader.ReadToEnd())"
    } catch {
        Write-Host "(Could not read response body)"
    }
}

Start-Sleep -Seconds 2

# Step 3: Check security logs on BE
Write-Host "`n=== Step 3: Check Security Logs on Backend ===" -ForegroundColor Cyan
$headers = @{ "X-Aegis-Token" = "aegis-secret-security-sync-token-2026" }
$logs = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/security/logs" -Headers $headers
Write-Host "Total security logs: $($logs.Count)"
if ($logs.Count -gt 0) {
    $latest = $logs[0]
    Write-Host "Latest: [$($latest.attackType)] $($latest.status) - $($latest.description)"
}

# Step 4: Check Dashboard alerts  
Write-Host "`n=== Step 4: Check Dashboard Alerts ===" -ForegroundColor Cyan
try {
    $alerts = Invoke-RestMethod -Uri "http://localhost:8082/api/alerts"
    Write-Host "Total dashboard alerts: $($alerts.Count)"
    if ($alerts.Count -gt 0) {
        $latestAlert = $alerts[$alerts.Count - 1]
        Write-Host "Latest alert: [$($latestAlert.severity)] $($latestAlert.title)"
    }
} catch {
    Write-Host "Dashboard not reachable (may still be starting)"
}

Write-Host "`n=== Test Complete ===" -ForegroundColor Green
