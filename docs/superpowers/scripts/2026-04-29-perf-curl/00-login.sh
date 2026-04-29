#!/usr/bin/env bash
# 00-login.sh — 登录冒烟 + current-user 校验
# 默认账号 admin/123456，可通过 USER/PASS 环境变量覆盖

DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"

# 注意：不能用 USER 变量名，会被 shell 环境的 $USER（如 djdev）覆盖默认值
LOGIN_USER="${LOGIN_USER:-admin}"
LOGIN_PASS="${LOGIN_PASS:-123456}"

section "0. 登录冒烟  user=$LOGIN_USER"

do_login "$LOGIN_USER" "$LOGIN_PASS" || { print_summary; exit 1; }

call_assert GET  /api/auth/current-user        200 0 "current-user 取当前登录身份"
call_assert GET  /api/auth/permissions         200 0 "permissions 取当前权限集合"

# 解析 empId/orgCode 写入 state，让后续脚本复用
EMP_ID="$(json_get "$LAST_BODY" data.empId)"
ORG_CODE="$(json_get "$LAST_BODY" data.mainOrgCode)"
[ -z "$EMP_ID" ] && EMP_ID="$USER"
[ -z "$ORG_CODE" ] && ORG_CODE="HQ"

# permissions 端点其实没返回 empId/orgCode，回到 current-user 重取
call GET /api/auth/current-user
EMP_ID="$(json_get "$LAST_BODY" data.empId)"
ORG_CODE="$(json_get "$LAST_BODY" data.mainOrgCode)"

state_set EMP_ID "$EMP_ID"
state_set ORG_CODE "$ORG_CODE"
state_set TS_PREFIX "$TS_PREFIX"
info "已写入 state: EMP_ID=$EMP_ID  ORG_CODE=$ORG_CODE  TS_PREFIX=$TS_PREFIX"

print_summary
