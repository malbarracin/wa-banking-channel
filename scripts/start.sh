#!/usr/bin/env bash
set -e
if [ ! -f .env ]; then
  cp .env.example .env
  echo "[INFO] .env creado desde .env.example"
fi
exec python3 scripts/start_stack.py
