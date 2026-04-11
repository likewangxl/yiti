# portal-content-center V1 首版切片设计（工作台聚合 + 产品资料库）

**创建日期**：2026-04-11
**修订日期**：2026-04-11（r2，根据 spec-document-reviewer 反馈修订）
**作者**：Claude Code (与 leid 协作)
**模块**：`portal-content-center`
**版本**：V1 首版（仅实现 10 个接口的子集）

**权威前置文档（按优先级）**：

1. `docs/common-dev-guide.md`（共享开发规范，全局权威）
2. `docs/modules/portal-content-center/03-接口设计与报文.md`（接口契约权威）
3. `docs/modules/portal-content-center/06-并发与事务策略.md`（并发/事务策略权威）
4. `docs/modules/portal-content-center/09-依赖契约摘要.md`（跨模块依赖权威）
5. `docs/modules/portal-content-center/05-表结构DDL.md`（数据模型权威）
6. `docs/schema/ddl-portal.sql`（DDL 源，已与 05 对齐）
7. `docs/schema/seed-v1.sql`（PT_RESOURCE 和角色绑定基线）

**本 spec 的定位**：**切片契约**，明确本次开发的范围、偏离原 03/06/09 文档的决策及理由。当 spec 与前置文档冲突时，**前置文档为权威**，本 spec 必须修订以对齐；当前置文档未覆盖或与现状有冲突时，由本 spec 显式声明决策。

---

## 0. r2 修订总结（与 r1 的差异）

r1 首版经 spec-document-reviewer 指出 6 处 P0 + 4 处 P1 的对齐缺口。r2 按 **"以前置文档为权威，不凭空发明"** 原则全面修订：

| 类别 | r1 问题 | r2 修订 |
|---|---|---|
| A.1 WorkspaceDTO 字段 | 凭空发明 `touchTasks / kpiCards / degraded / failedSources` | 严格按 03 §A.1 的 7 字段：`todoCount / unreadNotificationCount / recentTodos / recentNotifications / metricCards / shortcuts / aggregateErrors` |
| A.1/A.2/A.3 鉴权 | 凭空添加 `@BizAuth(PORTAL_WORKSPACE/PORTAL_SHORTCUT)` | **无需 @BizAuth**（遵循 03 §A.1/A.2/A.3）；天然按当前登录用户过滤 |
| D.4/D.5 事务方案 | `SELECT FOR UPDATE` + 同事务双向同步 | **采用 common-dev-guide §7 的 `@TransactionalEventListener(AFTER_COMMIT)`**（理由见 §4a） |
| D.6 前置检查 | 缺失 | 补 `addrbookMapper.countEmployeesReferringProduct`，refCount > 0 时抛 `PORTAL-40905`（本 spec 新增错误码） |
| 错误码映射 | `PORTAL-40302 = 数据范围越权`、`PORTAL-42203 = 负责人不存在` | 按 03 §I 权威清单：`PORTAL-40302 = 无权维护非本机构产品`、`PORTAL-42203 = 附件对象不存在` |
| Stub 设计 | 定义 `CustomerQueryApi.getTouchTaskSummary` + `MetricApi.getPersonalKpiCards` | 删除 CustomerQueryApi stub（A.1 不再有 touchTasks 字段）；MetricApi 采用 09 §4.1 的真实签名 `getUserMetricCards(empId): List<MetricCardDTO>` + MetricAdapter 包装模式（`@Autowired(required=false)`） |
| PT_RESOURCE | 放在"如有差异"模糊条款 | Phase 0 显式产出 `2026-04-11-portal-resources-align.sql`，补 3 条缺失资源 |
| D.4/D.5 缺校验 | 缺机构归属校验和错误码 | 补"当前用户 orgCode 必须等于 productDeptOrgCode 或为管理员"校验 |
| D.5 不可变字段 | 未说明 | 明示 `productCode` 和 `productDeptOrgCode` 不可修改 |
| D.7 > 5000 TODO 错误码 | `PORTAL-42299`（凭空发明） | 改为明确的行为：**V1 阶段 > 5000 行直接拒绝，返回 03 §I 已有的 `PORTAL-42207`**，消息文案说明"V1 暂不支持异步导出，请增加过滤条件将结果控制在 5000 行以内" |

---

## 1. 背景与目标

### 1.1 背景

项目 `branch-platform`（银行分行业务平台）已完成 5 个模块（common / auth / governance / workflow / bootstrap）并通过 100 条接口的全量测试。下一阶段进入 5 个待开发业务模块的实现。

5 个模块的完整设计文档已在 `docs/modules/<module>/01-09` 齐备，并通过 2026-04-10 的维度补齐达到"字段级可执行"精度。

### 1.2 本切片目标

选 **portal-content-center** 作为第一个进入开发的业务模块，但**不实现全部 30+ 接口**，而是选一个**最小可验证子集**（10 个接口），用于：

1. 从零建立 portal 模块的 Maven 骨架 + 包结构，成为后续 customer/bizapp/performance 开发的参考蓝本
2. 跑通"TDD（Service 单测 + Mapper Testcontainers 集成测试 + Controller MockMvc 集成测试）+ subagent-driven-development + code review"的完整工具链
3. 验证跨模块 Api 的 **Adapter + `@Autowired(required=false)`** 降级模式（为 MetricApi 未实现时的先行集成铺路，参照 09 §4.1）
4. 落地 `@BizAuth + DataScope` 的真实测试闭环（通过自定义 `@WithMockEmpContext` JUnit 扩展）

### 1.3 为什么选这 10 个接口

这 10 个接口的选取覆盖了 3 个关键维度：

| 维度 | 接口 | 价值 |
|---|---|---|
| **只读 CRUD** | D.3 / D.1 / D.2 | 最简单的 TDD 起手式，验证 Mapper + DATA_SCOPE + 字典翻译 |
| **写操作 + 事件** | D.4 / D.5 / D.6 | 验证单表事务 + 前置校验 + AFTER_COMMIT 事件发布 + 消费者 |
| **导出 + 聚合** | D.7 / A.1 / A.2 / A.3 | 验证导出规范附录 H + CompletableFuture 并行聚合 + 降级策略 |

### 1.4 范围外（V1 不做）

