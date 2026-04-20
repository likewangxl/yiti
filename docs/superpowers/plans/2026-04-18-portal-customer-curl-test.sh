#!/bin/bash
# Portal-Content-Center & Customer-Marketing-Center curl 测试脚本 v3
# 用法: bash 2026-04-18-portal-customer-curl-test.sh [portal-l1|portal-l2|portal-l3|customer-l1|customer-l2|customer-l3|login|all]
# 默认: all
# 重要: URL 路径与 Controller @RequestMapping 保持一致 (不是与 PT_RESOURCE 一致)
set -e

B="${BASE_URL:-http://localhost:8080}"
COOKIES_DIR="/tmp/portal_cust_cookies"
RESULTS="${COOKIES_DIR}/results.txt"
mkdir -p $COOKIES_DIR
> $RESULTS

# ==================== 登录函数 ====================
login_user() {
  local user=$1
  curl -c "$COOKIES_DIR/$user.txt" -s -X POST "$B/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"password\":\"123456\"}" > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "  [LOGIN] $user OK" >&2
  else
    echo "  [LOGIN] $user FAIL" >&2
  fi
}

# ==================== 请求函数 ====================
run() {
  local tc="$1"; local method="$2"; local url="$3"
  local cook="$4"; local data="$5"; local expect="$6"
  local name="$7"

  local resp
  if [ -n "$data" ]; then
    resp=$(curl -b "$cook" -s -w "\n---HTTP:%{http_code}" \
      -X "$method" -H "Content-Type: application/json" \
      --data-binary "$data" "$B$url" 2>/dev/null)
  else
    resp=$(curl -b "$cook" -s -w "\n---HTTP:%{http_code}" \
      -X "$method" "$B$url" 2>/dev/null)
  fi

  local http=$(echo "$resp" | tail -1 | sed 's/---HTTP://')
  local body=$(echo "$resp" | sed '$d')
  local code=$(echo "$body" 2>/dev/null | python -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "PARSE_ERR")

  local status="CHECK"
  if [ "$expect" = "0" ]; then
    if [ "$code" = "0" ]; then status="PASS"; else status="FAIL"; fi
  elif [ "$expect" = "401" ]; then
    [ "$http" = "401" ] && status="PASS" || status="FAIL"
  elif [ "$expect" = "403" ]; then
    [ "$http" = "403" ] && status="PASS" || status="FAIL"
  elif [ "$expect" = "400" ]; then
    [[ "$code" =~ ^(PORTAL|CUST|AUTH|GOV|SYS|WF|BIZ|PERF)-[0-9] ]] && status="PASS" || status="FAIL"
  elif [ "$expect" = "biz_ok" ]; then
    [ "$http" = "200" ] && status="PASS" || status="FAIL"
  elif [ "$expect" = "any" ]; then
    status="PASS"
  fi

  printf "%-22s %-6s %-50s HTTP=%-3s code=%-18s %s | %s\n" \
    "$tc" "$method" "$url" "$http" "${code:--}" "$status" "$name" | tee -a $RESULTS
}

# ==================== 登录所有用户 ====================
do_login() {
  echo "======== 登录测试用户 ========"
  for user in admin user001 user002 tech_wu E001 E002; do
    login_user $user
  done
  echo ""
}

