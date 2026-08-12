#!/usr/bin/env bash
# 只读静态 TDD：禁止连接数据库；仅检查三份人工 SQL 的目标实例 fail-close 文本契约。
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd -P)"

readonly -a SQL_FILES=(
    "docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql"
    "docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql"
    "docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql"
)
readonly -a PREFLIGHT_PREFIXES=(auth rpt_align rpt_seed)
readonly -a PREPARE_NAMES=(auth_preflight_guard rpt_align_preflight_guard rpt_seed_preflight_guard)
readonly -a APPROVAL_VARIABLES=(
    approved_target_server_uuid
    approved_target_hostname
    approved_target_port
    approved_target_schema
    approved_change_ticket
    approved_manifest_sha256
)

failures=0

fail() {
    printf 'FAIL %s: %s\n' "$1" "$2"
    failures=$((failures + 1))
}

contains_fixed() {
    local haystack="$1"
    local needle="$2"
    grep -Fq -- "$needle" <<<"${haystack}"
}

first_ddl_line() {
    # 这里把 DROP/CREATE/ALTER 等对象定义语句都视为 DDL；注释不能伪造通过。
    awk '
        /^[[:space:]]*--/ { next }
        {
            statement = $0
            sub(/[[:space:]]+--.*/, "", statement)
            if (statement ~ /^[[:space:]]*(DROP|CREATE|ALTER|TRUNCATE|RENAME)[[:space:]]+/) {
                print NR
                exit
            }
        }
    ' "$1"
}

first_line_matching() {
    awk -v expression="$2" '$0 ~ expression { print NR; exit }' "$1"
}

code_slice() {
    local file="$1"
    local start_line="$2"
    local end_line="$3"
    sed -n "${start_line},${end_line}p" "$file" \
        | sed -E '/^[[:space:]]*--/d; s/[[:space:]]+--.*$//'
}

