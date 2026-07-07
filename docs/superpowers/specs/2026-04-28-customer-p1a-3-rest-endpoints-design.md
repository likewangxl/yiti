# 客户营销中心 — P1a 3 个真缺失 REST 端点设计

> **背景**：[2026-04-28 偏离度分析底稿](../sessions/2026-04-28-customer-marketing-center-doc-code-deviation.md) §10.4 / §10.8 列出 3 个文档要求但代码未实现的 REST 端点。本设计为补齐方案。
> **日期**：2026-04-28
> **批次**：A（共 A/B/C 三批，本批仅含 3 个 REST 端点；422 错误码 = 批次 B；403 错误码 = 批次 C）
> **状态**：已与用户对齐 task_type=FOLLOW_UP 决策，进入实现

---

## 1. 端点清单

| # | 方法 | 路径 | Controller | Service | 状态 |
|---|---|---|---|---|---|
| A1 | POST | `/api/claims/{claimId}/re-touch` | `ClaimController` | `ClaimService.reTouch(claimId, req)` — **新增** | 需新增 service 方法 |
| A2 | GET | `/api/admin/touch-tasks/summary?orgCode=...` | `AdminTouchTaskController` | `TouchTaskQueryApi.getOrgTouchSummary(orgCode)` — 已有 | 仅补 Controller |
| A3 | GET | `/api/leads/import/batches/{batchId}` | `LeadImportController` | `LeadImportService.getBatchById(batchId)` — 已有 | 仅补 Controller |

---

## 2. A1 — POST `/api/claims/{claimId}/re-touch`

### 2.1 业务语义
对**已有认领关系**的客户重新发起一次触达任务。前置条件：当前客户最近一次触达任务**已 SUCCESS 或 CANCELLED**（即非 PENDING/IN_PROGRESS 状态），否则拒绝。

### 2.2 请求/响应
**请求 DTO**（新增 `dto/req/ReTouchReqDTO.java`）：
```java
public class ReTouchReqDTO {
    @NotBlank
    private String reason;           // 重新触达原因（10~500 字符）
    private String planFinishTime;   // 可选：计划完成时间，缺省按默认 SLA
}
```

**响应**：复用现有 `TouchTaskRespDTO`（返回新创建的触达任务详情，前端可直接跳转到任务详情页）。

### 2.3 业务流程
```
1. 校验认领存在 + 当前用户对该认领可写
   └─ claim = claimMapper.selectById(claimId)
   └─ if (claim == null) → CUST-40404 认领关系不存在
   └─ if (!isCurrentUserOrgOrSelf(claim)) → CUST-40305 无权操作非本机构认领关系
2. 校验认领状态 ACTIVE
   └─ if (claim.status != ACTIVE) → CUST-40404 认领已取消（沿用现有码）
3. 查询客户当前 PENDING/IN_PROGRESS 触达任务
   └─ if exists → CUST-40908 触达任务已有进行中（**新增枚举到 CustomerErrorCode**）
4. 查询客户最近一次触达任务
   └─ if (latest.status NOT IN (SUCCESS, CANCELLED)) → CUST-40909 最近触达未完成（**新增枚举**）
5. 调 TouchTaskService.createFromClaim(claim, taskType=FOLLOW_UP, reason=req.reason, planFinishTime=req.planFinishTime)
6. 返回新 TouchTaskRespDTO
```

### 2.4 鉴权
```java
@BizAuth(bizType = BizType.CLAIM, action = ActionType.UPDATE)
@PostMapping("/{claimId}/re-touch")
```
不加 `highRisk`（按用户决策）。

### 2.5 错误码补齐（与批次 A 强相关，本批一并补）
- **CUST-40908** `RE_TOUCH_HAS_RUNNING("CUST-40908", "触达任务已有进行中，不可重新发起")` — 新增枚举
- **CUST-40909** `RE_TOUCH_LAST_NOT_FINISHED("CUST-40909", "最近触达任务未完成，不可重新发起")` — 新增枚举

> 这两条原本规划在 02 §4 但代码未实现；属于 §10.8 P1 "409 错误码补"，因 A1 强依赖这两条所以一并落地。

---

## 3. A2 — GET `/api/admin/touch-tasks/summary`

### 3.1 业务语义
管理后台按机构维度查询触达任务汇总（PENDING / IN_PROGRESS / SUCCESS / CANCELLED 各计数 + SLA 各档位计数）。

### 3.2 请求/响应
**请求**：
- 必填：`orgCode`（机构代码，含子树由 service 内部解析）
- 可选：`startDate` / `endDate`（默认近 30 天）

**响应**：直接返回 `TouchTaskQueryApi.getOrgTouchSummary(orgCode)` 的结果（已有 DTO，名字根据 api 实际签名为准）。

### 3.3 鉴权
```java
@BizAuth(bizType = BizType.TOUCH_TASK, action = ActionType.READ)
@GetMapping("/summary")
```

### 3.4 错误
- 机构不存在 → 复用 ORG_NOT_FOUND 或返回空数据（按 service 现有行为）
- 无 admin 权限 → 由 `@BizAuth` 拦截

---

## 4. A3 — GET `/api/leads/import/batches/{batchId}`

### 4.1 业务语义
查询单个导入批次的详情（含状态、行数、错误文件 URL 等）。

### 4.2 请求/响应
**响应**：新增 `dto/resp/LeadImportBatchDetailRespDTO.java`（如已有 `LeadImportBatchEntity` 转换器则复用）：
```java
public class LeadImportBatchDetailRespDTO {
    private String id;
    private String sourceFileName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String batchStatus;
    private String errorFileObjectId;  // 错误明细文件 MinIO ID
    private String businessKey;
    private LocalDateTime createdTime;
    private String createdBy;
}
```

