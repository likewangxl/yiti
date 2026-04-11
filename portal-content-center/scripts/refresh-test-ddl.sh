#!/usr/bin/env bash
# Regenerate test-ddl-*-clean.sql files from docs/schema/ddl-*.sql
# Run from project root: bash portal-content-center/scripts/refresh-test-ddl.sh
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT/docs/schema"
TARGET_DIR="$PROJECT_ROOT/portal-content-center/src/test/resources/sql"

mkdir -p "$TARGET_DIR"

for module in auth governance portal; do
    src="$SOURCE_DIR/ddl-$module.sql"
    dst="$TARGET_DIR/test-ddl-$module-clean.sql"
    if [ ! -f "$src" ]; then
        echo "ERROR: $src not found"
        exit 1
    fi
    # Strip CREATE DATABASE and USE statements (case-insensitive)
    sed -E '/^[[:space:]]*CREATE[[:space:]]+DATABASE/Id; /^[[:space:]]*USE[[:space:]]+/Id' "$src" > "$dst"
    echo "Generated $dst"
done
