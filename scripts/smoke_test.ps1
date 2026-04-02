$ErrorActionPreference = "Stop"

function Assert-Equal($actual, $expected, $name) {
    if ($actual -ne $expected) {
        throw "[$name] Expected '$expected' but got '$actual'"
    }
    Write-Host "PASS: $name"
}

function Get-ErrorResponseBody($scriptBlock) {
    try {
        & $scriptBlock | Out-Null
        return @{ status = 200; body = "" }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        return @{ status = $status; body = $body }
    }
}

Write-Host "Running arithmetic-router smoke tests..."

$add = Invoke-RestMethod -Uri "http://localhost:8081/v1" -Method Post -ContentType "application/json" -Body '{"operation":"add","a":5,"b":3}'
Assert-Equal $add.result 8.0 "add v1"

$sub = Invoke-RestMethod -Uri "http://localhost:8082/v2" -Method Post -ContentType "application/json" -Body '{"operation":"subtract","numbers":[10,2,3]}'
Assert-Equal $sub.result 5.0 "subtract v2"

$mul = Invoke-RestMethod -Uri "http://localhost:8083/v3" -Method Post -ContentType "application/json" -Body '{"operation":"multiply","numbers":[4,5,2],"label":"batch_result","precision":"decimal"}'
Assert-Equal $mul.result 40.0 "multiply v3"
Assert-Equal $mul.label "batch_result" "multiply v3 metadata echo"

$divErr = Get-ErrorResponseBody {
    Invoke-RestMethod -Uri "http://localhost:8084/v1" -Method Post -ContentType "application/json" -Body '{"operation":"divide","a":5,"b":0}'
}
Assert-Equal $divErr.status 400 "divide by zero status"

$route = Invoke-RestMethod -Uri "http://localhost:8080/route" -Method Post -ContentType "application/json" -Body '{"operation":"multiply","numbers":[4,5,2],"label":"batch_result","precision":"decimal"}'
Assert-Equal $route.routedTo "multiply_v3" "router routedTo"
Assert-Equal $route.operation "multiply" "router operation"
Assert-Equal $route.version "v3" "router version"
if ([string]::IsNullOrWhiteSpace($route.correlationId)) {
    throw "[router correlationId] correlationId is missing"
}
Write-Host "PASS: router correlationId"

$routeErr = Get-ErrorResponseBody {
    Invoke-RestMethod -Uri "http://localhost:8080/route" -Method Post -ContentType "application/json" -Body '{"numbers":[1,2]}'
}
Assert-Equal $routeErr.status 400 "router invalid payload status"
if ($routeErr.body -notmatch '"correlationId"') {
    throw "[router invalid payload body] correlationId missing"
}
Write-Host "PASS: router invalid payload correlationId"

Write-Host "All smoke tests passed."
