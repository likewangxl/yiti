# customer-marketing-center P1 三批改动进度（2026-04-28 ~ 2026-04-29）

> 本文档汇总 customer-marketing-center 模块基于偏离度分析底稿（`2026-04-28-customer-marketing-center-doc-code-deviation.md`）
> 拆解的 P1 子批次 A/B/C 全部交付，记录范围、决策、commit、技术债。

---

## 1. 背景与全景

**起点**：`2026-04-28-customer-marketing-center-doc-code-deviation.md` 偏离度分析底稿 §10
列出 P1 业务确认事项 3 类、共 18 条改代码项：
1. 3 个真缺失 REST 端点（re-touch / summary / batch detail）
2. 422 系列 8 条错误码补实现
3. 403 系列 7 条错误码补实现

用户决策：**全部落地**，按用户先前批次顺序拆 A/B/C 三批，inline 执行 + 末端 reviewer
合并 spec+code review 模式。

**P1 整体进度**：✓ 全部交付，3 commit 推送 master。

| 批次 | commit | 测试增量 | 范围 |
|---|---|---|---|
| P1a | `9c98e46` | +14 测试（4 service + 10 controller） | 3 REST 端点 + 3 错误码 + 2 DTO |
| P1B | `a0b3ea8` | +11 测试（4 LeadImport + 2 TagCust + 5 TouchLog） | 8 条 422 校验 + CUST-40006 迁移 |
| P1C | `d702b7f` | +6 测试（3 TouchTask + 2 Customer + 1 Claim） | 7 条 403 + UserApi 跨模块 API |

**累计**：customer-marketing-center surefire 由 321（基线）→ 352（+31 新测试）。

---

## 2. P1a：3 REST 端点（commit `9c98e46`，2026-04-28）

**目标**：补 03 文档要求但代码缺失的 3 个端点 + 业务必需的 2 条 409 错误码。

### 2.1 落地清单

| 端点 | 实现位置 |
|---|---|
| `POST /api/claims/{claimId}/re-touch` | ClaimController + ClaimService.reTouch |
| `GET /api/admin/touch-tasks/summary` | AdminTouchTaskController.summary |
| `GET /api/leads/import/batches/{batchId}` | LeadImportController.getBatch |

### 2.2 新增错误码

- CUST-40305 CLAIM_ORG_FORBIDDEN（reTouch 跨机构防越权，P1C 后扩展到 cancelClaim）
- CUST-40908 RE_TOUCH_HAS_RUNNING
- CUST-40909 RE_TOUCH_LAST_NOT_FINISHED（占位，避免未来撞码）
- 409 段按编号递增重排，40907/40910/40911 留空给 B/C

### 2.3 新增/修改文件

- DTO：`ReTouchReqDTO`、`LeadImportBatchDetailRespDTO`
- Service：`TouchTaskService.createFollowUpTask`（不动既有 createFromClaim）
- PT_RESOURCE：`docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`（3 INSERT）
- 文档：CLAUDE.md REST 表 +3 行；02 §3 dto/req+resp +2 行；偏离度底稿 §10.4/§10.8 标 done

### 2.4 reviewer 结论

APPROVE WITH MINOR CHANGES — 0 CRITICAL / 1 MAJOR 已修（CustomerErrorCode 409 段按编号重排）/
4 MINOR 留作 B/C 技术债（NPE 防御、SecureRandom、@BizAuth 真实切面集成测试、explicit DTO 工厂方法）。

---

## 3. P1B：422 业务校验 8 条（commit `a0b3ea8`，2026-04-29）

**目标**：补 422 系列 8 条业务校验错误码。

### 3.1 用户决策

| 决策 | 选项 |
|---|---|
| CUST-40006 IMPORT_ROW_LIMIT_EXCEEDED 重叠 | (a) 重命名为 CUST-42205 IMPORT_ROWS_TOO_MANY |
| MAX_IMPORT_ROWS 1000 vs 5000 | 改为 5000（对齐 02 文档）|
| CUST-42201 行级校验 | 占位枚举，行级校验留 V1.x（既有 TODO）|
| CUST-42202 标签导入校验 | TagCustomerService.importCustomers 加 custId 在 cust_master 中存在性校验 |

### 3.2 落地清单

