#!/usr/bin/bash
# 受控 SQL batch runner 入口：解释器、helper 与执行根均为固定绝对路径。
set -euo pipefail

readonly DEPLOYED_HELPER="/opt/bank/controlled-sql-batch-runner/release/docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py"
readonly FIXED_PYTHON="/usr/bin/python3.10"

usage() {
    printf '%s\n' \
        '用法：2026-08-12-controlled-sql-batch-runner.sh --manifest <受控收件目录中的已签名 manifest> [--validate-only]' \
        '      2026-08-12-controlled-sql-batch-runner.sh --manifest <受控收件目录中的已签名 manifest> --execute' \
        '' \
        '默认与 --validate-only 只做受信部署根、签名、schema、哈希和静态 guard 验证；不会启动 mysql。' \
        '--execute 固定在同一 mysql 进程内执行恰好两轮；不存在轮次降级或 PATH/环境凭据覆盖入口。'
}

die() {
    printf 'FAIL-CLOSED: %s\n' "$*" >&2
    exit 64
}

manifest=''
mode='default'
while (($# > 0)); do
    case "$1" in
        --help|-h)
            usage
            exit 0
            ;;
        --manifest)
            (($# >= 2)) || die '--manifest 缺少路径'
            manifest="$2"
            shift 2
            ;;
        --validate-only)
            [[ "$mode" == 'default' ]] || die '--validate-only 不能与 --execute 组合'
            mode='validate'
            shift
            ;;
        --execute)
            [[ "$mode" == 'default' ]] || die '--execute 不能与 --validate-only 组合'
            mode='execute'
            shift
            ;;
        --force|--force=*|--host|--port|--database|--user|--password|--defaults-file|--defaults-extra-file|--socket|--protocol|--login-path)
            die "拒绝 $1：连接、凭据和行为只能来自受信部署根与已签名 manifest"
            ;;
        *)
            die "未知或不允许的参数：$1"
            ;;
    esac
done

[[ -n "$manifest" ]] || die '必须提供 --manifest'
[[ -x "$FIXED_PYTHON" && ! -L "$FIXED_PYTHON" ]] || die '固定 Python 解释器不可用或不是常规文件'
[[ -f "$DEPLOYED_HELPER" && ! -L "$DEPLOYED_HELPER" ]] || die '固定受信部署 helper 不存在或为符号链接'

if [[ "$mode" == 'execute' ]]; then
    exec /usr/bin/python3.10 "$DEPLOYED_HELPER" --manifest "$manifest" --execute
fi
exec /usr/bin/python3.10 "$DEPLOYED_HELPER" --manifest "$manifest" --validate-only