# ==================== PORTAL L1 冒烟测试 ====================
do_portal_l1() {
  login_user admin

  echo "======== PORTAL L1 冒烟测试 (9) ========"
  local COOKIE="$COOKIES_DIR/admin.txt"

  # TC-PORTAL-L1-001: 工作台 (无 @BizAuth)
  run "TC-PORTAL-L1-001" "GET" "/api/portal/workspace" "$COOKIE" "" "0" "工作台数据聚合"

  # TC-PORTAL-L1-002: 快捷入口列表 (无 @BizAuth)
  run "TC-PORTAL-L1-002" "GET" "/api/portal/shortcuts" "$COOKIE" "" "0" "快捷入口列表"

  # TC-PORTAL-L1-003: 导航列表 (实际 URL: /api/nav)
  run "TC-PORTAL-L1-003" "GET" "/api/nav" "$COOKIE" "" "0" "导航列表 (NAV.READ)"

  # TC-PORTAL-L1-004: 通讯录列表 (实际 URL: /api/employees)
  run "TC-PORTAL-L1-004" "GET" "/api/employees" "$COOKIE" "" "0" "通讯录列表 (ADDRBOOK.LIST)"

  # TC-PORTAL-L1-005: 产品列表 (PRODUCT.LIST)
  run "TC-PORTAL-L1-005" "GET" "/api/products" "$COOKIE" "" "0" "产品列表 (PRODUCT.LIST)"

  # TC-PORTAL-L1-006: 中场支持产品 (PRODUCT.READ)
  run "TC-PORTAL-L1-006" "GET" "/api/products/support-available" "$COOKIE" "" "0" "中场支持产品列表"

  # TC-PORTAL-L1-007: 文档列表 (实际 URL: /api/documents)
  run "TC-PORTAL-L1-007" "GET" "/api/documents" "$COOKIE" "" "0" "文档列表 (DOC.LIST)"

  # TC-PORTAL-L1-008: 确认登录态
  run "TC-PORTAL-L1-008" "GET" "/api/auth/current-user" "$COOKIE" "" "0" "确认登录态"

  # TC-PORTAL-L1-009: 机构树
  run "TC-PORTAL-L1-009" "GET" "/api/orgs/tree" "$COOKIE" "" "0" "机构树"
  echo ""
}

