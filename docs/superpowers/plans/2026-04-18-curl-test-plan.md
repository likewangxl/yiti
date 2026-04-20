# Portal-Content-Center & Customer-Marketing-Center 接口测试计划

> 版本：v1.0
> 日期：2026-04-18
> 目标：使用 curl 模拟真实用户登录后的完整业务流程测试

## 1. 测试概述

### 1.1 测试目标
- 验证 portal-content-center 和 customer-marketing-center 两个模块的所有接口
- 模拟真实用户登录流程（JWT Token）
- 覆盖所有核心业务场景

### 1.2 测试环境
- 开发环境：`http://localhost:8080`
- 数据库：MySQL localhost:3306/onepl
- 用户认证：JWT Token

### 1.3 测试用户
使用 `docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql` 中定义的测试账户：

| 工号 | 姓名 | 角色 | 机构 |
|------|------|------|------|
| E001 | 张三分行客户经理 | R_BRANCH_CM | ORG002 福州分行营业部 |
| E002 | 李四分支客户经理 | R_BRANCH_CM | ORG003 厦门分行 |
| E003 | 王五分行管理员 | R_BRANCH_ADMIN | ORG002 福州分行 |
| E004 | 赵六总行管理员 | R_ADMIN | ORG001 总行 |

密码统一：`123456`

---

## 2. 登录与环境准备

### 2.1 登录获取 Token

```bash
# 登录获取 JWT Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"empId":"E001","password":"123456"}' \
  -c cookies.txt -s | jq .
```

### 2.2 测试脚本头部

所有后续请求都需要带上 Cookie 或 Authorization Header：

```bash
# 使用 Cookie
curl -b cookies.txt http://localhost:8080/api/xxx

# 或使用 Authorization Header
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/xxx
```

---

## 3. Portal-Content-Center 接口测试

### 3.1 工作台接口

#### A.1 工作台数据聚合
```bash
curl -b cookies.txt http://localhost:8080/api/portal/workspace -s | jq .
```
**预期结果**：`code: "0"`，返回工作台四个区域数据
**权限**：无需登录即可访问

#### A.2 快捷入口列表
```bash
curl -b cookies.txt "http://localhost:8080/api/portal/shortcuts?shortcutType=ALL" -s | jq .
```
**预期结果**：`code: "0"`，返回快捷入口列表
**权限**：无需登录

#### A.3 保存个性化快捷入口
```bash
curl -X PUT http://localhost:8080/api/portal/shortcuts \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "shortcuts": [
      {
        "shortcutName": "新增线索",
        "shortcutUrl": "/customer-marketing/clue/create",
        "shortcutIcon": "icon-clue",
        "targetType": "INTERNAL",
        "sortOrder": 1
      }
    ]
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：无需登录

---

### 3.2 导航管理接口

#### B.1 导航列表（按 category 分组）
```bash
curl -b cookies.txt "http://localhost:8080/api/nav" -s | jq .
```
**预期结果**：`code: "0"`，返回导航列表
**权限**：`NAV.READ`

#### B.2 新增导航（管理员）
```bash
curl -X POST http://localhost:8080/api/admin/nav \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "navName": "测试导航",
    "navUrl": "https://test.bank.com",
    "navIcon": "icon-test",
    "navCategory": "测试分类",
    "sortOrder": 99
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回新导航 ID
**权限**：`NAV.WRITE`

#### B.3 编辑导航
```bash
curl -X PUT http://localhost:8080/api/admin/nav/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "navName": "测试导航-已编辑",
    "sortOrder": 100
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`NAV.WRITE`

#### B.4 删除导航
```bash
curl -X DELETE http://localhost:8080/api/admin/nav/1 \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`NAV.WRITE`

#### B.5 批量调整排序
```bash
curl -X PUT http://localhost:8080/api/admin/nav/sort \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '[
    {"id": 2, "sortOrder": 1},
    {"id": 3, "sortOrder": 2}
  ]' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`NAV.WRITE`

---

### 3.3 通讯录接口

#### C.1 通讯录列表（分页）
```bash
curl -b cookies.txt "http://localhost:8080/api/employees?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回员工列表
**权限**：`ADDRBOOK.LIST`

#### C.2 员工详情
```bash
curl -b cookies.txt http://localhost:8080/api/employees/E001 -s | jq .
```
**预期结果**：`code: "0"`，返回员工详情
**权限**：`ADDRBOOK.READ`

