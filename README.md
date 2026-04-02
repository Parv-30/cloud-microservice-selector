# Arithmetic Router (CPU-only)

Production-style microservices setup with an ONNX-backed routing agent and 4 arithmetic services.

## Services

- `guided-ui` → `http://localhost:8085`
- `router-agent` → `http://localhost:8080`
- `add-service` → `http://localhost:8081`
- `subtract-service` → `http://localhost:8082`
- `multiply-service` → `http://localhost:8083`
- `divide-service` → `http://localhost:8084`

`guided-ui` is a front page with guided fields. It sends only payload JSON shape to the router and never sends an explicit version key.

## Key guarantees

- CPU-only runtime (`onnxruntime`, no CUDA dependency in service images)
- Router validates exact 12 labels from `model_config.json` at startup
- Router decision path uses raw JSON body
- Divide-by-zero returns HTTP 400
- v3 metadata echo supported (`label`, `requestId`, `currency`)
- Correlation ID included in router responses and logs

## Quick start

1. Build jars:

```powershell
Set-Location "d:\Cloud Project 2"
mvn -DskipTests package
```

1. Start stack:

```powershell
Set-Location "d:\Cloud Project 2"
docker compose up --build -d
```

1. Validate:

```powershell
Set-Location "d:\Cloud Project 2"
.\scripts\smoke_test.ps1
```

## Artifact promotion

Use this to validate and promote artifacts then automatically restart router and run smoke tests:

```powershell
Set-Location "d:\Cloud Project 2"
.\scripts\promote_artifacts.ps1 -SourceDir "<path-to-real-artifacts>"
```

Required files in source dir:

- `model.onnx`
- `model_config.json`
- `tokenizer.json`

## CPU provider check (Python 3.12)

```powershell
Set-Location "d:\Cloud Project 2"
py -3.12 ml\trainer\check_gpu.py
```

## Useful commands

```powershell
# status
Set-Location "d:\Cloud Project 2"; docker compose ps

# logs
Set-Location "d:\Cloud Project 2"; docker compose logs router-agent --tail=200

# stop
Set-Location "d:\Cloud Project 2"; docker compose down
```

## Note

`ml/trainer/_generate_placeholder_artifacts.py` exists only for local smoke testing when real model artifacts are unavailable.
=======
# Cloud-microservice-selector
>>>>>>> 60269e6a86975c958ddb55e1c538e1d55415fa49