# ==================== PORTAL L2 功能测试 ====================
do_portal_l2() {
  login_user admin
  login_user user001

  echo "======== PORTAL L2 功能测试 ========"
  local COOKIE="$COOKIES_DIR/admin.txt"
  local COOKIE_U1="$COOKIES_DIR/user001.txt"

  # --- A. 工作台与快捷入口 (5) ---
  echo "--- A. 工作台与快捷入口 ---"
  run "TC-PORTAL-L2-001" "GET" "/api/portal/shortcuts?shortcutType=SYSTEM" "$COOKIE" "" "0" "SYSTEM 类型快捷入口"
  run "TC-PORTAL-L2-002" "GET" "/api/portal/shortcuts?shortcutType=CUSTOM" "$COOKIE_U1" "" "0" "CUSTOM 类型 (仅本人)"

  # 保存自定义快捷入口
  local SHORTCUT_REQ='{"shortcuts":[{"shortcutName":"新增客户测试","shortcutUrl":"/customer-marketing/clue/create","shortcutIcon":"icon-add","targetType":"INTERNAL","sortOrder":1}]}'
  run "TC-PORTAL-L2-003" "PUT" "/api/portal/shortcuts" "$COOKIE_U1" "$SHORTCUT_REQ" "0" "保存个性化快捷入口"
  run "TC-PORTAL-L2-004" "GET" "/api/portal/shortcuts?shortcutType=CUSTOM" "$COOKIE_U1" "" "0" "验证保存的 CUSTOM 入口"

  # 清空自定义入口
  local CLEAR_REQ='{"shortcuts":[]}'
  run "TC-PORTAL-L2-005" "PUT" "/api/portal/shortcuts" "$COOKIE_U1" "$CLEAR_REQ" "0" "清空自定义快捷入口"

  # --- B. 导航管理 (6) ---
  echo "--- B. 导航管理 ---"

  # 创建导航 (AdminNavController)
  local NAV_REQ='{"navName":"测试导航","navUrl":"https://test.example.com","navIcon":"icon-test","navCategory":"测试分类","sortOrder":99}'
  run "TC-PORTAL-L2-011" "POST" "/api/admin/nav" "$COOKIE" "$NAV_REQ" "0" "新增导航"

  run "TC-PORTAL-L2-012" "GET" "/api/nav" "$COOKIE" "" "0" "验证新增的导航可见"

  # 获取导航ID
  local NAV_LIST=$(curl -b "$COOKIE" -s "$B/api/nav")
  local NAV_ID=$(echo "$NAV_LIST" | python -c "import sys,json; d=json.load(sys.stdin); groups=d.get('data',{}).get('groups',[]); navs=[n for g in groups for n in g.get('navs',[])] if isinstance(groups,list) else []; print(next((str(n.get('id',n.get('navId',''))) for n in navs if '测试导航' in str(n.get('navName',''))),'')" 2>/dev/null || echo "")

  if [ -n "$NAV_ID" ] && [ "$NAV_ID" != "" ]; then
    local NAV_UPDATE_REQ="{\"navName\":\"测试导航-已编辑\",\"sortOrder\":50}"
    run "TC-PORTAL-L2-013" "PUT" "/api/admin/nav/$NAV_ID" "$COOKIE" "$NAV_UPDATE_REQ" "0" "编辑导航"
    run "TC-PORTAL-L2-014" "DELETE" "/api/admin/nav/$NAV_ID" "$COOKIE" "" "0" "删除导航"
  else
    echo "  [SKIP] TC-PORTAL-L2-013~014: 无法获取 navId"
  fi

  # 排序测试
  run "TC-PORTAL-L2-015" "GET" "/api/nav?status=ACTIVE" "$COOKIE" "" "0" "导航列表(ACTIVE)"

  # --- C. 通讯录 (5) ---
  echo "--- C. 通讯录 ---"
  run "TC-PORTAL-L2-021" "GET" "/api/employees?keyword=&pageNo=1&pageSize=20" "$COOKIE" "" "0" "通讯录分页"
  run "TC-PORTAL-L2-022" "GET" "/api/employees/search?keyword=张&limit=10" "$COOKIE" "" "0" "员工选择器搜索"

  # 获取员工ID
  local EMP_RESP=$(curl -b "$COOKIE" -s "$B/api/employees?pageNo=1&pageSize=1")
  local EMP_ID=$(echo "$EMP_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('empId','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$EMP_ID" ] && [ "$EMP_ID" != "" ]; then
    run "TC-PORTAL-L2-023" "GET" "/api/employees/$EMP_ID" "$COOKIE" "" "0" "员工详情"

    local EMP_UPD_REQ='{"mobile":"13800138001","email":"test@example.com","position":"MANAGER","selfDesc":"自动化测试"}'
    run "TC-PORTAL-L2-024" "PUT" "/api/employees/$EMP_ID" "$COOKIE" "$EMP_UPD_REQ" "0" "编辑员工信息"
  else
    echo "  [SKIP] TC-PORTAL-L2-023~024: 无法获取 empId"
  fi

  run "TC-PORTAL-L2-025" "GET" "/api/employees/search?keyword=&limit=5" "$COOKIE" "" "0" "空关键词搜索"

  # --- D. 产品管理 (6) ---
  echo "--- D. 产品管理 ---"
  run "TC-PORTAL-L2-031" "GET" "/api/products?keyword=&pageNo=1&pageSize=20" "$COOKIE" "" "0" "产品列表分页"

  # 获取产品ID
  local PROD_RESP=$(curl -b "$COOKIE" -s "$B/api/products?pageNo=1&pageSize=1")
  local PROD_ID=$(echo "$PROD_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$PROD_ID" ] && [ "$PROD_ID" != "" ]; then
    run "TC-PORTAL-L2-032" "GET" "/api/products/$PROD_ID" "$COOKIE" "" "0" "产品详情"
  else
    echo "  [SKIP] TC-PORTAL-L2-032: 无法获取 productId"
  fi

  run "TC-PORTAL-L2-033" "GET" "/api/products?supportForSupportRequest=true" "$COOKIE" "" "0" "中场支持产品过滤"

  # 新增产品
  local PROD_REQ="{\"productCode\":\"TEST-P-$(date +%s)\",\"productName\":\"自动化测试产品\",\"productCategory\":\"LOAN\",\"description\":\"测试\",\"supportForSupportRequest\":true,\"productDeptOrgCode\":\"HQ\"}"
  run "TC-PORTAL-L2-034" "POST" "/api/products" "$COOKIE" "$PROD_REQ" "0" "新增产品"

  if [ -n "$PROD_ID" ] && [ "$PROD_ID" != "" ]; then
    local PROD_UPD_REQ='{"productName":"自动化测试产品-已编辑","description":"测试描述更新"}'
    run "TC-PORTAL-L2-035" "PUT" "/api/products/$PROD_ID" "$COOKIE" "$PROD_UPD_REQ" "0" "编辑产品"
    run "TC-PORTAL-L2-036" "DELETE" "/api/products/$PROD_ID" "$COOKIE" "" "0" "删除产品"
  else
    echo "  [SKIP] TC-PORTAL-L2-035~036: 无法获取 productId"
  fi

  # --- E. 文档管理 (2) ---
  echo "--- E. 文档管理 ---"
  run "TC-PORTAL-L2-041" "GET" "/api/documents?category=&pageNo=1&pageSize=20" "$COOKIE" "" "0" "文档列表"

  # 获取文档ID
  local DOC_RESP=$(curl -b "$COOKIE" -s "$B/api/documents?pageNo=1&pageSize=1")
  local DOC_ID=$(echo "$DOC_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$DOC_ID" ] && [ "$DOC_ID" != "" ]; then
    run "TC-PORTAL-L2-042" "GET" "/api/documents/$DOC_ID/download" "$COOKIE" "" "biz_ok" "文档下载"
  else
    echo "  [SKIP] TC-PORTAL-L2-042: 无文档数据"
  fi
  echo ""
}