### 4.3 鉴权
```java
@BizAuth(bizType = BizType.LEAD, action = ActionType.READ)
@GetMapping("/import/batches/{batchId}")
```

### 4.4 错误
- 批次不存在 → CUST-40406 BATCH_NOT_FOUND（已有枚举）

---

## 5. 公共

### 5.1 PT_RESOURCE 注册
新文件 `docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`，含 3 条 INSERT：
```sql
INSERT INTO `PT_RESOURCE` (`RESOURCE_ID`, `RESOURCE_NAME`, `URL_PATTERN`, `METHOD`, `BIZ_TYPE`, `ACTION_TYPE`, ...) VALUES
('CUST_CLAIM_RETOUCH', '重新发起触达', '/api/claims/*/re-touch', 'POST', 'CLAIM', 'UPDATE', ...),
('CUST_TOUCH_ADMIN_SUMMARY', '机构触达汇总', '/api/admin/touch-tasks/summary', 'GET', 'TOUCH_TASK', 'READ', ...),
('CUST_LEAD_BATCH_DETAIL', '导入批次详情', '/api/leads/import/batches/*', 'GET', 'LEAD', 'READ', ...);
```
RESOURCE_ID 严格 ≤ 20 字符（项目硬约束）。

### 5.2 测试策略（TDD 红→绿→重构）
- **Service 单元测试**：仅 `ClaimServiceTest.reTouch_*`（A2/A3 复用现有 `TouchTaskQueryApi`/`LeadImportService` 已有覆盖）
  - `reTouch_success_createsFollowUpTask`
  - `reTouch_claimNotFound_throwsCust40404`
  - `reTouch_otherOrgClaim_throwsCust40305`
  - `reTouch_runningTouchTask_throwsCust40908`
  - `reTouch_lastNotFinished_throwsCust40909`
- **Controller 集成测试**：3 个端点共 12+ 用例
  - 每端点至少：成功路径 / 404 / 数据范围拒绝 / 409 状态冲突（A2 不需 409）
- **base class**：`AbstractControllerIntegrationTest` + `@WithMockEmpContext`

### 5.3 文件改动清单
**新增**（4 个 Java + 1 SQL + 1 spec）：
1. `customer-marketing-center/src/main/java/.../dto/req/ReTouchReqDTO.java`
2. `customer-marketing-center/src/main/java/.../dto/resp/LeadImportBatchDetailRespDTO.java`
3. `customer-marketing-center/src/test/java/.../service/ClaimServiceReTouchTest.java`（新文件而非加到 `ClaimServiceTest`，避免单文件过大）
4. `customer-marketing-center/src/test/java/.../controller/[Claim/AdminTouchTask/LeadImport]ControllerReTouchTest.java`（3 个新文件）
5. `docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`

**修改**（5 个 Java + 4 个文档）：
1. `customer-marketing-center/.../enums/CustomerErrorCode.java` — 加 `RE_TOUCH_HAS_RUNNING` + `RE_TOUCH_LAST_NOT_FINISHED`
2. `customer-marketing-center/.../service/ClaimService.java` — 加 `reTouch` 方法
3. `customer-marketing-center/.../controller/ClaimController.java` — 加 `POST /{claimId}/re-touch`
4. `customer-marketing-center/.../controller/admin/AdminTouchTaskController.java` — 加 `GET /summary`
5. `customer-marketing-center/.../controller/LeadImportController.java` — 加 `GET /batches/{batchId}`
6. `customer-marketing-center/CLAUDE.md` — REST 端点表 +3 行
7. `docs/modules/customer-marketing-center/03-接口设计与报文.md` — 删 §F.3 / §H.1 / §C.4 的"未实现"标注（如有），更新为已实现路径
8. `docs/modules/customer-marketing-center/02-后端架构.md` §3 包结构 — `dto/req/` 加 ReTouchReqDTO、`dto/resp/` 加 LeadImportBatchDetailRespDTO
9. `docs/superpowers/sessions/2026-04-28-customer-marketing-center-doc-code-deviation.md` §10.8 — P1 业务确认行加完成 ✓

### 5.4 完成验收
1. `mvn test -pl customer-marketing-center` 全绿（旧 321 + 新 ~17 = ~338 测试）
2. `MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED"` 解 cglib 兼容
3. 子 agent 一次性 review spec + code，输出 severity-rated 反馈
4. 应用 review 反馈后 commit + push

---

## 6. 风险与开放项

| 风险/开放项 | 说明 | 处理 |
|---|---|---|
| `TouchTaskService.createFromClaim` 签名是否支持 `taskType=FOLLOW_UP + reason` 入参 | 需 grep 确认 | 实现阶段第 1 步 grep；不支持则扩展签名 |
| `getOrgTouchSummary` 实际返回 DTO 类名 | 需读 `TouchTaskQueryApi` 源 | 实现阶段第 1 步读 api 文件 |
| `LeadImportBatch` 实体字段是否含 `createdBy` 等 RespDTO 字段 | 需读 entity | 实现阶段第 1 步读 entity，缺则简化 RespDTO |
| `ActionType` 枚举是否包含 `READ`/`UPDATE` | 需 grep `BizScopeApi` 模块 | 实现阶段第 1 步确认；不存在则用现有最接近的 |
| Controller 集成测试需要 H2 schema 包含 `cust_claim` + `touch_task` 等表 | 现有 321 测试已覆盖，可复用 | 沿用现有 `application-test.yml` |

风险均在实现阶段第 1 步「阅读现存代码」时统一确认，不阻塞设计 approval。
