#!/bin/sh
# Ejecuta el wrapper del servicio desde la raíz del repositorio.
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec sh "$project_dir/services/wa-users-service/mvnw" -f "$project_dir/services/wa-users-service/pom.xml" "$@"
