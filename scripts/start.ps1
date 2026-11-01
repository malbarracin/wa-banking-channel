# Levantar stack E2E completo del canal
if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  Write-Host "[INFO] .env creado desde .env.example. Revisar secretos antes de usar."
}
python scripts/start_stack.py
exit $LASTEXITCODE