#### C.3 编辑员工信息
```bash
curl -X PUT http://localhost:8080/api/employees/E001 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "selfDesc": "这是一个测试描述"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`ADDRBOOK.WRITE`

#### C.4 模糊搜索（员工选择器）
```bash
curl -b cookies.txt "http://localhost:8080/api/employees/search?keyword=张&limit=10" -s | jq .
```
**预期结果**：`code: "0"`，返回匹配的员工列表
**权限**：`ADDRBOOK.READ`

---

### 3.4 产品资料库接口

#### D.1 产品列表
```bash
curl -b cookies.txt "http://localhost:8080/api/products?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回产品列表
**权限**：`PRODUCT.LIST`

#### D.2 产品详情
```bash
curl -b cookies.txt http://localhost:8080/api/products/1 -s | jq .
```
**预期结果**：`code: "0"`，返回产品详情
**权限**：`PRODUCT.READ`

#### D.3 支持中场支持的产品列表
```bash
curl -b cookies.txt http://localhost:8080/api/products/support-available -s | jq .
```
**预期结果**：`code: "0"`，返回所有支持中场支持的产品
**权限**：`PRODUCT.READ`

#### D.4 新增产品（管理员）
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "productCode": "TEST001",
    "productName": "测试产品",
    "productCategory": "CATEGORY_A",
    "supportForSupportRequest": true,
    "productDeptOrgCode": "ORG002",
    "description": "这是一个测试产品"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回新产品 ID
**权限**：`PRODUCT.WRITE`

#### D.5 编辑产品
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "productName": "测试产品-已编辑",
    "status": "ACTIVE"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`PRODUCT.WRITE`

#### D.6 删除产品（逻辑删除）
```bash
curl -X DELETE http://localhost:8080/api/products/1 \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`PRODUCT.WRITE`

#### D.7 导出产品列表
```bash
curl -b cookies.txt "http://localhost:8080/api/products/export?pageNo=1&pageSize=20" \
  -o products_export.xlsx -s
file products_export.xlsx
```
**预期结果**：下载 Excel 文件
**权限**：`PRODUCT.EXPORT`（高危操作）

---

### 3.5 文档管理接口

#### E.1 文档列表
```bash
curl -b cookies.txt "http://localhost:8080/api/documents?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回文档列表
**权限**：`DOC.LIST`

#### E.2 下载文档
```bash
curl -b cookies.txt http://localhost:8080/api/documents/1/download \
  -o document.pdf -s
file document.pdf
```
**预期结果**：下载文档文件
**权限**：`DOC.READ`

#### E.3 上传文档
```bash
curl -X POST http://localhost:8080/api/admin/documents \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "docTitle": "测试文档",
    "docCategory": "CATEGORY_A",
    "fileObjectId": "file-xxx-xxx"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回文档 ID
**权限**：`DOC.WRITE`

#### E.4 编辑文档
```bash
curl -X PUT http://localhost:8080/api/admin/documents/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "docTitle": "测试文档-已编辑"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`DOC.WRITE`

#### E.5 删除文档
```bash
curl -X DELETE http://localhost:8080/api/admin/documents/1 \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`DOC.WRITE`

---

## 4. Customer-Marketing-Center 接口测试

### 4.1 标签管理

#### A.1 标签列表（分页）
```bash
curl -b cookies.txt "http://localhost:8080/api/tags?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回标签列表
**权限**：`TAG.LIST`

#### A.2 启用标签列表
```bash
curl -b cookies.txt http://localhost:8080/api/tags/enabled -s | jq .
```
**预期结果**：`code: "0"`，返回所有启用标签
**权限**：无需特殊权限

#### A.3 新增标签
```bash
curl -X POST http://localhost:8080/api/tags \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "tagName": "测试标签",
    "tagCode": "TEST_TAG",
    "tagCategory": "测试分类",
    "tagPriority": 100,
    "description": "这是一个测试标签"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回标签 ID
**权限**：`TAG.WRITE`

#### A.4 编辑标签
```bash
curl -X PUT http://localhost:8080/api/tags/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "tagName": "测试标签-已编辑",
    "tagPriority": 200
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`TAG.WRITE`

#### A.5 启用/禁用标签
```bash
curl -X PUT http://localhost:8080/api/tags/1/status \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"status": "DISABLED"}' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`TAG.WRITE`

#### A.6 标签关联客户批量导入
```bash
curl -X POST http://localhost:8080/api/tags/1/customers/import \
  -F "file=@/path/to/tag_customers.xlsx" \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`，返回预览或执行结果
**权限**：`TAG.IMPORT`（高危操作）

#### A.7 导出标签客户
```bash
curl -b cookies.txt http://localhost:8080/api/tags/1/customers/export \
  -o tag_customers.xlsx -s
