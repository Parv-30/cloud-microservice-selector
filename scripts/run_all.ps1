$ErrorActionPreference = "Stop"

Set-Location "d:\Cloud Project 2"

Write-Host "[1/4] CPU provider validation (Python 3.12)"
py -3.12 "ml/trainer/check_gpu.py"

Write-Host "[2/4] Build jars"
mvn -DskipTests package

Write-Host "[3/4] Start stack"
docker compose up --build -d

Write-Host "Waiting for router-agent health..."
$maxWaitSec = 90
$elapsed = 0
while ($true) {
	$status = docker inspect --format "{{.State.Health.Status}}" cloudproject2-router-agent-1 2>$null
	if ($status -eq "healthy") {
		break
	}
	if ($elapsed -ge $maxWaitSec) {
		throw "router-agent did not become healthy within $maxWaitSec seconds"
	}
	Start-Sleep -Seconds 3
	$elapsed += 3
}

Write-Host "[4/4] Smoke tests"
& "d:\Cloud Project 2\scripts\smoke_test.ps1"

Write-Host "All checks completed."
