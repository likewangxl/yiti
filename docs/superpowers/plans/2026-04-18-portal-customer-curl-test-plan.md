# Portal-Content-Center & Customer-Marketing-Center 接口测试计划

> 日期: 2026-04-18
> 状态: 待执行
> 范围: portal-content-center / customer-marketing-center
> 数据库: `onepl` (应用 `docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql`)
> 应用: `http://localhost:8080`
> 测试方式: bash + curl (模拟真实用户登录流程)

---

## 测试环境

- **BASE_URL**: `http://localhost:8080`
- **Cookie 保存**: `/tmp/cookies_<user>.txt`
- **统一密码**: `123456`
- **用户定义** (来自 seed-v1.sql):

| 用户 | 角色 | 可用范围 |
|------|------|---------|
| `admin` | R_ADMIN / R_BACK_TECH | 全系统 (ALL) |
| `user001` | R_RM (客户经理) | 本机构及下级 |
| `user002` | R_ORG_HEAD (机构负责人) | 本机构及下级 |
| `tech_wu` | R_BACK_TECH (后端技术) | 只读 |
| `E001` | R_RM (张三，客户经理) | 本机构及下级 |
| `E002` | R_RM (李四，客户经理) | 本机构及下级 |

> 注: 各业务模块 (PORTAL / CUSTOMER / PRODUCT 等) 的 DATA_SCOPE 权限需要通过 PT_ROLE_BIZ_SCOPE 授予，详见 §0.3。

---

## 前置依赖检查

执行测试前必须确认以下条件:

1. **应用启动**: `http://localhost:8080` 可访问
2. **数据库已初始化**: PT_* 表、portal/customer 模块表、种子数据已加载
3. **Redis 运行中**: 用于 Session 存储
4. **MinIO 运行中**: 文档上传/下载测试需要 (文件服务)
5. **PT_RESOURCE 数据完整**: portal/customer 相关资源已注册

验证命令:
```bash
# 检查应用健康
curl -s http://localhost:8080/actuator/health

# 检查数据库连接
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM PT_USER; SELECT COUNT(*) FROM PT_RESOURCE;"
```

---

## 测试执行方式

### 快速执行 (推荐)

```bash
cd docs/superpowers/plans
bash 2026-04-18-portal-customer-curl-test.sh
```

### 逐模块执行

```bash
# Step 1: 登录所有测试用户 (生成 cookie)
bash portal_customer_test.sh login

# Step 2: 执行 PORTAL L1 冒烟测试
bash portal_customer_test.sh portal-l1

# Step 3: 执行 PORTAL L2 功能测试
bash portal_customer_test.sh portal-l2

# Step 4: 执行 CUSTOMER L1 冒烟测试
bash portal_customer_test.sh customer-l1

# Step 5: 执行 CUSTOMER L2 功能测试
bash portal_customer_test.sh customer-l2
```

---

## 测试用例编号规范

格式: `TC-<模块>-<级别>-<序号>`

| 前缀 | 模块 | 级别 | 说明 |
|------|------|------|------|
| `TC-PORTAL-` | portal-content-center | L1/L2/L3 | 门户内容中心 |
| `TC-CUST-` | customer-marketing-center | L1/L2/L3 | 客户营销中心 |

---

## Part 1: Portal-Content-Center 测试用例

### 1.1 L1 冒烟测试 (10 条)

> 目标: 验证核心功能可用，基础 CRUD 不报错

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L1-001 | GET | /api/portal/workspace | admin | 工作台聚合接口 (无权限要求) |
| TC-PORTAL-L1-002 | GET | /api/portal/shortcuts | admin | 快捷入口列表 (无权限要求) |
| TC-PORTAL-L1-003 | GET | /api/nav | admin + NAV.READ | 导航列表 (需 @BizAuth) |
| TC-PORTAL-L1-004 | GET | /api/employees | admin + ADDRBOOK.LIST | 通讯录列表 |
| TC-PORTAL-L1-005 | GET | /api/products | admin + PRODUCT.LIST | 产品列表 |
| TC-PORTAL-L1-006 | GET | /api/products/support-available | admin + PRODUCT.READ | 中场支持产品列表 (缓存) |
| TC-PORTAL-L1-007 | GET | /api/documents | admin + DOC.LIST | 文档列表 |
| TC-PORTAL-L1-008 | GET | /api/auth/current-user | admin | 确认登录态 |
| TC-PORTAL-L1-009 | GET | /api/orgs/tree | admin | 确认机构树权限 |
| TC-PORTAL-L1-010 | POST | /api/auth/logout | admin | 登出 |