| 错误码 | 触发位置 |
|---|---|
| CUST-42201 LEAD_IMPORT_VALIDATION_FAILED | 占位（无触发逻辑）|
| CUST-42202 TAG_IMPORT_VALIDATION_FAILED | TagCustomerService.assertCustIdsValid |
| CUST-42203 IMPORT_FILE_FORMAT_INVALID | LeadImportService.assertFileFormatAllowed (csv/xlsx/xls) |
| CUST-42204 IMPORT_FILE_TOO_LARGE | LeadImportService preview（10MB）|
| CUST-42205 IMPORT_ROWS_TOO_MANY | 替代被删的 CUST-40006（5000 行）|
| CUST-42206 TOUCH_LOG_CONTENT_REQUIRED | TouchLogService.assertContentOrPhotoPresent |
| CUST-42207 TOUCH_LOG_PHOTO_LIMIT_EXCEEDED | TouchLogService.assertPhotoCountWithinLimit (>9) |
| CUST-42208 TOUCH_LOG_PHOTO_FORMAT_INVALID | TouchLogService.assertPhotoFormatsAllowed (jpg/jpeg/png/heic) |

### 3.3 reviewer 结论

REQUEST CHANGES → 1 MAJOR（custId 重复假阳性）已修（assertCustIdsValid 入口先 LinkedHashSet 去重）。
3 MINOR 留作技术债（手写 JSON 解析切 Jackson / 10MB 阈值 / 前端 i18n CUST-40006→CUST-42205 迁移）。

---

## 4. P1C：403 权限校验 7 条（commit `d702b7f`，2026-04-29）

**目标**：补 403 系列 7 条权限错误码。

### 4.1 用户决策

| 决策 | 选项 |
|---|---|
| CUST-40301 vs CUST-40906 | (A) 升级 LEAD_NOT_DRAFT(CUST-40003) → LEAD_EDIT_FORBIDDEN(CUST-40301) |
| CUST-40302 触达任务执行人校验范围 | (B) markSuccess + cancel 加 assignee 校验，addLog 不动；admin 绕过 |
| CUST-40303 删除客户越权 | (B) 加 admin force-delete 端点 → **本批占位，端点 follow-up** |
| CUST-40304 跨机构历史二次校验 | 沿用 @BizAuth 约定，不加 service 二次校验，占位枚举 |
| CUST-40305 cancelClaim 扩展 | (a) ClaimService.cancelClaim 也加跨机构校验（P1a 仅 reTouch 用过）|
| CUST-40306/40307 接收人校验 | (a) 通过新增 UserApi.getUserRoleCodes/getUserByEmpId 校验角色+机构 |

### 4.2 落地清单

| 错误码 | 触发位置 |
|---|---|
| CUST-40301 LEAD_EDIT_FORBIDDEN | LeadService.requireExistsAndDraft（升级自 40003）|
| CUST-40302 TOUCH_TASK_ACCESS_FORBIDDEN | TouchTaskService.assertAssigneeOrAdmin |
| CUST-40303 CUSTOMER_DELETE_NEED_APPROVAL | ⚠ 占位（admin 端点 follow-up）|
| CUST-40304 HISTORY_ACCESS_FORBIDDEN | ⚠ 占位（@BizAuth 已覆盖）|
| CUST-40305 CLAIM_ORG_FORBIDDEN | P1a→P1C 扩展到 cancelClaim |
| CUST-40306 TRANSFER_ROLE_MISMATCH | CustomerService.assertReceiverEligible |
| CUST-40307 TRANSFER_ORG_MISMATCH | 同上 |

### 4.3 跨模块 API 新增

`auth-permission-center` 新增 `UserApi.getUserRoleCodes(empId) → Set<String>`：
- UserFacade 实现：`UserRoleMapper.selectRolesByUserId` + roleCode 提取
- 已知不走缓存（PermissionCacheService 缓存的是 roleIds 不是 roleCodes，无法直接复用）

### 4.4 签名变更（破坏性，模块内）

- `TouchTaskService.markSuccess(taskId)` → `markSuccess(taskId, operatorEmpId, isAdmin)`
- `TouchTaskService.cancel(taskId, reason)` → `cancel(taskId, reason, operatorEmpId, isAdmin)`
- `ClaimService.cancelClaim(claimId, reason, empId)` → `+operatorOrgCode`

bootstrap CustomerMarketingCenterIT.java:150 同步更新（reviewer 发现的 CRITICAL 漏改已修）。

### 4.5 ⚠️ BREAKING CHANGE

**CUST-40003 (LEAD_NOT_DRAFT)** 重命名为 **CUST-40301 (LEAD_EDIT_FORBIDDEN)**：
- code: `CUST-40003` → `CUST-40301`
- message: "线索非草稿状态，不允许编辑" → "无权编辑非草稿状态线索"
- HTTP 语义：409 → 403

**前端 i18n 字符串映射需同步更新**。

### 4.6 reviewer 结论

REQUEST CHANGES → 1 CRITICAL（bootstrap IT markSuccess 旧签名）已修；
3 MAJOR 部分修复（UserApi 加 cache javadoc 标注 / 偏离度新增 follow-up 段含
CUST-40003→40301 BREAKING CHANGE 警示）；MINOR/NIT 留作后续技术债。

---

## 5. 累计未处理技术债清单