- **B 章节 网址导航**（B.1-B.5）
- **C 章节 通讯录 REST 接口**（C.1-C.4；但内部 Repository 仍需建立，D.2 产品详情的负责人回显和 D.4/D.5 的 empId 存在性校验依赖它）
- **E 章节 文档管理**（E.1-E.5）
- **异步导出任务**（D.7 > 5000 行分支，V1 返回 `PORTAL-42207` 拒绝）
- **BPMN 文件补齐**
- **Kafka / RocketMQ / sys_event_outbox 表**（V1 事件只在进程内用 `@TransactionalEventListener(AFTER_COMMIT)` 发布）
- **`common-test` 抽出**（下一个模块开发时再考虑）
- **前端集成**

---

## 2. 整体架构

### 2.1 新建 portal-content-center Maven 模块

```
portal-content-center/
├── pom.xml                                 # 声明依赖 common/auth/governance/workflow 等
├── CLAUDE.md                               # 模块级上下文
└── src/
    ├── main/
    │   ├── java/com/bank/branch/platform/portal/
    │   │   ├── api/                        # 对外 API（ProductApi + dto）
    │   │   ├── adapter/                    # ★ 未实现模块的 Adapter 包装（替代原 stub 方案）
    │   │   │   ├── MetricAdapter.java      # 包装 @Autowired(required=false) MetricApi
    │   │   │   └── dto/
    │   │   │       └── MetricCardDTO.java  # 本地投影，与 09 §4.1 字段一致
    │   │   ├── controller/                 # 3 个 Controller
    │   │   │   ├── WorkspaceController.java # A.1
    │   │   │   ├── ShortcutController.java  # A.2/A.3
    │   │   │   └── ProductController.java   # D.1-D.7
    │   │   ├── facade/                     # ProductFacade (ProductApi 实现)
    │   │   ├── service/                    # 业务逻辑
    │   │   │   ├── WorkspaceService.java
    │   │   │   ├── ShortcutService.java
    │   │   │   ├── ProductService.java
    │   │   │   ├── AddrbookService.java    # ★ 内部用，不对外
    │   │   │   └── ProductExportService.java
    │   │   ├── listener/                   # ★ AFTER_COMMIT 事件消费者
    │   │   │   └── ProductResponsibleSyncListener.java
    │   │   ├── mapper/
    │   │   ├── entity/
    │   │   ├── enums/
    │   │   ├── convert/
    │   │   ├── config/
    │   │   │   ├── PortalMyBatisConfig.java
    │   │   │   └── PortalAsyncConfig.java  # 工作台聚合线程池
    │   │   └── typehandler/
    │   │       └── JsonStringListTypeHandler.java
    │   └── resources/
    │       └── mapper/                     # MyBatis XML
    └── test/
        ├── java/com/bank/branch/platform/portal/
        │   ├── support/
        │   │   ├── WithMockEmpContext.java
        │   │   ├── MockEmpContextExtension.java
        │   │   ├── AbstractMapperIntegrationTest.java
        │   │   └── AbstractControllerIntegrationTest.java
        │   ├── service/
        │   ├── mapper/
        │   ├── listener/                   # 事件消费者测试
        │   └── controller/
        └── resources/
            └── sql/
                ├── ddl-combined-for-portal.sql  # 由 Phase 0 产出
                └── portal-test-data.sql
```

### 2.2 父 pom.xml 与 bootstrap 的修改

- 父 pom.xml 的 `<modules>` 增加 `portal-content-center`（放在 workflow-center 之后）
- 父 pom.xml 的 `<dependencyManagement>` 增加 portal-content-center 的 artifact 声明
- bootstrap/pom.xml 增加 portal-content-center 依赖

### 2.3 portal/pom.xml 依赖清单

**运行时依赖**：
- common-web / common-trace / common-security / common-aop / common-db
- auth-permission-center（`@BizAuth`、`DataScopeContext`、`CurrentUserApi`、`OrgApi`、`BizScopeApi`）
- system-governance-center（`FileApi`、`NotifyApi`、`AuditApi`、`DictApi`）
- workflow-center（`WorkflowQueryApi`）
- spring-boot-starter-web
- mybatis-spring-boot-starter
- spring-boot-starter-data-redis
- easyexcel（Alibaba）—— D.7 同步导出

**测试依赖**：
- spring-boot-starter-test
- mybatis-spring-boot-starter-test
- testcontainers-mysql
- testcontainers-junit-jupiter
- spring-security-test

### 2.4 基础包路径

`com.bank.branch.platform.portal`

### 2.5 关键架构决策（与已有模块一致）

1. **贫血模型**：Entity 不含业务逻辑，Service + DAO + Entity 分层
2. **跨模块只通过 `*Api`**：严禁直接依赖其他模块的 mapper/entity/serviceImpl
3. **MyBatis XML 优先**：所有 SQL 写在 XML
4. **错误码前缀**：`PORTAL-{HTTP_STATUS}{SEQ}`
5. **事件发布**：**V1 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `ApplicationEventPublisher`**（进程内），详见 §4a
6. **禁用测试并行**：`junit.jupiter.execution.parallel.enabled = false`（避免 `@WithMockEmpContext` 的 ThreadLocal 串数据）

---

## 3. 数据模型

### 3.1 使用的表（3 张，全部为 portal 自有）

| 表 | 主键 | 本次作用 | DDL 源 |
|---|---|---|---|
| `product_info` | `id` VARCHAR(64) | 产品资料库的主表 | `ddl-portal.sql §4` |
| `portal_shortcut` | `id` VARCHAR(32) | 快捷入口 | `ddl-portal.sql §2` |
| `addrbook_employee` | `emp_id` VARCHAR(32) | 通讯录（内部用，不开 REST） | `ddl-portal.sql §3` |

**不涉及的表**：`portal_nav`、`doc_info`

### 3.2 JSON 字段处理

`product_info.responsible_emp_ids` 和 `addrbook_employee.responsible_product_ids` 都是 `TEXT` 类型存 JSON 数组字符串。

**处理方式**：
- **存储**：Jackson 序列化为 `["E10001","E10002"]`
- **TypeHandler**：自定义 `JsonStringListTypeHandler implements TypeHandler<List<String>>`
- **查询**：**应用层反序列化 + IN 查询**，不使用 MySQL `JSON_CONTAINS`（避免方言耦合）
- **约束**：`responsibleEmpIds` 最多 10 个（应用层校验，03 §D.4 未明示上限时采用 spec 决定的 10）
- **TypeHandler 测试**：必须覆盖 `null` / `""` / `"[]"` / `"[\"E001\"]"`