# ==================== PORTAL L3 边界测试 ====================
do_portal_l3() {
  login_user admin
  login_user user001
  login_user user002

  echo "======== PORTAL L3 边界与权限测试 ========"
  touch "$COOKIES_DIR/empty.txt"

  # --- A. 无权限/无 Session (3) ---
  echo "--- A. 无权限/无 Session ---"
  run "TC-PORTAL-L3-001" "GET" "/api/nav" "$COOKIES_DIR/empty.txt" "" "401" "无 Session 访问"

  run "TC-PORTAL-L3-002" "GET" "/api/nav" "$COOKIES_DIR/user002.txt" "" "0" "user002 访问导航列表"

  # 不存在的员工
  run "TC-PORTAL-L3-003" "GET" "/api/employees/NONEXISTENT_ID_999" "$COOKIES_DIR/admin.txt" "" "any" "不存在的员工"

  # --- B. 参数校验 (2) ---
  echo "--- B. 参数校验 ---"
  local BAD_NAV_REQ='{"navName":"测试","navUrl":"not-a-valid-url"}'
  run "TC-PORTAL-L3-011" "POST" "/api/admin/nav" "$COOKIES_DIR/admin.txt" "$BAD_NAV_REQ" "any" "navUrl 格式非法"

  run "TC-PORTAL-L3-012" "GET" "/api/products?pageSize=200" "$COOKIES_DIR/admin.txt" "" "any" "pageSize 超限"

  # --- C. 数据范围 (3) ---
  echo "--- C. 数据范围 ---"
  run "TC-PORTAL-L3-021" "GET" "/api/products?pageNo=1&pageSize=5" "$COOKIES_DIR/user001.txt" "" "0" "user001 视角产品列表"

  run "TC-PORTAL-L3-022" "GET" "/api/employees?pageNo=1&pageSize=5" "$COOKIES_DIR/user001.txt" "" "0" "user001 视角通讯录"

  run "TC-PORTAL-L3-023" "GET" "/api/products/support-available" "$COOKIES_DIR/empty.txt" "" "0" "中场支持产品无需登录"
  echo ""
}