file tag_customers.xlsx
```
**预期结果**：下载 Excel 文件
**权限**：`TAG.EXPORT`（高危操作）

---

### 4.2 线索管理

#### B.1 线索列表（分页）
```bash
curl -b cookies.txt "http://localhost:8080/api/leads?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回线索列表
**权限**：`LEAD.LIST`

#### B.2 线索详情
```bash
curl -b cookies.txt http://localhost:8080/api/leads/1 -s | jq .
```
**预期结果**：`code: "0"`，返回线索详情
**权限**：`LEAD.READ`

#### B.3 新建线索草稿
```bash
curl -X POST http://localhost:8080/api/leads \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "custName": "测试公司",
    "unifiedCreditCode": "91350100XXXXXXXXX",
    "industry": "MANUFACTURING",
    "groupType": "INDEPENDENT",
    "customerType": "CORPORATE",
    "isKeystone": true,
    "enterpriseType": "PRIVATE",
    "isAccountOpened": false,
    "customerDesc": "这是一个测试客户描述",
    "creditAmount": 10000000.00
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回线索 ID 和状态
**权限**：`LEAD.WRITE`

#### B.4 编辑草稿
```bash
curl -X PUT http://localhost:8080/api/leads/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "customerDesc": "测试描述-已编辑"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`LEAD.WRITE`

#### B.5 提交审批
```bash
curl -X POST http://localhost:8080/api/leads/1/submit \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"remark": "请审批"}' -s | jq .
```
**预期结果**：`code: "0"`，返回流程实例 ID
**权限**：`LEAD.WRITE`

#### B.6 删除草稿
```bash
curl -X DELETE http://localhost:8080/api/leads/1 \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`LEAD.WRITE`

#### B.7 已通过线索生成新版本
```bash
curl -X POST http://localhost:8080/api/leads/1/edit \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "customerDesc": "更新后的描述"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回新版本线索 ID
**权限**：`LEAD.WRITE`

---

### 4.3 线索批量导入

#### C.1 导入预览
```bash
curl -X POST http://localhost:8080/api/leads/import/preview \
  -F "file=@/path/to/leads_import.xlsx" \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`，返回预览结果
**权限**：`LEAD.IMPORT`（高危操作）

#### C.2 执行导入
```bash
curl -X POST http://localhost:8080/api/leads/import \
  -F "file=@/path/to/leads_import.xlsx" \
  -F "confirmToken=xxx" \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`，返回批次 ID
**权限**：`LEAD.IMPORT`（高危操作）

#### C.3 导入批次列表
```bash
curl -b cookies.txt "http://localhost:8080/api/leads/import/batches?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回批次列表
**权限**：`LEAD.LIST`

#### C.4 批次详情
```bash
curl -b cookies.txt http://localhost:8080/api/leads/import/batches/1 -s | jq .
```
**预期结果**：`code: "0"`，返回批次详情
**权限**：`LEAD.READ`

---

### 4.4 客户管理

#### D.1 客户列表
```bash
curl -b cookies.txt "http://localhost:8080/api/customers?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回客户列表
**权限**：`CUSTOMER.LIST`

#### D.2 客户详情
```bash
curl -b cookies.txt http://localhost:8080/api/customers/1 -s | jq .
```
**预期结果**：`code: "0"`，返回客户详情
**权限**：`CUSTOMER.READ`

#### D.3 跨机构全量历史
```bash
curl -b cookies.txt http://localhost:8080/api/customers/1/history -s | jq .
```
**预期结果**：`code: "0"`，返回客户历史
**权限**：`CUSTOMER.READ`

#### D.4 转交维护负责人
```bash
curl -X POST http://localhost:8080/api/customers/1/transfer \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "targetEmpId": "E002",
    "reason": "工作调整"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`CUSTOMER.TRANSFER`（高危操作）

#### D.5 发起删除审批
```bash
curl -X POST http://localhost:8080/api/customers/1/delete-apply \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"reason": "客户已注销"}' -s | jq .
```
**预期结果**：`code: "0"`，返回新线索 ID
**权限**：`CUSTOMER.DELETE`（高危操作）

#### D.6 导出客户
```bash
curl -b cookies.txt "http://localhost:8080/api/customers/export" \
  -o customers.xlsx -s