### 3.3 DATA_SCOPE 过滤

`product_info` 按 `product_dept_org_code` 过滤。参考 `docs/common-dev-guide.md §5.1` 的 7 种 `DataScopeType` 完整枚举：

| DataScopeType | 谓词模板 | portal 是否使用 |
|---|---|---|
| `ALL` | `1=1` | ✅ |
| `ORG_SUBTREE` | `t.product_dept_org_code IN (...)` | ✅ |
| `ORG_SELF` | `t.product_dept_org_code = #{orgCode}` | ✅ |
| `SELF_CREATED` | `t.created_by = #{empId}` | ✅（管理员视图非本机构时可用） |
| `SELF` | `t.{selfCol} = #{empId}` | ❌ |
| `SELF_ASSIGNED` | `t.{assigneeCol} = #{empId}` | ❌ |
| `WORKFLOW_PARTICIPANT` | `workflow 参与人` | ❌ |

**注意**：`SELF_CREATED` 和 `SELF` 是 common-dev-guide §5.1 明确区分的两个不同枚举值（一个看 `created_by`，一个看可配置的 `selfCol`）。portal 使用 `SELF_CREATED`，不是 `SELF`。

MyBatis XML 过滤片段：

```xml
<sql id="dataScopeFilter">
    <choose>
        <when test="dataScope.type == 'ALL'"/>
        <when test="dataScope.type == 'ORG_SUBTREE'">
            AND product_dept_org_code IN
            <foreach collection="dataScope.orgSubtree" item="o" open="(" close=")" separator=",">#{o}</foreach>
        </when>
        <when test="dataScope.type == 'ORG_SELF'">
            AND product_dept_org_code = #{dataScope.orgCode}
        </when>
        <when test="dataScope.type == 'SELF_CREATED'">
            AND created_by = #{dataScope.empId}
        </when>
        <otherwise>AND 1 = 0</otherwise>  <!-- Fail Close -->
    </choose>
</sql>
```

**Fail Close 原则**：未知 DataScope 类型时 `AND 1=0`。

### 3.4 Entity 字段映射

遵循 `docs/modules/portal-content-center/05-表结构DDL.md` 权威定义，本 spec 不重复字段清单。关键点：
- `product_info.status` 取值：`ACTIVE` / `DISABLED`
- `portal_shortcut.shortcut_type` 取值：`SYSTEM` / `CUSTOM`
- `addrbook_employee.status` 取值：`ACTIVE` / `RESIGNED`
- 所有 `deleted` 字段默认 `0`，逻辑删除更新为 `1`

---

## 4. 接口契约详情

**权威来源**：本章节的接口字段、URL、错误码严格对齐 `docs/modules/portal-content-center/03-接口设计与报文.md`。本 spec 只记录**范围决策**和**行为差异**，不重复字段清单。

### 4.1 D.3 `GET /api/products/support-available` — 支持中场支持的产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **业务逻辑**：`WHERE support_for_support_request = 1 AND status = 'ACTIVE' AND deleted = 0 ORDER BY product_name`
- **V1 范围决策**：**不实现 03 §D.3 提到的 `portal:product:support-available:all` 缓存**，V1 每次查库，后续视性能需要再加 Redis 缓存

