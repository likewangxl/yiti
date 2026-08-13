#!/usr/bin/bash
# 受控 SQL runner 的普通 shell 入口仅支持 validate-only。
# bash 会在脚本第一行前处理 BASH_ENV/动态链接环境，故它永远不是生产 execute 入口；
# execute 只能由 root-managed systemd unit 直接启动固定 Python -I launcher。
set -euo pipefail

readonly DEPLOYED_HELPER="/opt/bank/controlled-sql-batch-runner/release/docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py"
readonly FIXED_PYTHON="/usr/bin/python3.10"

usage() {
    printf '%s\n' \
        '用法：2026-08-12-controlled-sql-batch-runner.sh --manifest <受控收件目录中的已签名 manifest> [--validate-only]' \
        '' \
        '默认与 --validate-only 只做受信部署根、签名、schema、hash 和静态 guard 验证；不会启动 mysql。' \
        '普通 shell 入口永久拒绝 --execute；生产执行必须由 root-managed systemd unit 触发。'
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
            [[ "$mode" == 'default' ]] || die '--validate-only 不能重复提供'
            mode='validate'
            shift
            ;;
        --execute|--execute=*)
            die '普通 shell 入口永久拒绝 --execute；请由 root-managed systemd unit 启动'
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
exec /usr/bin/python3.10 -I "$DEPLOYED_HELPER" --manifest "$manifest" --validate-only
