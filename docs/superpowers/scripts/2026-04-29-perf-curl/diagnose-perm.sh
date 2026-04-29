#!/usr/bin/env bash
# diagnose-perm.sh — 当 curl 测试出现 401/403 时使用
#
# 用法：
#   ./diagnose-perm.sh                       # 诊断 admin
#   ./diagnose-perm.sh <username>            # 诊断指定账号
#   ./diagnose-perm.sh <username> <method> <url>
#                                            # 同时检查某 URL 是否注册到 PT_RESOURCE
#                                            # 例： ./diagnose-perm.sh admin GET /api/perf/metrics
#
# 直查 yiti 库的 PT_USER / PT_USER_ROLE / PT_ROLE_RESOURCE / PT_ROLE_BIZ_SCOPE / PT_RESOURCE，
# 输出与鉴权链相关的实际数据，避免凭脚本/文档假设跑偏。

DB_USER="${DB_USER:-root}"
DB_PWD="${DB_PWD:-djdev}"
DB_NAME="${DB_NAME:-yiti}"

USERNAME="${1:-admin}"
METHOD="${2:-}"
URL="${3:-}"

run_sql() {
  mysql -u "$DB_USER" -p"$DB_PWD" -h localhost "$DB_NAME" --table -e "$1" 2>&1 | grep -v 'Warning:'
}

echo "================ 1. PT_USER 行 ================"
run_sql "SELECT USER_ID, USERNAME, USERCHNNAME, ISENABLED, ISLOCKED, PASS_WRONG_COUNT, REMARK
        FROM PT_USER WHERE USERNAME = '$USERNAME' OR USER_ID = '$USERNAME';"

echo
echo "================ 2. 用户的角色 ================"
run_sql "SELECT r.ROLE_ID, r.ROLE_CODE, r.ROLE_CHNAME
        FROM PT_USER u
        JOIN PT_USER_ROLE ur ON ur.USER_ID = u.USER_ID
        JOIN PT_ROLE r ON r.ROLE_ID = ur.ROLE_ID
        WHERE u.USERNAME = '$USERNAME' OR u.USER_ID = '$USERNAME';"

echo
echo "================ 3. 角色绑定的资源数 (TOP 10) ================"
run_sql "SELECT ur.ROLE_ID, COUNT(rr.RESOURCE_ID) cnt
        FROM PT_USER u
        JOIN PT_USER_ROLE ur ON ur.USER_ID = u.USER_ID
        LEFT JOIN PT_ROLE_RESOURCE rr ON rr.ROLE_ID = ur.ROLE_ID
        WHERE u.USERNAME = '$USERNAME' OR u.USER_ID = '$USERNAME'
        GROUP BY ur.ROLE_ID
        ORDER BY cnt DESC LIMIT 10;"

echo
echo "================ 4. PERF_* 资源在 PT_RESOURCE 中的注册情况 ================"
run_sql "SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, STATUS
        FROM PT_RESOURCE
        WHERE RESOURCE_ID LIKE 'P_PERF%'
        ORDER BY RESOURCE_ID LIMIT 20;"

echo
echo "================ 5. 该用户在 PERF_CONFIG / PERF_QUERY / PERF_ADJUST 上的 BizScope ================"
run_sql "SELECT bs.ROLE_ID, bs.BIZ_TYPE, bs.SCOPE_TYPE, bs.SCOPE_VALUE
        FROM PT_USER u
        JOIN PT_USER_ROLE ur ON ur.USER_ID = u.USER_ID
        JOIN PT_ROLE_BIZ_SCOPE bs ON bs.ROLE_ID = ur.ROLE_ID
        WHERE (u.USERNAME = '$USERNAME' OR u.USER_ID = '$USERNAME')
          AND bs.BIZ_TYPE LIKE 'PERF_%';"

if [ -n "$METHOD" ] && [ -n "$URL" ]; then
  echo
  echo "================ 6. URL 是否能匹配到已注册资源? METHOD=$METHOD URL=$URL ================"
  run_sql "SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, STATUS
          FROM PT_RESOURCE
          WHERE STATUS = 0
            AND RESOURCE_METHOD = '$METHOD'
            AND '$URL' LIKE REPLACE(REPLACE(RESOURCE_URL, '/*', '/%'), '/%/', '/%/');"

  echo
  echo "================ 7. 该用户是否被授予以上资源? ================"
  run_sql "SELECT DISTINCT res.RESOURCE_ID, res.RESOURCE_URL, res.RESOURCE_METHOD
          FROM PT_RESOURCE res
          JOIN PT_ROLE_RESOURCE rr ON rr.RESOURCE_ID = res.RESOURCE_ID
          JOIN PT_USER_ROLE ur ON ur.ROLE_ID = rr.ROLE_ID
          JOIN PT_USER u ON u.USER_ID = ur.USER_ID
          WHERE (u.USERNAME = '$USERNAME' OR u.USER_ID = '$USERNAME')
            AND res.STATUS = 0
            AND res.RESOURCE_METHOD = '$METHOD'
            AND '$URL' LIKE REPLACE(REPLACE(res.RESOURCE_URL, '/*', '/%'), '/%/', '/%/');"
fi

echo
echo "================ 提示 ================"
echo "1) ISENABLED=0 表示启用 (反直觉，已验证 AuthService.java:304)"
echo "2) 若第 6 步行数为 0：URL 没注册或 method 写错 → 改用对应 P_PERF_* 资源的 URL"
echo "3) 若第 6 有但第 7 为 0：该用户没绑这个资源 → 给角色补 PT_ROLE_RESOURCE 行"
echo "4) 若第 5 步 PERF_* BizScope 为空：data scope 也会拦 → 加 PT_ROLE_BIZ_SCOPE"
echo "5) 重置基线种子: bash docs/superpowers/sql/<最新对齐脚本>.sql (执行前必须备份)"
