#!/bin/bash
# ============================================================
# API 集成测试脚本
# 用于手动测试后端 API 接口
#
# 前置条件:
#   1. MySQL 运行中, 数据库 onepl 已创建
#   2. Redis 运行中 (localhost:6379)
#   3. Bootstrap 服务已启动 (mvn spring-boot:run)
#   4. 测试数据已导入: mysql -uroot -p123456 onepl < docs/testing/test-data.sql
#
# 使用: bash docs/testing/api-test.sh
# ============================================================

BASE_URL="http://localhost:8080"
COOKIE_FILE="/tmp/cookies.txt"
TOKEN_FILE="/tmp/token.txt"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# 清理函数
cleanup() {
    rm -f "$COOKIE_FILE" "$TOKEN_FILE"
}

# 测试前清理
cleanup

echo "========================================"
echo "  API 集成测试"
echo "  目标: $BASE_URL"
echo "========================================"
echo ""

# ============================================================
# 模块 1: 认证接口
# ============================================================
echo ""
echo "========================================"
echo " 模块 1: 认证接口"
echo "========================================"

echo ""
info "1.1 登录成功 - admin 用户"
RESULT=$(curl -s -c "$COOKIE_FILE" -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password"}')
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "登录成功"
    TOKEN=$(echo "$RESULT" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "$TOKEN" > "$TOKEN_FILE"
else
    fail "登录失败"
fi

echo ""
info "1.2 登录失败 - 密码错误"
RESULT=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongpassword"}')
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":"AUTH-40101"'; then
    pass "密码错误返回 AUTH-40101"
else
    fail "预期 AUTH-40101，实际: $RESULT"
fi

echo ""
info "1.3 登录失败 - 用户不存在"
RESULT=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"nobody","password":"password"}')
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":"AUTH-40101"'; then
    pass "用户不存在返回 AUTH-40101"
else
    fail "预期 AUTH-40101，实际: $RESULT"
fi

echo ""
info "1.4 获取当前用户 - 已登录"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/auth/currentUser")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "获取当前用户成功"
else
    fail "获取当前用户失败"
fi

echo ""
info "1.5 获取当前用户 - 未登录"
RESULT=$(curl -s -X GET "$BASE_URL/api/auth/currentUser")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":"AUTH-40105"'; then
    pass "未登录返回 AUTH-40105"
else
    fail "预期 AUTH-40105，实际: $RESULT"
fi

echo ""
info "1.6 登出"
RESULT=$(curl -s -b "$COOKIE_FILE -c $COOKIE_FILE" -X POST "$BASE_URL/api/auth/logout")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "登出成功"
else
    fail "登出失败"
fi

# ============================================================
# 模块 2: 组织接口 (无需认证)
# ============================================================
echo ""
echo "========================================"
echo " 模块 2: 组织接口"
echo "========================================"

echo ""
info "2.1 获取组织树"
RESULT=$(curl -s -X GET "$BASE_URL/api/orgs/tree")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "获取组织树成功"
else
    fail "获取组织树失败"
fi

echo ""
info "2.2 按 orgCode 查询"
RESULT=$(curl -s -X GET "$BASE_URL/api/orgs/BJ")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "按 orgCode 查询成功"
else
    fail "按 orgCode 查询失败"
fi

echo ""
info "2.3 获取子机构"
RESULT=$(curl -s -X GET "$BASE_URL/api/orgs/HQ/children")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "获取子机构成功"
else
    fail "获取子机构失败"
fi

# ============================================================
# 模块 3: 角色接口 (需要认证)
# ============================================================
echo ""
echo "========================================"
echo " 模块 3: 角色接口"
echo "========================================"

# 重新登录获取 session
curl -s -c "$COOKIE_FILE" -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password"}' > /dev/null

echo ""
info "3.1 分页查询角色"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/roles/list?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "分页查询角色成功"
else
    fail "分页查询角色失败"
fi

echo ""
info "3.2 根据角色ID查询"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/roles/R001")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "根据角色ID查询成功"
else
    fail "根据角色ID查询失败"
fi

echo ""
info "3.3 根据角色编码查询"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/roles/code/ADMIN")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "根据角色编码查询成功"
else
    fail "根据角色编码查询失败"
fi

# ============================================================
# 模块 4: 字典/配置接口
# ============================================================
echo ""
echo "========================================"
echo " 模块 4: 字典/配置接口"
echo "========================================"

echo ""
info "4.1 分页查询字典"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/sys/dicts?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "分页查询字典成功"
else
    fail "分页查询字典失败"
fi

echo ""
info "4.2 获取字典项 (INDUSTRY)"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/sys/dicts/INDUSTRY/items")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "获取字典项成功"
else
    fail "获取字典项失败"
fi

echo ""
info "4.3 获取字典标签"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/sys/dicts/STATUS/label/ACT")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "获取字典标签成功"
else
    fail "获取字典标签失败"
fi

echo ""
info "4.4 查询配置项"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/sys/configs?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询配置项成功"
else
    fail "查询配置项失败"
fi

# ============================================================
# 模块 5: 业务范围接口
# ============================================================
echo ""
echo "========================================"
echo " 模块 5: 业务范围接口"
echo "========================================"

echo ""
info "5.1 查询角色业务范围"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/biz-scopes/R001")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询角色业务范围成功"
else
    fail "查询角色业务范围失败"
fi

echo ""
info "5.2 分页查询业务范围"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/biz-scopes/list?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "分页查询业务范围成功"
else
    fail "分页查询业务范围失败"
fi

# ============================================================
# 模块 6: 资源接口
# ============================================================
echo ""
echo "========================================"
echo " 模块 6: 资源接口"
echo "========================================"

echo ""
info "6.1 查询资源树"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/resources/tree")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询资源树成功"
else
    fail "查询资源树失败"
fi

echo ""
info "6.2 查询资源详情"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/admin/resources/1")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询资源详情成功"
else
    fail "查询资源详情失败"
fi

# ============================================================
# 模块 7: 工作流接口
# ============================================================
echo ""
echo "========================================"
echo " 模块 7: 工作流接口"
echo "========================================"

echo ""
info "7.1 按 businessKey 查询流程"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/workflow/processes/LEAD:L20260001")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "按 businessKey 查询流程成功"
else
    fail "按 businessKey 查询流程失败"
fi

echo ""
info "7.2 按 bizType+bizId 查询"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/workflow/processes/biz/LEAD/L20260001")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "按 bizType+bizId 查询成功"
else
    fail "按 bizType+bizId 查询失败"
fi

echo ""
info "7.3 查询待办列表"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/workflow/tasks/todo?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询待办列表成功"
else
    fail "查询待办列表失败"
fi

echo ""
info "7.4 查询已办列表"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/workflow/tasks/done?pageNo=1&pageSize=10")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "查询已办列表成功"
else
    fail "查询已办列表失败"
fi

# ============================================================
# 模块 8: 鉴权链路测试
# ============================================================
echo ""
echo "========================================"
echo " 模块 8: 鉴权链路测试"
echo "========================================"

echo ""
info "8.1 无 Session 访问受保护端点"
RESULT=$(curl -s -X GET "$BASE_URL/api/orgs/tree")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":"AUTH-40105"'; then
    pass "无 Session 返回 AUTH-40105 (需要登录)"
else
    fail "预期 AUTH-40105，实际: $RESULT"
fi

echo ""
info "8.2 带 Session 访问受保护端点"
RESULT=$(curl -s -b "$COOKIE_FILE" -X GET "$BASE_URL/api/orgs/tree")
echo "响应: $RESULT"
if echo "$RESULT" | grep -q '"code":0'; then
    pass "带 Session 访问成功"
else
    fail "带 Session 访问失败"
fi

# ============================================================
# 清理
# ============================================================
cleanup

echo ""
echo "========================================"
echo "  API 集成测试完成"
echo "========================================"