### 4.2 D.1 `GET /api/products` — 产品列表

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="LIST")`
- **请求参数**：严格按 03 §D.1 的 `keyword / category / status / productDeptOrgCode / supportForSupportRequest / pageNo / pageSize`
- **响应**：`PageResult<ProductDTO>`
- **ProductDTO 字段**：严格按 03 §D.1 定义，包含 `productCategoryDesc`（字典翻译）、`productDeptOrgName`（机构名回填）、`responsibleEmps`（负责人简要列表）、`updatedByName`
- **DATA_SCOPE**：按 `product_dept_org_code`

### 4.3 D.2 `GET /api/products/{id}` — 产品详情

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **响应 ProductDetailDTO**：扩展 D.1 的 ProductDTO，额外包含 `description`、`fileDownloadUrl`（调 `FileApi.getDownloadUrl`）、`canEdit`（当前用户 orgCode == productDeptOrgCode 或管理员）
- **责任链路**：负责人回显走本地 `addrbook_employee` 表 JOIN
- **脱敏**：负责人手机号 `138****5678`
- **错误码**：`PORTAL-40003` 产品不存在

### 4.4 D.4 `POST /api/products` — 新增产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体**：严格按 03 §D.4 的 `ProductCreateReqDTO`
- **业务流程**：
  1. **写前校验（事务外）**：
     - 当前用户的 `orgCode` == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
     - `productCategory` 在 `PRODUCT_CATEGORY` 字典中存在，否则 `PORTAL-42200`（通用参数校验）
     - `productDeptOrgCode` 在 `EXT_ORG_INFO` 中存在（调 `OrgApi`）
     - `fileObjectId` 若提供，调 `FileApi.getFileInfo()` 验证存在，否则 `PORTAL-42203`（附件对象不存在）
     - `responsibleEmpIds`（若提供）中每个 empId 在 `addrbook_employee` 中存在且 `status=ACTIVE`，否则 `PORTAL-40902`（员工已离职）；列表长度 ≤ 10
  2. **事务内**：
     - INSERT `product_info`，依赖 `uk_product_code_deleted` 唯一约束兜底；捕获 `DuplicateKeyException` 转 `PORTAL-40901`
     - 写审计日志（`AuditApi`）
     - 若 `responsibleEmpIds` 非空：`ApplicationEventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(productId, removed=[], added=responsibleEmpIds, source=PRODUCT_SIDE))`
     - 事务提交
  3. **事务提交后**（`@TransactionalEventListener(phase = AFTER_COMMIT)`）：
     - `ProductResponsibleSyncListener` 对每个 added empId 更新 `addrbook_employee.responsible_product_ids`（追加 productId）
     - 使用乐观锁 `WHERE emp_id=? AND updated_time=?` 更新，失败重试 3 次（间隔 100/500/2000ms）
     - 所有重试失败后记录 ERROR 日志（V1 无 DLQ，待 governance 的事件总线就绪后升级）
- **响应**：`ApiResult<String>`（返回 productId）

### 4.5 D.5 `PUT /api/products/{id}` — 编辑产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体**：严格按 03 §D.5 的 `ProductUpdateReqDTO`
- **不可变字段**：`productCode` 和 `productDeptOrgCode` **禁止**修改（请求体中根本没有这两个字段）
- **业务流程**：
  1. **写前校验**：
     - `SELECT * FROM product_info WHERE id=? AND deleted=0`；不存在抛 `PORTAL-40003`
     - 当前用户 orgCode == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
     - 若修改 `productCategory`：字典校验
     - 若修改 `fileObjectId`：`FileApi.getFileInfo()` 校验
     - 若修改 `responsibleEmpIds`：每个 empId 校验存在 + 在职，长度 ≤ 10
     - 若修改 `supportForSupportRequest` 为 `false` 且有员工正在负责该产品：记 WARN 日志（03 §D.5 未要求阻塞，仅提示）
  2. **事务内**：
     - `SELECT ... FOR UPDATE` 锁定 `product_info` 行（确保 responsible_emp_ids 的 diff 计算与 UPDATE 原子）
     - 计算 `responsibleEmpIds` 的 `removed` 和 `added` 列表
     - UPDATE `product_info`
     - 写审计日志
     - 若 `responsibleEmpIds` 有变更：发布 `ProductResponsibleUpdatedEvent(productId, removed, added, source=PRODUCT_SIDE)`
     - 若 `fileObjectId` 有变更：`FileApi.detachBizRelation(旧 fileObjectId)` + `FileApi.attachBizRelation(新 fileObjectId)`
     - 清理 `portal:product:support-available:all` 缓存（若 V1 未实现缓存，则此步骤记 TODO 注释）
  3. **事务提交后**：同 D.4 的消费者逻辑，但对 removed 做删除、added 做追加
- **响应**：`ApiResult<Void>`

### 4.6 D.6 `DELETE /api/products/{id}` — 删除产品（逻辑删除）

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **业务流程**（严格按 06 §3.4 权威设计）：
  1. `SELECT * FROM product_info WHERE id=?`；不存在抛 `PORTAL-40003`
  2. 权限校验：当前用户 orgCode == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
  3. **前置引用检查**（事务内）：
     ```java
     int refCount = addrbookMapper.countEmployeesReferringProduct(productId);
     if (refCount > 0) {
         throw new BizException("PORTAL-40905",
             "仍有 " + refCount + " 名员工将该产品列为负责产品，请先清理");
     }
     ```
  4. **软删**：`UPDATE product_info SET deleted=1, status='DISABLED', updated_by=?, updated_time=NOW() WHERE id=?`
  5. 解除附件关联：`FileApi.detachBizRelation(fileObjectId, "PRODUCT", productId)`
  6. 写高危审计日志（level=HIGH）
  7. 事务提交后发布 `ProductResponsibleUpdatedEvent(productId, removed=原responsibleEmpIds, added=[], source=PRODUCT_SIDE)`（让监听器清理 addrbook 的反向引用）

**r2 新增错误码**：`PORTAL-40905` 产品仍被员工引用，不可删除。已计划在 §4.10 错误码清单登记。

### 4.7 D.7 `GET /api/products/export` — 产品导出

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="EXPORT")`（高危）
- **行为（V1 简化版本）**：
  1. `SELECT count(*)` 取精确命中行数
  2. **N ≤ 5000**：同步流式返回 Excel（EasyExcel）
  3. **N > 5000**：**V1 直接返回 `PORTAL-42207`**，错误消息："V1 暂不支持异步导出，当前命中 N 行超过同步阈值 5000，请增加过滤条件"
  4. **取消 r1 的 `PORTAL-42299` TODO 码**（凭空发明），全部走 03 §I 已有的 `PORTAL-42207`
- **列定义**：严格按 03 §H.7.1 的 10 列清单；**Phase 7 的 TDD 红用例必须按该清单生成 fixture**
- **脱敏**：负责人手机号列 `138****5678`
- **审计**：`AuditApi.log(EXPORT, bizType=PRODUCT, filter=..., rowCount=..., result=...)`

### 4.8 A.2 `GET /api/portal/shortcuts` — 查询快捷入口

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.2）
- **请求参数**：`shortcutType` 可选（SYSTEM/CUSTOM/ALL，默认 ALL）
- **业务逻辑**：`WHERE (shortcut_type='SYSTEM') OR (shortcut_type='CUSTOM' AND emp_id=#{currentEmpId}) ORDER BY shortcut_type DESC, sort_order ASC`

### 4.9 A.3 `PUT /api/portal/shortcuts` — 保存个性化快捷入口

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.3，仅操作本人记录）
- **请求体**：`ShortcutSaveReqDTO { shortcuts: List<ShortcutItemDTO> }`
- **ShortcutItemDTO 字段**（严格按 03 §A.3）：`shortcutName(@NotBlank @Size(max=100))` / `shortcutUrl(@NotBlank @Size(max=500))` / `shortcutIcon(@Size(max=100))` / `targetType(@NotBlank @Pattern("INTERNAL|EXTERNAL"))` / `sortOrder(@NotNull @Min(0))`
- **业务逻辑**：全量替换当前用户的 CUSTOM 类型记录（先 DELETE 后 INSERT，事务内）
- **响应**：`ApiResult<Void>`

### 4.10 A.1 `GET /api/portal/workspace` — 工作台数据聚合

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.1，天然按当前登录用户）
- **请求参数**：无
- **响应 WorkspaceRespDTO 字段（严格按 03 §A.1 权威定义的 7 个字段）**：

| 字段 | 类型 | 来源 | V1 实现 |
|---|---|---|---|
| `todoCount` | int | `WorkflowQueryApi.countPendingTasks(empId)` | ✅ 实现 |
| `unreadNotificationCount` | int | `NotifyApi.countUnread(empId)` | ✅ 实现 |
| `recentTodos` | `List<TodoItemDTO>` (最多 5 条) | `WorkflowQueryApi.listRecentPendingTasks(empId, 5)` | ✅ 实现（若 WorkflowQueryApi 无此方法则 TODO 注明） |
| `recentNotifications` | `List<NotificationItemDTO>` (最多 5 条) | `NotifyApi.queryNotifications(empId, false, Page.of(1,5))` | ✅ 实现 |
| `metricCards` | `List<MetricCardDTO>` | **MetricAdapter**（见下） | ✅ 实现（V1 返回空列表，performance 未实现时） |
| `shortcuts` | `List<ShortcutDTO>` | 内部调 `ShortcutService.listMy()` | ✅ 实现 |
| `aggregateErrors` | `Map<String,String>` | 每个子调用的失败错误码映射 | ✅ 实现 |