#### TC-PORTAL-L1-001 详细步骤

```bash
# 1. 登录 (admin)
curl -c /tmp/cookies_admin.txt -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | python -c "import sys,json; print('login:', json.load(sys.stdin)['code'])"

# 2. 调用工作台
curl -b /tmp/cookies_admin.txt -s http://localhost:8080/api/portal/workspace \
  -H "Accept: application/json" | python -c "
import sys,json
d=json.load(sys.stdin)
print('code:', d['code'])
if 'data' in d and d['data']:
    print('todoCount:', d['data'].get('todoCount'))
    print('metricCards:', len(d['data'].get('metricCards',[])))
"
```

期望: `code: 0`, 响应包含 `todoCount`, `metricCards` 数组

---

### 1.2 L2 功能测试 (25 条)

#### A. 工作台与快捷入口 (5 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L2-001 | GET | /api/portal/shortcuts?shortcutType=SYSTEM | admin | SYSTEM 类型快捷入口 |
| TC-PORTAL-L2-002 | GET | /api/portal/shortcuts?shortcutType=CUSTOM | user001 | CUSTOM 类型 (仅本人) |
| TC-PORTAL-L2-003 | PUT | /api/portal/shortcuts | user001 | 保存个性化快捷入口 |
| TC-PORTAL-L2-004 | GET | /api/portal/shortcuts | user001 | 验证保存后 CUSTOM 入口 |
| TC-PORTAL-L2-005 | PUT | /api/portal/shortcuts (清空) | user001 | 清理自定义入口 |

#### TC-PORTAL-L2-003 请求体示例

```json
{
  "shortcuts": [
    {
      "shortcutName": "新增客户",
      "shortcutUrl": "/customer-marketing/clue/create",
      "shortcutIcon": "icon-add",
      "targetType": "INTERNAL",
      "sortOrder": 1
    }
  ]
}
```

#### B. 导航管理 (6 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L2-011 | POST | /api/admin/nav | admin + NAV.WRITE | 新增导航 |
| TC-PORTAL-L2-012 | GET | /api/nav | admin | 验证新增的导航可见 |
| TC-PORTAL-L2-013 | PUT | /api/admin/nav/{navId} | admin + NAV.WRITE | 编辑导航 |
| TC-PORTAL-L2-014 | PUT | /api/admin/nav/sort | admin + NAV.WRITE | 批量调整排序 |
| TC-PORTAL-L2-015 | DELETE | /api/admin/nav/{navId} | admin + NAV.WRITE | 删除导航 (逻辑删除) |
| TC-PORTAL-L2-016 | GET | /api/nav?status=DISABLED | admin | 验证删除后状态 |

#### TC-PORTAL-L2-011 请求体

```json
{
  "navName": "测试导航",
  "navUrl": "https://example.com",
  "navIcon": "icon-test",
  "navCategory": "测试分类",
  "sortOrder": 99
}
```

错误码验证:
- 成功: `code: 0`
- URL 格式非法: `PORTAL-42202`
- 名称重复: `PORTAL-40903`

#### C. 通讯录 (5 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L2-021 | GET | /api/employees?keyword=张三&pageNo=1&pageSize=20 | admin | 通讯录模糊搜索 |
| TC-PORTAL-L2-022 | GET | /api/employees/search?keyword=张 | admin + ADDRBOOK.READ | 员工选择器搜索 |
| TC-PORTAL-L2-023 | GET | /api/employees/{empId} | admin | 员工详情 (完整字段) |
| TC-PORTAL-L2-024 | PUT | /api/employees/{empId} | user001 (本人) | 编辑本人通讯录 |
| TC-PORTAL-L2-025 | GET | /api/employees/search?keyword=空&limit=5 | admin | 空关键词校验 |