file customers.xlsx
```
**预期结果**：下载 Excel 文件
**权限**：`CUSTOMER.EXPORT`（高危操作）

---

### 4.5 待认领客户池

#### E.1 待认领客户池列表
```bash
curl -b cookies.txt "http://localhost:8080/api/customer-pool?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回客户池列表
**权限**：`CUSTOMER_POOL.LIST`

#### E.2 认领客户
```bash
curl -X POST http://localhost:8080/api/customer-pool/1/claim \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "remark": "认领备注"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回认领结果
**权限**：`CLAIM.WRITE`

---

### 4.6 已认领客户

#### F.1 已认领客户列表
```bash
curl -b cookies.txt "http://localhost:8080/api/my-claims?scope=MINE&pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回认领列表
**权限**：`CLAIM.LIST`

#### F.2 取消认领
```bash
curl -X POST http://localhost:8080/api/claims/1/cancel \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"reason": "客户不符合条件"}' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`CLAIM.WRITE`

#### F.3 重新发起触达
```bash
curl -X POST http://localhost:8080/api/claims/1/re-touch \
  -b cookies.txt -s | jq .
```
**预期结果**：`code: "0"`，返回新触达任务 ID
**权限**：`CLAIM.WRITE`

---

### 4.7 触达任务

#### G.1 触达任务列表
```bash
curl -b cookies.txt "http://localhost:8080/api/touch-tasks?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回触达任务列表
**权限**：`TOUCH_TASK.LIST`

#### G.2 任务详情
```bash
curl -b cookies.txt http://localhost:8080/api/touch-tasks/1 -s | jq .
```
**预期结果**：`code: "0"`，返回任务详情
**权限**：`TOUCH_TASK.READ`

#### G.3 触达成功
```bash
curl -X POST http://localhost:8080/api/touch-tasks/1/success \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "logContent": "电话沟通，客户有意向",
    "photoUrls": ["minio://test/photo1.jpg"],
    "clientUuid": "550e8400-e29b-41d4-a716-446655440000",
    "operatorLocation": "福州市",
    "nextAction": "下周上门拜访"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回任务状态
**权限**：`TOUCH_TASK.WRITE`

#### G.4 触达取消
```bash
curl -X POST http://localhost:8080/api/touch-tasks/1/cancel \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "cancelReason": "客户无法联系",
    "clientUuid": "550e8400-e29b-41d4-a716-446655440001"
  }' -s | jq .
```
**预期结果**：`code: "0"`
**权限**：`TOUCH_TASK.WRITE`

#### G.5 新增触达日志
```bash
curl -X POST http://localhost:8080/api/touch-tasks/1/logs \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "logContent": "追加日志：再次电话联系",
    "clientUuid": "550e8400-e29b-41d4-a716-446655440002"
  }' -s | jq .
```
**预期结果**：`code: "0"`，返回日志 ID
**权限**：`TOUCH_TASK.WRITE`

---

### 4.8 触达管理视图

#### H.1 机构汇总
```bash
curl -b cookies.txt "http://localhost:8080/api/admin/touch-tasks/summary" -s | jq .
```
**预期结果**：`code: "0"`，返回机构汇总数据
**权限**：`TOUCH_REPORT.LIST`

#### H.2 全行触达明细
```bash
curl -b cookies.txt "http://localhost:8080/api/admin/touch-tasks?pageNo=1&pageSize=20" -s | jq .
```
**预期结果**：`code: "0"`，返回触达明细
**权限**：`TOUCH_REPORT.LIST`

#### H.3 导出触达明细
```bash
curl -b cookies.txt "http://localhost:8080/api/admin/touch-tasks/export" \
  -o touch_tasks.xlsx -s
