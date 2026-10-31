@echo off
REM Levantar stack E2E completo del canal
if not exist .env (
  copy .env.example .env
  echo [INFO] .env creado desde .env.example. Revisar secretos antes de usar.
)
docker compose up -d --build