- **TodoItemDTO / NotificationItemDTO / MetricCardDTO / ShortcutDTO 字段清单**：严格对齐 03 §A.1 和 09 §4.1
- **并发编排**：`CompletableFuture.allOf(...)` + `orTimeout(2, SECONDS)` + 独立线程池 `portalAggregateExecutor`
- **每路超时**：
  - shortcuts: 100 ms（本地查询）
  - todos / notifications / metricCards：500 ms 各自
- **降级策略**：
  - 任一路失败/超时 → 该字段返回默认值（0 或空列表）
  - **失败信息写入 `aggregateErrors` Map**：key 是区域名（`"todos"` / `"notifications"` / `"metrics"` / `"shortcuts"`），value 是错误码或异常消息
  - `aggregateErrors` 为空表示全部成功；任一失败则非空，整体仍返回 200 OK
  - **不使用 r1 凭空发明的 `degraded: true` / `failedSources` 字段**

### 4.11 错误码清单（V1 使用的子集）

以 `docs/modules/portal-content-center/03-接口设计与报文.md §I` 为权威来源。本 spec 新增 1 个。

| code | HTTP | 消息（权威来源：03 §I） | 本切片触发 |
|---|---|---|---|
| `PORTAL-40003` | 400 | 产品不存在 | D.2 / D.5 / D.6 |
| `PORTAL-40302` | 403 | 无权维护非本机构产品 | D.4 / D.5 / D.6 |
| `PORTAL-40901` | 409 | 产品代码已存在 | D.4 |
| `PORTAL-40902` | 409 | 员工已离职 | D.4 / D.5（responsibleEmpIds 校验） |
| `PORTAL-40905` | 409 | **（r2 新增）** 产品仍被员工引用，不可删除 | D.6 |
| `PORTAL-42200` | 422 | 参数校验失败（通用） | 所有写接口 |
| `PORTAL-42203` | 422 | 附件对象不存在 | D.4 / D.5 |
| `PORTAL-42207` | 422 | 导出行数超过上限（V1 同步阈值 5000） | D.7 |
| `PORTAL-50002` | 500 | 导出文件生成失败 | D.7 |

**新增错误码 `PORTAL-40905` 的登记**：Phase 0/1 任务包含"在 `auth-permission-center` 的错误码枚举或 common-web 错误码配置中登记 `PORTAL-40905`"；若项目没有集中错误码枚举，则在 portal 模块的 `PortalErrorCodes` 常量类中声明。

### 4.12 事件发布（V1 本模块）

| 事件类 | 发布时机 | 消费者 | 备注 |
|---|---|---|---|
| `ProductResponsibleUpdatedEvent` | D.4 / D.5 / D.6 事务提交后 | `ProductResponsibleSyncListener`（本模块内） | V1 同进程，AFTER_COMMIT |

**事件字段**：`productId / removed(List<String>) / added(List<String>) / source(enum: PRODUCT_SIDE/ADDRBOOK_SIDE)`

**消费者行为**：
- 对每个 removed empId：从 `addrbook_employee.responsible_product_ids` 中移除 productId
- 对每个 added empId：追加 productId
- **乐观锁**：`WHERE emp_id=? AND updated_time=?`
- **重试策略**：失败重试 3 次（指数退避 100/500/2000 ms），全部失败记 ERROR 日志
- **防重入**：事件的 `source=PRODUCT_SIDE` 标记，消费者仅更新 addrbook，不发反向事件

---

## 4a. 关键偏离：V1 使用 AFTER_COMMIT 事件而非 outbox

### 4a.1 原设计（06 §2.4 / §3.3）

06 文档要求 **sys_event_outbox** 表驱动的事务消息模式：
1. 本地事务内 UPDATE 业务表 + INSERT outbox 记录
2. 异步 Worker 扫描 outbox 发布事件
3. 外部事件总线（Kafka / RocketMQ）分发
4. 事件消费者在独立事务内执行

### 4a.2 V1 偏离的理由

经核实：
- `common-dev-guide.md §7` 定义的是 **`@TransactionalEventListener(AFTER_COMMIT)`** 方案（进程内）
- **`sys_event_outbox` 表在 common 基础设施中尚未实现**（grep `sys_event_outbox` 在 common 模块零命中）
- **事件总线（Kafka / RocketMQ）在 V1 不引入**（本切片范围外）

### 4a.3 V1 决策

V1 采用 common-dev-guide §7 的 **`@TransactionalEventListener(AFTER_COMMIT)` + `ApplicationEventPublisher`** 进程内事件方案：

**优点**：
- 不依赖未就绪的 common 基础设施
- 与 governance 模块的事件实现模式一致（已在 governance-center 中运行）
- 事务边界清晰：主事务成功提交后才触发消费者
- 单进程内可靠性已足够 V1 场景

**局限（风险与补偿）**：
- 消费者失败时主事务无法回滚（因为已提交）
- 消费者自身重试 3 次后仍失败只能记 ERROR 日志
- **无持久化**：如果消费者执行期间进程崩溃，事件丢失

**V2 升级路径**：
- 当 common 的 `sys_event_outbox` 和 `OutboxWriter` 就绪后，切换到 outbox 模式
- 切换影响：Service 层把 `eventPublisher.publishEvent` 替换为 `outboxWriter.write`
- 消费者逻辑不变（依然是 Spring Bean 监听器）
- 测试代码需更新事务语义

### 4a.4 与 06 文档的冲突声明

**本 spec 的 §4.4 / §4.5 / §4.6 与 `06-并发与事务策略.md §2.4 / §3.3` 有冲突**。冲突部分由本 spec 的 V1 决策覆盖，理由如上。待 common 基础设施就绪后，**06 文档的 outbox 方案重新生效**，本 spec 的 §4a 应标记为 V2 升级的迁移点。

---

## 5. 跨模块依赖策略

### 5.1 已存在模块的 API（直接 `@Autowired`）