#### TC-PORTAL-L2-024 请求体

```json
{
  "mobile": "13800138001",
  "email": "test@example.com",
  "position": "MANAGER",
  "selfDesc": "测试自我描述",
  "responsibleProductIds": []
}
```

错误码验证:
- 编辑本人: `code: 0`
- 编辑他人: `PORTAL-40301`
- 离职员工: `PORTAL-40902`

#### D. 产品管理 (6 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L2-031 | GET | /api/products?keyword=&pageNo=1&pageSize=20 | admin | 产品列表分页 |
| TC-PORTAL-L2-032 | GET | /api/products/{id} | admin | 产品详情 |
| TC-PORTAL-L2-033 | GET | /api/products?supportForSupportRequest=true | admin | 中场支持产品过滤 |
| TC-PORTAL-L2-034 | POST | /api/products | admin | 新增产品 |
| TC-PORTAL-L2-035 | PUT | /api/products/{id} | admin | 编辑产品 |
| TC-PORTAL-L2-036 | DELETE | /api/products/{id} | admin | 删除产品 (逻辑删除) |

#### TC-PORTAL-L2-034 请求体

```json
{
  "productCode": "TEST-PROD-001",
  "productName": "测试产品",
  "productCategory": "LOAN",
  "description": "这是一条测试产品说明",
  "supportForSupportRequest": true,
  "productDeptOrgCode": "HQ"
}
```

#### E. 文档管理 (3 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L2-041 | GET | /api/documents?category=&pageNo=1&pageSize=20 | admin | 文档列表 |
| TC-PORTAL-L2-042 | GET | /api/documents/{id}/download | admin + DOC.READ | 文档下载 (302/流) |
| TC-PORTAL-L2-043 | POST | /api/admin/documents | admin + DOC.WRITE | 新增文档元数据 |

> 注: TC-PORTAL-L2-043 依赖 MinIO 上传文件后获得的 fileObjectId，单独测试时需要先调用文件上传接口。

---

### 1.3 L3 边界与权限测试 (10 条)

#### A. 无权限/无 Session 测试 (4 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L3-001 | GET | /api/nav | 无 cookie | 无 Session → 401 |
| TC-PORTAL-L3-002 | GET | /api/admin/nav | admin | 无 NAV.WRITE → 403 |
| TC-PORTAL-L3-003 | GET | /api/employees/{不存在ID} | admin | 不存在员工 → 40002 |
| TC-PORTAL-L3-004 | DELETE | /api/admin/nav/99999 | admin | 不存在导航 → 40001 |

#### B. 参数校验测试 (3 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L3-011 | POST | /api/admin/nav | admin | navUrl 格式非法 → 42202 |
| TC-PORTAL-L3-012 | PUT | /api/employees/{empId} | user001 | mobile 格式错误 → 校验错误 |
| TC-PORTAL-L3-013 | GET | /api/products?pageSize=200 | admin | pageSize 超限 → 校验失败 |

#### C. 数据范围测试 (3 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-PORTAL-L3-021 | GET | /api/products | user001 | 数据按本机构过滤 |
| TC-PORTAL-L3-022 | GET | /api/employees | user001 | 数据按本机构过滤 |
| TC-PORTAL-L3-023 | GET | /api/products/support-available | 无需登录 | 公开接口无需权限 |

---

## Part 2: Customer-Marketing-Center 测试用例

### 2.1 L1 冒烟测试 (10 条)

