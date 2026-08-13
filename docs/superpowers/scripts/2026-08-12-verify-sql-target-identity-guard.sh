#!/usr/bin/env bash
# 静态入口：实现位于同目录、仅覆盖三份批准脚本的受限 MySQL grammar；绝不连接数据库或执行 SQL。
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
exec python3 "${SCRIPT_DIR}/2026-08-12-verify-sql-target-identity-guard.py" "$@"