file touch_tasks.xlsx
```
**预期结果**：下载 Excel 文件
**权限**：`TOUCH_REPORT.EXPORT`（高危操作）

---

## 5. 错误响应测试

### 5.1 权限不足测试
```bash
# 未登录用户访问受保护接口
curl http://localhost:8080/api/products -s | jq .
```
**预期结果**：`code: "401"` 或 `code: "403"`，无权限访问

### 5.2 资源不存在测试
```bash
curl -b cookies.txt http://localhost:8080/api/products/99999 -s | jq .
```
**预期结果**：`code: "PORTAL-40003"` 或 `code: "CUST-40003"`，产品不存在

### 5.3 参数校验失败测试
```bash
curl -X POST http://localhost:8080/api/tags \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"tagName": ""}' -s | jq .
```
**预期结果**：`code: "COMMON-40000"`，参数校验失败

### 5.4 业务冲突测试
```bash
# 尝试编辑非草稿状态的线索
curl -X PUT http://localhost:8080/api/leads/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"customerDesc": "测试"}' -s | jq .
```
**预期结果**：`code: "CUST-40906"`，线索非草稿状态不可编辑

---

## 6. 测试执行脚本

### 6.1 完整测试脚本

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
COOKIE_FILE="cookies.txt"

# 清理旧 Cookie
rm -f $COOKIE_FILE

echo "===== 1. 登录获取 Token ====="
curl -X POST $BASE_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"empId":"E001","password":"123456"}' \
  -c $COOKIE_FILE -s | jq .

echo ""
echo "===== 2. Portal 工作台测试 ====="
curl -b $COOKIE_FILE $BASE_URL/api/portal/workspace -s | jq .

echo ""
echo "===== 3. Portal 产品列表测试 ====="
curl -b $COOKIE_FILE "$BASE_URL/api/products?pageNo=1&pageSize=10" -s | jq .

echo ""
echo "===== 4. Customer 标签列表测试 ====="
curl -b $COOKIE_FILE "$BASE_URL/api/tags?pageNo=1&pageSize=10" -s | jq .

echo ""
echo "===== 5. Customer 线索列表测试 ====="
curl -b $COOKIE_FILE "$BASE_URL/api/leads?pageNo=1&pageSize=10" -s | jq .

echo ""
echo "===== 6. Customer 客户列表测试 ====="
curl -b $COOKIE_FILE "$BASE_URL/api/customers?pageNo=1&pageSize=10" -s | jq .

# 清理
rm -f $COOKIE_FILE
```

---

## 7. 验收标准

### 7.1 功能验收
- [ ] 所有接口返回正确的响应格式
- [ ] 权限控制正常工作
- [ ] 数据 CRUD 操作正常
- [ ] 分页、排序正常工作
- [ ] 导出功能正常（下载 Excel）

### 7.2 错误处理验收
- [ ] 未登录用户返回 401
- [ ] 无权限用户返回 403
- [ ] 资源不存在返回 404 或对应错误码
- [ ] 参数校验失败返回 422
- [ ] 业务冲突返回 409

### 7.3 性能验收
- [ ] 列表查询 < 300ms
- [ ] 详情查询 < 200ms
- [ ] 写操作 < 500ms

---

## 8. 已知问题

### 8.1 PT_RESOURCE 对齐问题（已修复）
修复文件：`docs/superpowers/sql/2026-04-18-pt-resource-fix.sql`

主要修复内容：
1. 修正 `RES_LEAD_IMP_BATCH` URL（`/api/leads/import-batches/*` → `/api/leads/import/batches/*`）
2. 修正 `RES_CUSTOMER_DEL_APP` METHOD（DELETE → POST）
3. 删除冗余 `RES_CUST_POOL`
4. 修正标签 METHOD（POST → PUT）
5. 添加缺失条目角色映射

### 8.2 需要手动确认的问题
1. `RES_LEAD_EXPORT` URL 为 `/api/leads/export`（POST），但 API 文档未定义 lead export 接口
2. `RES_TAG_ENABLE` / `RES_TAG_DISABLE` 方法已修正为 PUT

---

## 9. 附录

### 9.1 错误码速查表

| 错误码 | 含义 | 常见触发条件 |
|--------|------|-------------|
| `PORTAL-40001` | 导航不存在 | B.3/B.4 编辑/删除导航 |
| `PORTAL-40002` | 通讯录员工不存在 | C.2/C.3 员工详情/编辑 |
| `PORTAL-40003` | 产品不存在 | D.2-D.6 产品相关操作 |
| `CUST-40001` | 标签不存在 | A.4 编辑标签 |
| `CUST-40002` | 线索不存在 | B.2-B.7 线索相关操作 |
| `CUST-40003` | 客户不存在 | D.2-D.5 客户相关操作 |
| `CUST-40004` | 客户池记录不存在 | E.2 认领客户 |
| `CUST-40005` | 触达任务不存在 | G.2-G.5 触达任务操作 |
| `CUST-40906` | 线索非草稿状态不可编辑 | B.4/B.6 编辑/删除非草稿线索 |
| `CUST-40908` | 触达任务已有进行中 | F.3 重新发起触达 |
