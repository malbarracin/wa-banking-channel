#!/bin/sh
# Run the users service Maven Wrapper from the repository root.
service_dir=$(CDPATH= cd -- "$(dirname -- "$0")/services/wa-users-service" && pwd) || exit 1
cd "$service_dir" || exit 1
exec sh ./mvnw "$@"