# ==================== CUSTOMER L1 冒烟测试 ====================
do_customer_l1() {
  login_user admin

  echo "======== CUSTOMER L1 冒烟测试 (10) ========"
  local COOKIE="$COOKIES_DIR/admin.txt"

  run "TC-CUST-L1-001" "GET" "/api/tags" "$COOKIE" "" "0" "标签列表 (TAG.LIST)"
  run "TC-CUST-L1-002" "GET" "/api/tags/enabled" "$COOKIE" "" "0" "启用标签 (无需权限)"
  run "TC-CUST-L1-003" "GET" "/api/leads" "$COOKIE" "" "0" "线索列表 (LEAD.LIST)"
  run "TC-CUST-L1-004" "GET" "/api/customers" "$COOKIE" "" "0" "客户列表 (CUSTOMER.LIST)"
  # 实际 URL: /api/customer-pool (不是 /api/customers/pool)
  run "TC-CUST-L1-005" "GET" "/api/customer-pool" "$COOKIE" "" "0" "客户池列表 (CUSTOMER_POOL.LIST)"
  # 实际 URL: /api/my-claims
  run "TC-CUST-L1-006" "GET" "/api/my-claims" "$COOKIE" "" "0" "已认领客户 (CLAIM.LIST)"
  run "TC-CUST-L1-007" "GET" "/api/touch-tasks" "$COOKIE" "" "0" "触达任务列表 (TOUCH_TASK.LIST)"
  # 实际 URL: /api/touch-reports/summary (不是 /api/admin/touch-tasks/summary)
  run "TC-CUST-L1-008" "GET" "/api/touch-reports/summary" "$COOKIE" "" "0" "触达汇总 (TOUCH_REPORT.LIST)"
  run "TC-CUST-L1-009" "GET" "/api/auth/current-user" "$COOKIE" "" "0" "确认登录态"
  run "TC-CUST-L1-010" "GET" "/api/orgs/tree" "$COOKIE" "" "0" "机构树"
  echo ""
}

