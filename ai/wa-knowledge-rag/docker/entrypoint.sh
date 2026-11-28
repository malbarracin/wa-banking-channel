#!/bin/sh
set -eu

role="${1:-api}"

case "$role" in
  api)
    kb db migrate
    exec kb-api
    ;;
  sync)
    exec kb sync --loop
    ;;
  *)
    exec "$@"
    ;;
esac