check_file() {
    local relative_path="$1"
    local prefix="$2"
    local prepare_name="$3"
    local file="${REPO_ROOT}/${relative_path}"
    local failures_before="${failures}"
    local ddl_line error_line guard_sql_line prepare_line execute_line guard_code hard_stop_code
    local -a missing=()

    if [[ ! -f "${file}" ]]; then
        fail "${relative_path}" "目标 SQL 文件不存在"
        return
    fi

    ddl_line="$(first_ddl_line "${file}")"
    if [[ -z "${ddl_line}" ]]; then
        fail "${relative_path}" "未找到首个 DDL，无法证明 guard 顺序"
        ddl_line=0
    fi

    error_line="$(first_line_matching "${file}" "^[[:space:]]*SET[[:space:]]+@${prefix}_preflight_error[[:space:]]*:=")"
    guard_sql_line="$(first_line_matching "${file}" "^[[:space:]]*SET[[:space:]]+@${prefix}_preflight_guard_sql[[:space:]]*:=")"
    prepare_line="$(first_line_matching "${file}" "^[[:space:]]*PREPARE[[:space:]]+${prepare_name}[[:space:]]+FROM[[:space:]]+@${prefix}_preflight_guard_sql;")"
    execute_line="$(first_line_matching "${file}" "^[[:space:]]*EXECUTE[[:space:]]+${prepare_name};")"

    error_line="${error_line:-0}"
    guard_sql_line="${guard_sql_line:-0}"
    prepare_line="${prepare_line:-0}"
    execute_line="${execute_line:-0}"

    if (( ddl_line == 0 || error_line == 0 || guard_sql_line == 0 || prepare_line == 0 || execute_line == 0 )); then
        fail "${relative_path}" "缺少既有 preflight error / guard SQL / PREPARE / EXECUTE 之一"
        guard_code=''
        hard_stop_code=''
    elif ! (( error_line < guard_sql_line && guard_sql_line < prepare_line && prepare_line < execute_line && execute_line < ddl_line )); then
        fail "${relative_path}" "guard PREPARE/EXECUTE 必须位于首个 DDL 前（error=${error_line}, guard=${guard_sql_line}, prepare=${prepare_line}, execute=${execute_line}, ddl=${ddl_line}）"
        guard_code="$(code_slice "${file}" "${error_line}" "${execute_line}")"
        hard_stop_code="$(code_slice "${file}" "${guard_sql_line}" "${execute_line}")"
    else
        guard_code="$(code_slice "${file}" "${error_line}" "${execute_line}")"
        hard_stop_code="$(code_slice "${file}" "${guard_sql_line}" "${execute_line}")"
    fi

    # 未注入的审批变量必须停机，不能以空值或标签 SELECT 继续。
    for variable in "${APPROVAL_VARIABLES[@]}"; do
        if ! contains_fixed "${guard_code}" "@${variable} IS NULL"; then
            missing+=("@${variable} IS NULL")
        fi
        if ! contains_fixed "${guard_code}" "CHAR_LENGTH(TRIM(@${variable})) = 0"; then
            missing+=("@${variable} 非空校验")
        fi
    done
    if ! contains_fixed "${guard_code}" "@approved_manifest_sha256 NOT REGEXP '^[0-9A-Fa-f]{64}$'"; then
        missing+=("@approved_manifest_sha256 SHA-256 格式校验")
    fi
    if ((${#missing[@]} > 0)); then
        fail "${relative_path}" "审批变量 fail-close 缺失：${missing[*]}"
    fi

    missing=()
    for token in \
        '@@server_uuid IS NULL' \
        'CHAR_LENGTH(TRIM(@@server_uuid)) = 0' \
        '@@hostname IS NULL' \
        'CHAR_LENGTH(TRIM(@@hostname)) = 0' \
        '@@port IS NULL' \
        'CHAR_LENGTH(TRIM(CAST(@@port AS CHAR))) = 0' \
        'DATABASE() IS NULL' \
        'CHAR_LENGTH(TRIM(DATABASE())) = 0'; do
        if ! contains_fixed "${guard_code}" "${token}"; then
            missing+=("${token}")
        fi
    done
    if ((${#missing[@]} > 0)); then
        fail "${relative_path}" "实际会话身份非空校验缺失：${missing[*]}"
    fi

    missing=()
    for token in \
        'BINARY @approved_target_server_uuid <> BINARY @@server_uuid' \
        'BINARY @approved_target_hostname <> BINARY @@hostname' \
        'BINARY @approved_target_port <> BINARY CAST(@@port AS CHAR)' \
        'BINARY @approved_target_schema <> BINARY DATABASE()'; do
        if ! contains_fixed "${guard_code}" "${token}"; then
            missing+=("${token}")
        fi
    done
    if ((${#missing[@]} > 0)); then
        fail "${relative_path}" "缺少审批值与 @@server_uuid/@@hostname/@@port/DATABASE() 的二进制精确匹配：${missing[*]}"
    fi

    if ! contains_fixed "${guard_code}" "target identity guard:"; then
        fail "${relative_path}" "身份异常未写入既有 preflight error 的 target identity guard 分支"
    fi

    # 证明条件会流入既有 PREPARE 不存在对象 hard-stop，而非只输出 SELECT 标签。
    missing=()
    for token in \
        "@${prefix}_preflight_guard_sql := IF(" \
        "@${prefix}_preflight_error IS NULL" \
        "CONCAT('SELECT * FROM __${prefix}_preflight_stop_" \
        "PREPARE ${prepare_name} FROM @${prefix}_preflight_guard_sql;" \
        "EXECUTE ${prepare_name};"; do
        if ! contains_fixed "${hard_stop_code}" "${token}"; then
            missing+=("${token}")
        fi
    done
    if ((${#missing[@]} > 0)); then
        fail "${relative_path}" "既有 PREPARE 不存在对象 hard-stop 链缺失：${missing[*]}"
    fi

    if grep -Eq "DATABASE\\(\\)[[:space:]]*=[[:space:]]*'yiti_test'" "${file}"; then
        fail "${relative_path}" "仍存在硬编码 yiti_test 的 DATABASE() 门；必须只信任审批变量精确匹配"
    fi

    missing=()
    for token in \
        '同一个已核身份的写会话' \
        '禁止把只读预检和写执行分连接' \
        'mysql --force' \
        '-- SET @approved_target_server_uuid' \
        '-- SET @approved_target_hostname' \
        '-- SET @approved_target_port' \
        '-- SET @approved_target_schema' \
        '-- SET @approved_change_ticket' \
        '-- SET @approved_manifest_sha256' \
        '-- SOURCE'; do
        if ! grep -Fq -- "${token}" "${file}"; then
            missing+=("${token}")
        fi
    done
    if ((${#missing[@]} > 0)); then
        fail "${relative_path}" "同一写会话执行契约注释缺失：${missing[*]}"
    fi

    if (( failures == failures_before )); then
        printf 'PASS %s: guard PREPARE line %s, EXECUTE line %s, first DDL line %s\n' \
            "${relative_path}" "${prepare_line}" "${execute_line}" "${ddl_line}"
    fi
}

printf 'STATIC TARGET IDENTITY GUARD CHECK (no database connection)\n'
for index in "${!SQL_FILES[@]}"; do
    check_file "${SQL_FILES[index]}" "${PREFLIGHT_PREFIXES[index]}" "${PREPARE_NAMES[index]}"
done

if (( failures > 0 )); then
    printf 'RESULT: FAIL (%s violation(s))\n' "${failures}"
    exit 1
fi

printf 'RESULT: PASS (all target identity guards are fail-close before DDL)\n'