> 目标: 核心业务流程 (标签→线索→客户→触达) 不报错

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L1-001 | GET | /api/tags | admin + TAG.LIST | 标签列表 |
| TC-CUST-L1-002 | GET | /api/tags/enabled | admin | 启用标签 (无需权限) |
| TC-CUST-L1-003 | GET | /api/leads | admin + LEAD.LIST | 线索列表 |
| TC-CUST-L1-004 | GET | /api/customers | admin + CUSTOMER.LIST | 客户列表 |
| TC-CUST-L1-005 | GET | /api/customer-pool | admin + CUSTOMER_POOL.LIST | 客户池列表 |
| TC-CUST-L1-006 | GET | /api/my-claims | admin + CLAIM.LIST | 已认领客户 |
| TC-CUST-L1-007 | GET | /api/touch-tasks | admin + TOUCH_TASK.LIST | 触达任务列表 |
| TC-CUST-L1-008 | GET | /api/admin/touch-tasks/summary | admin + TOUCH_REPORT.LIST | 触达汇总 |
| TC-CUST-L1-009 | GET | /api/auth/current-user | admin | 确认登录态 |
| TC-CUST-L1-010 | POST | /api/auth/logout | admin | 登出 |

---

### 2.2 L2 功能测试 (30 条)

#### A. 标签管理 (5 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-001 | POST | /api/tags | admin + TAG.WRITE | 新增标签 |
| TC-CUST-L2-002 | GET | /api/tags/{tagId} | admin | 标签详情 |
| TC-CUST-L2-003 | PUT | /api/tags/{tagId} | admin + TAG.WRITE | 编辑标签 |
| TC-CUST-L2-004 | PUT | /api/tags/{tagId}/status | admin + TAG.WRITE | 启用/禁用标签 |
| TC-CUST-L2-005 | DELETE | /api/tags/{tagId} | admin + TAG.WRITE | 删除标签 |

#### TC-CUST-L2-001 请求体

```json
{
  "tagName": "测试标签",
  "tagCode": "TEST_TAG",
  "tagCategory": "测试分类",
  "tagPriority": 50,
  "description": "自动化测试创建的标签"
}
```

#### B. 线索管理 (8 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-011 | POST | /api/leads | admin + LEAD.WRITE | 新建线索草稿 |
| TC-CUST-L2-012 | GET | /api/leads/{leadId} | admin + LEAD.READ | 线索详情 |
| TC-CUST-L2-013 | PUT | /api/leads/{leadId} | admin + LEAD.WRITE | 编辑草稿 |
| TC-CUST-L2-014 | POST | /api/leads/{leadId}/submit | admin + LEAD.WRITE | 提交审批 |
| TC-CUST-L2-015 | POST | /api/leads | admin + LEAD.WRITE | 创建第二个草稿 |
| TC-CUST-L2-016 | DELETE | /api/leads/{draftLeadId} | admin + LEAD.WRITE | 删除草稿 |
| TC-CUST-L2-017 | GET | /api/leads?leadStatus=DRAFT | admin | 按状态过滤线索 |
| TC-CUST-L2-018 | GET | /api/leads?keyword=&pageNo=1&pageSize=20 | admin | 线索模糊搜索 |

#### TC-CUST-L2-011 请求体

```json
{
  "custName": "测试客户有限公司",
  "unifiedCreditCode": "91110000123456789X",
  "industry": "IT",
  "groupType": "INDEPENDENT",
  "customerType": "CORPORATE",
  "isKeystone": false,
  "enterpriseType": "PRIVATE",
  "isAccountOpened": false,
  "customerDesc": "自动化测试创建的线索"
}
```

#### C. 客户管理 (5 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-021 | GET | /api/customers?keyword=&pageNo=1&pageSize=20 | admin | 客户列表分页 |
| TC-CUST-L2-022 | GET | /api/customers/{custId} | admin + CUSTOMER.READ | 客户详情 |
| TC-CUST-L2-023 | GET | /api/customers/{custId}/history | admin | 跨机构历史 (只读特例) |
| TC-CUST-L2-024 | GET | /api/customers?status=VALID&isKeystone=true | admin | 多条件组合过滤 |
| TC-CUST-L2-025 | GET | /api/customers?claimed=true | admin | 已认领客户过滤 |