### 5.1 P1A-followup（4 条）

1. ClaimService.reTouch:204 `claim.getOrgId().equals(operatorOrgCode)` NPE 风险（P1c 抽公共方法时一并修）
2. TouchTaskService.generateTaskNo `new Random()` 每次 new 实例 → 改 `SecureRandom` 共享
3. 3 个 controller 测试缺 `@BizAuth` 真实切面集成测试（仅 mock 整 service）
4. LeadImportBatchDetailRespDTO 用 BeanUtils.copyProperties 全字段裸拷贝 → 加显式 `static from(LeadImportBatch)` 工厂方法

### 5.2 P1B-followup（4 条）

1. TouchLogService.parsePhotoUrls 手写 JSON 解析 → 切 Jackson `ObjectMapper.readValue`
2. LeadImportService 10MB 文件阈值远宽于 5000 行实际容量（200B/行 × 5000 = 1MB），考虑降到 ~3MB
3. 前端 i18n 检查 CUST-40006 → CUST-42205 字符串映射迁移
4. TouchLogServiceTest 缺边界用例（9 张正向上限 / 大小写混合 / 非 JSON 数组形态）

### 5.3 P1C-followup（7 条，已记入偏离度底稿）

1. **CUST-FU-40003-breaking-frontend**：CUST-40003 → CUST-40301 前端 i18n 迁移
2. **CUST-FU-40303-admin-delete**：admin force-delete 端点（依赖 CustMasterMapper 新增 deleteById）
3. **CUST-FU-40304-history-doc**：03 文档对齐 @BizAuth 约定（说明 service 层无二次校验）
4. **CUST-FU-40306-rolecode-const**：抽 `RoleCodeConstants.R_RM` 公共常量类（避免硬编码字符串）
5. **CUST-FU-40307-receiver-not-found**：新增 CUST-404xx RECEIVER_NOT_FOUND 错误码区分接收人不存在
6. **CUST-FU-userapi-cache**：UserApi.getUserRoleCodes 加 PermissionCacheService 缓存（cache key `auth:user-role-codes:{empId}` 5min TTL）
7. **CUST-FU-claim-org-check-extract**：抽 `assertSameOrg(claim, operatorOrgCode)` 私有方法 + reTouch NPE 防御

### 5.4 偏离度底稿原 P2 长期项（未启动）

- 错误码编号体系整体重对齐（双方零一致条目，需统一规划）
- `@BizAuth.highRisk` 字段 9 处落地（待框架支持）

---

## 6. 关键学习与陷阱

### 6.1 Stale jar 处理

**多次踩坑**：跨模块改动后必须 `mvn clean install -DskipTests` 全模块刷 m2，否则 bootstrap test
会用旧 jar 报奇怪的 ClassNotFoundException / UnsatisfiedDependencyException / 编译方法找不到。
CLAUDE.md 已记录此规则，建议每批末尾验收前固定执行。

### 6.2 git stash + mvn 误用

P1C 验收期间执行 `git stash && mvn install && ... ; git stash pop` 验证 baseline，
mvn install 把当前 main 类编译进 m2 jar 后，stash pop 还原源码但 jar 已是 stash 状态，
导致 customer-marketing-center 编译报 "cannot find symbol getUserRoleCodes"。
**教训**：stash 验证后必须重新跑 install 刷新 jar。

### 6.3 Inline 模式 vs spec/plan 文件

P1a 写了完整 spec + plan，P1B/P1C 沿用 inline 模式（直接侦察 → 决策点确认 → 执行）。
Inline 模式更高效，但需要：
- 明确决策点抛给用户（不假定）
- 末端 reviewer 合并 review 弥补缺失的预审
- 提交时 commit message 完整描述决策与边界

### 6.4 sed 批量更新测试方法签名

P1C 改 markSuccess/cancel/cancelClaim 签名，用 sed 批量替换 5+ 测试调用比逐个 Edit 高效 5×。
模式：`sed -i 's|markSuccess("\([^"]*\)")$|markSuccess("\1", "E10001", false)|g'`。

---

## 7. 文件位置索引

- **本文档**：`docs/superpowers/sessions/2026-04-29-customer-p1-three-batches-progress.md`
- **偏离度底稿**：`docs/superpowers/sessions/2026-04-28-customer-marketing-center-doc-code-deviation.md`
- **P1a spec**：`docs/superpowers/specs/2026-04-28-customer-p1a-3-rest-endpoints-design.md`
- **P1a plan**：`docs/superpowers/plans/2026-04-28-customer-p1a-3-rest-endpoints.md`
- **P1a SQL**：`docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`
- **模块 CLAUDE.md**：`customer-marketing-center/CLAUDE.md`（已索引本文档）
- **错误码 enum**：`customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/CustomerErrorCode.java`