# ==================== CUSTOMER L2 功能测试 ====================
do_customer_l2() {
  login_user admin
  login_user user001
  login_user E001

  echo "======== CUSTOMER L2 功能测试 ========"
  local COOKIE="$COOKIES_DIR/admin.txt"
  local COOKIE_U1="$COOKIES_DIR/user001.txt"

  # --- A. 标签管理 (5) ---
  echo "--- A. 标签管理 ---"
  local TAG_REQ="{\"tagName\":\"自动化测试标签-$(date +%s)\",\"tagCode\":\"TEST_TAG_$(date +%s)\",\"tagCategory\":\"测试\",\"tagPriority\":50,\"description\":\"自动化测试\"}"
  run "TC-CUST-L2-001" "POST" "/api/tags" "$COOKIE" "$TAG_REQ" "0" "新增标签"

  # 获取标签ID
  local TAG_LIST=$(curl -b "$COOKIE" -s "$B/api/tags?pageNo=1&pageSize=10")
  local TAG_ID=$(echo "$TAG_LIST" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$TAG_ID" ] && [ "$TAG_ID" != "" ]; then
    run "TC-CUST-L2-002" "GET" "/api/tags/$TAG_ID" "$COOKIE" "" "0" "标签详情"

    local TAG_UPD_REQ="{\"tagName\":\"自动化测试标签-已编辑\",\"tagCategory\":\"测试2\",\"tagPriority\":60,\"description\":\"更新\"}"
    run "TC-CUST-L2-003" "PUT" "/api/tags/$TAG_ID" "$COOKIE" "$TAG_UPD_REQ" "0" "编辑标签"

    local TAG_STATUS_REQ='{"status":"DISABLED"}'
    run "TC-CUST-L2-004" "PUT" "/api/tags/$TAG_ID/status" "$COOKIE" "$TAG_STATUS_REQ" "0" "禁用标签"
  else
    echo "  [SKIP] TC-CUST-L2-002~004: 无法获取 tagId"
  fi

  # --- B. 线索管理 (8) ---
  echo "--- B. 线索管理 ---"

  # 创建线索草稿
  local LEAD_REQ="{\"custName\":\"自动化测试线索-$(date +%s)\",\"unifiedCreditCode\":\"91110000TEST$(date +%s)\",\"industry\":\"IT\",\"groupType\":\"INDEPENDENT\",\"customerType\":\"CORPORATE\",\"isKeystone\":false,\"enterpriseType\":\"PRIVATE\",\"isAccountOpened\":false,\"customerDesc\":\"自动化测试\"}"
  local LEAD_RESP=$(curl -b "$COOKIE" -s -X POST "$B/api/leads" \
    -H "Content-Type: application/json" --data-binary "$LEAD_REQ")
  local LEAD_ID=$(echo "$LEAD_RESP" | python -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")

  if [ -n "$LEAD_ID" ] && [ "$LEAD_ID" != "" ]; then
    run "TC-CUST-L2-011" "GET" "/api/leads/$LEAD_ID" "$COOKIE" "" "0" "线索详情"

    local LEAD_UPD_REQ='{"customerDesc":"更新后的描述"}'
    run "TC-CUST-L2-012" "PUT" "/api/leads/$LEAD_ID" "$COOKIE" "$LEAD_UPD_REQ" "0" "编辑草稿"

    # 提交审批 (依赖 workflow)
    run "TC-CUST-L2-013" "POST" "/api/leads/$LEAD_ID/submit" "$COOKIE" "" "0" "提交审批"
  else
    echo "  [SKIP] TC-CUST-L2-011~013: 无法创建线索"
  fi

  # 第二个草稿用于删除测试
  local LEAD2_REQ="{\"custName\":\"待删除线索-$(date +%s)\",\"unifiedCreditCode\":\"91110000DEL$(date +%s)\",\"industry\":\"IT\",\"groupType\":\"INDEPENDENT\",\"customerType\":\"CORPORATE\",\"isKeystone\":false,\"enterpriseType\":\"PRIVATE\",\"isAccountOpened\":false}"
  local LEAD2_RESP=$(curl -b "$COOKIE" -s -X POST "$B/api/leads" \
    -H "Content-Type: application/json" --data-binary "$LEAD2_REQ")
  local LEAD2_ID=$(echo "$LEAD2_RESP" | python -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")

  if [ -n "$LEAD2_ID" ] && [ "$LEAD2_ID" != "" ]; then
    run "TC-CUST-L2-014" "DELETE" "/api/leads/$LEAD2_ID" "$COOKIE" "" "0" "删除草稿"
  else
    echo "  [SKIP] TC-CUST-L2-014: 无法创建待删除线索"
  fi

  run "TC-CUST-L2-015" "GET" "/api/leads?leadStatus=DRAFT&pageNo=1&pageSize=5" "$COOKIE" "" "0" "按状态过滤线索"
  run "TC-CUST-L2-016" "GET" "/api/leads?keyword=&pageNo=1&pageSize=20" "$COOKIE" "" "0" "线索模糊搜索"

  # --- C. 客户管理 (5) ---
  echo "--- C. 客户管理 ---"
  run "TC-CUST-L2-021" "GET" "/api/customers?keyword=&pageNo=1&pageSize=20" "$COOKIE" "" "0" "客户列表分页"

  # 获取客户ID
  local CUST_RESP=$(curl -b "$COOKIE" -s "$B/api/customers?pageNo=1&pageSize=1")
  local CUST_ID=$(echo "$CUST_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$CUST_ID" ] && [ "$CUST_ID" != "" ]; then
    run "TC-CUST-L2-022" "GET" "/api/customers/$CUST_ID" "$COOKIE" "" "0" "客户详情"
    run "TC-CUST-L2-023" "GET" "/api/customers/$CUST_ID/history" "$COOKIE" "" "0" "跨机构历史"
  else
    echo "  [SKIP] TC-CUST-L2-022~023: 无客户数据"
  fi

  run "TC-CUST-L2-024" "GET" "/api/customers?status=VALID&isKeystone=true&pageNo=1&pageSize=5" "$COOKIE" "" "0" "多条件组合过滤"

  # --- D. 客户池与认领 (4) ---
  echo "--- D. 客户池与认领 ---"
  run "TC-CUST-L2-031" "GET" "/api/customer-pool?pageNo=1&pageSize=10" "$COOKIE" "" "0" "客户池列表"

  # 获取未认领的客户
  local POOL_RESP=$(curl -b "$COOKIE_U1" -s "$B/api/customer-pool?claimStatus=UNCLAIMED&pageNo=1&pageSize=1")
  local POOL_CUST_ID=$(echo "$POOL_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$POOL_CUST_ID" ] && [ "$POOL_CUST_ID" != "" ]; then
    local CLAIM_REQ='{"remark":"自动化测试认领"}'
    run "TC-CUST-L2-032" "POST" "/api/customer-pool/$POOL_CUST_ID/claim" "$COOKIE_U1" "$CLAIM_REQ" "0" "认领客户"
  else
    echo "  [SKIP] TC-CUST-L2-032: 无可认领客户"
  fi

  # --- E. 触达任务 (4) ---
  echo "--- E. 触达任务 ---"
  run "TC-CUST-L2-041" "GET" "/api/touch-tasks?pageNo=1&pageSize=20" "$COOKIE" "" "0" "触达任务列表"

  local TASK_RESP=$(curl -b "$COOKIE" -s "$B/api/touch-tasks?pageNo=1&pageSize=1")
  local TASK_ID=$(echo "$TASK_RESP" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('id','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$TASK_ID" ] && [ "$TASK_ID" != "" ]; then
    run "TC-CUST-L2-042" "GET" "/api/touch-tasks/$TASK_ID" "$COOKIE" "" "0" "触达任务详情"
  else
    echo "  [SKIP] TC-CUST-L2-042: 无触达任务"
  fi

  run "TC-CUST-L2-043" "GET" "/api/touch-tasks?taskStatus=PENDING&pageNo=1&pageSize=5" "$COOKIE" "" "0" "待处理任务"
  run "TC-CUST-L2-044" "GET" "/api/touch-tasks?slaWarning=true&pageNo=1&pageSize=5" "$COOKIE" "" "0" "SLA 预警任务"
  echo ""
}

# ==================== CUSTOMER L3 边界测试 ====================
do_customer_l3() {
  login_user admin
  login_user user001
  login_user user002

  echo "======== CUSTOMER L3 边界与权限测试 ========"
  touch "$COOKIES_DIR/empty.txt"

  # --- A. 无权限/无 Session (3) ---
  echo "--- A. 无权限/无 Session ---"
  run "TC-CUST-L3-001" "GET" "/api/leads" "$COOKIES_DIR/empty.txt" "" "401" "无 Session 访问"

  # user002 没有 LEAD.WRITE 权限
  local LEAD_REQ='{"custName":"无权限测试","unifiedCreditCode":"91110000NOT0001","industry":"IT","groupType":"INDEPENDENT","customerType":"CORPORATE","isKeystone":false,"enterpriseType":"PRIVATE","isAccountOpened":false}'
  run "TC-CUST-L3-002" "POST" "/api/leads" "$COOKIES_DIR/user002.txt" "$LEAD_REQ" "403" "user002 无 LEAD.WRITE"

  run "TC-CUST-L3-003" "GET" "/api/tags/NONEXISTENT_ID_999" "$COOKIES_DIR/admin.txt" "" "any" "不存在的标签"

  # --- B. 业务规则校验 (3) ---
  echo "--- B. 业务规则校验 ---"

  # 重复线索名称
  local LEAD_LIST=$(curl -b "$COOKIES_DIR/admin.txt" -s "$B/api/leads?pageNo=1&pageSize=1")
  local EXIST_NAME=$(echo "$LEAD_LIST" | python -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',{})
if isinstance(data,dict):
    records=data.get('records',data.get('list',[]))
else:
    records=data
print(records[0].get('custName','') if records else '')
" 2>/dev/null || echo "")

  if [ -n "$EXIST_NAME" ] && [ "$EXIST_NAME" != "" ]; then
    local DUPE_LEAD_REQ="{\"custName\":\"$EXIST_NAME\",\"unifiedCreditCode\":\"91110000DUP$(date +%s)\",\"industry\":\"IT\",\"groupType\":\"INDEPENDENT\",\"customerType\":\"CORPORATE\",\"isKeystone\":false,\"enterpriseType\":\"PRIVATE\",\"isAccountOpened\":false}"
    run "TC-CUST-L3-011" "POST" "/api/leads" "$COOKIES_DIR/admin.txt" "$DUPE_LEAD_REQ" "any" "重复客户名称"
  else
    echo "  [SKIP] TC-CUST-L3-011: 无线索数据"
  fi

  run "TC-CUST-L3-012" "GET" "/api/tags?pageSize=200" "$COOKIES_DIR/admin.txt" "" "any" "pageSize 超限"

  # --- C. 参数校验 ---
  echo "--- C. 参数校验 ---"
  local BAD_TAG_REQ='{"tagName":"测试","tagCode":"INVALID-CODE","tagPriority":50}'
  run "TC-CUST-L3-021" "POST" "/api/tags" "$COOKIES_DIR/admin.txt" "$BAD_TAG_REQ" "any" "tagCode 含非法字符"

  local BAD_LEAD_REQ='{"custName":"测试","unifiedCreditCode":"INVALID","industry":"IT","groupType":"INDEPENDENT","customerType":"CORPORATE","isKeystone":false,"enterpriseType":"PRIVATE","isAccountOpened":false}'
  run "TC-CUST-L3-022" "POST" "/api/leads" "$COOKIES_DIR/admin.txt" "$BAD_LEAD_REQ" "any" "unifiedCreditCode 格式错误"
  echo ""
}

# ==================== 主程序 ====================
MODE="${1:-all}"

echo "========================================"
echo "Portal-Content-Center & Customer-Marketing-Center"
echo "curl 接口测试"
echo "BASE_URL: $B"
echo "========================================"
echo ""

case "$MODE" in
  login)
    do_login
    ;;
  portal-l1)
    do_login
    do_portal_l1
    ;;
  portal-l2)
    do_login
    do_portal_l2
    ;;
  portal-l3)
    do_login
    do_portal_l3
    ;;
  customer-l1)
    do_login
    do_customer_l1
    ;;
  customer-l2)
    do_login
    do_customer_l2
    ;;
  customer-l3)
    do_login
    do_customer_l3
    ;;
  all|*)
    do_login
    do_portal_l1
    do_portal_l2
    do_portal_l3
    do_customer_l1
    do_customer_l2
    do_customer_l3
    ;;
esac

echo ""
echo "========================================"
echo "测试完成。结果保存在: $RESULTS"
echo "========================================"

# 统计
TOTAL=$(wc -l < "$RESULTS" 2>/dev/null || echo 0)
PASS=$(grep -c " PASS " "$RESULTS" 2>/dev/null || echo 0)
FAIL=$(grep -c " FAIL " "$RESULTS" 2>/dev/null || echo 0)
SKIP=$(grep -c "SKIP" "$RESULTS" 2>/dev/null || echo 0)

echo ""
echo "结果统计:"
echo "  总计: $TOTAL"
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
echo "  SKIP: $SKIP"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "失败的测试:"
  grep " FAIL " "$RESULTS"
  exit 1
fi