#### D. 客户池与认领 (4 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-031 | GET | /api/customer-pool?pageNo=1&pageSize=20 | admin | 客户池列表 |
| TC-CUST-L2-032 | POST | /api/customer-pool/{custId}/claim | user001 + CLAIM.WRITE | 认领客户 |
| TC-CUST-L2-033 | GET | /api/my-claims?scope=ORG | user001 | 查看本机构已认领 |
| TC-CUST-L2-034 | POST | /api/claims/{claimId}/cancel | user001 + CLAIM.WRITE | 取消认领 |

#### TC-CUST-L2-032 请求体

```json
{
  "remark": "自动化测试认领"
}
```

#### E. 触达任务 (6 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-041 | GET | /api/touch-tasks?pageNo=1&pageSize=20 | admin | 触达任务列表 |
| TC-CUST-L2-042 | GET | /api/touch-tasks/{taskId} | admin + TOUCH_TASK.READ | 触达任务详情 |
| TC-CUST-L2-043 | GET | /api/touch-tasks?taskStatus=PENDING | admin | 按状态过滤 |
| TC-CUST-L2-044 | GET | /api/touch-tasks?taskStatus=IN_PROGRESS | admin | 进行中任务 |
| TC-CUST-L2-045 | GET | /api/touch-tasks?taskStatus=SUCCESS | admin | 已完成任务 |
| TC-CUST-L2-046 | GET | /api/touch-tasks?slaWarning=true | admin | SLA 预警任务 |

> 注: 触达成功/取消需要已有 PENDING 状态的任务，需要前置创建测试数据或使用已存在的任务。

#### F. 触达管理视图 (2 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L2-051 | GET | /api/admin/touch-tasks/summary | admin + TOUCH_REPORT.LIST | 机构汇总 |
| TC-CUST-L2-052 | GET | /api/admin/touch-tasks?pageNo=1&pageSize=20 | admin + TOUCH_REPORT.LIST | 全行触达明细 |

---

### 2.3 L3 边界与权限测试 (10 条)

#### A. 无权限/无 Session 测试 (3 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L3-001 | GET | /api/leads | 无 cookie | 无 Session → 401 |
| TC-CUST-L3-002 | POST | /api/leads | user001 (无 LEAD.WRITE) | 无写权限 → 403 |
| TC-CUST-L3-003 | GET | /api/tags/{不存在ID} | admin | 不存在标签 → 40001 |

#### B. 业务规则校验 (4 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L3-011 | POST | /api/leads | admin | 重复 custName → CUST-40901 |
| TC-CUST-L3-012 | DELETE | /api/leads/{已提交leadId} | admin | 非草稿不可删除 → 40906 |
| TC-CUST-L3-013 | POST | /api/touch-tasks/{taskId}/success | user001 (非本人任务) | 无权操作 → 40302 |
| TC-CUST-L3-014 | POST | /api/customer-pool/{custId}/claim | user001 | 重复认领 → 40902 |

#### TC-CUST-L3-011 请求体 (重复名称)

```json
{
  "custName": "已存在的客户名",
  "unifiedCreditCode": "91110000987654321X",
  "industry": "IT",
  "groupType": "INDEPENDENT",
  "customerType": "CORPORATE",
  "isKeystone": false,
  "enterpriseType": "PRIVATE",
  "isAccountOpened": false
}
```

期望: `code: CUST-40901` 或 `code: CUST-40906`

#### C. 参数校验 (3 条)

| TC | 方法 | URL | Cookie | 说明 |
|----|------|-----|--------|------|
| TC-CUST-L3-021 | POST | /api/tags | admin | tagCode 含特殊字符 → 校验失败 |
| TC-CUST-L3-022 | GET | /api/tags?pageSize=200 | admin | pageSize 超限 → 校验失败 |
| TC-CUST-L3-023 | POST | /api/leads | admin | unifiedCreditCode 格式错误 → 校验失败 |

---

## 测试脚本模板

### 登录辅助函数

```bash
#!/bin/bash
# 登录函数 (放在测试脚本开头)
B="http://localhost:8080"
COOKIES_DIR="/tmp/portal_cust_cookies"
mkdir -p $COOKIES_DIR

login_user() {
  local user=$1
  curl -c "$COOKIES_DIR/$user.txt" -s -X POST "$B/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"password\":\"123456\"}" > /dev/null
  echo "Logged in as $user"
}

# 预登录所有用户
for user in admin user001 user002 tech_wu E001 E002; do
  login_user $user
done
```