| API | 模块 | 用途 |
|---|---|---|
| `CurrentUserApi` | auth-permission-center | 获取当前用户 empId / orgCode |
| `DataScopeContext` | auth-permission-center | 获取当前 BizType 的 DataScope |
| `OrgApi` | auth-permission-center | 校验 orgCode 存在 |
| `BizScopeApi` | auth-permission-center | 写权限校验 `checkWritePermission` |
| `DictApi` | system-governance-center | 字典翻译 / 校验 |
| `FileApi` | system-governance-center | 附件 upload / getDownloadUrl / attachBizRelation / detachBizRelation |
| `NotifyApi` | system-governance-center | 未读数 / 通知列表 |
| `AuditApi` | system-governance-center | 审计日志写入 |
| `WorkflowQueryApi` | workflow-center | 查询待办任务（countPendingTasks / listRecentPendingTasks） |

**如果 `WorkflowQueryApi.listRecentPendingTasks(empId, limit)` 不存在**（只有 count）：Phase 0 任务要确认 workflow-center 的 `WorkflowQueryApi` 现有方法清单，若缺少则在 A.1 实现时 TODO 注释并在 `aggregateErrors` 中返回 `"recent_todos": "API not implemented"`。

### 5.2 未实现模块的 Adapter 模式（替代 r1 的 stub）

**不再使用 r1 的 `@Primary @ConditionalOnMissingBean` stub bean 方案**。改为 **09 §4.1 明示的 Adapter 包装模式**：

**MetricAdapter**（在 `portal.adapter` 包下）：

```java
@Service
public class MetricAdapter {
    @Autowired(required = false)
    private MetricApi metricApi;

    public List<MetricCardDTO> fetch(String empId) {
        if (metricApi == null) {
            return Collections.emptyList();  // performance 模块未实现时的降级
        }
        try {
            return metricApi.getUserMetricCards(empId);
        } catch (Exception ex) {
            log.warn("metricApi.getUserMetricCards failed empId={}", empId, ex);
            return Collections.emptyList();
        }
    }
}
```

**MetricCardDTO**：
- V1 在 `portal.adapter.dto.MetricCardDTO` 下定义，字段严格对齐 09 §4.1 的 11 字段
- 命名空间选择 `portal.adapter.dto` 是临时的，待 performance 模块创建真实 DTO 时，portal 改为 `import com.bank.branch.platform.performance.api.dto.MetricCardDTO`

**MetricApi 接口**：
- V1 **不在 portal 内定义** `MetricApi` 接口（因为 09 §4.1 说明 performance 模块负责定义）
- 使用 `@Autowired(required = false) MetricApi metricApi` 是 Spring 的标准机制，未实现时 bean 为 null
- 编译期要求：**必须有一个 MetricApi 类存在于 classpath**，否则 Spring 不知道注入哪个类型
  - **Phase 1 任务**：在 `portal.adapter` 包下定义一个最小的 `MetricApi` 接口（**只为编译通过**），3 个方法签名严格对齐 09 §4.1
  - 类头 javadoc 标注：`V1 临时占位接口，待 performance-engine-center 模块创建正式接口后，本接口删除，import 迁移到 performance.api.MetricApi`
  - 不提供任何 @Service 实现（bean 为 null，Adapter 返回空列表降级）

### 5.3 不需要 Stub 的确认

经核实 03 §A.1 的 WorkspaceRespDTO 字段，**A.1 工作台聚合不需要 `CustomerQueryApi`**。r1 凭空发明的 `touchTasks` 和 `getTouchTaskSummary` 在 03 文档中不存在，完全移除。

---

## 6. 测试策略

### 6.1 三层测试分工

| 层级 | 工具 | 数量预估 | 单次耗时 | 作用 |
|---|---|---|---|---|
| **Service 单测** | Mockito + JUnit 5 | 60-80 | 10-50 ms | 业务逻辑 / 状态机 / 字段校验 / 错误码 / 事件发布时机 |
| **Mapper 集成测试** | Testcontainers MySQL 8.0 | 20-30 | 50-200 ms | SQL 正确性 / JSON TypeHandler 往返 / DATA_SCOPE 过滤 |
| **Controller 集成测试** | MockMvc + `@WithMockEmpContext` | 15-25 | 100-500 ms | HTTP 报文契约 / @BizAuth 注解生效 / 异常映射 |
| **Listener 集成测试** | Spring + AFTER_COMMIT 验证 | 5-8 | 200-500 ms | `ProductResponsibleSyncListener` 在事务提交后被触发 |

**合计**：100-141 个测试，首次 ~45 秒（含容器启动），后续 ~30 秒。

### 6.2 Testcontainers 基类设计

```java
@Testcontainers
@SpringBootTest
@Sql(scripts = "/sql/portal-test-data.sql", executionPhase = BEFORE_TEST_METHOD)
public abstract class AbstractMapperIntegrationTest {

    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("onepl")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .withInitScript("sql/ddl-combined-for-portal.sql");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

**关键点**：
- `ddl-combined-for-portal.sql` 由 Phase 0 产出，**合并 `ddl-auth.sql + ddl-governance.sql + ddl-portal.sql`**，**去掉所有 `CREATE DATABASE` / `USE database` 语句**（Testcontainers 的 database 由 `.withDatabaseName()` 指定）
- `portal-test-data.sql` 是每个测试方法的 fixture 重置脚本
- `withReuse(true)` 让容器跨测试类复用

### 6.3 `@WithMockEmpContext` 自定义扩展

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ORG_SUBTREE";  // r2: 默认改为 ORG_SUBTREE 而非 ALL
    String[] orgSubtree() default {"ORG_SZ_001"};
}
```

**Extension 实现**：
- `BeforeEachCallback`：读取注解参数 → 设置 `SecurityContextHolder` + `DataScopeContextHolder`（ThreadLocal）
- `AfterEachCallback`：清理 ThreadLocal / SecurityContextHolder

**默认值 r2 修订理由**：reviewer 建议默认 `ALL` 会让测试漏掉 DATA_SCOPE 真实过滤分支，改为 `ORG_SUBTREE` 更能代表主要业务场景。

---

## 7. 开发计划（Phase 粒度）

按 `D.3 → D.1 → D.2 → D.4 → D.5 → D.6 → D.7 → A.2 → A.3 → A.1` 顺序分 10 个业务 Phase，加上 Phase 0/1/10 的基础设施共约 14-16 个 commit。详细任务列表由下一步 `writing-plans` 技能生成。