### 通用请求函数

```bash
run_test() {
  local tc="$1"
  local method="$2"
  local url="$3"
  local cookie="$4"
  local data="$5"
  local expect="$6"

  if [ -n "$data" ]; then
    resp=$(curl -b "$cookie" -s -w "\n---HTTP:%{http_code}" \
      -X "$method" -H "Content-Type: application/json" \
      --data-binary "$data" "$B$url")
  else
    resp=$(curl -b "$cookie" -s -w "\n---HTTP:%{http_code}" \
      -X "$method" "$B$url")
  fi

  http=$(echo "$resp" | tail -1 | sed 's/---HTTP://')
  body=$(echo "$resp" | sed '$d')
  code=$(echo "$body" | python -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "PARSE_ERR")

  if [ "$expect" = "0" ]; then
    [ "$code" = "0" ] && echo "PASS $tc HTTP=$http code=$code" || echo "FAIL $tc HTTP=$http code=$code"
  elif [ "$expect" = "401" ]; then
    [ "$http" = "401" ] && echo "PASS $tc" || echo "FAIL $tc HTTP=$http"
  elif [ "$expect" = "403" ]; then
    [ "$http" = "403" ] && echo "PASS $tc" || echo "FAIL $tc HTTP=$http"
  else
    echo "CHECK $tc HTTP=$http code=$code"
  fi
}
```

---

## 预期结果统计

| 模块 | L1 | L2 | L3 | 总计 |
|------|----|----|----|------|
| portal-content-center | 10 | 25 | 10 | **45** |
| customer-marketing-center | 10 | 30 | 10 | **50** |
| **合计** | 20 | 55 | 20 | **95** |

---

## 已知依赖与风险

1. **MinIO 依赖**: 文档上传/下载测试 (TC-PORTAL-L2-043, TC-PORTAL-L2-042) 需要 MinIO 运行，无 MinIO 时标记为 `ENV-SKIP`
2. **Workflow 依赖**: 线索提交审批 (TC-CUST-L2-014) 需要 Flowable 工作流引擎正常运行
3. **测试数据依赖**: 部分 L3 测试 (如 TC-CUST-L3-013) 需要特定的预置数据 (如已存在的 PENDING 状态触达任务)
4. **DATA_SCOPE 配置**: L3 权限测试依赖 PT_ROLE_BIZ_SCOPE 中已配置正确的 bizType 权限，需确认种子数据完整

---

## 附录: 错误码速查

### Portal-Content-Center 错误码

| code | HTTP | 说明 |
|------|------|------|
| PORTAL-40001 | 400 | 导航不存在 |
| PORTAL-40002 | 400 | 通讯录员工不存在 |
| PORTAL-40003 | 400 | 产品不存在 |
| PORTAL-40301 | 403 | 无权编辑本人以外的通讯录 |
| PORTAL-40302 | 403 | 无权维护非本机构产品 |
| PORTAL-40901 | 409 | 产品代码已存在 |
| PORTAL-40902 | 409 | 员工已离职 |
| PORTAL-40903 | 409 | 导航名称在同一分类下已存在 |
| PORTAL-42202 | 422 | 导航 URL 格式非法 |
| PORTAL-42203 | 422 | 附件对象不存在 |

### Customer-Marketing-Center 错误码

| code | HTTP | 说明 |
|------|------|------|
| CUST-40001 | 400 | 标签不存在 |
| CUST-40003 | 400 | 线索不存在 |
| CUST-40004 | 400 | 客户不存在 |
| CUST-40302 | 403 | 无权访问非本人触达任务 |
| CUST-40901 | 409 | 存在在途流程，不允许此操作 |
| CUST-40902 | 409 | 客户已被本机构认领 |
| CUST-40906 | 409 | 线索非草稿状态不可编辑 |
| CUST-42201 | 422 | 标签参数校验失败 |