### 7.1 Phase 0 — 基础设施（关键任务清单）

| Task | 产出 |
|---|---|
| 0.1 创建 portal-content-center Maven 模块（pom.xml + 目录骨架） | 验证 `mvn compile` |
| 0.2 更新父 pom + bootstrap pom | |
| 0.3 创建 `portal.config.PortalMyBatisConfig` + `PortalAsyncConfig`（portalAggregateExecutor 线程池） | |
| 0.4 创建 `ddl-combined-for-portal.sql`（合并 auth + governance + portal DDL，去掉 CREATE DATABASE/USE 语句） | 放在 `src/test/resources/sql/` |
| 0.5 Testcontainers 基类 `AbstractMapperIntegrationTest`（smoke test：mvn test 验证 MySQL 启动 + DDL 执行成功） | |
| 0.6 `@WithMockEmpContext` 扩展 + `MockEmpContextExtension`（最小 Controller + 测试验证 context 注入） | |
| 0.7 `JsonStringListTypeHandler` + 单测（覆盖 null / "" / "[]" / 正常 4 种边界） | |
| 0.8 `PortalErrorCodes` 常量类（至少包含 V1 使用的 9 个错误码 + `PORTAL-40905` 新增） | |
| 0.9 **PT_RESOURCE 对齐 SQL**：产出 `docs/superpowers/sql/2026-04-11-portal-resources-align.sql`，补 3 条缺失资源：`RES_PORTAL_WORKSPACE`、`RES_PRODUCT_SUPPORT_AVAILABLE`、`RES_SHORTCUT_REPLACE_PUT` | 注意：A.1/A.2/A.3 虽然 03 文档不需要 @BizAuth，但 PT_RESOURCE 应补齐以便管理台展示 |

### 7.2 Phase 1 — Adapter 与占位接口

| Task | 产出 |
|---|---|
| 1.1 `portal.adapter.MetricApi` 接口定义（严格对齐 09 §4.1 的 3 个方法签名） | 类头 javadoc 标注 V1 临时占位 |
| 1.2 `portal.adapter.dto.MetricCardDTO`（对齐 09 §4.1 的 11 字段） | |
| 1.3 `portal.adapter.MetricAdapter`（`@Autowired(required=false)` + 降级逻辑） | MetricAdapter 单测验证 null bean 时返回空列表 |

### 7.3 Phase 2 — Entity / Mapper 基础

| Task | 产出 |
|---|---|
| 2.1 `ProductInfo` Entity + `ProductInfoMapper` + `ProductInfoMapper.xml` 基础 CRUD（insertProduct / selectById / softDelete） | Mapper 集成测试先写失败再实现 |
| 2.2 `PortalShortcut` Entity + `PortalShortcutMapper` + XML | |
| 2.3 `AddrbookEmployee` Entity + `AddrbookEmployeeMapper` + XML（含 `countEmployeesReferringProduct(productId)`） | 后者用于 D.6 前置检查 |
| 2.4 `ProductConverter` / `ShortcutConverter` + 单测 | |

### 7.4 Phase 3-9 — 业务接口 TDD 循环

每个 Phase 独立启动一个 subagent，严格按 TDD 红-绿-重构：

| Phase | 接口 | 关键测试点 |
|---|---|---|
| 3 | D.3 `GET /api/products/support-available` | Service 单测 + Mapper 集成 + Controller 集成 = 首个闭环 |
| 4 | D.1 `GET /api/products` | 3 种 DATA_SCOPE 过滤分支 |
| 5 | D.2 `GET /api/products/{id}` | 字典翻译 + 附件下载链接 + 负责人回显 + 脱敏 |
| 6 | D.4 `POST /api/products` | productCode 唯一冲突 + AFTER_COMMIT 事件 + Listener 测试 |
| 7 | D.5 `PUT /api/products/{id}` | FOR UPDATE 锁 + responsibleEmpIds diff + 不可变字段验证 |
| 8 | D.6 `DELETE /api/products/{id}` | 前置引用检查（PORTAL-40905） + 反向同步 |
| 9 | D.7 `GET /api/products/export` | 同步流式 Excel + N>5000 拒绝 + 10 列清单严格对齐 |

**subagent 复杂度警告**：D.4 / D.5 / D.6 涉及事件发布和消费者，建议每个 subagent 的任务范围限定在单个接口内，Listener 的实现由 D.4 的 subagent 首次创建，D.5 / D.6 复用。

### 7.5 Phase 10-11 — 快捷入口 + 工作台聚合

| Phase | 接口 |
|---|---|
| 10 | A.2 `GET /api/portal/shortcuts` + A.3 `PUT /api/portal/shortcuts`（一个 subagent 做两个接口，依赖简单） |
| 11 | A.1 `GET /api/portal/workspace`（最复杂，5 路 CompletableFuture + 降级） |

### 7.6 Phase 12 — 收尾

| Task | 产出 |
|---|---|
| 12.1 执行 `docs/superpowers/sql/2026-04-11-portal-resources-align.sql` 到测试库，验证 3 条新资源就位 | |
| 12.2 `mvn -pl bootstrap spring-boot:run`，smoke test 真实 HTTP 请求每个接口 | |
| 12.3 全量测试绿灯：`mvn test`（所有模块） | |
| 12.4 最后 commit `chore(portal): V1 首版 10 个接口完成` | |

### 7.7 Commit 粒度

每个 Phase 一个 commit，共约 14-16 个 commit。commit message 格式：

```
feat(portal): D.3 支持中场支持的产品查询

- ProductInfoMapper.listSupportAvailable 实现
- ProductService.listSupportAvailable 单测通过
- ProductController GET /api/products/support-available
- Mapper 集成测试覆盖 status/deleted/support 过滤

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

### 7.8 subagent 使用策略

- **Phase 0 / 1 / 2**：主 Claude 直接执行（涉及 Maven 骨架和基础组件，需要上下文连贯）
- **Phase 3-11（业务接口）**：每个 Phase 启动一个 subagent，严格 TDD 红-绿-重构
- **Phase 12**：主 Claude 收尾 + 启动 `superpowers:code-reviewer` 审查整体实现

---

## 8. 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | Testcontainers 首次下载 MySQL 镜像（~500 MB） | 首次测试慢 | 提前 `docker pull mysql:8.0.36` + `withReuse(true)` |
| R2 | 父 pom 添加 portal 模块触发全量 reactor 编译 | 无关干扰 | 使用 `mvn -pl portal-content-center` 限定范围 |
| R3 | `@BizAuth` 依赖 auth 模块的 Filter/Interceptor | Controller 集成测试鉴权不生效 | 参考 governance-center 的 Controller 集成测试配置 |
| R4 | `JsonStringListTypeHandler` 处理 null / `""` 的边界 | NPE | 单测覆盖 4 种边界 |
| R5 | AFTER_COMMIT 事件消费者在进程崩溃时丢失事件 | 双向同步数据不一致 | 记 ERROR 日志 + V2 切换到 outbox |
| R6 | A.1 聚合线程池 queue 满 | 高并发响应慢 | queue=32 + `CallerRunsPolicy` |
| R7 | MetricApi 接口占位类被替换时 import 迁移 | 小范围重构 | javadoc 明确 V1 临时 |
| R8 | workflow-center 的 `WorkflowQueryApi.listRecentPendingTasks` 可能不存在 | A.1 recentTodos 无法填充 | Phase 0 确认；如缺失则 aggregateErrors 返回 TODO 标记 |
| R9 | DATA_SCOPE `orgSubtree` 为空 | 可能返回全表 | XML 强制 `<otherwise>AND 1=0</otherwise>` fail close |
| R10 | `@WithMockEmpContext` 并行测试串数据 | 偶发测试失败 | 禁用测试并行（`junit.jupiter.execution.parallel.enabled=false`） |
| R11 | AFTER_COMMIT 消费者的乐观锁更新频繁失败 | 双向同步延迟 / 数据不一致 | 指数退避重试 3 次；失败记 ERROR；对 responsibleEmpIds 频繁变更的场景做限流（V2） |
| R12 | `ddl-combined-for-portal.sql` 合并时遗漏 `USE` / `CREATE DATABASE` 导致 Testcontainers 启动失败 | 测试无法启动 | Phase 0.4 任务必须包含"grep 搜索 CREATE DATABASE / USE 并人工确认删除"的验证步骤 |

---

## 9. 验收标准

- ✅ `mvn -pl portal-content-center compile` 成功
- ✅ `mvn -pl portal-content-center test` 全部通过
- ✅ Service 单测覆盖率 ≥ 70%（Jacoco）
- ✅ `mvn -pl bootstrap spring-boot:run` 启动成功，无 error 日志
- ✅ Smoke test：每个接口至少 1 个成功的 MockMvc 测试命中并返回 200
- ✅ 10 个接口的 commit 历史（feat/chore/fix 规范）
- ✅ PT_RESOURCE 对齐脚本 `2026-04-11-portal-resources-align.sql` 已生成并提交
- ✅ 现有 auth/governance/workflow 模块测试不被破坏（`mvn test` 全量通过）
- ✅ code-reviewer subagent 出具的 review 报告无 P0 级问题

---

## 10. 后续步骤

1. 本 spec 由 spec-document-reviewer 审查并由用户最终确认
2. 进入 `superpowers:writing-plans` 技能，基于本 spec 生成详细的任务级实现计划（每个 Phase 的 Task 列表、TDD 红-绿-重构顺序、验收 checkpoint）
3. 实现计划确认后，启动 `superpowers:subagent-driven-development` 执行
4. 每个 Phase 完成后 commit
5. 全部完成后，启动 `superpowers:code-reviewer` 进行最终审查
6. 根据 review 反馈修正，进入 `superpowers:finishing-a-development-branch` 流程

---

## 附录 A：决策一览

| # | 决策项 | 选择 |
|---|---|---|
| ① | 起步模块 | portal 子域 10 接口 |
| ② | TDD 测试层级 | Service 单测 + Mapper Testcontainers + Controller MockMvc |
| ③ | BPMN 缺失处理 | Mock WorkflowQueryApi，不阻塞 |
| ④ | commit 粒度 | 每个接口一个 commit |
| 问题 1 | A.1 跨模块依赖缺失 | 定义 API 占位 + **MetricAdapter 降级包装模式**（r2 修订） |
| 问题 2 | 占位接口位置 | `portal.adapter` 子包（r2 重命名自 r1 的 `portal.stub`） |
| 问题 3 | 负责人回显 | addrbook_employee 内部 Repository |
| 问题 4 | Mapper 集成测试数据库 | Testcontainers MySQL 8.0 |
| 问题 5 | 10 个接口开发顺序 | D.3 → D.1 → D.2 → D.4 → D.5 → D.6 → D.7 → A.2 → A.3 → A.1 |
| 问题 6 | 鉴权集成点 | 真实 @BizAuth + `@WithMockEmpContext` 扩展（默认 dataScope=ORG_SUBTREE） |

---

## 附录 B：与前置文档的偏离清单

| # | 前置文档 | 原定义 | V1 本 spec 决策 | 理由 |
|---|---|---|---|---|
| B1 | 06 §2.4 / §3.3 | outbox 事务消息模式 | `@TransactionalEventListener(AFTER_COMMIT)` 进程内事件 | `sys_event_outbox` 基础设施未就绪，V2 升级 |
| B2 | 03 §D.3 | 5 分钟 Redis 缓存 | V1 不实现缓存 | 性能需求验证后 V2 补 |
| B3 | 03 §D.5 | "编辑后清理 `portal:product:support-available:all` 缓存" | V1 缓存未引入，此步骤 TODO | 与 B2 同步 |
| B4 | 03 §I | 错误码清单不含 `PORTAL-40905` | V1 新增 `PORTAL-40905` 产品仍被员工引用 | 06 §3.4 明确需要此场景错误码，03 §I 未登记是文档不一致，本 spec 顺手修补 |
| B5 | 09 §4.1 | MetricApi 来自 performance-engine-center | 在 portal.adapter 包定义**占位接口**（编译期满足） | performance 模块未实现，V1 用 `@Autowired(required=false)` 降级 |
| B6 | seed-v1.sql §3.2 | 缺 3 条 portal 资源 | Phase 0 产出 `2026-04-11-portal-resources-align.sql` 补齐 | 与前置文档对齐的增量脚本 |

---

**文档结束**
